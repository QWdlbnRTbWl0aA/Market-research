package local.myhome.marketresearch.orchestrator.discovery;
import java.net.URI;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/v1/discoveries")
public class DiscoveryController {
    public record Start(String query) {}
    private final DiscoveryFlow flow;private final DiscoveryNotices notices;private final DiscoveryStore store;
    public DiscoveryController(DiscoveryFlow flow,DiscoveryNotices notices,DiscoveryStore store){this.flow=flow;this.notices=notices;this.store=store;}
    @PostMapping public ResponseEntity<Map<String,Object>> start(@RequestHeader("Idempotency-Key") String key,@RequestBody Start request) {
        var state=flow.start(key,request.query());return ResponseEntity.accepted().location(URI.create("/v1/discoveries/"+state.id)).body(view(state));
    }
    @GetMapping public List<Map<String,Object>> list(){return store.ids().stream().map(flow::get).map(this::view).toList();}
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id){return view(flow.get(id));}
    @GetMapping("/{id}/results") public Map<String,Object> results(@PathVariable UUID id){return flow.snapshot(flow.get(id));}
    @PostMapping("/{id}/resume") public void resume(@PathVariable UUID id){flow.resume(id);}
    @PostMapping("/{id}/recheck-etsy-quota") public void recheckEtsyQuota(@PathVariable UUID id) throws Exception {flow.recheckEtsyQuota(id);}
    @PostMapping("/{id}/continue-search") public void continueSearch(@PathVariable UUID id){flow.continueSearch(id);}
    @PostMapping("/{id}/review-briefs") public void reviewBriefs(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){flow.reviewBriefs(id,key);}
    @PostMapping("/{id}/approve-trend") public void approve(@PathVariable UUID id){flow.approve(id);}
    @PostMapping("/{id}/dismiss-trend") public void dismiss(@PathVariable UUID id){flow.dismiss(id);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> cancel(@PathVariable UUID id){flow.cancel(id);return ResponseEntity.accepted().build();}
    @GetMapping("/notifications") public List<Map<String,Object>> notices(){return notices.list();}
    @PostMapping("/notifications/{key}/retry") public void retryNotice(@PathVariable String key){notices.retry(key);}
    private Map<String,Object> view(DiscoveryState s) {
        var m=new LinkedHashMap<String,Object>();m.put("id",s.id);m.put("query",s.query);m.put("status",s.status);
        m.put("searchStatus",s.searchStatus);m.put("error",s.error);m.put("listingCount",s.listings.size());
        m.put("workerErrors",s.workerErrors);
        m.put("coverage",SearchFrontier.coverage(s));
        m.put("samplingPageBudget",s.samplingPageBudget);
        m.put("analyzedCount",s.processed.stream().filter(id->!s.excluded.contains(id)).count());m.put("excludedCount",s.excluded.size());
        m.put("pendingListings",s.listings.keySet().stream().filter(id->!s.processed.contains(id)&&!s.excluded.contains(id)).count());
        m.put("nextSearchAttemptEpochMillis",s.nextSearchAttempt);
        m.put("queries",s.queries.stream().map(q->Map.of("query",q.text,"status",q.status,"pages",q.pages,
            "usablePerReturned",q.usableYield(),"parentUsablePerReturned",q.parentYield,
            "yieldImproved",q.usableYield()>q.parentYield,"novelListings",q.novel,
            "stopReason",Objects.requireNonNullElse(q.stopReason,""),
            "expansionStopReason",Objects.requireNonNullElse(q.expansionStopReason,""),
            "deferredQueries",q.deferredQueries)).toList());
        m.put("analysisJobs",s.batches.stream().map(batch->Map.of("job",Objects.toString(batch.job,""),"status",batch.status,
            "branch",Objects.toString(batch.branch,""),"listings",batch.ids.size(),"nextAttemptAt",batch.nextAttemptAt)).toList());
        m.put("productJobs",s.handoffs.stream().map(h->Map.of("key",h.key,"status",h.status,"job",Objects.toString(h.job,""))).toList());
        m.put("changes",s.changes);m.put("nextWatchEpochMillis",s.nextWatch);m.put("iteration",s.iteration);
        return m;
    }
}
