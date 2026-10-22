package local.myhome.marketresearch.contracts.content;

import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery;

public final class ProductDefinition {
    private ProductDefinition() {}
    public record Request(UUID runId,String query,Discovery.Product recommendation,List<Discovery.Listing> references) {}
    public record FileSpec(String filename,String format,String dimensions,int pagesOrItems,String contents) {}
    public record VisualSpec(String style,List<String> palette,String typography,String layout,List<String> constraints) {}
    public record Brief(String product,String audience,String useCase,List<FileSpec> files,VisualSpec visual,
        List<String> listingIds,String referenceUse,String commercialContext,List<String> acceptanceChecks,
        List<String> assumptions,List<String> unresolvedQuestions) {}
    public record View(UUID id,String status,String error,Brief brief,long nextAttemptAt) {
        public View(UUID id,String status,String error,Brief brief){this(id,status,error,brief,0);}
    }
}
