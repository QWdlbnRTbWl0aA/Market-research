package local.myhome.marketresearch.analysis.market;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketAnalysisIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    HttpResponse<String> post(String path,Object body) throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test void refinementAndBusinessEndpointsPreserveEvidenceBoundaries() throws Exception {
        var listing=MarketEnginesTest.listing(1,11,2);
        var refinement=new RefinementRequest("halloween printable",1_000_000,20,List.of(listing),
            List.of(new Group("games","Games","halloween classroom printable games")),List.of(new Assignment("1","games",true)));
        var plan=post("/v1/refinement-plans",refinement);
        assertEquals(200,plan.statusCode());assertTrue(plan.body().contains("10000"));
        var request=MarketEnginesTest.evidence(Map.of("count",0,"results",List.of()));
        var result=post("/v1/business-analyses",request);
        assertEquals(200,result.statusCode());
        var opportunity=json.readTree(result.body()).get("opportunities").get(0);
        assertTrue(opportunity.get("verifiedMonthlyRevenueUsd").isNull());
        assertEquals("EXPLORATORY_CHEAP_TEST",opportunity.get("evidenceBand").asString());
    }
    @Test void invalidEvidenceReturns400InsteadOfInventingResults() throws Exception {
        var request=MarketEnginesTest.evidence(Map.of());
        var invalid=new BusinessRequest(request.observedAt(),request.targetMonthlyUsd(),request.listings(),request.groups(),
            List.of(new Assignment("999","games",true)),request.shops(),request.reviews(),Map.of());
        assertEquals(400,post("/v1/business-analyses",invalid).statusCode());
    }
}
