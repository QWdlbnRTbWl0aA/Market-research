package local.myhome.marketresearch.orchestrator.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.content.ProductDefinition;
import local.myhome.marketresearch.tools.*;
import tools.jackson.databind.ObjectMapper;

/** Actual orchestrator HTTP, database and workers; asynchronous dependencies are local protocol fixtures. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "research.discovery.search-delay-ms=3600000","research.discovery.analysis-delay-ms=3600000",
    "research.discovery.delivery-delay-ms=3600000","research.discovery.watch-delay-ms=3600000",
    "research.notifications.delay-ms=3600000","research.discovery.batch-size=200","research.discovery.max-shops=2",
    "research.discovery.max-shop-review-requests=0","research.discovery.max-listing-review-requests=0"})
class DiscoveryFlowIT {
    static final ObjectMapper JSON=new ObjectMapper();
    static final Map<UUID,TaskView> jobs=new ConcurrentHashMap<>();
    static final Map<UUID,ProductDefinition.View> products=new ConcurrentHashMap<>();
    static final Map<String,UUID> keys=new ConcurrentHashMap<>();
    static final List<Map<String,Object>> archived=new CopyOnWriteArrayList<>();
    static final AtomicReference<Throwable> serverFailure=new AtomicReference<>();
    static final AtomicInteger analysisPosts=new AtomicInteger();
    static final HttpServer server=startServer();
    @LocalServerPort int port;
    @Autowired DiscoveryFlow flow;@MockitoSpyBean DiscoveryStore store;@Autowired DiscoveryNotices notices;@Autowired JdbcTemplate db;
    @MockitoBean ProviderAccess provider;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        String url="http://127.0.0.1:"+server.getAddress().getPort();
        for(String name:List.of("analysis","content","archive"))r.add("market-research.services."+name+"-base-url",()->url);
    }
    @BeforeEach void clear() {db.update("DELETE FROM discoveries");db.update("DELETE FROM discovery_notices");jobs.clear();products.clear();keys.clear();archived.clear();analysisPosts.set(0);serverFailure.set(null);}
    @AfterAll static void close(){server.stop(0);}
    RestClient client(){return RestClient.create("http://127.0.0.1:"+port);}
    static HttpServer startServer() {
        try {
            var s=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
            s.createContext("/",DiscoveryFlowIT::handle);s.start();return s;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @SuppressWarnings("unchecked") static void handle(HttpExchange x) {
        try {
            String path=x.getRequestURI().getPath(),method=x.getRequestMethod();Object result=null;
            if(method.equals("DELETE")||path.endsWith("/resume")){x.sendResponseHeaders(204,-1);return;}
            if(path.endsWith("/evidence"))result=Map.of("fixture","Synthetic stage evidence","protocol","discovery-v2");
            else if(path.equals("/v1/discovery-jobs")&&method.equals("POST")) {
                analysisPosts.incrementAndGet();var task=JSON.readValue(x.getRequestBody(),Task.class);
                String key=x.getRequestHeaders().getFirst("Idempotency-Key");
                UUID id=keys.computeIfAbsent(key,k->UUID.randomUUID());
                Refinement refinement=null;Assessment assessment=null;
                if(task.kind().equals("REFINE")) {
                    boolean broad=task.listings().stream().anyMatch(l->Integer.parseInt(l.id())<100);
                    refinement=new Refinement(broad?List.of("Jewish holiday classroom printables","Jewish home celebration printables"):List.of(),
                        List.of(broad?"broad":"focused"),"TEST_FIXTURE: focused families, no unexplored child intent");
                } else {
                    String listing=task.listings().getFirst().id();
                    assessment=new Assessment("TEST_FIXTURE cohort reasoning",List.of("activities"),List.of(product(listing)),List.of("Fictional test evidence"));
                }
                var view=new TaskView(id,task.kind(),"SUCCEEDED",null,task.listings().size(),0,refinement,assessment);
                jobs.put(id,view);result=view;
            } else if(path.startsWith("/v1/discovery-jobs/"))result=jobs.get(UUID.fromString(path.substring(path.lastIndexOf('/')+1)));
            else if(path.equals("/v1/product-definition-jobs")&&method.equals("POST")) {
                var request=JSON.readValue(x.getRequestBody(),ProductDefinition.Request.class);
                UUID id=keys.computeIfAbsent(x.getRequestHeaders().getFirst("Idempotency-Key"),k->UUID.randomUUID());
                var brief=new ProductDefinition.Brief(request.recommendation().name(),"Teachers","Classroom",
                    List.of(new ProductDefinition.FileSpec("activities.pdf","PDF","A4",4,"Four original activities")),
                    new ProductDefinition.VisualSpec("Flat",List.of("#333333"),"Licensed font","Print margins",List.of("Original art")),
                    request.recommendation().listingIds(),"Inspiration only","Hypothetical USD 3",
                    List.of("Check print dimensions"),List.of("Template generation"),List.of());
                var view=new ProductDefinition.View(id,"SUCCEEDED",null,brief);products.put(id,view);result=view;
            } else if(path.startsWith("/v1/product-definition-jobs/")&&path.endsWith("/review")) {
                String jobPath=path.substring(0,path.lastIndexOf('/'));
                result=products.get(UUID.fromString(jobPath.substring(jobPath.lastIndexOf('/')+1)));
            } else if(path.startsWith("/v1/product-definition-jobs/"))result=products.get(UUID.fromString(path.substring(path.lastIndexOf('/')+1)));
            else if(path.equals("/v1/discovery-archives")){archived.add(JSON.readValue(x.getRequestBody(),Map.class));x.sendResponseHeaders(204,-1);return;}
            else throw new IllegalStateException("Unexpected local fixture route");
            byte[] body=JSON.writeValueAsBytes(result);x.getResponseHeaders().add("Content-Type","application/json");
            x.sendResponseHeaders(method.equals("POST")?202:200,body.length);x.getResponseBody().write(body);
        }catch(Throwable e){serverFailure.set(e);try{x.sendResponseHeaders(500,-1);}catch(Exception ignored){}}
        finally{x.close();}
    }
    static Product product(String id) {
        return new Product("TEST_FIXTURE four holiday activities "+id,"Teachers","Classroom",List.of("Four PDF sheets"),"Original",
            BigDecimal.valueOf(3),"Unverified scenario","Template export","QA unknown","Season aligned",List.of(id),
            List.of("No verified revenue"),"LOW","Test evidence","Season aligned experiment rule").resolved("fixture-opportunity-"+id);
    }
    static Map<String,Object> row(int id,boolean digital) {
        return Map.of("listing_id",id,"shop_id",11+id%2,"title","TEST_FIXTURE printable "+id,"description","Original activity contents",
            "listing_type",digital?"download":"physical","price",Map.of("amount",300,"divisor",100,"currency_code","USD"));
    }
    void tickUntil(UUID id,String status) {
        for(int i=0;i<200&&!flow.get(id).status.equals(status);i++){flow.searchTick();flow.analysisTick();flow.deliveryTick();}
        assertEquals(status,flow.get(id).status,"error="+flow.get(id).error);assertNull(serverFailure.get());
    }
    @Test void boundedSamplingDrainsToCompleteArchiveWithoutClaimingMarketExhaustion() throws Exception {
        when(provider.search(anyString(),eq(100),anyInt())).thenAnswer(call -> {
            String query=call.getArgument(0);int offset=call.getArgument(2);
            int base=query.equals("jewish printables")?1:query.contains("classroom")?1000:2000;
            return Map.of("count",20000,"results",java.util.stream.IntStream.range(base+offset,base+offset+100)
                .mapToObj(id->row(id,true)).toList());
        });
        when(provider.shop(anyString())).thenReturn(Map.of("shop_id",11,"transaction_sold_count",100));
        var state=flow.start("bounded-sampling","Jewish printables");
        state.samplingPageBudget=6;store.save(state);
        tickUntil(state.id,"WATCHING");
        var finished=flow.get(state.id);
        assertEquals("SAMPLING_COMPLETE",finished.searchStatus);
        assertEquals(6,finished.queries.stream().mapToInt(branch->branch.pages).sum());
        assertTrue(finished.queries.stream().filter(branch->branch.depth==1).allMatch(branch->branch.pages>0));
        assertTrue(finished.queries.stream().anyMatch(branch->"DEFERRED".equals(branch.status)));
        assertTrue(finished.listings.keySet().stream().allMatch(finished.processed::contains));
        assertEquals("SAMPLING_COMPLETE",archived.getFirst().get("searchStatus"));
        assertEquals(6,((List<?>)archived.getFirst().get("sourcePages")).size());
        clearInvocations(provider,store);
        for(int index=0;index<5;index++){flow.searchTick();flow.analysisTick();flow.deliveryTick();}
        verifyNoInteractions(provider);verify(store,never()).save(any());
    }
    @Test void completedBriefCanBeExplicitlyRevalidatedBeforeButNotAfterArchival() {
        var state=flow.start("review-briefs","Synthetic printable intent");
        state.searchStatus="EXHAUSTED";state.queries.getFirst().status="DONE";
        var source=SearchPolicy.listing(row(1,true));state.listings.put("1",source);state.processed.add("1");
        var handoff=new DiscoveryState.Handoff();handoff.key="fixture-handoff";
        handoff.request=new ProductDefinition.Request(state.id,state.query,product("1"),List.of(source));
        state.handoffs.add(handoff);store.save(state);
        flow.deliveryTick();flow.deliveryTick();
        assertEquals("SUCCEEDED",flow.get(state.id).handoffs.getFirst().status);
        flow.reviewBriefs(state.id,"review-1");
        assertNull(flow.get(state.id).handoffs.getFirst().brief);
        flow.deliveryTick();flow.deliveryTick();flow.deliveryTick();
        assertEquals("WATCHING",flow.get(state.id).status);
        assertEquals(1,archived.size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->flow.reviewBriefs(state.id,"too-late"));
    }
    @Test void localProviderContentionRestoresPersistedOpenBranchWithoutConsumingPage() throws Exception {
        when(provider.search(anyString(),anyInt(),anyInt())).thenThrow(new ProviderBusy());
        var state=flow.start("busy-search","Synthetic printable search");
        flow.searchTick();
        var waiting=flow.get(state.id);
        assertEquals("OPEN",waiting.queries.getFirst().status);
        assertEquals(0,waiting.queries.getFirst().pages);
        assertEquals("ACTIVE",waiting.status);
        verify(provider).search(anyString(),anyInt(),eq(0));
    }
    @Test void broadSearchRunsThroughRefinementCohortAnalysisBriefsArchiveAndApprovalGatedWatch() throws Exception {
        when(provider.search(anyString(),anyInt(),anyInt())).thenAnswer(call->{
            String query=call.getArgument(0);int offset=call.getArgument(2);
            if(query.equals("jewish printables"))return Map.of("count",50000,"results",java.util.stream.IntStream.rangeClosed(1,100).mapToObj(i->row(i,i<=50)).toList());
            int base=query.contains("classroom")?1000:2000;
            return Map.of("count",500,"results",java.util.stream.IntStream.range(base+offset,base+offset+100).mapToObj(i->row(i,true)).toList());
        });
        when(provider.shop(anyString())).thenReturn(Map.of("shop_id",11,"transaction_sold_count",100,"created_timestamp",1));
        var created=client().post().uri("/v1/discoveries").header("Idempotency-Key","broad").body(Map.of("query","Jewish printables")).retrieve().toEntity(Map.class);
        assertEquals(202,created.getStatusCode().value());assertNotNull(created.getHeaders().getLocation());
        UUID id=UUID.fromString(created.getBody().get("id").toString());
        tickUntil(id,"WATCHING");var completed=flow.get(id);
        assertEquals(1050,completed.listings.size());assertEquals(1050,completed.processed.size());
        assertEquals("EXHAUSTED",completed.searchStatus);assertEquals(3,completed.queries.size());
        assertTrue(completed.queries.get(1).usableYield()>completed.queries.getFirst().usableYield());
        assertEquals(6,completed.batches.size());assertEquals(6,completed.handoffs.size());
        assertTrue(completed.handoffs.stream().allMatch(h->h.brief!=null));
        assertEquals(1,archived.size());assertEquals(1,notices.list().size());
        assertEquals(1050,((Map<?,?>)archived.getFirst().get("listings")).size());
        assertFalse(((List<?>)archived.getFirst().get("sourcePages")).isEmpty());
        assertFalse(((Map<?,?>)archived.getFirst().get("analysisEvidence")).isEmpty());
        assertFalse(((Map<?,?>)archived.getFirst().get("contentEvidence")).isEmpty());
        notices.tick();assertEquals("WAITING_CONFIGURATION",notices.list().getFirst().get("STATUS"));
        int posts=analysisPosts.get();
        completed.nextWatch=0;store.save(completed);
        when(provider.search(anyString(),eq(25),eq(0))).thenReturn(Map.of("count",1000,"results",
            java.util.stream.IntStream.range(9000,9025).mapToObj(i->row(i,true)).toList()));
        flow.watchTick();flow.watchTick();flow.watchTick();
        assertEquals("AWAITING_APPROVAL",flow.get(id).status);
        for(int i=0;i<3;i++){flow.searchTick();flow.analysisTick();flow.deliveryTick();flow.watchTick();}
        assertEquals(posts,analysisPosts.get(),"Weekly checks must not launch model work");
        flow.dismiss(id);assertEquals("WATCHING",flow.get(id).status);
        var decision=flow.get(id);decision.status="AWAITING_APPROVAL";store.save(decision);
        flow.approve(id);assertEquals(2,flow.get(id).iteration);assertTrue(flow.get(id).reassessmentPending);
        assertEquals("DONE",flow.get(id).queries.getFirst().status,"Do not refetch the superseded broad pool after a focused-query trend alert");
        assertEquals(0,flow.get(id).queries.get(1).pages);
        assertEquals(0,flow.get(id).queries.get(1).returned);
        assertEquals(id,flow.start("broad","Jewish printables").id);
        flow.cancel(id);flow.deliveryTick();assertEquals("CANCELLED",flow.get(id).status);
    }
    @Test void knownQuotaWaitIsNotCompletionAndDoesNotSpinProviderRequests() throws Exception {
        when(provider.search(anyString(),anyInt(),anyInt())).thenThrow(new ProviderDeferred(System.currentTimeMillis()+60000));
        var run=flow.start("wait","Jewish printables");flow.searchTick();flow.searchTick();
        assertEquals("ACTIVE",flow.get(run.id).status);assertEquals("ACTIVE",flow.get(run.id).searchStatus);
        assertTrue(flow.get(run.id).nextSearchAttempt>System.currentTimeMillis());
        verify(provider,times(1)).search(anyString(),anyInt(),anyInt());
    }
    @Test void savedEvidenceCanBeAnalyzedAndDefinedWhileSearchWaitsOnEtsy() {
        var run=flow.start("independent-evidence","Jewish printables");
        run.nextSearchAttempt=System.currentTimeMillis()+3600000;run.queries.getFirst().refined=true;
        for(int id=1;id<=200;id++){var source=SearchPolicy.listing(row(id,true));run.listings.put(source.id(),source);run.queries.getFirst().listingIds.add(source.id());}
        for(String shop:List.of("11","12")){run.shops.put(shop,Map.of("shop_id",Integer.parseInt(shop),"transaction_sold_count",100));run.shopChecked.put(shop,System.currentTimeMillis());}
        store.save(run);
        for(int count=0;count<8;count++){flow.analysisTick();flow.deliveryTick();}
        var progressed=flow.get(run.id);
        assertEquals(200,progressed.processed.size());assertEquals("SUCCEEDED",progressed.batches.getFirst().status);
        assertEquals("SUCCEEDED",progressed.handoffs.getFirst().status);assertNotNull(progressed.handoffs.getFirst().brief);
        assertEquals(run.nextSearchAttempt,progressed.nextSearchAttempt,"The Etsy retry pointer must not be cleared to unlock analysis");
        assertEquals("ACTIVE",progressed.searchStatus);assertEquals(0,progressed.queries.getFirst().pages);
        verifyNoInteractions(provider);assertEquals(1,analysisPosts.get());assertTrue(archived.isEmpty());
    }
    @Test void aCohortThatNeedsNewEvidenceWaitsWithoutPollingOrSendingEtsyCalls() {
        var run=flow.start("missing-evidence","Jewish printables");run.nextSearchAttempt=System.currentTimeMillis()+3600000;
        for(int id=1;id<=200;id++){var source=SearchPolicy.listing(row(id,true));run.listings.put(source.id(),source);run.queries.getFirst().listingIds.add(source.id());}
        store.save(run);flow.analysisTick();flow.analysisTick();
        var waiting=flow.get(run.id);assertEquals(run.nextSearchAttempt,waiting.batches.getFirst().nextAttemptAt);
        assertNull(waiting.batches.getFirst().request);assertTrue(waiting.shops.isEmpty());
        clearInvocations(store);
        for(int index=0;index<10;index++)flow.analysisTick();
        verify(store,never()).get(any());verify(store,never()).save(any());verifyNoInteractions(provider);
        assertEquals(0,analysisPosts.get());assertTrue(waiting.processed.isEmpty());
    }
    @Test void quotaIndependenceProjectionMigrationPreservesStateAndMakesExistingDataRunnable() {
        var run=flow.start("old-quota-projection","Jewish printables");run.nextSearchAttempt=System.currentTimeMillis()+3600000;
        for(int id=1;id<=200;id++){var source=SearchPolicy.listing(row(id,true));run.listings.put(source.id(),source);run.queries.getFirst().listingIds.add(source.id());}
        store.save(run);String expected=JSON.writeValueAsString(store.get(run.id));
        db.update("UPDATE discoveries SET projection_version=2,ANALYSIS_due=? WHERE id=?",run.nextSearchAttempt,run.id.toString());
        var migrated=new DiscoveryStore(db,JSON);
        assertTrue(migrated.ready("ANALYSIS").contains(run.id));assertFalse(migrated.ready("SEARCH").contains(run.id));
        assertEquals(expected,JSON.writeValueAsString(migrated.get(run.id)),"Only scheduling projections change");
        assertEquals(3,db.queryForObject("SELECT projection_version FROM discoveries WHERE id=?",Integer.class,run.id.toString()));
        var secondRestart=new DiscoveryStore(db,JSON);assertEquals(expected,JSON.writeValueAsString(secondRestart.get(run.id)));
    }
    @Test void interruptedSearchIsBlockedOnRestartAndCancellationRemainsAvailable() {
        var run=flow.start("crash","Jewish printables");run.queries.getFirst().status="FETCHING";store.save(run);
        var env=new org.springframework.mock.env.MockEnvironment();
        var restarted=new DiscoveryFlow(store,notices,JSON,env,provider);
        assertEquals("BLOCKED",restarted.get(run.id).status);
        restarted.resume(run.id);assertEquals("OPEN",restarted.get(run.id).queries.getFirst().status);
        restarted.cancel(run.id);restarted.deliveryTick();assertEquals("CANCELLED",restarted.get(run.id).status);
    }
    @Test void exhaustedBranchCannotAbandonAnUnexploredSibling() throws Exception {
        var run=flow.start("saturation","Jewish printables");run.queries.getFirst().stableAnalyses=1;
        run.queries.add(new DiscoveryState.Query("unexplored focused intent",null));
        var l=new Listing("1","11","TEST_FIXTURE listing","Description",List.of(),BigDecimal.ONE,"USD",0,0,0);
        run.listings.put("1",l);
        var batch=new DiscoveryState.Batch();batch.branch=run.query;batch.ids=List.of("1");batch.key="saturation-job";batch.job=UUID.randomUUID();
        batch.request=new Task("BUSINESS",run.query,List.of(l),List.of(),List.of(),List.of());run.batches.add(batch);
        jobs.put(batch.job,new TaskView(batch.job,"BUSINESS","SUCCEEDED",null,1,0,null,
            new Assessment("TEST_FIXTURE: no additional business insight",List.of(),List.of(),List.of("Sampling limit"),false,"Only repeats known product hypotheses")));
        store.save(run);flow.analysisTick();flow.searchTick();
        assertEquals("ACTIVE",flow.get(run.id).searchStatus);
        assertEquals("DONE",flow.get(run.id).queries.getFirst().status);
        assertEquals("OPEN",flow.get(run.id).queries.get(1).status);
        verifyNoInteractions(provider);
        when(provider.search(eq("unexplored focused intent"),anyInt(),anyInt())).thenReturn(Map.of("count",0,"results",List.of()));
        flow.searchTick();flow.searchTick();
        assertEquals("EXHAUSTED",flow.get(run.id).searchStatus);
        verify(provider,times(1)).search(eq("unexplored focused intent"),anyInt(),anyInt());
    }
    @Test void pageCapWaitsForAnalysisThenBlocksInsteadOfClaimingExhaustion() throws Exception {
        when(provider.search(anyString(),anyInt(),anyInt())).thenReturn(Map.of("count",10000,"results",
            java.util.stream.IntStream.rangeClosed(1,100).mapToObj(i->row(i,true)).toList()));
        String url="http://127.0.0.1:"+server.getAddress().getPort();
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("research.discovery.max-pages-per-query","1")
            .withProperty("research.discovery.batch-size","200").withProperty("research.discovery.max-shops","0")
            .withProperty("research.discovery.max-listing-review-requests","0").withProperty("research.discovery.max-shop-review-requests","0");
        for(String name:List.of("analysis","content","archive"))env.withProperty("market-research.services."+name+"-base-url",url);
        var limited=new DiscoveryFlow(store,notices,JSON,env,provider);var run=limited.start("cap","Jewish printables");
        run.queries.getFirst().refined=true;store.save(run);
        limited.searchTick();limited.searchTick();assertEquals("ACTIVE",limited.get(run.id).status);
        limited.analysisTick();limited.analysisTick();limited.analysisTick();limited.analysisTick();limited.searchTick();
        assertEquals("BLOCKED",limited.get(run.id).status);assertEquals("PAGE_SAFETY_LIMIT",limited.get(run.id).error);
        assertNotEquals("EXHAUSTED",limited.get(run.id).searchStatus);
    }
    DiscoveryFlow limitedExpansion(String limit, String value) {
        String url="http://127.0.0.1:"+server.getAddress().getPort();
        var env=new org.springframework.mock.env.MockEnvironment().withProperty(limit,value)
            .withProperty("research.discovery.batch-size","100").withProperty("research.discovery.max-shops","0")
            .withProperty("research.discovery.max-listing-review-requests","0").withProperty("research.discovery.max-shop-review-requests","0");
        for(String name:List.of("analysis","content","archive"))env.withProperty("market-research.services."+name+"-base-url",url);
        return new DiscoveryFlow(store,notices,JSON,env,provider);
    }
    @Test void expansionLimitsAllowAcceptedPagesAnalysisBriefsAndEvidenceCompleteArchive() throws Exception {
        for(String limit:List.of("research.discovery.max-queries","research.discovery.max-depth")) {
            clear();reset(provider);
            var limited=limitedExpansion(limit,limit.endsWith("max-queries")?"1":"0");
            when(provider.search(anyString(),eq(100),anyInt())).thenAnswer(call -> {
                int offset=call.getArgument(2);
                return Map.of("count",200,"results",java.util.stream.IntStream.range(1000+offset,1100+offset).mapToObj(i->row(i,true)).toList());
            });
            var run=limited.start("limited-"+limit,"Jewish printables");
            for(int index=0;index<50&&!limited.get(run.id).status.equals("WATCHING");index++) {
                limited.searchTick();limited.analysisTick();limited.deliveryTick();
            }
            var done=limited.get(run.id);
            assertEquals("WATCHING",done.status,"error="+done.error);
            assertEquals("BOUNDED_COMPLETE",done.searchStatus);
            assertEquals(1,done.queries.size());assertEquals(2,done.queries.getFirst().pages);
            assertEquals(200,done.processed.size());assertEquals(2,done.handoffs.size());
            assertTrue(done.handoffs.stream().allMatch(h->"SUCCEEDED".equals(h.status)));
            assertEquals(limit.endsWith("max-queries")?"QUERY_EXPANSION_LIMIT":"DEPTH_EXPANSION_LIMIT",done.queries.getFirst().expansionStopReason);
            assertEquals(2,analysisPosts.get(),"Only business jobs; no refinement can add a branch");
            assertEquals(2,((List<?>)archived.getFirst().get("sourcePages")).size());
            verify(provider).search("jewish printables",100,0);verify(provider).search("jewish printables",100,100);
            clearInvocations(provider,store);
            limited.searchTick();limited.analysisTick();limited.deliveryTick();
            verifyNoInteractions(provider);verify(store,never()).save(any());
        }
    }
    @Test void legacyBlockedRunResumesWithoutLosingAcceptedOrDeferredQueries() throws Exception {
        var limited=limitedExpansion("research.discovery.max-queries","2");
        var run=limited.start("resume-limit","Jewish printables");
        var branch=run.queries.getFirst();branch.pages=1;branch.returned=100;branch.usable=100;branch.total=5000;
        branch.status="REFINING";branch.refinementJob=UUID.randomUUID();
        var original=SearchPolicy.listing(row(1,true));run.listings.put("1",original);branch.listingIds.add("1");
        var excluded=SearchPolicy.listing(row(2,true));run.listings.put("2",excluded);branch.listingIds.add("2");
        branch.sample=List.of(original,excluded);branch.usable=1;run.excluded.add("2");
        run.processed.add("1");run.status="BLOCKED";run.error="REFINEMENT_SAFETY_LIMIT";
        jobs.put(branch.refinementJob,new TaskView(branch.refinementJob,"REFINE","SUCCEEDED",null,0,0,
            new Refinement(List.of("Accepted child","Deferred child"),List.of("family"),"Fixture",List.of("2")),null));
        store.save(run);
        limited.resume(run.id);limited.searchTick();
        var recovered=limited.get(run.id);var recoveredBranch=recovered.queries.getFirst();
        assertEquals("ACTIVE",recovered.status);assertNull(recovered.error);
        assertEquals(2,recovered.queries.size());assertEquals("accepted child",recovered.queries.get(1).text);
        assertEquals(List.of("deferred child"),recoveredBranch.deferredQueries);
        assertEquals("QUERY_EXPANSION_LIMIT",recoveredBranch.expansionStopReason);
        assertEquals("OPEN",recoveredBranch.status,"Partial expansion must not abandon the parent pool");
        assertEquals(original,recovered.listings.get("1"));assertTrue(recovered.processed.contains("1"));
        assertEquals(1,recoveredBranch.usable,"Previously applied exclusions must not be subtracted again");
        assertTrue(recovered.excluded.contains("2"));
        assertEquals(branch.refinementJob,recoveredBranch.refinementJob);
        var restarted=limitedExpansion("research.discovery.max-queries","2");
        assertEquals(List.of("deferred child"),restarted.get(run.id).queries.getFirst().deferredQueries);
        assertEquals(0,analysisPosts.get(),"Reuse the saved refinement job, never regenerate it");
        verifyNoInteractions(provider);
    }
    @Test void blockedAnalysisIsDurablyIdleWhileAcceptedSearchAndExistingHandoffsContinue() throws Exception {
        var limited=limitedExpansion("research.discovery.max-queries","1");
        var run=limited.start("independent-workers","Jewish printables");
        run.queries.getFirst().refined=true;
        var source=SearchPolicy.listing(row(1,true));run.listings.put("1",source);
        var batch=new DiscoveryState.Batch();batch.ids=List.of("1");batch.branch=run.query;batch.key="failed-batch";
        batch.request=new Task("BUSINESS",run.query,List.of(source),List.of(),List.of(),List.of());
        batch.job=UUID.randomUUID();batch.status="RUNNING";run.batches.add(batch);
        jobs.put(batch.job,new TaskView(batch.job,"BUSINESS","BLOCKED","PROVIDER_OR_VALIDATION_FAILED",1,0,null,null));
        var handoff=new DiscoveryState.Handoff();handoff.key="independent-handoff";
        handoff.request=new ProductDefinition.Request(run.id,run.query,product("1"),List.of(source));run.handoffs.add(handoff);
        store.save(run);limited.analysisTick();
        assertEquals("ACTIVE",limited.get(run.id).status);
        assertEquals("DEPENDENCY_JOB_BLOCKED",limited.get(run.id).workerErrors.get("ANALYSIS"));
        clearInvocations(store);
        limited.analysisTick();limited.analysisTick();
        verify(store,never()).get(any());verify(store,never()).save(any());
        when(provider.search(anyString(),eq(100),eq(0))).thenReturn(Map.of("count",1,"results",List.of(row(2,true))));
        limited.searchTick();limited.deliveryTick();limited.deliveryTick();
        var paused=limited.get(run.id);
        assertEquals(2,paused.listings.size());assertTrue(paused.processed.isEmpty());
        assertEquals("SUCCEEDED",paused.handoffs.getFirst().status);assertNotNull(paused.handoffs.getFirst().brief);
        assertTrue(archived.isEmpty(),"Unresolved analysis cannot be archived as complete");
        var restarted=limitedExpansion("research.discovery.max-queries","1");
        assertEquals(paused.workerErrors,restarted.get(run.id).workerErrors);
        jobs.put(batch.job,new TaskView(batch.job,"BUSINESS","SUCCEEDED",null,1,0,null,
            new Assessment("Recovered",List.of(),List.of(),List.of(),false,"Fixture")));
        restarted.resume(run.id);restarted.analysisTick();
        var recovered=restarted.get(run.id);
        assertTrue(recovered.workerErrors.isEmpty());assertTrue(recovered.processed.contains("1"));
        assertEquals(batch.job,recovered.batches.getFirst().job);assertNotNull(recovered.handoffs.getFirst().brief);
        assertEquals(0,analysisPosts.get(),"The original failed job is resumed rather than resubmitted");
        recovered.workerErrors.put("DELIVERY","DEPENDENCY_JOB_BLOCKED");store.save(recovered);
        restarted.cancel(run.id);restarted.deliveryTick();assertEquals("CANCELLED",restarted.get(run.id).status);
    }
    @Test void semanticJunkDoesNotBecomeAPerListingResearchProject() {
        var run=flow.start("junk","Jewish printables");
        var l=new Listing("1","11","TEST_FIXTURE unrelated digital item","Description",List.of(),BigDecimal.ONE,"USD",0,0,0);
        run.listings.put("1",l);var q=run.queries.getFirst();q.status="REFINING";q.refinementJob=UUID.randomUUID();
        q.sample=List.of(l);q.usable=1;q.returned=1;q.total=1;q.pages=1;
        jobs.put(q.refinementJob,new TaskView(q.refinementJob,"REFINE","SUCCEEDED",null,0,0,
            new Refinement(List.of(),List.of(),"Irrelevant intent",List.of("1")),null));
        store.save(run);tickUntil(run.id,"WATCHING");
        assertTrue(flow.get(run.id).excluded.contains("1"));assertTrue(flow.get(run.id).batches.isEmpty());
        assertEquals(0,analysisPosts.get());verifyNoInteractions(provider);
    }
    @Test void idleDiscoveriesAreNeitherLoadedSerializedNorWrittenByWorkers() {
        var state=flow.start("idle","idle intent");
        state.status="WATCHING";state.nextWatch=System.currentTimeMillis()+604800000;store.save(state);
        clearInvocations(store);
        for(int count=0;count<20;count++){flow.searchTick();flow.analysisTick();flow.deliveryTick();flow.watchTick();}
        verify(store,never()).get(any());
        verify(store,never()).save(any());
        verifyNoInteractions(provider);
    }
    @Test void identicalNonterminalJobPollsDoNotPersistTheAggregate() {
        var state=flow.start("poll","intent");
        var batch=new DiscoveryState.Batch();batch.job=UUID.randomUUID();batch.status="RUNNING";
        batch.request=new Task("BUSINESS",state.query,List.of(),List.of(),List.of(),List.of());
        state.batches.add(batch);store.save(state);
        jobs.put(batch.job,new TaskView(batch.job,"BUSINESS","RUNNING",null,0,0,null,null));
        clearInvocations(store);
        flow.analysisTick();flow.analysisTick();
        verify(store,never()).save(any());
    }
    @Test void archivalRetryUsesTheSameFrozenEvidenceEvenAfterLiveStateChanges() {
        var state=flow.start("archive-freeze","intent");
        state.listings.put("1",new Listing("1","11","Original evidence","Full original description",List.of(),
            new BigDecimal("3.0000"),"USD",0,0,0));
        var first=store.freezeArchive(state,()->flow.snapshot(state));
        state.listings.clear();
        var retry=store.freezeArchive(state,()->{throw new AssertionError("Frozen evidence must not be rebuilt");});
        assertEquals(JSON.writeValueAsString(first),JSON.writeValueAsString(retry));
        assertEquals(1,((Map<?,?>)retry.get("listings")).size());
    }
}
