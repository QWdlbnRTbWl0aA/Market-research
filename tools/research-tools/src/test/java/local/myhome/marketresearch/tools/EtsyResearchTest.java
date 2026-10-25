package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class EtsyResearchTest {
    @TempDir Path directory;
    ObjectMapper json = new ObjectMapper();
    AtomicLong clock = new AtomicLong(1_000_000);
    AtomicLong slept = new AtomicLong();
    QuotaLedger ledger;
    MockRestServiceServer server;
    EtsyGateway gateway;
    EtsyResearch research;

    @BeforeEach void setup() {
        ledger = new QuotaLedger(directory.resolve("state"),json,clock::get,
            millis -> { slept.addAndGet(millis); clock.addAndGet(millis); });
        var builder = RestClient.builder().baseUrl("https://example.invalid");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new EtsyGateway(builder.build(),json,() -> "test-only");
        research = new EtsyResearch(gateway,ledger,json);
    }
    private Map<String,String> quota(String remaining) {
        return Map.of("x-limit-per-second","5","x-limit-per-day","5000","x-remaining-today",remaining);
    }
    private void state(List<Double> calls, Map<String,String> quota) throws Exception {
        PrivateJson.write(directory.resolve("state/budget.json"),Map.of("calls",calls,"quota",quota),json);
    }
    private void expect(String path, Object body, String remaining) {
        server.expect(requestTo("https://example.invalid"+path)).andExpect(method(HttpMethod.GET))
            .andExpect(header("x-api-key","test-only")).andRespond(withSuccess(json.writeValueAsString(body),MediaType.APPLICATION_JSON)
                .header("x-limit-per-second","5").header("x-limit-per-day","5000").header("x-remaining-today",remaining));
    }
    @Test void unknownQuotaBlocksSearchWithoutNetwork() {
        assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/listings/active",b,false)));
        server.verify();
    }
    @Test void preservesApplicationPrefixInProviderBaseUrl() throws Exception {
        var builder = RestClient.builder().baseUrl("https://example.invalid/v3/application");
        var local = MockRestServiceServer.bindTo(builder).build();
        local.expect(requestTo("https://example.invalid/v3/application/openapi-ping"))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        var adapter = new EtsyGateway(builder.build(),json,() -> "test-only");
        ledger.locked(6,1,b -> adapter.get("/openapi-ping",b,true));
        local.verify();
    }
    @Test void exhaustedRollingBudgetBlocksProbeWithoutNetwork() throws Exception {
        state(Collections.nCopies(6,1000.0),quota("100"));
        assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/openapi-ping",b,true)));
        server.verify();
    }
    @Test void reserveThresholdBlocksSearch() throws Exception {
        state(List.of(),quota("10"));
        assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/listings/active",b,false)));
        server.verify();
    }
    @Test void requestReservationSurvivesTransportFailure() throws Exception {
        server.expect(requestTo("https://example.invalid/openapi-ping"))
            .andRespond(request -> { throw new java.io.IOException("private transport failure"); });
        assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/openapi-ping",b,true)));
        ledger.locked(6,1,b -> { assertEquals(1,b.priorAttempts()); return null; });
        server.verify();
    }
    @Test void preservesLegacyLedgerEnforcesSpacingAndReconcilesHeaders() throws Exception {
        state(List.of(999.0),quota("100"));
        expect("/listings/active",Map.of("results",List.of()),"99");
        ledger.locked(6,1,b -> {
            gateway.get("/listings/active",b,false);
            assertEquals(2,b.priorAttempts()); assertEquals(99,b.number("x-remaining-today"));
            return null;
        });
        assertEquals(1000,slept.get());
        assertEquals(1_001_000,clock.get());
        server.verify();
    }
    @Test void rateLimitPersistsCooldownAndNeverRetriesOrLeaksErrorBody() throws Exception {
        server.expect(requestTo("https://example.invalid/openapi-ping")).andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","120").body("sensitive provider body"));
        var error = assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/openapi-ping",b,true)));
        assertFalse(error.getMessage().contains("sensitive"));
        assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> null));
        server.verify();
    }
    @Test void rollingWindowExpiresOldRequests() throws Exception {
        clock.set(200_000_000);
        state(List.of(1.0),quota("100"));
        ledger.locked(6,1,b -> { assertEquals(0,b.priorAttempts()); return null; });
    }
    @Test void raisedSharedLimitReusesExistingHistoryAndStillEnforcesTheNewBoundary() throws Exception {
        state(Collections.nCopies(400,1000.0),quota("4600"));
        String before=Files.readString(directory.resolve("state/budget.json"));
        assertThrows(ProviderDeferred.class,()->ledger.locked(400,2,b->null));
        ledger.locked(QuotaLedger.DAILY_REQUEST_LIMIT,2,b->{assertEquals(400,b.priorAttempts());return null;});
        assertEquals(before,Files.readString(directory.resolve("state/budget.json")),"Rechecking policy must not reset the ledger");
        state(Collections.nCopies(4499,1000.0),quota("501"));
        assertThrows(ProviderDeferred.class,()->ledger.locked(QuotaLedger.DAILY_REQUEST_LIMIT,2,b->null),"Reserve capacity for probe plus request");
        ledger.locked(QuotaLedger.DAILY_REQUEST_LIMIT,1,b->{assertEquals(4499,b.priorAttempts());return null;});
        assertThrows(ToolFailure.class,()->ledger.locked(4501,1,b->null));server.verify();
    }
    @Test void providerFailureIsNotRetriedAndDoesNotExposeBody() {
        server.expect(requestTo("https://example.invalid/openapi-ping"))
            .andRespond(withServerError().body("private provider diagnostic"));
        var error = assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/openapi-ping",b,true)));
        assertEquals("Etsy returned HTTP 500; stopped without retry.",error.getMessage());
        server.verify();
    }
    @Test void malformedSuccessStillConsumesItsReservedRequest() throws Exception {
        server.expect(requestTo("https://example.invalid/openapi-ping"))
            .andRespond(withSuccess("not-json",MediaType.APPLICATION_JSON));
        assertThrows(ToolFailure.class,() -> ledger.locked(6,1,b -> gateway.get("/openapi-ping",b,true)));
        ledger.locked(6,1,b -> { assertEquals(1,b.priorAttempts()); return null; });
        server.verify();
    }
    @Test void processLockPreventsConcurrentCollector() throws Exception {
        ledger.locked(6,1,b -> {
            assertThrows(ToolFailure.class,() -> ledger.locked(6,1,other -> null));
            return null;
        });
    }
    @Test void collectsOneThousandInTenPagesAndPreservesFormats() throws Exception {
        expect("/openapi-ping",Map.of(),"4999");
        for (int page=0;page<10;page++) page(page,page*100);
        var report = research.collect(directory.resolve("cohort"));
        assertEquals(1000,report.get("unique_listings")); assertEquals(11,report.get("requests"));
        Map<?,?> input = json.readValue(Files.readString(directory.resolve("cohort/input.json")),Map.class);
        assertEquals(1000,((List<?>)input.get("listings")).size());
        assertEquals(20_000,slept.get());
        server.verify();
    }
    @Test void overlappingPagesUseExtraPageWithoutSyntheticPadding() throws Exception {
        expect("/openapi-ping",Map.of(),"4999");
        for (int page=0;page<11;page++) page(page,Math.max(0,page*100-50));
        var report = research.collect(directory.resolve("cohort"));
        assertEquals(1000,report.get("unique_listings")); assertEquals(1100,report.get("fetched_rows"));
        assertEquals(12,report.get("requests")); server.verify();
    }
    private void page(int page,int start) {
        expect("/listings/active?keywords=halloween+printable&sort_on=score&limit=100&offset="+page*100,
            Map.of("count",10000,"results",IntStream.range(start,start+100).mapToObj(i -> Map.of("listing_id",i+1)).toList()),"4990");
    }
    @Test void emptyResultsStopWithoutPadding() throws Exception {
        expect("/openapi-ping",Map.of(),"4999");
        expect("/listings/active?keywords=halloween+printable&sort_on=score&limit=100&offset=0",
            Map.of("count",0,"results",List.of()),"4998");
        assertThrows(ToolFailure.class,() -> research.collect(directory.resolve("cohort")));
        assertFalse(Files.exists(directory.resolve("cohort/input.json"))); server.verify();
    }
    @Test void insufficientProviderQuotaStopsBeforeSearch() throws Exception {
        expect("/openapi-ping",Map.of(),"10");
        assertThrows(ToolFailure.class,() -> research.collect(directory.resolve("cohort")));
        server.verify();
    }
    @Test void existingCohortPreventsAllProviderCalls() throws Exception {
        PrivateJson.write(directory.resolve("cohort/input.json"),Map.of(),json);
        assertThrows(ToolFailure.class,() -> research.collect(directory.resolve("cohort")));
        server.verify();
    }
    @Test void insufficientBatchCapacityStopsBeforeProbe() throws Exception {
        state(Collections.nCopies(QuotaLedger.DAILY_REQUEST_LIMIT-14,1000.0),quota("4999"));
        assertThrows(ToolFailure.class,() -> research.collect(directory.resolve("cohort")));
        server.verify();
    }
    @Test void discoveryRetainsLimitOneAndFiveRequestBudget() throws Exception {
        expect("/openapi-ping",Map.of(),"4999");
        for (String phrase : List.of("halloween+printable","halloween+scavenger+hunt+printable",
                "halloween+classroom+games+printable","halloween+svg"))
            expect("/listings/active?keywords="+phrase+"&sort_on=score&limit=1&offset=0",
                Map.of("count",100,"results",List.of(Map.of("listing_id",1))),"4998");
        assertEquals(5,research.discover(directory.resolve("discovery")).get("requests"));
        Map<?,?> saved = json.readValue(Files.readString(directory.resolve("discovery/observations.json")),Map.class);
        var first = (Map<?,?>)((List<?>)saved.get("observations")).get(1);
        assertEquals("int",((Map<?,?>)first.get("field_types")).get("listing_id"));
        assertEquals(1,((Map<?,?>)first.get("sample")).get("listing_id"));
        assertTrue(((Map<?,?>)first.get("sample")).containsKey("is_digital"));
        server.verify();
    }
}
