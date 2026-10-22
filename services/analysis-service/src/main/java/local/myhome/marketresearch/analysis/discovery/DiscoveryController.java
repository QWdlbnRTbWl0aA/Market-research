package local.myhome.marketresearch.analysis.discovery;
import java.net.URI;
import java.util.UUID;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/v1/discovery-jobs")
public class DiscoveryController {
    private final DiscoveryJobs jobs;
    public DiscoveryController(DiscoveryJobs jobs){this.jobs=jobs;}
    @PostMapping public ResponseEntity<TaskView> submit(@RequestHeader("Idempotency-Key") String key,@RequestBody Task task) {
        var job=jobs.submit(key,task);return ResponseEntity.accepted().location(URI.create("/v1/discovery-jobs/"+job.id())).body(job);
    }
    @GetMapping("/{id}") public TaskView get(@PathVariable UUID id){return jobs.get(id);}
    @DeleteMapping("/{id}") public void cancel(@PathVariable UUID id){jobs.cancel(id);}
    @PostMapping("/{id}/resume") public void resume(@PathVariable UUID id){jobs.resume(id);}
}
