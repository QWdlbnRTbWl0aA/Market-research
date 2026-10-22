package local.myhome.marketresearch.analysis.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.tools.*;

class DiscoveryJobsTest {
    ObjectMapper json=new ObjectMapper();JdbcTemplate db;DataSourceTransactionManager tx;
    ProviderAccess provider;DiscoveryJobs jobs;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
        provider=mock(ProviderAccess.class);jobs=new DiscoveryJobs(db,json,provider,tx,"test-v1");
    }
    Listing listing(int id,String title,long views){return new Listing(""+id,"11",title,"Printable activity instructions",List.of("printable"),new BigDecimal("3"),"USD",views,2,1);}
    Task task(List<Listing> listings){return new Task("BUSINESS","Jewish printables",listings,List.of(Map.of("shop_id",11,"transaction_sold_count",1000)),List.of(),List.of());}
    Product product(){return new Product("Four holiday classroom activity sheets","Teachers","Classroom stations",List.of("4 PDF activity sheets, A4 and US Letter"),
        "Original differentiated activities",new BigDecimal("3"),"Unverified 10 copies/month scenario","Template generation and automated export","Proofreading effort unknown",
        "Season aligned",List.of("1"),List.of("Public proxies do not establish listing sales"),"LOW","Small convenience sample, not demand proof","Publish inexpensively and inspect actual sales during the relevant season");}
    Map<String,Object> report(){return json.convertValue(new Assessment("Cohort-level interpretation",List.of("classroom activities"),List.of(product()),List.of("Revenue unknown")),Map.class);}
    @Test void cohortAnalysisIsReviewedAndUnchangedListingsAreNeverSentAgain() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(report());
        var sample=java.util.stream.IntStream.rangeClosed(1,1000).mapToObj(i->listing(i,"Printable activity "+i,10)).toList();
        var job=jobs.submit("batch-1",task(sample));assertEquals("ACCEPTED",job.status());
        assertEquals(job.id(),jobs.submit("batch-1",task(sample)).id());
        jobs.tick();assertEquals("ACCEPTED",jobs.get(job.id()).status());
        jobs.tick();var done=jobs.get(job.id());assertEquals("SUCCEEDED",done.status());assertEquals(1000,done.newListings());
        assertEquals(1,done.assessment().products().size());
        var repeated=jobs.submit("batch-2",task(sample.stream().map(l->listing(Integer.parseInt(l.id()),l.title(),99)).toList()));
        jobs.tick();assertEquals("SUCCEEDED",jobs.get(repeated.id()).status());assertEquals(1000,jobs.get(repeated.id()).cachedListings());
        verify(provider,times(2)).reason(anyString(),anyString(),any());
        var changed=jobs.submit("batch-3",task(List.of(listing(1,"Changed product contents",100))));
        jobs.tick();jobs.tick();assertEquals(1,jobs.get(changed.id()).newListings());
        verify(provider,times(4)).reason(anyString(),anyString(),any());
    }
    @Test void schemaAndCitationFailuresBlockWithoutCachingOrAutomaticRebilling() throws Exception {
        var bad=new LinkedHashMap<>(report());bad.put("products",List.of(new Product("Unknown reference","Buyer","Use",List.of("4 sheets"),"Original",BigDecimal.ONE,
            "Hypothesis","Automatic","Unknown","Season",List.of("9999"),List.of("Unknown"),"LOW","Explanation","Stop rule")));
        when(provider.reason(anyString(),anyString(),any())).thenReturn(bad);
        var job=jobs.submit("bad",task(List.of(listing(1,"Printable",1))));
        jobs.tick();assertEquals("BLOCKED",jobs.get(job.id()).status());jobs.tick();
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM discovery_cache",Integer.class));
        verify(provider,times(1)).reason(anyString(),anyString(),any());
        when(provider.reason(anyString(),anyString(),any())).thenReturn(report());
        jobs.resume(job.id());jobs.tick();jobs.tick();assertEquals("SUCCEEDED",jobs.get(job.id()).status());
    }
    @Test void cancellationAndCrashRecoveryAreExplicit() throws Exception {
        var task=task(List.of(listing(1,"Printable",1)));var cancelled=jobs.submit("cancel",task);
        jobs.cancel(cancelled.id());jobs.tick();assertEquals("CANCELLED",jobs.get(cancelled.id()).status());verifyNoInteractions(provider);
        var interrupted=jobs.submit("interrupt",task);
        db.update("UPDATE discovery_jobs SET status='RUNNING' WHERE id=?",interrupted.id().toString());
        var restarted=new DiscoveryJobs(db,json,provider,tx,"test-v1");
        assertEquals("BLOCKED",restarted.get(interrupted.id()).status());restarted.tick();verifyNoInteractions(provider);
        assertThrows(ResponseStatusException.class,()->jobs.submit("interrupt",task(List.of(listing(2,"Different",1)))));
    }
    @Test void localLockContentionIsDeferredNotFailed() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenThrow(new ProviderBusy()).thenReturn(report());
        var job=jobs.submit("busy",task(List.of(listing(1,"Printable",1))));
        jobs.tick();assertEquals("ACCEPTED",jobs.get(job.id()).status());
        jobs.tick();jobs.tick();assertEquals("SUCCEEDED",jobs.get(job.id()).status());
    }
    @Test void refinementIsBoundedAndVersionChangesInvalidateCache() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(new Refinement(
            List.of("Jewish holiday games"),List.of("holiday classroom games"),"Focused intent"),Map.class));
        var request=new Task("REFINE","Jewish printables",List.of(listing(1,"Printable",1)),List.of(),List.of(),List.of());
        var job=jobs.submit("refine",request);jobs.tick();assertEquals("SUCCEEDED",jobs.get(job.id()).status());
        assertEquals(1,jobs.get(job.id()).refinement().queries().size());
        when(provider.reason(anyString(),anyString(),any())).thenReturn(report());
        var first=jobs.submit("first",task(request.listings()));jobs.tick();jobs.tick();
        assertEquals("SUCCEEDED",jobs.get(first.id()).status());
        var changed=new DiscoveryJobs(db,json,provider,tx,"test-v2");
        var second=changed.submit("second",task(request.listings()));changed.tick();changed.tick();
        assertEquals(1,changed.get(second.id()).newListings());
    }
    @Test void knownQuotaWaitRetainsPhaseAndResumesWithoutOperatorIntervention() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenThrow(new ProviderDeferred(System.currentTimeMillis()+60000)).thenReturn(report());
        var job=jobs.submit("quota",task(List.of(listing(1,"Printable",1))));
        jobs.tick();assertEquals("WAITING_QUOTA",jobs.get(job.id()).status());assertTrue(jobs.get(job.id()).nextAttemptAt()>System.currentTimeMillis());
        jobs.tick();verify(provider,times(1)).reason(anyString(),anyString(),any());
        db.update("UPDATE discovery_jobs SET next_attempt=0 WHERE id=?",job.id().toString());
        jobs.tick();jobs.tick();assertEquals("SUCCEEDED",jobs.get(job.id()).status());
    }
    @Test void semanticRelationshipsReuseAnOpportunityButPreserveMeaningfulVariants() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(report());
        var first=jobs.submit("opportunity-a",task(List.of(listing(1,"First listing",1))));
        jobs.tick();jobs.tick();
        String id=jobs.get(first.id()).assessment().products().getFirst().opportunityId();
        assertNotNull(id);
        var additional=product().related("ADDITIONAL_EVIDENCE",id,"Same buyer need despite different wording and source").resolved(null);
        var more=json.convertValue(new Assessment("Additional support",List.of(),List.of(additional),List.of("Proxy"),false,"Known opportunity"),Map.class);
        ((Map<String,Object>)((List<?>)more.get("products")).getFirst()).put("name","A differently worded title for the same classroom need");
        when(provider.reason(anyString(),anyString(),any())).thenReturn(more);
        var second=jobs.submit("opportunity-b",task(List.of(listing(1,"Changed source",2))));
        jobs.tick();jobs.tick();
        var result=jobs.get(second.id()).assessment().products().getFirst();
        assertEquals(id,result.opportunityId());assertEquals("ADDITIONAL_EVIDENCE",result.relationship());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM discovery_opportunities",Integer.class));
        var variant=product().related("MEANINGFUL_VARIANT",id,"Different activity format solves a materially distinct classroom need");
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(
            new Assessment("Material variant",List.of(),List.of(variant),List.of("Proxy")),Map.class));
        var third=jobs.submit("opportunity-c",task(List.of(listing(1,"Another changed source",3))));
        jobs.tick();jobs.tick();
        assertEquals("SUCCEEDED",jobs.get(third.id()).status());
        assertNotEquals(id,jobs.get(third.id()).assessment().products().getFirst().opportunityId());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM discovery_opportunities",Integer.class));
        var evidence=jobs.evidence(second.id());
        assertFalse(((Map<?,?>)evidence.get("calls")).isEmpty());
        assertFalse(((Map<?,?>)evidence.get("opportunitySupport")).isEmpty());
    }
    @Test void repeatedValidatedRefinementUsesCacheAndUnknownOpportunityTargetsAreRejected() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(
            new Refinement(List.of(),List.of("known"),"No further intent"),Map.class));
        var request=new Task("REFINE","intent",List.of(listing(1,"Source",1)),List.of(),List.of(),List.of());
        var first=jobs.submit("refine-a",request);jobs.tick();assertEquals("SUCCEEDED",jobs.get(first.id()).status());
        var repeated=jobs.submit("refine-b",request);jobs.tick();assertEquals("SUCCEEDED",jobs.get(repeated.id()).status());
        verify(provider,times(1)).reason(anyString(),anyString(),any());
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(new Assessment(
            "Bad identity",List.of(),List.of(product().related("ADDITIONAL_EVIDENCE","nonexistent","Not a real target")),List.of()),Map.class));
        var invalid=jobs.submit("bad-identity",task(List.of(listing(1,"Source",1))));
        jobs.tick();assertEquals("BLOCKED",jobs.get(invalid.id()).status());
    }
}
