package local.myhome.marketresearch.tools;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ProductComparisonTest {
    @TempDir Path root;
    ObjectMapper json=new ObjectMapper();
    @Test void corpusTamperingFailsBeforeProviderDispatch() throws Exception {
        ComparisonFixture.create(root,json);
        Files.writeString(root.resolve("data/halloween-run/comparison/listings.json"),"[]");
        assertThrows(ToolFailure.class,()->new FrozenCorpus(root,json));
    }
    @Test void independentPipelineStoresRawAndAcceptedOutputsWithoutAgentConclusions() throws Exception {
        ComparisonFixture.create(root,json);var corpus=new FrozenCorpus(root,json);var count=new AtomicInteger();
        Path output=root.resolve("output");
        var pipeline=new ProductComparison(corpus,json,(purpose,instructions,evidence)->{
            count.incrementAndGet();assertFalse(instructions.contains("AGENT_PRIVATE_CONCLUSIONS"));
            assertFalse(json.writeValueAsString(evidence).contains("AGENT_PRIVATE_CONCLUSIONS"));
            if(purpose.contains("scan"))return Map.of("candidates",List.of(Map.of("concept","Original pack","listingIds",List.of("1"))),"limitations",List.of());
            if(purpose.contains("critical"))return Map.of("audit",List.of(),"final",ComparisonFixture.report());
            return ComparisonFixture.report();
        },output);
        assertEquals("SUCCEEDED",pipeline.run("gemini").get("status"));assertEquals(3,count.get());
        assertTrue(Files.exists(output.resolve("product-definition-raw.json")));
        assertTrue(Files.exists(output.resolve("accepted-report.json")));
        assertTrue(Files.readString(output.resolve("accepted-report.json")).contains("API_PIPELINE"));
    }
    @Test void validationDoesNotRankProductsButRejectsUnknownEvidenceAndWrongArithmetic() throws Exception {
        ComparisonFixture.create(root,json);var corpus=new FrozenCorpus(root,json);
        var pipeline=new ProductComparison(corpus,json,(a,b,c)->Map.of(),root.resolve("output"));
        var report=ComparisonFixture.report();assertDoesNotThrow(()->pipeline.validateReport(report));
        var product=(Map<String,Object>)((List<?>)report.get("products")).getFirst();
        product.put("monthlySalesNeeded",8);assertThrows(ToolFailure.class,()->pipeline.validateReport(report));
        assertThrows(ToolFailure.class,()->corpus.citations(Map.of("listingIds",List.of("999"))));
    }
    @Test void sharedAdditionalAllowanceDoesNotResetWithAnotherRun() throws Exception {
        ComparisonFixture.create(root,json);
        var first=ProductComparison.spendingCeiling(root,json);
        assertEquals(0,new BigDecimal("1.9995175").compareTo(first));
        PrivateJson.write(root.resolve("data/provider-ledgers/openai-stage.json"),Map.of("committedUsd",1,"calls",List.of()),json);
        assertEquals(first,ProductComparison.spendingCeiling(root,json));
        assertThrows(ToolFailure.class,()->new ModelBudget(root.resolve("data/provider-ledgers"),json,first,750_000)
            .openai("too-large",750001,8000,()->{fail("Must not dispatch");return null;}));
    }
    @Test void allowsOneGroundingRepairButNeverAnUnboundedRetryLoop() throws Exception {
        ComparisonFixture.create(root,json);var corpus=new FrozenCorpus(root,json);var count=new AtomicInteger();
        var pipeline=new ProductComparison(corpus,json,(purpose,instructions,evidence)->{
            count.incrementAndGet();
            return Map.of("candidates",List.of(Map.of("concept","Invented","listingIds",List.of("999"))),"limitations",List.of());
        },root.resolve("failed"));
        assertThrows(ToolFailure.class,()->pipeline.run("gemini"));assertEquals(2,count.get());
        assertTrue(Files.exists(root.resolve("failed/scan-0-raw.json")));
        assertTrue(Files.exists(root.resolve("failed/scan-0-repair-raw.json")));
        assertFalse(Files.exists(root.resolve("failed/accepted-report.json")));
    }
    @Test void malformedJsonGetsOnlyOneFormatRepairThenNormalValidation() throws Exception {
        ComparisonFixture.create(root,json);var corpus=new FrozenCorpus(root,json);var count=new AtomicInteger();
        var pipeline=new ProductComparison(corpus,json,(purpose,instructions,evidence)->{
            count.incrementAndGet();
            if(purpose.equals("comparison-scan-0"))throw new MalformedModelResponse("{incomplete");
            if(purpose.contains("scan"))return Map.of("candidates",List.of(Map.of("concept","Original","listingIds",List.of("1"))),"limitations",List.of());
            if(purpose.contains("critical"))return Map.of("audit",List.of(),"final",ComparisonFixture.report());
            return ComparisonFixture.report();
        },root.resolve("repaired"));
        assertEquals("SUCCEEDED",pipeline.run("gemini").get("status"));assertEquals(4,count.get());
    }
}
