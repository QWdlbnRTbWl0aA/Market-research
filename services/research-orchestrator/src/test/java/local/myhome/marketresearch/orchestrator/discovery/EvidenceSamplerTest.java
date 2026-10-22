package local.myhome.marketresearch.orchestrator.discovery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import local.myhome.marketresearch.contracts.analysis.Discovery.Listing;
import local.myhome.marketresearch.tools.ProviderAccess;

class EvidenceSamplerTest {
    @Test void shopSampleSpansEngagementRatherThanTakingFirstRankedShops() {
        var listings=java.util.stream.IntStream.rangeClosed(1,10).mapToObj(id->listing(id,id*10)).toList();
        assertEquals(List.of("1","6","10"),EvidenceSampler.shops(listings,3));
    }
    Listing listing(int id,long favorites){return new Listing(""+id,""+id,"TEST_FIXTURE","Contents",List.of(),BigDecimal.ONE,"USD",1,favorites,1);}
    @Test void reviewEvidenceIsBoundedDatedAndDoesNotExposeBuyerIdentifiers() {
        var records=EvidenceSampler.reviews(Map.of("results",List.of(Map.of("listing_id",1,"review","Purchased and used",
            "rating",5,"create_timestamp",100,"buyer_user_id","not-for-models"))),"shop:1");
        assertEquals(1,records.size());assertFalse(records.getFirst().containsKey("buyer_user_id"));
        assertTrue(records.getFirst().containsKey("evidenceId"));assertTrue(records.getFirst().containsKey("observedAt"));
        assertTrue(EvidenceSampler.reviews(Map.of("results",List.of()),"listing:1").isEmpty());
    }
    @Test void enrichmentRespectsProbeBudgetsAndNeverRetriesEmptyReviews() throws Exception {
        var provider=mock(ProviderAccess.class);
        when(provider.shop(anyString())).thenAnswer(call->Map.of("shop_id",Integer.parseInt(call.getArgument(0)),
            "transaction_sold_count",100,"created_timestamp",1));
        when(provider.shopReviews(anyString())).thenReturn(Map.of("results",List.of()));
        when(provider.listingReviews(anyString())).thenReturn(Map.of("results",List.of(Map.of("listing_id",1,"review","Used in class"))));
        var settings=new MockEnvironment().withProperty("research.discovery.max-shops","3")
            .withProperty("research.discovery.max-shop-review-requests","1").withProperty("research.discovery.max-listing-review-requests","1");
        var worker=new DiscoveryAnalysis(provider,RestClient.create(),new ObjectMapper(),settings);
        var state=new DiscoveryState();state.id=UUID.randomUUID();state.query="TEST_FIXTURE";state.analysisBatchSize=3;
        var branch=new DiscoveryState.Query(state.query,null);state.queries.add(branch);
        for(int id=1;id<=3;id++){var listing=listing(id,id);state.listings.put(listing.id(),listing);branch.listingIds.add(listing.id());}
        assertTrue(worker.advance(state));
        for(int index=0;index<6;index++)assertTrue(worker.advance(state));
        var task=state.batches.getFirst().request;
        assertNotNull(task);assertEquals(3,task.shops().size());assertEquals(1,task.reviews().size());
        verify(provider,times(3)).shop(anyString());verify(provider,times(1)).shopReviews(anyString());
        verify(provider,times(1)).listingReviews(anyString());verifyNoMoreInteractions(provider);
    }
}
