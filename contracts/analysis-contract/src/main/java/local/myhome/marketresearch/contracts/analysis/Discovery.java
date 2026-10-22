package local.myhome.marketresearch.contracts.analysis;

import java.math.BigDecimal;
import java.util.*;

/** Versioned HTTP contracts; observed facts remain separate from model interpretations. */
public final class Discovery {
    private Discovery() {}
    public static final String VERSION="discovery-v1";
    public record Listing(String id,String shopId,String title,String description,List<String> tags,
        BigDecimal price,String currency,long views,long favorites,long createdAt) {}
    public record Task(String kind,String query,List<Listing> listings,List<Map<String,Object>> shops,
        List<String> previousFamilies,List<String> previousSummaries) {}
    public record Refinement(List<String> queries,List<String> families,String reason,List<String> excludedListingIds) {
        public Refinement {if(excludedListingIds==null)excludedListingIds=List.of();}
        public Refinement(List<String> queries,List<String> families,String reason){this(queries,families,reason,List.of());}
    }
    public record Product(String name,String audience,String useCase,List<String> deliverables,
        String differentiation,BigDecimal proposedPriceUsd,String recurringRevenueHypothesis,
        String automationPlan,String manualWorkAssumptions,String seasonality,List<String> listingIds,
        List<String> counterevidence,String confidence,String confidenceExplanation,String experiment) {}
    public record Assessment(String summary,List<String> families,List<Product> products,List<String> limitations,
        Boolean newInsights,String marginalValueReason) {
        public Assessment(String summary,List<String> families,List<Product> products,List<String> limitations) {
            this(summary,families,products,limitations,true,"Insight stopping was not assessed by this fixture or legacy caller");
        }
    }
    public record TaskView(UUID id,String kind,String status,String error,int newListings,int cachedListings,
        Refinement refinement,Assessment assessment,long nextAttemptAt) {
        public TaskView(UUID id,String kind,String status,String error,int newListings,int cachedListings,Refinement refinement,Assessment assessment) {
            this(id,kind,status,error,newListings,cachedListings,refinement,assessment,0);
        }
    }
    public record Snapshot(UUID runId,String query,List<Map<String,Object>> queries,
        List<Assessment> assessments,List<UUID> productJobs,String finishedAt,String protocol) {}
}
