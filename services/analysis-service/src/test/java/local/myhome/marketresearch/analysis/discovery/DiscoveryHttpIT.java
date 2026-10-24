package local.myhome.marketresearch.analysis.discovery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.tools.ProviderAccess;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:discovery-http;DB_CLOSE_DELAY=-1","research.discovery.worker-delay-ms=3600000"})
class DiscoveryHttpIT {
    @LocalServerPort int port;@Autowired DiscoveryJobs jobs;@MockitoBean ProviderAccess provider;
    @Test void asyncContractIsIdempotentReviewedAndCacheAware() throws Exception {
        var fixture=new DiscoveryJobsTest();var task=fixture.task(List.of(fixture.listing(1,"TEST_FIXTURE printable",1)));
        when(provider.reason(anyString(),anyString(),any())).thenReturn(fixture.report());
        var http=RestClient.create("http://127.0.0.1:"+port);
        var response=http.post().uri("/v1/discovery-jobs").header("Idempotency-Key","http-task").body(task).retrieve().toEntity(TaskView.class);
        assertEquals(202,response.getStatusCode().value());assertNotNull(response.getHeaders().getLocation());
        var id=response.getBody().id();jobs.tick();jobs.tick();
        var completed=http.get().uri(response.getHeaders().getLocation()).retrieve().body(TaskView.class);
        assertEquals("SUCCEEDED",completed.status());assertEquals(1,completed.newListings());
        assertEquals(id,http.post().uri("/v1/discovery-jobs").header("Idempotency-Key","http-task").body(task).retrieve().body(TaskView.class).id());
        var repeat=http.post().uri("/v1/discovery-jobs").header("Idempotency-Key","new-key-same-listing").body(task).retrieve().body(TaskView.class);
        jobs.tick();assertEquals(1,jobs.get(repeat.id()).cachedListings());verify(provider,times(2)).reason(anyString(),anyString(),any());

        // Resume the same HTTP resource after a rejected response; retain its payload and rejected output.
        var changed=fixture.task(List.of(fixture.listing(1,"TEST_FIXTURE changed contents",1)));
        var invalid=new LinkedHashMap<>(fixture.report());invalid.put("newInsights",null);
        when(provider.reason(anyString(),anyString(),any())).thenReturn(invalid,fixture.report(),fixture.report());
        var recovering=http.post().uri("/v1/discovery-jobs").header("Idempotency-Key","http-recovery")
            .body(changed).retrieve().body(TaskView.class);
        jobs.tick();assertEquals("BLOCKED",jobs.get(recovering.id()).status());
        http.post().uri("/v1/discovery-jobs/"+recovering.id()+"/resume").retrieve().toBodilessEntity();
        jobs.tick();jobs.tick();
        assertEquals("SUCCEEDED",jobs.get(recovering.id()).status());
        assertEquals(recovering.id(),http.post().uri("/v1/discovery-jobs").header("Idempotency-Key","http-recovery")
            .body(changed).retrieve().body(TaskView.class).id());
        var evidence=http.get().uri("/v1/discovery-jobs/"+recovering.id()+"/evidence").retrieve().body(Map.class);
        assertEquals(3,((List<?>)evidence.get("outputs")).size());
        var calls=(Map<?,?>)evidence.get("calls");
        assertTrue(((Map<?,?>)calls.get("discovery-business")).get("instructions").toString().contains("RECOVERY:"));
    }
}
