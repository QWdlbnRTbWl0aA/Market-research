package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ChatGptModelsTest {
    @TempDir Path root;
    ObjectMapper json=new ObjectMapper();ChatGptModels models;MockRestServiceServer server;
    @BeforeEach void setup() throws Exception {
        var session=mock(ChatGptSession.class);when(session.accessToken()).thenReturn("fixture-access");when(session.model()).thenReturn("account-model");
        var builder=RestClient.builder().baseUrl("https://api.invalid/v1");server=MockRestServiceServer.bindTo(builder).build();
        models=new ChatGptModels(builder.build(),session,json,root,1);
    }
    String completed(String answer) {
        return "data: "+json.writeValueAsString(Map.of("type","response.completed","response",Map.of("status","completed",
            "output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",answer)))),
            "usage",Map.of("input_tokens",100,"output_tokens",50))))+"\n\n";
    }
    List<?> calls() throws Exception {return (List<?>)json.readValue(Files.readString(root.resolve("data/provider-ledgers/chatgpt/usage.json")),Map.class).get("calls");}
    @Test void supportedResponsesContractWaitsForCompletionAndTracksPlanUsageSeparately() throws Exception {
        server.expect(requestTo("https://api.invalid/v1/responses")).andExpect(header("Authorization","Bearer fixture-access"))
            .andExpect(jsonPath("$.store").value(false)).andExpect(jsonPath("$.stream").value(true))
            .andExpect(jsonPath("$.max_output_tokens").doesNotExist()).andExpect(jsonPath("$.text.format.type").value("json_schema"))
            .andRespond(withSuccess("data: {\"type\":\"response.output_text.delta\",\"delta\":\"unconfirmed\"}\n\n"+completed("{\"summary\":\"confirmed\"}"),MediaType.TEXT_EVENT_STREAM));
        assertEquals("confirmed",models.ask("discovery-business#fixture","Return JSON",Map.of()).get("summary"));
        assertEquals(100,models.usage().get("inputTokens"));assertEquals("chatgpt",models.usage().get("provider"));
        assertFalse(models.usage().containsKey("estimatedUsd"));
        assertEquals(1,calls().size());assertThrows(ChatGptUnavailable.class,()->models.ask("next","JSON",Map.of()));
        assertEquals(1,calls().size(),"Local daily quota does not erase history");assertFalse(Files.exists(root.resolve("data/provider-ledgers/openai-stage.json")));server.verify();
    }
    @Test void interruptedStreamsRemainUncertainAndCannotAutomaticallyFallBack() throws Exception {
        server.expect(requestTo("https://api.invalid/v1/responses"))
            .andRespond(withSuccess("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n",MediaType.TEXT_EVENT_STREAM));
        ToolFailure failure=assertThrows(ToolFailure.class,()->models.ask("discovery-review","JSON",Map.of()));
        assertFalse(failure instanceof ChatGptUnavailable);
        assertEquals("REQUEST_STARTED_OR_UNCERTAIN",((Map<?,?>)calls().getFirst()).get("status"));server.verify();
    }
    @Test void explicitSubscriptionLimitFailureIsSafeForGuardedFallback() throws Exception {
        server.expect(requestTo("https://api.invalid/v1/responses")).andRespond(withSuccess(
            "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n",MediaType.TEXT_EVENT_STREAM));
        var failure=assertThrows(ChatGptUnavailable.class,()->models.ask("discovery-business","JSON",Map.of()));
        assertEquals("subscription_sharing_usage_limit_exceeded",failure.reason);
        assertEquals("DEFINITIVELY_UNAVAILABLE",((Map<?,?>)calls().getFirst()).get("status"));server.verify();
    }
    @Test void completedMalformedJsonRetainsSettledUsageAndDoesNotPermitFallback() throws Exception {
        server.expect(requestTo("https://api.invalid/v1/responses")).andRespond(withSuccess(completed("not JSON"),MediaType.TEXT_EVENT_STREAM));
        assertThrows(MalformedModelResponse.class,()->models.ask("discovery-business","JSON",Map.of()));
        assertEquals(true,models.usage().get("tokenUsageKnown"));assertEquals("COMPLETED",((Map<?,?>)calls().getFirst()).get("status"));server.verify();
    }
}
