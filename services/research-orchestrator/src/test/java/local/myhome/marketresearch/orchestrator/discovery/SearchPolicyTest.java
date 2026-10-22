package local.myhome.marketresearch.orchestrator.discovery;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class SearchPolicyTest {
    @Test void filteringIsStructuralNotABusinessScore() {
        var row=new HashMap<String,Object>(Map.of("listing_id",1,"shop_id",11,"title","A printable","listing_type","physical"));
        assertNull(SearchPolicy.listing(row));row.put("listing_type","download");
        var listing=SearchPolicy.listing(row);assertNotNull(listing);assertNull(listing.price());assertNull(listing.currency());
        row.put("price",Map.of("amount",300,"divisor",100,"currency_code","USD"));
        assertEquals(0,SearchPolicy.listing(row).price().compareTo(new java.math.BigDecimal("3")));
    }
    @Test void noveltyIsDistinctFromPageOrBudgetCaps() {
        assertTrue(SearchPolicy.lowNovelty(0,100));assertTrue(SearchPolicy.lowNovelty(9,100));
        assertFalse(SearchPolicy.lowNovelty(10,100));assertTrue(SearchPolicy.lowNovelty(0,0));
        assertEquals("jewish printables",SearchPolicy.normalize(" Jewish   printables "));
    }
    @Test void weeklyScanOnlyDetectsChangesAndDoesNotConcludeDemand() {
        var ids=java.util.stream.IntStream.range(1,26).mapToObj(Integer::toString).toList();
        var baseline=Map.<String,Object>of("count",100,"ids",ids,"medianUsd",3,"digitalShare",1);
        assertFalse((boolean)SearchPolicy.changes("q",baseline,baseline).get("significant"));
        var altered=new HashMap<>(baseline);altered.put("count",200);
        assertTrue((boolean)SearchPolicy.changes("q",baseline,altered).get("significant"));
        altered=new HashMap<>(baseline);altered.put("medianUsd",5);
        assertTrue((boolean)SearchPolicy.changes("q",baseline,altered).get("significant"));
        altered=new HashMap<>(baseline);altered.put("ids",List.of());
        assertTrue((boolean)SearchPolicy.changes("q",baseline,altered).get("significant"));
    }
}
