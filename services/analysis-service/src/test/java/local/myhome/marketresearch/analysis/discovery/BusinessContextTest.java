package local.myhome.marketresearch.analysis.discovery;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;

class BusinessContextTest {
    @Test void cohortEvidenceIncludesRelevantShopReviewsWithoutAttributingUnrelatedShops() {
        var listings=List.of(new Listing("1","11","Printable","Description",List.of(),BigDecimal.ONE,"USD",1,1,1));
        var relevant=Map.<String,Object>of("source","shop:11","listing_id",999,"rating",5);
        var direct=Map.<String,Object>of("listing_id",1,"rating",4);
        var unrelated=Map.<String,Object>of("source","shop:22","listing_id",888,"rating",5);
        assertEquals(List.of(relevant,direct),BusinessContext.reviews(listings,List.of(relevant,direct,unrelated)));
        var shop=Map.<String,Object>of("shop_id",11,"transaction_sold_count",100);
        assertEquals(List.of(shop),BusinessContext.shops(listings,List.of(shop,Map.of("shop_id",22))));
    }
    @Test void contextBoundsRepeatedReasoningWithoutChangingOpportunityIdentityOrCanonicalRecords() {
        String longText="Detailed operating assumptions. ".repeat(100);
        var product=new Product("Specific printable","Specific buyer","Specific need",List.of("Exactly described contents"),
            longText,BigDecimal.ONE,longText,longText,longText,"Holiday",List.of("1"),List.of(longText),"LOW",longText,longText).resolved("opportunity-id");
        var original=new Opportunity("opportunity-id",null,product,List.of("1","2"),3);
        var compact=BusinessContext.opportunities(List.of(original)).getFirst();
        assertEquals(original.id(),compact.id());assertEquals(original.evidenceIds(),compact.evidenceIds());
        assertEquals(original.revision(),compact.revision());
        assertEquals(product.differentiation(),compact.product().differentiation());
        assertEquals(product.deliverables(),compact.product().deliverables());
        assertEquals(product.audience(),compact.product().audience());assertEquals(product.useCase(),compact.product().useCase());
        assertTrue(compact.product().experiment().length()<200);assertEquals(longText,original.product().experiment());
        var summaries=BusinessContext.summaries(IntStream.range(0,12).mapToObj(index->index+longText).toList());
        assertEquals(6,summaries.size());assertTrue(summaries.getFirst().startsWith("6"));
        assertTrue(summaries.stream().allMatch(summary->summary.length()<650));
    }
}
