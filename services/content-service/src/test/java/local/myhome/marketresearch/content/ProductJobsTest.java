package local.myhome.marketresearch.content;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.content.ProductDefinition.*;
import local.myhome.marketresearch.tools.*;

class ProductJobsTest {
    ObjectMapper json=new ObjectMapper();JdbcTemplate db;ProviderAccess provider;ProductJobs jobs;
    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        provider=mock(ProviderAccess.class);jobs=new ProductJobs(db,json,provider);
    }
    Request request() {
        var p=new Product("Holiday classroom activity pack","Teachers","Stations",List.of("4 PDF sheets"),"Original designs",BigDecimal.valueOf(3),
            "Unverified scenario","Template exports","Unknown QA effort","Season aligned",List.of("1"),List.of("No listing revenue"),"LOW","Proxy evidence","Season aligned test");
        return new Request(UUID.randomUUID(),"Jewish printables",p,List.of(new Listing("1","11","Activity pack","Description",List.of(),BigDecimal.ONE,"USD",1,1,1)));
    }
    Brief brief(String filename,List<String> ids) {
        return new Brief("Four holiday classroom activities","Teachers","Stations",
            List.of(new FileSpec(filename,"PDF","A4 210x297 mm",4,"One activity on each page; four original activity types")),
            new VisualSpec("Original flat illustration",List.of("#334455"),"Licensed readable font","Generous print margins",List.of("No copied art")),
            ids,"Inspiration only; no reproduction","Proposed USD 3, not observed revenue",List.of("Verify all page dimensions"),List.of("Template-based export"),List.of());
    }
    @Test void definesReviewsAndPreservesTheContractWithoutGeneratingAssets() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(brief("activity.pdf",List.of("1")),Map.class));
        var request=request();var job=jobs.submit("product",request);assertEquals(job.id(),jobs.submit("product",request).id());
        jobs.tick();assertEquals("ACCEPTED",jobs.get(job.id()).status());jobs.tick();
        assertEquals("SUCCEEDED",jobs.get(job.id()).status());assertEquals(4,jobs.get(job.id()).brief().files().getFirst().pagesOrItems());
        verify(provider,times(2)).reason(anyString(),anyString(),any());
        assertThrows(ResponseStatusException.class,()->jobs.submit("product",request()));
    }
    @Test void inventedReferencesAndUnsafeFilenamesAreRejectedWithoutRetry() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(brief("../activity.pdf",List.of("1")),Map.class));
        var job=jobs.submit("bad",request());jobs.tick();assertEquals("BLOCKED",jobs.get(job.id()).status());jobs.tick();
        assertEquals(1,((List<?>)jobs.evidence(job.id()).get("outputs")).size());
        assertEquals("Invalid deliverable",jobs.evidence(job.id()).get("failureDetail"));
        verify(provider,times(1)).reason(anyString(),anyString(),any());
        assertThrows(ResponseStatusException.class,()->ProductJobs.validate(brief("activity.pdf",List.of("9")),request()));
    }
    @Test void transientReviewRetryPreservesDraftAndWaitsAcrossRestart() throws Exception {
        var answer=json.convertValue(brief("activity.pdf",List.of("1")),Map.class);
        when(provider.reason(anyString(),anyString(),any())).thenReturn(answer)
            .thenThrow(new ProviderFailure("HTTP_503",503,true,System.currentTimeMillis()+60000,"","detail_object",""))
            .thenReturn(answer);
        var job=jobs.submit("transient",request());jobs.tick();jobs.tick();
        assertEquals("WAITING_RETRY",jobs.get(job.id()).status());
        var draft=jobs.evidence(job.id()).get("draft");
        jobs=new ProductJobs(db,json,provider);jobs.tick();
        verify(provider,times(2)).reason(anyString(),anyString(),any());
        assertEquals(draft,jobs.evidence(job.id()).get("draft"));
        db.update("UPDATE product_definition_jobs SET next_attempt=0 WHERE id=?",job.id().toString());
        jobs.tick();assertEquals("SUCCEEDED",jobs.get(job.id()).status());
        verify(provider,times(1)).reason(eq("product-definition"),anyString(),any());
    }
    @Test void rejectedBriefReviewCanBeRetriedWithoutRedefiningTheProduct() throws Exception {
        var valid=json.convertValue(brief("activity.pdf",List.of("1")),Map.class);
        var invalid=new LinkedHashMap<>(valid);invalid.put("intentPreserved",false);
        when(provider.reason(anyString(),anyString(),any())).thenReturn(valid,invalid,valid);
        var job=jobs.submit("review-recovery",request());jobs.tick();jobs.tick();
        assertEquals("BLOCKED",jobs.get(job.id()).status());
        assertEquals(2,((List<?>)jobs.evidence(job.id()).get("outputs")).size());
        jobs.resume(job.id());jobs.tick();
        assertEquals("SUCCEEDED",jobs.get(job.id()).status());
        verify(provider,times(1)).reason(eq("product-definition"),anyString(),any());
        verify(provider,times(2)).reason(eq("product-definition-review"),anyString(),any());
        var inputs=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(provider,times(3)).reason(anyString(),anyString(),inputs.capture());
        var feedback=(Map<?,?>)inputs.getAllValues().getLast();
        assertEquals("Business intent was not preserved",feedback.get("validationFailure"));
        assertTrue(feedback.containsKey("lastRejectedOutput"));
    }
    @Test void explicitRevalidationIsIdempotentAndDoesNotRepeatDefinition() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenReturn(json.convertValue(brief("activity.pdf",List.of("1")),Map.class));
        var job=jobs.submit("revalidation",request());jobs.tick();jobs.tick();
        assertEquals("ACCEPTED",jobs.reviewAgain(job.id(),"review-round-1").status());
        assertEquals("ACCEPTED",jobs.reviewAgain(job.id(),"review-round-1").status());
        jobs.tick();
        assertEquals("SUCCEEDED",jobs.reviewAgain(job.id(),"review-round-1").status());
        jobs.tick();
        verify(provider,times(1)).reason(eq("product-definition"),anyString(),any());
        verify(provider,times(2)).reason(eq("product-definition-review"),anyString(),any());
        assertEquals(3,((List<?>)jobs.evidence(job.id()).get("outputs")).size());
    }
    @Test void pendingCancellationAndInterruptedCallRecoveryDoNotSendCalls() {
        var a=jobs.submit("cancel",request());jobs.cancel(a.id());jobs.tick();assertEquals("CANCELLED",jobs.get(a.id()).status());
        var b=jobs.submit("crash",request());db.update("UPDATE product_definition_jobs SET status='RUNNING' WHERE id=?",b.id().toString());
        var recovered=new ProductJobs(db,json,provider);recovered.tick();assertEquals("BLOCKED",recovered.get(b.id()).status());verifyNoInteractions(provider);
    }
    @Test void localContentionWaitsWithoutFailingTheJob() throws Exception {
        when(provider.reason(anyString(),anyString(),any())).thenThrow(new ProviderBusy()).thenReturn(json.convertValue(brief("activity.pdf",List.of("1")),Map.class));
        var job=jobs.submit("wait",request());jobs.tick();assertEquals("ACCEPTED",jobs.get(job.id()).status());
        jobs.tick();jobs.tick();assertEquals("SUCCEEDED",jobs.get(job.id()).status());
    }
    @Test void definitionCanChooseDifferentQuantitiesWhileKeepingBusinessIntent() {
        var base=brief("activity.pdf",List.of("1"));
        var independent=new Brief(base.product(),base.audience(),base.useCase(),
            List.of(new FileSpec("activity.pdf","PDF","A4",8,"Eight independently designed activity sheets")),
            base.visual(),base.listingIds(),base.referenceUse(),base.commercialContext(),base.acceptanceChecks(),base.assumptions(),List.of());
        assertDoesNotThrow(()->ProductJobs.validate(independent,request()));
        assertEquals(8,independent.files().getFirst().pagesOrItems());
    }
    @Test void coherentCountsDimensionsAndBusinessIntentAreRequiredNotJustPresentFields() {
        var base=brief("activity.pdf",List.of("1"));
        var wrongBuyer=new Brief(base.product(),"Different buyers","Unrelated need",base.files(),base.visual(),
            base.listingIds(),base.referenceUse(),base.commercialContext(),base.acceptanceChecks(),base.assumptions(),List.of());
        assertThrows(ResponseStatusException.class,()->ProductJobs.validate(wrongBuyer,request()));
        var contradictory=new Brief(base.product(),base.audience(),base.useCase(),base.files(),base.visual(),base.listingIds(),
            base.referenceUse(),base.commercialContext(),base.acceptanceChecks(),base.assumptions(),List.of(),
            List.of(new Criterion("activity.pdf","COUNT","99","Inspect PDF"),new Criterion("activity.pdf","DIMENSIONS","Wrong size","Inspect PDF")),
            true,"Same buying purpose","READY");
        assertThrows(ResponseStatusException.class,()->ProductJobs.validate(contradictory,request()));
        var unresolved=new Brief(base.product(),base.audience(),base.useCase(),base.files(),base.visual(),base.listingIds(),
            base.referenceUse(),base.commercialContext(),base.acceptanceChecks(),base.assumptions(),List.of("Critical unverified language"),
            base.criteria(),true,"Same purpose","READY");
        assertThrows(ResponseStatusException.class,()->ProductJobs.validate(unresolved,request()));
    }
}
