package local.myhome.marketresearch.content;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import local.myhome.marketresearch.contracts.content.ProductDefinition.*;
import local.myhome.marketresearch.tools.ProviderAccess;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:product-http;DB_CLOSE_DELAY=-1","research.product.worker-delay-ms=3600000"})
class ProductHttpIT {
    @LocalServerPort int port;@Autowired ProductJobs jobs;@MockitoBean ProviderAccess provider;
    @Test void briefJobHasStableLocationAndDoesNotGenerateAssets() throws Exception {
        var fixture=new ProductJobsTest();var request=fixture.request();
        when(provider.reason(anyString(),anyString(),any())).thenReturn(fixture.json.convertValue(fixture.brief("activities.pdf",List.of("1")),Map.class));
        var http=RestClient.create("http://127.0.0.1:"+port);
        var response=http.post().uri("/v1/product-definition-jobs").header("Idempotency-Key","http-product").body(request).retrieve().toEntity(View.class);
        assertEquals(202,response.getStatusCode().value());assertNotNull(response.getHeaders().getLocation());
        jobs.tick();jobs.tick();var ready=http.get().uri(response.getHeaders().getLocation()).retrieve().body(View.class);
        assertEquals("SUCCEEDED",ready.status());assertEquals("activities.pdf",ready.brief().files().getFirst().filename());
        assertEquals(ready.id(),http.post().uri("/v1/product-definition-jobs").header("Idempotency-Key","http-product").body(request).retrieve().body(View.class).id());
        verify(provider,times(2)).reason(anyString(),anyString(),any());
    }
}
