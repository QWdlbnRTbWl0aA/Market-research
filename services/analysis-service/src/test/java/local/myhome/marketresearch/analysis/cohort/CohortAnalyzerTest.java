package local.myhome.marketresearch.analysis.cohort;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CohortAnalyzerTest {
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static Map<String,Object> listing(long id, String type, int amount, String currency) {
        Map<String,Object> row = new HashMap<>();
        row.put("listing_id", id); row.put("shop_id", id % 2 + 1);
        row.put("title", "Halloween Printable!");
        row.put("listing_type", type);
        row.put("price",Map.of("amount",amount,"divisor",100,"currency_code",currency));
        row.put("tags",List.of("Halloween", "HALLOWEEN", "printable"));
        row.put("views",0); row.put("num_favorers",0);
        row.put("original_creation_timestamp",NOW.minusSeconds(86400).getEpochSecond());
        row.put("is_personalizable",false); row.put("is_customizable",false);
        return row;
    }

    @Test void deduplicatesAndSeparatesDigitalPricesCurrenciesAndPhysicalListings() {
        var a = listing(1,"download",100,"USD");
        var b = listing(2,"download",300,"USD");
        var c = listing(3,"download",10000,"EUR");
        var physical = listing(4,"physical",99900,"USD");
        var unknown = listing(5,"unrecognized",100,"USD");
        var report = CohortAnalyzer.analyze("halloween",NOW,List.of(a,a,b,c,physical,unknown,Map.of()));
        assertEquals(7,report.received());
        assertEquals(1,report.invalidIdentifiers()); assertEquals(1,report.duplicates());
        assertEquals(5,report.uniqueListings()); assertEquals(3,report.digitalListings());
        assertEquals(1,report.physicalListings()); assertEquals(1,report.unknownTypeListings());
        assertEquals(0,new BigDecimal("2").compareTo(report.digitalPricesByCurrency().get("USD").median()));
        assertEquals(0,new BigDecimal("1.5").compareTo(report.digitalPricesByCurrency().get("USD").p25()));
        assertEquals(0,new BigDecimal("100").compareTo(report.digitalPricesByCurrency().get("EUR").median()));
        assertEquals(3,report.topTags().get("halloween")); // one vote per listing, not per repeated tag
        assertEquals(2,report.repeatedDigitalTitles());
        assertEquals(3,report.automationBuckets().get("STANDARD_DOWNLOAD"));
        assertEquals(2,report.digitalShops());
        assertEquals(0,new BigDecimal("0.555556").compareTo(report.shopHhi()));
    }

    @Test void preservesMissingnessAndZeroAndRejectsFutureAgeAndBadMoney() {
        var a = listing(1,"download",100,"USD");
        a.remove("views"); a.remove("shop_id"); a.remove("is_customizable");
        a.put("price",Map.of("amount",100,"divisor",0,"currency_code","USD"));
        a.put("original_creation_timestamp",NOW.plusSeconds(86400).getEpochSecond());
        var b = listing(2,"download",100,"USD");
        b.put("is_personalizable",true); b.put("num_favorers",-1);
        var report = CohortAnalyzer.analyze("halloween",NOW,List.of(a,b));
        assertEquals(1,report.digitalViews().known()); assertEquals(1,report.digitalViews().missing());
        assertEquals(1,report.zeroViewListings()); assertEquals(1,report.digitalFavorites().missing());
        assertEquals(1,report.missingDigitalShopIds()); assertEquals(1,report.invalidOrMissingAgeListings());
        assertEquals(1,report.missingPriceListings());
        assertEquals(1,report.automationBuckets().get("AUTOMATION_UNKNOWN"));
        assertEquals(1,report.automationBuckets().get("TEMPLATE_REVIEW"));
    }

    @Test void emptyDigitalCohortHasUnknownConcentrationAndNoCandidates() {
        var report = CohortAnalyzer.analyze("halloween",NOW,List.of(listing(1,"physical",100,"USD")));
        assertNull(report.largestShopShare()); assertNull(report.shopHhi());
        assertNull(report.digitalViews().median());
        assertTrue(report.experimentCandidates().isEmpty());
    }

    @Test void economicsChargesManualTimeAndDoesNotInventUnknownReceipts() {
        var costs = new ExperimentEconomics.Costs(new BigDecimal("0.10"),new BigDecimal("0.02"),
            new BigDecimal("0.01"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("5"),new BigDecimal("60"));
        var result = ExperimentEconomics.evaluate(costs,new BigDecimal("2"));
        assertEquals(0,new BigDecimal("5.13").compareTo(result.operatingCost()));
        assertEquals(0,new BigDecimal("-3.13").compareTo(result.expectedSurplus()));
        assertNull(ExperimentEconomics.evaluate(costs,null).expectedSurplus());
        assertNull(ExperimentEconomics.evaluate(costs,null).receiptsPerCost());
    }

    @Test void zeroCostDoesNotProduceInfiniteRatioAndNegativeCostsFail() {
        var zero = new ExperimentEconomics.Costs(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        assertNull(ExperimentEconomics.evaluate(zero,BigDecimal.ONE).receiptsPerCost());
        assertThrows(IllegalArgumentException.class, () -> ExperimentEconomics.evaluate(zero,new BigDecimal("-1")));
    }

    @Test void unknownHumanTimeCannotSilentlyBecomeZeroOperatingCost() {
        var unknown = new ExperimentEconomics.Costs(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,null,new BigDecimal("60"));
        var evaluation = ExperimentEconomics.evaluate(unknown,BigDecimal.TEN);
        assertNull(evaluation.operatingCost());
        assertNull(evaluation.expectedSurplus());
        assertNull(evaluation.receiptsPerCost());
    }
}
