package local.myhome.marketresearch.orchestrator;
import java.net.URI;
import java.util.UUID;
import local.myhome.marketresearch.contracts.analysis.AnalysisReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/v1/research-runs")
public class ResearchController {
    private final ResearchRuns runs;
    public ResearchController(ResearchRuns runs) { this.runs = runs; }
    @PostMapping
    public ResponseEntity<ResearchRuns.RunView> submit(@RequestHeader("Idempotency-Key") String key,
            @RequestBody ResearchRuns.SubmitRun request) {
        var run = runs.submit(key, request);
        return ResponseEntity.accepted().location(URI.create("/v1/research-runs/" + run.id())).body(run);
    }
    @GetMapping("/{id}") public ResearchRuns.RunView get(@PathVariable UUID id) { return runs.get(id); }
    @GetMapping("/{id}/report") public AnalysisReport report(@PathVariable UUID id) { return runs.report(id); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        runs.cancel(id); return ResponseEntity.accepted().build();
    }
}
