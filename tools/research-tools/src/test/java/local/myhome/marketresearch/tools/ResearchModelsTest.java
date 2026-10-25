package local.myhome.marketresearch.tools;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ResearchModelsTest {
    @TempDir Path root;
    ObjectMapper json=new ObjectMapper();
    @Test void invalidUnicodeCannotReachEitherProviderOrReservePaidBudget() throws Exception {
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),Map.of("checkedAt",Instant.now().toString()),json);
        var settings=new ModelSettings(null,null,"gemini-3.5-flash-lite","gpt-5.4-mini",true);
        var builder=RestClient.builder().baseUrl("https://provider.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var models=new ResearchModels(builder.build(),builder.build(),settings,json,root);
        String broken=("A".repeat(159)+"\uD83C\uDF83").substring(0,160);
        for(String provider:List.of("openai","gemini")) {
            var failure=assertThrows(ToolFailure.class,()->models.askOn(provider,"test","JSON",Map.of("description",broken),java.math.BigDecimal.ONE,750_000));
            assertEquals("Model prompt contains invalid Unicode; no call sent.",failure.getMessage());
        }
        assertFalse(Files.exists(root.resolve("data/provider-ledgers/openai-stage.json")));server.verify();
    }
    @Test void geminiThoughtPartsAreNotParsedAsAnswerJsonAndUsageIsRetained() throws Exception {
        Path key=root.resolve("gemini.env");Files.writeString(key,"GEMINI_LLM_API_KEY=test-only");
        var settings=new ModelSettings(key,null,"gemini-3.5-flash-lite","gpt-5.4-mini",true);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),Map.of("checkedAt",Instant.now().toString(),"geminiMetadataHttp",200,"geminiFreeTierVerified",true),json);
        var builder=RestClient.builder().baseUrl("https://gemini.invalid");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gemini.invalid/models/gemini-3.5-flash-lite:generateContent"))
            .andRespond(withSuccess(json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(
                Map.of("thought",true,"text","Not JSON: private reasoning must not be parsed"),Map.of("text","{\"candidates\":[]}"))))),
                "usageMetadata",Map.of("promptTokenCount",100,"candidatesTokenCount",20))),MediaType.APPLICATION_JSON));
        var result=new ResearchModels(builder.build(),builder.build(),settings,json,root).askOn("gemini","test","JSON",Map.of(),new java.math.BigDecimal("2"),750_000);
        assertTrue(result.containsKey("candidates"));
        Map<?,?> ledger=json.readValue(Files.readString(root.resolve("data/provider-ledgers/gemini-free.json")),Map.class);
        assertEquals(1,((List<?>)ledger.get("events")).size());server.verify();
    }
    @Test void quotaFailureRetainsOnlySafeDiagnosticsAndNeverFallsBack() throws Exception {
        Path key=root.resolve("gemini.env");Files.writeString(key,"GEMINI_LLM_API_KEY=test-only");
        var settings=new ModelSettings(key,null,"gemini-3.5-flash-lite","gpt-5.4-mini",true);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),Map.of("checkedAt",Instant.now().toString(),"geminiMetadataHttp",200,"geminiFreeTierVerified",true),json);
        var builder=RestClient.builder().baseUrl("https://gemini.invalid");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gemini.invalid/models/gemini-3.5-flash-lite:generateContent"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"private-project-details\",\"details\":[{\"violations\":[{\"quotaValue\":\"0\",\"quotaMetric\":\"generate_content\"}]}]}}"));
        var models=new ResearchModels(builder.build(),builder.build(),settings,json,root);
        assertThrows(ToolFailure.class,()->models.askOn("gemini","test","JSON",Map.of(),new java.math.BigDecimal("2"),750_000));
        assertFalse(Files.readString(root.resolve("data/provider-ledgers/gemini-quota-error.json")).contains("private-project-details"));
        assertFalse(Files.exists(root.resolve("data/provider-ledgers/openai-stage.json")));server.verify();
    }
    @Test void explicitGeminiComparisonNeverFallsBackWhenFreeAllowanceIsExhausted() throws Exception {
        var settings=new ModelSettings(null,null,"gemini-2.5-flash-lite","gpt-5.4-mini",true);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),Map.of("checkedAt",Instant.now().toString(),
            "geminiMetadataHttp",200,"geminiFreeTierVerified",true,"openAiMetadataHttp",200),json);
        PrivateJson.write(root.resolve("data/provider-ledgers/gemini-free.json"),Map.of("day",
            java.time.LocalDate.now(java.time.ZoneId.of("America/Los_Angeles")).toString(),"calls",12),json);
        var builder=RestClient.builder().baseUrl("https://example.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var models=new ResearchModels(builder.build(),builder.build(),settings,json,root);
        assertThrows(ToolFailure.class,()->models.askOn("gemini","comparison","JSON",Map.of(),new java.math.BigDecimal("1.9995175"),750_000));
        assertFalse(Files.exists(root.resolve("data/provider-ledgers/openai-stage.json")));server.verify();
    }
    @Test void unverifiedGeminiTierUsesAuthorizedOpenaiAndRecordsTokens() throws Exception {
        Path key=root.resolve("test.env");Files.writeString(key,"OPENAI_API_KEY=test-only");
        var settings=new ModelSettings(null,key,"gemini-2.5-flash-lite","gpt-5.4-mini",false);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),
            Map.of("checkedAt",Instant.now().toString(),"openAiMetadataHttp",200,"geminiFreeTierVerified",false),json);
        var builder=RestClient.builder().baseUrl("https://example.invalid/v1");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/chat/completions"))
            .andExpect(header("Authorization","Bearer test-only"))
            .andRespond(withSuccess(json.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content","{\"recommendations\":[]}"))),
                "usage",Map.of("prompt_tokens",100,"completion_tokens",200))),MediaType.APPLICATION_JSON));
        var geminiBuilder=RestClient.builder().baseUrl("https://gemini.invalid");
        var gemini=MockRestServiceServer.bindTo(geminiBuilder).build();
        var models=new ResearchModels(builder.build(),geminiBuilder.build(),settings,json,root);
        assertTrue(models.ask("test","Return JSON",Map.of("sample","public")).containsKey("recommendations"));
        Map<?,?> ledger=json.readValue(Files.readString(root.resolve("data/provider-ledgers/openai-stage.json")),Map.class);
        assertEquals(1,((List<?>)ledger.get("calls")).size());
        server.verify();gemini.verify();
    }
    @Test void missingPreflightPreventsAllModelCalls() {
        var builder=RestClient.builder().baseUrl("https://example.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var settings=new ModelSettings(null,null,"gemini-2.5-flash-lite","gpt-5.4-mini",false);
        var models=new ResearchModels(builder.build(),builder.build(),settings,json,root);
        assertThrows(ToolFailure.class,()->models.ask("test","JSON",Map.of()));
        server.verify();
    }
    @Test void exhaustedConfirmedFreeAllowanceFallsBackWithinPaidStageBudget() throws Exception {
        Path key=root.resolve("test.env");Files.writeString(key,"OPENAI_API_KEY=test-only");
        var settings=new ModelSettings(null,key,"gemini-2.5-flash-lite","gpt-5.4-mini",true);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),
            Map.of("checkedAt",Instant.now().toString(),"openAiMetadataHttp",200,"geminiMetadataHttp",200,"geminiFreeTierVerified",true),json);
        PrivateJson.write(root.resolve("data/provider-ledgers/gemini-free.json"),Map.of("day",
            java.time.LocalDate.now(java.time.ZoneId.of("America/Los_Angeles")).toString(),"calls",12),json);
        var builder=RestClient.builder().baseUrl("https://example.invalid/v1");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/chat/completions")).andRespond(withSuccess(
            "{\"choices\":[{\"message\":{\"content\":\"{}\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20}}",MediaType.APPLICATION_JSON));
        var geminiBuilder=RestClient.builder().baseUrl("https://gemini.invalid");
        var gemini=MockRestServiceServer.bindTo(geminiBuilder).build();
        new ResearchModels(builder.build(),geminiBuilder.build(),settings,json,root).ask("fallback","JSON",Map.of());
        server.verify();gemini.verify();
    }
    @Test void confirmedFreeGeminiIsPreferredWithoutPaidCalls() throws Exception {
        Path key=root.resolve("gemini.env");Files.writeString(key,"GEMINI_LLM_API_KEY=test-only");
        var settings=new ModelSettings(key,null,"gemini-2.5-flash-lite","gpt-5.4-mini",true);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),
            Map.of("checkedAt",Instant.now().toString(),"geminiMetadataHttp",200,"geminiFreeTierVerified",true),json);
        var openaiBuilder=RestClient.builder().baseUrl("https://openai.invalid");
        var openai=MockRestServiceServer.bindTo(openaiBuilder).build();
        var builder=RestClient.builder().baseUrl("https://gemini.invalid");
        var gemini=MockRestServiceServer.bindTo(builder).build();
        gemini.expect(requestTo("https://gemini.invalid/models/gemini-2.5-flash-lite:generateContent"))
            .andExpect(header("x-goog-api-key","test-only")).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"groups\\\":[]}\"}]}}],\"usageMetadata\":{\"totalTokenCount\":100}}",
                MediaType.APPLICATION_JSON));
        var result=new ResearchModels(openaiBuilder.build(),builder.build(),settings,json,root).ask("free","JSON",Map.of());
        assertTrue(result.containsKey("groups"));
        assertFalse(Files.exists(root.resolve("data/provider-ledgers/openai-stage.json")));
        Map<?,?> ledger=json.readValue(Files.readString(root.resolve("data/provider-ledgers/gemini-free.json")),Map.class);
        assertEquals(1,((Number)ledger.get("calls")).intValue());
        gemini.verify();openai.verify();
    }
}
