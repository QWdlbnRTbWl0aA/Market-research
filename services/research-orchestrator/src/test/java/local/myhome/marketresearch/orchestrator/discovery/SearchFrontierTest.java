package local.myhome.marketresearch.orchestrator.discovery;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SearchFrontierTest {
    @Test void additionalPassPreservesEvidenceAndDeduplicatesDeferredPhrases() {
        var state=new DiscoveryState();
        var parent=new DiscoveryState.Query("Jewish printables",null);
        parent.status="DONE";parent.pages=7;parent.usable=20;parent.novel=20;
        parent.listingIds.add("saved-source");parent.refinementJob=UUID.randomUUID();
        parent.expansionStopReason="QUERY_EXPANSION_LIMIT";
        parent.deferredQueries.addAll(List.of("Jewish games","  JEWISH games ","Hebrew worksheets"));
        state.queries.add(parent);
        assertTrue(SearchFrontier.nextPass(state,1,5));
        assertEquals(2,state.searchPass);assertEquals(2,state.queries.size());
        assertEquals(7,parent.pages);assertTrue(parent.listingIds.contains("saved-source"));
        assertEquals(3,parent.deferredQueries.size(),"Historical suggestions remain auditable");
        state.queries.getLast().status="DONE";
        assertTrue(SearchFrontier.nextPass(state,1,5));assertEquals(3,state.queries.size());
        assertFalse(SearchFrontier.nextPass(state,1,5));
    }
    @Test void skippedRefinementRequiresNovelEvidenceAndRespectsBranchExhaustion() {
        var state=new DiscoveryState();var branch=new DiscoveryState.Query("Jewish printables",null);
        branch.status="DONE";branch.pages=2;branch.usable=10;branch.novel=10;
        branch.sample.add(null);branch.expansionStopReason="QUERY_EXPANSION_LIMIT";
        state.queries.add(branch);
        branch.stableAnalyses=2;assertFalse(SearchFrontier.nextPass(state,10,5));
        branch.stableAnalyses=0;branch.novel=0;assertFalse(SearchFrontier.nextPass(state,10,5));
        branch.novel=10;assertTrue(SearchFrontier.nextPass(state,10,5));
        assertEquals("REFINING",branch.status);assertEquals(2,branch.pages);
        assertFalse(SearchFrontier.nextPass(state,10,5),"Reopened work is not repeatedly reopened");
    }
    @Test void backpressureLeavesAnalysisRunnableAndSearchResumesWhenDrained() {
        var state=new DiscoveryState();state.pendingListingLimit=2;state.analysisBatchSize=2;
        var branch=new DiscoveryState.Query("Jewish printables",null);state.queries.add(branch);
        state.listings.put("1",null);state.listings.put("2",null);
        assertEquals(DiscoveryReadiness.IDLE,DiscoveryReadiness.next(state,"SEARCH"));
        assertEquals(0,DiscoveryReadiness.next(state,"ANALYSIS"));
        state.processed.add("1");assertEquals(0,DiscoveryReadiness.next(state,"SEARCH"));
    }
}
