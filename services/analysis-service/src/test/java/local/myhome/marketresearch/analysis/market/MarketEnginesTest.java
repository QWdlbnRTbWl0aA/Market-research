package local.myhome.marketresearch.analysis.market;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;

class MarketEnginesTest {
    private static final Instant NOW=Instant.parse("2026-10-21T00:00:00Z");
    static Map<String,Object> listing(long id,long shop,int usd) {
        return Map.of("listing_id",id,"shop_id",shop,"listing_type","download",
            "price",Map.of("amount",usd*100,"divisor",100,"currency_code","USD"));
    }
    static BusinessRequest evidence(Map<String,Object> review) {
        return new BusinessRequest(NOW,new BigDecimal("30"),List.of(listing(1,11,2),listing(2,12,30)),
            List.of(new Group("games","Halloween games","halloween printable games")),
            List.of(new Assignment("1","games",true),new Assignment("2","games",true)),
            List.of(Map.of("shop_id",11,"transaction_sold_count",100000),Map.of("shop_id",12,"transaction_sold_count",900)),
            Map.of("1",review,"2",review),Map.of());
    }
    @Test void splitsHugeSearchWithoutEnumeratingAndCountsUsableDigitalYield() {
        var request=new RefinementRequest("halloween printable",1_000_000,20,
            List.of(listing(1,11,2),Map.of("listing_id",2,"listing_type","physical")),
            List.of(new Group("games","Games","halloween classroom games"),new Group("bad","Bad","unrelated")),
            List.of(new Assignment("1","games",true),new Assignment("2","games",true)));
        var plan=SearchRefiner.refine(request);
        assertEquals(10000,plan.estimatedParentPages());assertEquals(1,plan.usable());
        assertEquals(1,plan.searches().size());assertTrue(plan.searches().getFirst().splitRequired());
    }
    @Test void cheapAndExpensiveItemsCanBothMeetSameModestTarget() {
        var request=evidence(Map.of("count",1,"results",List.of(Map.of("transaction_id",9,"create_timestamp",NOW.minusSeconds(86400).getEpochSecond()))));
        var opportunity=BusinessAnalyzer.analyze(request).opportunities().getFirst();
        assertNull(opportunity.verifiedMonthlyRevenueUsd());
        assertEquals(100900L,opportunity.observedShopLifetimeTransactions());
        assertEquals(2,opportunity.shopsWithRecentReviews());
        var cheap=opportunity.comparables().stream().filter(c->c.listingId().equals("1")).findFirst().orElseThrow();
        var expensive=opportunity.comparables().stream().filter(c->c.listingId().equals("2")).findFirst().orElseThrow();
        assertEquals(15,cheap.unitsNeededForTargetUsd());assertEquals(1,expensive.unitsNeededForTargetUsd());
        assertEquals(0,new BigDecimal("20").compareTo(cheap.revenueAtTenPercentReviewRate()));
        assertEquals(0,new BigDecimal("100").compareTo(cheap.revenueAtTwoPercentReviewRate()));
        assertEquals(0,new BigDecimal("300").compareTo(expensive.revenueAtTenPercentReviewRate()));
        assertEquals("MULTI_SHOP_RECENT_PURCHASE_PROXY",opportunity.evidenceBand());
    }
    @Test void absentOrOldReviewsDoNotBecomeZeroRevenueAndCappedPagesAreExplicit() {
        var old=Map.<String,Object>of("count",1000,"results",List.of(Map.of("create_timestamp",NOW.minusSeconds(40*86400L).getEpochSecond())));
        var report=BusinessAnalyzer.analyze(evidence(old));
        var comparable=report.opportunities().getFirst().comparables().getFirst();
        assertFalse(comparable.reviewSampleComplete());assertEquals(0,comparable.observedReviews30d());
        assertNull(comparable.revenueAtTenPercentReviewRate());assertNull(report.opportunities().getFirst().verifiedMonthlyRevenueUsd());
    }
    @Test void unknownAndDuplicateClassificationIdsAreRejected() {
        var request=evidence(Map.of());
        var bad=new BusinessRequest(request.observedAt(),request.targetMonthlyUsd(),request.listings(),request.groups(),
            List.of(new Assignment("999","games",true)),request.shops(),request.reviews(),Map.of());
        assertThrows(IllegalArgumentException.class,()->BusinessAnalyzer.analyze(bad));
        var repeated=new BusinessRequest(request.observedAt(),request.targetMonthlyUsd(),request.listings(),request.groups(),
            List.of(new Assignment("1","games",true),new Assignment("1","games",true)),request.shops(),request.reviews(),Map.of());
        assertThrows(IllegalArgumentException.class,()->BusinessAnalyzer.analyze(repeated));
    }
    @Test void nonUsdPriceIsNotComparedToUsdTarget() {
        assertNull(BusinessAnalyzer.usdPrice(Map.of("price",Map.of("amount",200,"divisor",100,"currency_code","EUR"))));
        assertNull(BusinessAnalyzer.usdPrice(Map.of("price",Map.of("amount",200,"divisor",0,"currency_code","USD"))));
    }
    @Test void fabricatedModelRevenueFieldsAreRejected() {
        var request=evidence(Map.of());
        var bad=new BusinessRequest(request.observedAt(),request.targetMonthlyUsd(),request.listings(),request.groups(),
            request.assignments(),request.shops(),request.reviews(),Map.of("verifiedRevenue",1000));
        assertThrows(IllegalArgumentException.class,()->BusinessAnalyzer.analyze(bad));
    }
    @Test void rejectsProseAsSearchKeywords() {
        var plan=SearchRefiner.refine(new RefinementRequest("halloween printable",100000,20,List.of(),
            List.of(new Group("games","Games","halloween printable games, activities and classroom use; digital downloads")),List.of()));
        assertTrue(plan.searches().isEmpty());
    }
    @Test void modelConfidenceIsCappedWithoutIndependentRecentPurchaseProxies() {
        var request=evidence(Map.of());
        var recommendation=new LinkedHashMap<String,Object>(Map.of("groupId","games","decision","TEST","confidence","MEDIUM",
            "rationale","Cheap experiment, unknown conversion","differentiator","Specific audience","automationPlan","Reusable template",
            "risks",List.of("Seasonal"),"citedListingIds",List.of("1"),"citedShopIds",List.of("11")));
        var completed=new BusinessRequest(request.observedAt(),request.targetMonthlyUsd(),request.listings(),request.groups(),
            request.assignments(),request.shops(),request.reviews(),Map.of("recommendations",List.of(recommendation)));
        var report=BusinessAnalyzer.analyze(completed);
        var rows=(List<?>)report.modelInterpretation().get("recommendations");
        assertEquals("LOW",((Map<?,?>)rows.getFirst()).get("confidence"));
        recommendation.put("decision",null);
        assertThrows(IllegalArgumentException.class,()->BusinessAnalyzer.analyze(completed));
    }
}
