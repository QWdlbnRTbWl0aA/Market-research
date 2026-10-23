package local.myhome.marketresearch.orchestrator.discovery;

import java.math.BigDecimal;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.Product;
import local.myhome.marketresearch.contracts.content.ProductDefinition.Brief;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Read-only projections of saved handoffs; never contacts workers or providers. */
@RestController
@RequestMapping("/v1/brief-library/runs")
public class BriefLibrary {
    public record Run(UUID id,String query,String status,int iteration,int availableBriefs,int pendingBriefs) {}
    public record Summary(UUID jobId,String opportunityId,String title,String audience,String readiness,int fileCount,int unresolvedCount) {}
    public record Reference(String listingId,String url,String title,BigDecimal price,String currency) {}
    public record Document(String formatVersion,UUID runId,int iteration,String query,UUID jobId,String opportunityId,
                           Brief brief,Product recommendation,List<Reference> references) {}
    public record Bundle(String formatVersion,UUID runId,int iteration,String query,List<Document> briefs) {}
    private final DiscoveryStore store;
    private final ObjectMapper json;

    public BriefLibrary(DiscoveryStore store,ObjectMapper json) { this.store=store;this.json=json; }

    @GetMapping public List<Run> runs() {
        return store.ids().stream().map(store::get).map(state -> new Run(state.id,state.query,state.status,state.iteration,
            available(state).size(),state.handoffs.size()-available(state).size()))
            .sorted(Comparator.comparingInt(Run::availableBriefs).reversed().thenComparing(run -> run.id().toString())).toList();
    }
    @GetMapping("/{runId}/briefs") public List<Summary> briefs(@PathVariable UUID runId) {
        return available(store.get(runId)).stream().map(handoff -> new Summary(handoff.job,
            handoff.request.recommendation().opportunityId(),handoff.brief.product(),handoff.brief.audience(),
            handoff.brief.readiness(),handoff.brief.files().size(),handoff.brief.unresolvedQuestions().size())).toList();
    }
    @GetMapping("/{runId}/briefs/{jobId}") public Document brief(@PathVariable UUID runId,@PathVariable UUID jobId) {
        var state=store.get(runId);
        var handoff=available(state).stream().filter(value -> jobId.equals(value.job)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Completed brief not found in this run"));
        return document(state,handoff);
    }
    @GetMapping("/{runId}/briefs/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID runId,@PathVariable UUID jobId,
                                          @RequestParam(defaultValue="json") String format) {
        var document=brief(runId,jobId);
        return switch(format) {
            case "json" -> attachment("brief-"+jobId+".json",MediaType.APPLICATION_JSON,
                json.writerWithDefaultPrettyPrinter().writeValueAsBytes(document));
            case "markdown" -> attachment("brief-"+jobId+".md",MediaType.parseMediaType("text/markdown;charset=UTF-8"),
                BriefMarkdown.render(document).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use json or markdown");
        };
    }
    @GetMapping("/{runId}/download") public ResponseEntity<byte[]> downloadAll(@PathVariable UUID runId) {
        var state=store.get(runId);
        var bundle=new Bundle("product-brief-bundle-v1",runId,state.iteration,state.query,
            available(state).stream().map(value -> document(state,value)).toList());
        return attachment("briefs-"+runId+".json",MediaType.APPLICATION_JSON,
            json.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle));
    }
    private List<DiscoveryState.Handoff> available(DiscoveryState state) {
        return state.handoffs.stream().filter(value -> "SUCCEEDED".equals(value.status)&&value.brief!=null&&value.job!=null).toList();
    }
    private Document document(DiscoveryState state,DiscoveryState.Handoff handoff) {
        var references=handoff.request.references().stream().filter(value -> handoff.brief.listingIds().contains(value.id()))
            .map(value -> new Reference(value.id(),"https://www.etsy.com/listing/"+value.id(),value.title(),value.price(),value.currency())).toList();
        return new Document("product-brief-v1",state.id,state.iteration,state.query,handoff.job,
            handoff.request.recommendation().opportunityId(),handoff.brief,handoff.request.recommendation(),references);
    }
    private ResponseEntity<byte[]> attachment(String filename,MediaType type,byte[] bytes) {
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString()).body(bytes);
    }
}
