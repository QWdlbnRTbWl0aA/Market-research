package local.myhome.marketresearch.archive;
import java.net.URI;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.AnalysisReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@RestController
@RequestMapping("/v1/research-reports")
public class ReportArchive {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public ReportArchive(JdbcTemplate db, ObjectMapper json) {
        this.db = db; this.json = json;
        db.execute("CREATE TABLE IF NOT EXISTS research_reports(run_id VARCHAR(36) PRIMARY KEY, report VARCHAR(1000000) NOT NULL)");
    }
    @PostMapping
    public synchronized ResponseEntity<AnalysisReport> archive(@RequestBody AnalysisReport report) {
        if (report.researchRunId() == null || report.source() == null || report.findings() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report provenance required");
        String payload = json.writeValueAsString(report);
        var rows = db.queryForList("SELECT report FROM research_reports WHERE run_id=?", report.researchRunId().toString());
        if (!rows.isEmpty() && !payload.equals(rows.getFirst().get("REPORT").toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run already has a different report");
        if (rows.isEmpty()) db.update("INSERT INTO research_reports VALUES(?,?)", report.researchRunId().toString(), payload);
        return ResponseEntity.created(URI.create("/v1/research-reports/" + report.researchRunId())).body(report);
    }
    @GetMapping("/{id}")
    public AnalysisReport retrieve(@PathVariable UUID id) {
        var rows = db.queryForList("SELECT report FROM research_reports WHERE run_id=?", id.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return json.readValue(rows.getFirst().get("REPORT").toString(), AnalysisReport.class);
    }
}
