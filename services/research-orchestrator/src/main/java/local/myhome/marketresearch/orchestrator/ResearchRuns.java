package local.myhome.marketresearch.orchestrator;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@EnableScheduling
public class ResearchRuns {
    public record SubmitRun(String query, String scenario) {}
    public record RunView(UUID id, String query, String scenario, String status, UUID analysisJobId,
                          String errorCode, int retryCount, long nextPollAt) {}
    private final JdbcTemplate db;
    private final RestClient analysis;
    private final RestClient archive;
    private final boolean workersEnabled;

    public ResearchRuns(JdbcTemplate db,
            @Value("${market-research.services.analysis-base-url}") String analysisUrl,
            @Value("${market-research.services.archive-base-url}") String archiveUrl,
            @Value("${research.discovery.workers-enabled:true}") boolean workersEnabled) {
        this.db = db;
        this.workersEnabled = workersEnabled;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        analysis = RestClient.builder().baseUrl(analysisUrl).requestFactory(factory).build();
        archive = RestClient.builder().baseUrl(archiveUrl).requestFactory(factory).build();
        db.execute("""
            CREATE TABLE IF NOT EXISTS research_runs (
                id VARCHAR(36) PRIMARY KEY, idem VARCHAR(200) UNIQUE NOT NULL,
                query VARCHAR(300) NOT NULL, scenario VARCHAR(20) NOT NULL,
                status VARCHAR(30) NOT NULL, job_id VARCHAR(36),
                next_poll BIGINT NOT NULL, retries INT NOT NULL DEFAULT 0,
                error VARCHAR(100), deadline BIGINT NOT NULL)
            """);
    }

    public synchronized RunView submit(String key, SubmitRun request) {
        String scenario = request.scenario() == null ? "FAST" : request.scenario();
        if (key.isBlank() || key.length() > 200 || request.query() == null
                || request.query().isBlank() || request.query().length() > 300
                || !Set.of("FAST","SLOW","FAIL").contains(scenario))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid research request");
        String query = request.query().trim();
        var rows = db.queryForList("SELECT id, query, scenario FROM research_runs WHERE idem=?", key);
        if (!rows.isEmpty()) {
            var row = rows.getFirst();
            if (!query.equals(row.get("QUERY")) || !scenario.equals(row.get("SCENARIO")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key has different input");
            return get(UUID.fromString(row.get("ID").toString()));
        }
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        db.update("INSERT INTO research_runs(id,idem,query,scenario,status,next_poll,deadline) VALUES(?,?,?,?,'ACCEPTED',?,?)",
                id.toString(), key, query, scenario, now, now + Duration.ofHours(4).toMillis());
        return get(id);
    }

    public synchronized RunView get(UUID id) {
        var rows = db.queryForList("SELECT * FROM research_runs WHERE id=?", id.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var row = rows.getFirst();
        return new RunView(id, (String)row.get("QUERY"), (String)row.get("SCENARIO"),
            (String)row.get("STATUS"), row.get("JOB_ID") == null ? null : UUID.fromString(row.get("JOB_ID").toString()),
            (String)row.get("ERROR"), ((Number)row.get("RETRIES")).intValue(), ((Number)row.get("NEXT_POLL")).longValue());
    }

    public synchronized void cancel(UUID id) {
        get(id);
        db.update("UPDATE research_runs SET status='CANCELLATION_REQUESTED',next_poll=?,retries=0 WHERE id=? AND status IN ('ACCEPTED','ANALYZING','ARCHIVING')",
                System.currentTimeMillis(), id.toString());
    }

    public synchronized AnalysisReport report(UUID id) {
        if (!"SUCCEEDED".equals(get(id).status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is not ready");
        try {
            return archive.get().uri("/v1/research-reports/" + id).retrieve().body(AnalysisReport.class);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Archive unavailable");
        }
    }

    @Scheduled(fixedDelay = 250)
    public synchronized void tick() {
        if (!workersEnabled) return;
        long now = System.currentTimeMillis();
        for (var row : db.queryForList("""
                SELECT id,deadline FROM research_runs WHERE status IN
                ('ACCEPTED','ANALYZING','ARCHIVING','CANCELLATION_REQUESTED') AND next_poll<=?
                """, now)) {
            UUID id = UUID.fromString(row.get("ID").toString());
            var run = get(id);
            if (((Number)row.get("DEADLINE")).longValue() < now) {
                fail(id, "WORKFLOW_DEADLINE_EXCEEDED");
                continue;
            }
            try {
                advance(run);
            } catch (RestClientResponseException error) {
                if (error.getStatusCode().is4xxClientError() && error.getStatusCode().value() != 429) {
                    fail(id, "DEPENDENCY_HTTP_" + error.getStatusCode().value());
                } else retry(run);
            } catch (RestClientException error) {
                retry(run);
            } catch (IllegalArgumentException | IllegalStateException error) {
                fail(id, "INVALID_DEPENDENCY_RESPONSE");
            }
        }
    }

    private void advance(RunView run) {
        UUID id = run.id();
        switch (run.status()) {
            case "ACCEPTED" -> {
                var request = new SimulationRequest(id, run.query(), run.scenario(), fixtures());
                var response = analysis.post().uri("/v1/analysis-jobs").header("Idempotency-Key", id.toString())
                    .body(request).retrieve().body(Map.class);
                if (response == null || response.get("jobId") == null) throw new IllegalStateException();
                db.update("UPDATE research_runs SET job_id=? WHERE id=?", response.get("jobId").toString(), id.toString());
                transition(id, "ANALYZING");
            }
            case "ANALYZING" -> {
                var response = analysis.get().uri("/v1/analysis-jobs/" + run.analysisJobId()).retrieve().body(Map.class);
                if (response == null || response.get("status") == null) throw new IllegalStateException();
                switch (response.get("status").toString()) {
                    case "SUCCEEDED" -> transition(id, "ARCHIVING");
                    case "FAILED" -> fail(id, "ANALYSIS_FAILED");
                    case "CANCELLED" -> transition(id, "CANCELLED");
                    case "ACCEPTED", "RUNNING", "CANCELLATION_REQUESTED" -> transition(id, "ANALYZING");
                    default -> throw new IllegalStateException();
                }
            }
            case "ARCHIVING" -> {
                var report = analysis.get().uri("/v1/analysis-jobs/" + run.analysisJobId() + "/report")
                    .retrieve().body(AnalysisReport.class);
                if (report == null || !id.equals(report.researchRunId())) throw new IllegalStateException();
                archive.post().uri("/v1/research-reports").body(report).retrieve().toBodilessEntity();
                transition(id, "SUCCEEDED");
            }
            case "CANCELLATION_REQUESTED" -> {
                if (run.analysisJobId() != null)
                    analysis.delete().uri("/v1/analysis-jobs/" + run.analysisJobId()).retrieve().toBodilessEntity();
                transition(id, "CANCELLED");
            }
            default -> throw new IllegalStateException();
        }
    }

    private void transition(UUID id, String status) {
        db.update("UPDATE research_runs SET status=?,next_poll=?,retries=0,error=NULL WHERE id=?",
                status, System.currentTimeMillis() + 500, id.toString());
    }
    private void retry(RunView run) {
        int count = run.retryCount() + 1;
        if (count >= 6) { fail(run.id(), "DEPENDENCY_RETRIES_EXHAUSTED"); return; }
        db.update("UPDATE research_runs SET retries=?,next_poll=?,error='DEPENDENCY_UNAVAILABLE' WHERE id=?",
                count, System.currentTimeMillis() + Math.min(30_000, 500L << count), run.id().toString());
    }
    private void fail(UUID id, String code) {
        db.update("UPDATE research_runs SET status='FAILED',error=? WHERE id=?", code, id.toString());
    }

    // Deliberately synthetic and stable: replaying after a crash sends identical input.
    static List<ListingEvidence> fixtures() {
        return List.of(
            new ListingEvidence("fixture-1","shop-a","Halloween classroom bingo","download",new BigDecimal("3.00"),"USD",100L,8L),
            new ListingEvidence("fixture-2","shop-b","Halloween scavenger hunt","download",new BigDecimal("5.00"),"USD",200L,12L),
            new ListingEvidence("fixture-3","shop-c","Halloween printable clues","download",new BigDecimal("4.00"),"EUR",null,0L),
            new ListingEvidence("fixture-4","shop-a","Physical Halloween banner","physical",new BigDecimal("9.00"),"USD",50L,1L));
    }
}
