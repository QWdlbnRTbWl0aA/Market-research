package local.myhome.marketresearch.orchestrator.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.content.ProductDefinition.*;
import local.myhome.marketresearch.tools.ProviderAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:brief-library-test;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("viewer")
class BriefLibraryIT {
    @LocalServerPort int port;
    @Autowired DiscoveryStore store;
    @Autowired DiscoveryFlow flow;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired DiscoveryNotices notices;
    @Autowired local.myhome.marketresearch.orchestrator.ResearchRuns legacy;
    @MockitoBean ProviderAccess provider;
    DiscoveryState state;
    UUID readyJob,reviewJob;
    RestClient client(){return RestClient.create("http://127.0.0.1:"+port);}
    String path(){return "/v1/brief-library/runs/"+state.id;}

    @BeforeEach void setup() {
        database.update("DELETE FROM discoveries");
        database.update("DELETE FROM research_runs");database.update("DELETE FROM discovery_notices");
        state=store.create("viewer-fixture","TEST_FIXTURE Jewish printables");
        readyJob=add("READY",List.of(),"First brief — עברית");
        reviewJob=add("REQUIRES_REVIEW",List.of("Source text must be verified"),"<img src=x onerror=alert(1)>");
        var pending=new DiscoveryState.Handoff();pending.job=UUID.randomUUID();pending.status="DEFINING";
        pending.brief=state.handoffs.getFirst().brief; // A retained older result must not be offered as a completed new review.
        state.handoffs.add(pending);store.save(state);
    }
    UUID add(String readiness,List<String> questions,String title) {
        var product=new Product(title,"Teachers","Learning",List.of("One sheet"),"Original",BigDecimal.ONE,
            "Unverified hypothesis","Template export","QA time unknown","Evergreen",List.of("123"),
            List.of("No proven sales"),"LOW","Proxy only","Publish and measure").resolved("opportunity-"+state.handoffs.size());
        var brief=new Brief(title,"Teachers","Learning",List.of(new FileSpec("learning.pdf","PDF","A4",1,"Exact content manifest")),
            new VisualSpec("Minimal",List.of("#112233"),"Open-license font","Wide margins",List.of("Original artwork")),
            List.of("123"),"Inspiration, not copying","Price hypothesis",List.of("Inspect exported PDF"),
            List.of("A4 chosen independently"),questions,List.of(new Criterion("learning.pdf","COUNT","1","Read page count"),
                new Criterion("learning.pdf","DIMENSIONS","A4","Inspect MediaBox")),true,"Same buying purpose",readiness);
        var handoff=new DiscoveryState.Handoff();handoff.job=UUID.randomUUID();handoff.status="SUCCEEDED";handoff.brief=brief;
        handoff.request=new Request(state.id,state.query,product,List.of(new Listing("123","7","Example source","Large source description excluded from export",
            List.of(),BigDecimal.valueOf(2),"USD",10,1,1)));
        state.handoffs.add(handoff);return handoff.job;
    }
    @Test void libraryProjectsCompletedBriefsWithoutResearchPayloadsOrProviderCalls() {
        var runs=client().get().uri("/v1/brief-library/runs").retrieve().body(List.class);
        assertEquals(1,runs.size());var run=(Map<?,?>)runs.getFirst();
        assertEquals(2,run.get("availableBriefs"));assertEquals(1,run.get("pendingBriefs"));
        String summary=client().get().uri(path()+"/briefs").retrieve().body(String.class);
        assertEquals(2,json.readValue(summary,List.class).size());
        assertTrue(summary.contains("REQUIRES_REVIEW"));assertFalse(summary.contains("Large source description"));
        assertFalse(summary.contains("call_inputs"));
        String before=database.queryForObject("SELECT state FROM discoveries WHERE id=?",String.class,state.id.toString());
        flow.searchTick();flow.analysisTick();flow.deliveryTick();flow.watchTick();
        notices.queue("viewer-notice","Notice","Saved notice");notices.tick();
        assertEquals("PENDING",notices.list().getFirst().get("STATUS"));
        var legacyRun=legacy.submit("viewer-legacy",new local.myhome.marketresearch.orchestrator.ResearchRuns.SubmitRun("Fixture", "FAST"));
        legacy.tick();assertEquals("ACCEPTED",legacy.get(legacyRun.id()).status());
        assertEquals(before,database.queryForObject("SELECT state FROM discoveries WHERE id=?",String.class,state.id.toString()));
        verifyNoInteractions(provider);
    }
    @Test void detailsAndExportsIncludeCompleteBriefBusinessContextAndReviewWarnings() {
        var document=client().get().uri(path()+"/briefs/"+reviewJob).retrieve().body(BriefLibrary.Document.class);
        assertEquals(List.of("Source text must be verified"),document.brief().unresolvedQuestions());
        assertEquals("https://www.etsy.com/listing/123",document.references().getFirst().url());
        assertEquals("Original",document.recommendation().differentiation());
        var download=client().get().uri(path()+"/briefs/"+readyJob+"/download").retrieve().toEntity(String.class);
        assertTrue(download.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        assertTrue(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
        assertEquals("First brief — עברית",json.readValue(download.getBody(),BriefLibrary.Document.class).brief().product());
        String markdown=client().get().uri(path()+"/briefs/"+reviewJob+"/download?format=markdown").retrieve().body(String.class);
        assertFalse(markdown.contains("<img"));assertTrue(markdown.contains("&lt;img"));
        for(String expected:List.of("Source text must be verified","Exact content manifest","Inspect MediaBox","Same buying purpose",
                "Price hypothesis","Template export","No proven sales","https://www.etsy.com/listing/123","REQUIRES_REVIEW"))
            assertTrue(markdown.contains(expected),expected);
        var bundle=client().get().uri(path()+"/download").retrieve().body(BriefLibrary.Bundle.class);
        assertEquals(2,bundle.briefs().size());
        verifyNoInteractions(provider);
    }
    @Test void missingCrossRunPendingAndInvalidFormatRequestsAreRejected() {
        var other=store.create("other","Other intent");
        for(String route:List.of("/v1/brief-library/runs/"+other.id+"/briefs/"+readyJob,
                path()+"/briefs/"+UUID.randomUUID(),path()+"/briefs/"+state.handoffs.getLast().job)) {
            var failure=assertThrows(HttpClientErrorException.class,()->client().get().uri(route).retrieve().toBodilessEntity());
            assertEquals(404,failure.getStatusCode().value());
        }
        var failure=assertThrows(HttpClientErrorException.class,()->client().get()
            .uri(path()+"/briefs/"+readyJob+"/download?format=pdf").retrieve().toBodilessEntity());
        assertEquals(400,failure.getStatusCode().value());
        var empty=client().get().uri("/v1/brief-library/runs/"+other.id+"/download").retrieve().body(BriefLibrary.Bundle.class);
        assertTrue(empty.briefs().isEmpty());verifyNoInteractions(provider);
    }
    @Test void webAssetsAreLocalAndRenderSourceContentAsTextNotHtml() {
        String html=client().get().uri("/").retrieve().body(String.class);
        assertTrue(html.contains("Product briefs"));assertTrue(html.contains("/briefs.js"));
        assertFalse(java.util.regex.Pattern.compile("<(?:script|link)\\b[^>]*(?:src|href)=[\"']https?://",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html).find(),
            "External account-settings links are allowed; executable and stylesheet assets must stay local");
        String script=client().get().uri("/briefs.js").retrieve().body(String.class);
        assertTrue(script.contains("textContent"));assertFalse(script.contains("innerHTML"));
        assertFalse(script.contains("method: 'POST'"));
        assertTrue(client().get().uri("/briefs.css").retrieve().body(String.class).contains("@media"));
        verifyNoInteractions(provider);
    }
}
