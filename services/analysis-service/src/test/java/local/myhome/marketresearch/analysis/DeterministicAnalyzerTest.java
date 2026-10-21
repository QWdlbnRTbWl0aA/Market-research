package local.myhome.marketresearch.analysis;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.*;
import org.junit.jupiter.api.Test;

class DeterministicAnalyzerTest {
    @Test void deduplicatesFiltersPhysicalAndKeepsCurrenciesSeparate() {
        var a = new ListingEvidence("a","shop-a","Halloween","download",new BigDecimal("2"),"USD",null,0L);
        var b = new ListingEvidence("b","shop-a","Halloween","download",new BigDecimal("4"),"USD",10L,2L);
        var c = new ListingEvidence("c","shop-c","Halloween","download",new BigDecimal("100"),"EUR",0L,null);
        var physical = new ListingEvidence("p","shop-p","Banner","physical",BigDecimal.TEN,"USD",1L,0L);
        var report = DeterministicAnalyzer.analyze(new SimulationRequest(UUID.randomUUID(),"Halloween","FAST",List.of(a,a,b,c,physical)));
        assertEquals(4,report.sampleSize());
        assertEquals(3,report.digitalListings());
        assertEquals(2,report.distinctShops());
        assertEquals(new BigDecimal("3"),report.medianPrices().get("USD"));
        assertEquals(new BigDecimal("100"),report.medianPrices().get("EUR"));
        assertEquals("INSUFFICIENT_EVIDENCE",report.opportunityDecision());
    }
}
