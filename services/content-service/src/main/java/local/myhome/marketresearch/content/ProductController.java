package local.myhome.marketresearch.content;
import java.net.URI;
import java.util.UUID;
import local.myhome.marketresearch.contracts.content.ProductDefinition.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/v1/product-definition-jobs")
public class ProductController {
    private final ProductJobs jobs;
    public ProductController(ProductJobs jobs){this.jobs=jobs;}
    @PostMapping public ResponseEntity<View> submit(@RequestHeader("Idempotency-Key") String key,@RequestBody Request request) {
        var job=jobs.submit(key,request);return ResponseEntity.accepted().location(URI.create("/v1/product-definition-jobs/"+job.id())).body(job);
    }
    @GetMapping("/{id}") public View get(@PathVariable UUID id){return jobs.get(id);}
    @GetMapping("/{id}/evidence") public java.util.Map<String,Object> evidence(@PathVariable UUID id){return jobs.evidence(id);}
    @DeleteMapping("/{id}") public void cancel(@PathVariable UUID id){jobs.cancel(id);}
    @PostMapping("/{id}/resume") public void resume(@PathVariable UUID id){jobs.resume(id);}
    @PostMapping("/{id}/review") public ResponseEntity<View> review(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(jobs.reviewAgain(id,key));
    }
}
