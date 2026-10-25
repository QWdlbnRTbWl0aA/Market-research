package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class ChatGptRoutingTest {
    @TempDir Path root;
    ObjectMapper json=new ObjectMapper();
    ProviderAccess access(boolean paid) throws Exception {
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),Map.of("checkedAt",Instant.now().toString()),json);
        var env=new MockEnvironment().withProperty("research.runtime.root",root.toString())
            .withProperty("research.runtime.live-enabled","true").withProperty("research.runtime.paid-enabled",Boolean.toString(paid))
            .withProperty("research.runtime.model-provider","openai").withProperty("research.runtime.openai-allowance-id","existing-allowance");
        return new ProviderAccess(env,json);
    }
    @ParameterizedTest
    @ValueSource(strings={"discovery-refinement","product-definition","product-definition-review"})
    void chatGptIsDefaultForContinuousStagesWithoutRequiringPaidApiEnabled(String stage) throws Exception {
        try(var primary=mockConstruction(ChatGptModels.class,(model,context)->{
            when(model.ask(anyString(),anyString(),any())).thenReturn(Map.of("answer","primary"));
            when(model.usage()).thenReturn(Map.of("provider","chatgpt","requestSent",true));
        });var fallback=mockConstruction(ResearchModels.class,(model,context)->when(model.usage()).thenReturn(Map.of("tokenUsageKnown",false)))) {
            assertEquals("primary",access(false).reason(stage,"JSON",Map.of()).get("answer"));
            verify(fallback.constructed().getFirst(),never()).askOn(anyString(),anyString(),anyString(),any(),any(),anyInt(),any());
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"discovery-review","product-definition","product-definition-review"})
    void definitiveUnavailabilityUsesTheExistingNamedAllowanceOnly(String stage) throws Exception {
        try(var primary=mockConstruction(ChatGptModels.class,(model,context)->{
            when(model.ask(anyString(),anyString(),any())).thenThrow(new ChatGptUnavailable("SIGN_IN_REQUIRED"));
            when(model.usage()).thenReturn(Map.of("provider","chatgpt","requestSent",false));
        });var fallback=mockConstruction(ResearchModels.class,(model,context)->{
            when(model.usage()).thenReturn(Map.of("provider","openai","requestSent",true));
            when(model.askOn(anyString(),anyString(),anyString(),any(),any(),anyInt(),any())).thenReturn(Map.of("answer","fallback"));
        })) {
            var access=access(true);assertEquals("fallback",access.reason(stage,"JSON",Map.of()).get("answer"));
            verify(fallback.constructed().getFirst()).askOn(eq("openai"),anyString(),anyString(),any(),any(),eq(750_000),eq("existing-allowance"));
            assertEquals("SIGN_IN_REQUIRED",access.lastUsage().get("fallbackReason"));
            assertEquals(2,((List<?>)access.lastUsage().get("attempts")).size());
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"discovery-business","product-definition","product-definition-review"})
    void uncertainPrimaryFailureNeverPurchasesAnApiRetry(String stage) throws Exception {
        try(var primary=mockConstruction(ChatGptModels.class,(model,context)->{
            when(model.ask(anyString(),anyString(),any())).thenThrow(new ToolFailure("Stream interrupted"));
            when(model.usage()).thenReturn(Map.of("provider","chatgpt","requestSent",true,"tokenUsageKnown",false));
        });var fallback=mockConstruction(ResearchModels.class,(model,context)->when(model.usage()).thenReturn(Map.of("tokenUsageKnown",false)))) {
            assertThrows(ToolFailure.class,()->access(true).reason(stage,"JSON",Map.of()));
            verify(fallback.constructed().getFirst(),never()).askOn(anyString(),anyString(),anyString(),any(),any(),anyInt(),any());
        }
    }
}
