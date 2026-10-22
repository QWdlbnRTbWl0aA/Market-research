package local.myhome.marketresearch.contracts.content;

import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery;

public final class ProductDefinition {
    private ProductDefinition() {}
    public record Request(UUID runId,String query,Discovery.Product recommendation,List<Discovery.Listing> references) {}
    public record FileSpec(String filename,String format,String dimensions,int pagesOrItems,String contents) {}
    public record VisualSpec(String style,List<String> palette,String typography,String layout,List<String> constraints) {}
    public record Criterion(String file,String check,String expected,String method) {}
    public record Brief(String product,String audience,String useCase,List<FileSpec> files,VisualSpec visual,
        List<String> listingIds,String referenceUse,String commercialContext,List<String> acceptanceChecks,
        List<String> assumptions,List<String> unresolvedQuestions,List<Criterion> criteria,
        Boolean intentPreserved,String intentRationale,String readiness) {
        public Brief(String product,String audience,String useCase,List<FileSpec> files,VisualSpec visual,
                List<String> listingIds,String referenceUse,String commercialContext,List<String> acceptanceChecks,
                List<String> assumptions,List<String> unresolvedQuestions) {
            this(product,audience,useCase,files,visual,listingIds,referenceUse,commercialContext,acceptanceChecks,assumptions,
                unresolvedQuestions,files==null?List.of():files.stream().flatMap(f->java.util.stream.Stream.of(
                    new Criterion(f.filename(),"COUNT",Integer.toString(f.pagesOrItems()),"Inspect exported page/item count"),
                    new Criterion(f.filename(),"DIMENSIONS",f.dimensions(),"Inspect exported dimensions"))).toList(),
                true,"Preserves recommendation buyer and use case",unresolvedQuestions!=null&&unresolvedQuestions.isEmpty()?"READY":"REQUIRES_REVIEW");
        }
    }
    public record View(UUID id,String status,String error,Brief brief,long nextAttemptAt) {
        public View(UUID id,String status,String error,Brief brief){this(id,status,error,brief,0);}
    }
}
