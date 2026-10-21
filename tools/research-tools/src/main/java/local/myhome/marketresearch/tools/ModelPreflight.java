package local.myhome.marketresearch.tools;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Metadata checks are non-generative. No provider body/account IDs enter the public result. */
final class ModelPreflight {
    private final RestClient gemini,openai;
    private final ObjectMapper json;
    private final ModelSettings settings;
    ModelPreflight(RestClient gemini,RestClient openai,ObjectMapper json,ModelSettings settings) {
        this.gemini=gemini;this.openai=openai;this.json=json;this.settings=settings;
    }
    Map<String,Object> run(Path root) throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("checkedAt",Instant.now().toString());
        result.put("openAiModel",settings.openAiModel());result.put("geminiModel",settings.geminiModel());
        result.put("geminiMetadataHttp",get(gemini,"/models/"+settings.geminiModel(),"x-goog-api-key",
            () -> SecretFiles.get(settings.geminiCredentialsFile(),"GEMINI_LLM_API_KEY")));
        result.put("geminiAccountQuota","Not exposed by model metadata; AI Studio/project billing access required.");
        result.put("geminiAccountUsage","Unknown: no account-level usage credential configured.");
        result.put("geminiFreeTierVerified",settings.geminiFreeConfirmed());
        result.put("openAiMetadataHttp",get(openai,"/models/"+settings.openAiModel(),"Authorization",
            () -> "Bearer "+SecretFiles.get(settings.openAiCredentialsFile(),"OPENAI_API_KEY")));
        long midnight=LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        result.put("openAiUsageHttp",get(openai,"/organization/usage/completions?start_time="+midnight,"Authorization",
            () -> "Bearer "+SecretFiles.get(settings.openAiCredentialsFile(),"OPENAI_API_KEY")));
        result.put("openAiAccountUsage","Requires an organization Admin API key; unavailable counters remain unknown.");
        result.put("openAiStageBudgetUsd",2);
        result.put("generationCalls",0);
        PrivateJson.write(root.resolve("data/provider-ledgers/model-preflight.json"),result,json);
        return result;
    }
    private int get(RestClient client,String path,String header,java.util.function.Supplier<String> key) {
        try { return client.get().uri(path).header(header,key.get()).exchange((req,res)->res.getStatusCode().value()); }
        catch (RuntimeException e) { return 0; }
    }
}
