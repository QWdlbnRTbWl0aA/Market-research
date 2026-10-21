package local.myhome.marketresearch.analysis;
import java.net.URI;
import java.util.UUID;
import local.myhome.marketresearch.contracts.analysis.*;
import local.myhome.marketresearch.contracts.job.JobView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/v1/analysis-jobs")
public class AnalysisController {
    private final AnalysisJobs jobs;
    public AnalysisController(AnalysisJobs jobs) { this.jobs = jobs; }
    @PostMapping
    public ResponseEntity<JobView> submit(@RequestHeader("Idempotency-Key") String key, @RequestBody SimulationRequest request) {
        var id = jobs.submit(key, request);
        return ResponseEntity.accepted().location(URI.create("/v1/analysis-jobs/" + id)).body(jobs.get(id));
    }
    @GetMapping("/{id}") public JobView get(@PathVariable UUID id) { return jobs.get(id); }
    @GetMapping("/{id}/report") public AnalysisReport report(@PathVariable UUID id) { return jobs.report(id); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        jobs.cancel(id); return ResponseEntity.accepted().build();
    }
}
