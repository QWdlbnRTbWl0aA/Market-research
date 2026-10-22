package local.myhome.marketresearch.orchestrator.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.tools.ProviderAccess;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

class OpportunityHandoffTest {
    @Test void additionalEvidenceNeverCreatesAnotherBriefWhileMeaningfulVariantsDo() throws Exception {
        var builder=RestClient.builder().baseUrl("http://fixture.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();var json=new ObjectMapper();
        var worker=new DiscoveryAnalysis(mock(ProviderAccess.class),builder.build(),json,new MockEnvironment());
        var state=new DiscoveryState();state.id=UUID.randomUUID();state.query="TEST_FIXTURE";
        var listing=new Listing("1","11","Source","Contents",List.of(),BigDecimal.ONE,"USD",0,0,0);
        state.listings.put("1",listing);state.queries.add(new DiscoveryState.Query(state.query,null));
        var first=product("Initial hypothesis").resolved("opportunity-1");
        ingest(worker,state,listing,first,server,json);
        assertEquals(1,state.handoffs.size());
        var added=product("Semantically equivalent, differently worded idea")
            .related("ADDITIONAL_EVIDENCE","opportunity-1","Same buyer need and offer; new supporting source").resolved("opportunity-1");
        ingest(worker,state,listing,added,server,json);
        assertEquals(1,state.handoffs.size(),"Evidence must not incur another definition/review pair");
        var variant=product("Materially different activity format")
            .related("MEANINGFUL_VARIANT","opportunity-1","Different delivery format solves a different use context").resolved("opportunity-2");
        ingest(worker,state,listing,variant,server,json);
        assertEquals(2,state.handoffs.size());assertEquals(2,state.opportunities.size());server.verify();
    }
    private Product product(String title) {
        return new Product(title,"Teachers","Classroom stations",List.of("4 PDF sheets"),"Original offer",BigDecimal.valueOf(3),
            "Unverified scenario","Template export","Unknown effort","Seasonal",List.of("1"),List.of("Public proxy only"),
            "LOW","Unknown sales","Publish and measure");
    }
    private void ingest(DiscoveryAnalysis worker,DiscoveryState state,Listing listing,Product product,
                        MockRestServiceServer server,ObjectMapper json) throws Exception {
        var batch=new DiscoveryState.Batch();batch.branch=state.query;batch.ids=List.of("1");batch.job=UUID.randomUUID();
        batch.request=new Task("BUSINESS",state.query,List.of(listing),List.of(),List.of(),List.of());state.batches.add(batch);
        var report=new Assessment("Cohort conclusion",List.of(),List.of(product),List.of());
        server.expect(requestTo("http://fixture.invalid/v1/discovery-jobs/"+batch.job)).andRespond(
            withSuccess(json.writeValueAsString(new TaskView(batch.job,"BUSINESS","SUCCEEDED",null,1,0,null,report)),MediaType.APPLICATION_JSON));
        assertTrue(worker.advance(state));
        server.verify();server.reset();
    }
}
