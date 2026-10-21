package local.myhome.marketresearch.contracts.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MarketResearch {
    private MarketResearch() {}
    public record Group(String id,String label,String query) {}
    public record Assignment(String listingId,String groupId,boolean usable) {}
    public record RefinementRequest(String seed,long resultCount,int maxPages,List<Map<String,Object>> listings,
                                    List<Group> groups,List<Assignment> assignments) {}
    public record FocusedSearch(String groupId,String query,long parentPages,boolean splitRequired) {}
    public record RefinementPlan(long estimatedParentPages,int sampled,int digital,int usable,
            BigDecimal usablePerRequest,List<FocusedSearch> searches,List<String> limitations) {}
    public record BusinessRequest(Instant observedAt,BigDecimal targetMonthlyUsd,List<Map<String,Object>> listings,
            List<Group> groups,List<Assignment> assignments,List<Map<String,Object>> shops,
            Map<String,Map<String,Object>> reviews,Map<String,Object> modelInterpretation) {}
    public record Comparable(String listingId,String shopId,BigDecimal priceUsd,Integer unitsNeededForTargetUsd,int observedReviews30d,
            boolean reviewSampleComplete,BigDecimal revenueAtTenPercentReviewRate,
            BigDecimal revenueAtTwoPercentReviewRate) {}
    public record Opportunity(String groupId,String label,int usableListings,int distinctShops,
            BigDecimal medianPriceUsd,Integer unitsNeededForTargetUsd,int shopsWithRecentReviews,
            Long observedShopLifetimeTransactions,BigDecimal verifiedMonthlyRevenueUsd,String evidenceBand,
            List<Comparable> comparables,List<String> caveats) {}
    public record BusinessReport(String methodologyVersion,BigDecimal targetMonthlyUsd,List<Opportunity> opportunities,
                                 Map<String,Object> modelInterpretation,List<String> limitations) {}
}
