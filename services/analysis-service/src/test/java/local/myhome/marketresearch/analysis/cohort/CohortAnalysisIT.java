package local.myhome.marketresearch.analysis.cohort;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CohortAnalysisIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    private HttpResponse<String> post(Object body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/v1/etsy-cohort-analyses"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void analyzesOneThousandListingsOverRealHttpAndDoesNotRequireProviderKeys() throws Exception {
        var rows = IntStream.rangeClosed(1,1000).mapToObj(i ->
            CohortAnalyzerTest.listing(i,"download",i % 2 == 0 ? 100 : 300,"USD")).toList();
        var response = post(Map.of("query","halloween printable","observedAt","2026-10-21T00:00:00Z","listings",rows));
        assertEquals(200,response.statusCode());
        var body = json.readTree(response.body());
        assertEquals(1000,body.get("received").asInt());
        assertEquals(1000,body.get("uniqueListings").asInt());
        assertEquals(1000,body.get("digitalListings").asInt());
        assertEquals(1000,body.get("experimentCandidates").size());
        assertEquals(2.0,body.get("digitalPricesByCurrency").get("USD").get("median").asDouble());
        assertFalse(response.body().contains("expectedRevenue")); // no sales inference from views
    }
    @Test void rejectsOversizedEmptyMissingAndFutureRequests() throws Exception {
        var row = CohortAnalyzerTest.listing(1,"download",100,"USD");
        assertEquals(400,post(Map.of("query","halloween","observedAt","2026-10-21T00:00:00Z","listings",
            Collections.nCopies(1001,row))).statusCode());
        assertEquals(400,post(Map.of("query","halloween","observedAt","2026-10-21T00:00:00Z","listings",List.of())).statusCode());
        assertEquals(400,post(Map.of("query","halloween","listings",List.of(row))).statusCode());
        assertEquals(400,post(Map.of("query","halloween","observedAt",Instant.now().plusSeconds(3600).toString(),
            "listings",List.of(row))).statusCode());
    }
}
