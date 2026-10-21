package local.myhome.marketresearch.analysis;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.*;
import local.myhome.marketresearch.contracts.job.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@EnableScheduling
public class AnalysisJobs {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public AnalysisJobs(JdbcTemplate db, ObjectMapper json) {
        this.db = db; this.json = json;
        db.execute("""
            CREATE TABLE IF NOT EXISTS analysis_jobs (
                id VARCHAR(36) PRIMARY KEY, idem VARCHAR(200) UNIQUE NOT NULL,
                request VARCHAR(1000000) NOT NULL, status VARCHAR(30) NOT NULL,
                due BIGINT NOT NULL, created BIGINT NOT NULL, updated BIGINT NOT NULL,
                report VARCHAR(1000000), error VARCHAR(100))
            """);
    }
    public synchronized UUID submit(String key, SimulationRequest request) {
        if (key.isBlank() || key.length() > 200 || request.researchRunId() == null
                || request.query() == null || request.query().isBlank()
                || request.listings() == null || request.listings().isEmpty()
                || !Set.of("FAST","SLOW","FAIL").contains(request.scenario())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation request");
        }
        String payload = json.writeValueAsString(request);
        var existing = db.queryForList("SELECT id, request FROM analysis_jobs WHERE idem=?", key);
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (!payload.equals(row.get("REQUEST").toString()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key has different input");
            return UUID.fromString(row.get("ID").toString());
        }
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        db.update("INSERT INTO analysis_jobs(id,idem,request,status,due,created,updated) VALUES(?,?,?,'ACCEPTED',?,?,?)",
                id.toString(), key, payload, now + ("SLOW".equals(request.scenario()) ? 30_000 : 500), now, now);
        return id;
    }
    public synchronized JobView get(UUID id) {
        var rows = db.queryForList("SELECT * FROM analysis_jobs WHERE id=?", id.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var row = rows.getFirst();
        var status = JobStatus.valueOf(row.get("STATUS").toString());
        return new JobView(id, status, status == JobStatus.SUCCEEDED ? 100 : 0, "SIMULATED_ANALYSIS",
                status == JobStatus.SUCCEEDED ? URI.create("/v1/analysis-jobs/" + id + "/report") : null,
                (String) row.get("ERROR"), row.get("ERROR") == null ? null : "Requested simulation failure",
                Instant.ofEpochMilli(((Number)row.get("CREATED")).longValue()),
                Instant.ofEpochMilli(((Number)row.get("UPDATED")).longValue()));
    }
    public synchronized AnalysisReport report(UUID id) {
        if (get(id).status() != JobStatus.SUCCEEDED) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return json.readValue(db.queryForObject("SELECT report FROM analysis_jobs WHERE id=?", String.class, id.toString()), AnalysisReport.class);
    }
    public synchronized void cancel(UUID id) {
        get(id);
        db.update("UPDATE analysis_jobs SET status='CANCELLATION_REQUESTED',updated=? WHERE id=? AND status IN ('ACCEPTED','RUNNING')",
                System.currentTimeMillis(), id.toString());
    }
    @Scheduled(fixedDelay = 250)
    public synchronized void tick() {
        long now = System.currentTimeMillis();
        db.update("UPDATE analysis_jobs SET status='CANCELLED',updated=? WHERE status='CANCELLATION_REQUESTED'", now);
        db.update("UPDATE analysis_jobs SET status='RUNNING',updated=? WHERE status='ACCEPTED'", now);
        for (var row : db.queryForList("SELECT id, request FROM analysis_jobs WHERE status='RUNNING' AND due<=?", now)) {
            var request = json.readValue(row.get("REQUEST").toString(), SimulationRequest.class);
            if ("FAIL".equals(request.scenario())) {
                db.update("UPDATE analysis_jobs SET status='FAILED',error='SIMULATED_FAILURE',updated=? WHERE id=?", now, row.get("ID"));
            } else {
                var report = json.writeValueAsString(DeterministicAnalyzer.analyze(request));
                db.update("UPDATE analysis_jobs SET status='SUCCEEDED',report=?,updated=? WHERE id=?", report, now, row.get("ID"));
            }
        }
    }
}
