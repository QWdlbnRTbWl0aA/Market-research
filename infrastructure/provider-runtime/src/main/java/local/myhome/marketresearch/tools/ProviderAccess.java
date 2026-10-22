package local.myhome.marketresearch.tools;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

/** Shared infrastructure only: credentials, conservative ledgers and transport. No business decisions. */
public class ProviderAccess {
    private final Environment env;
    private final ObjectMapper json;
    private final Path root;
    private final ThreadLocal<Map<String,Object>> lastUsage=new ThreadLocal<>();
    public Map<String,Object> lastUsage(){return lastUsage.get();}
    public ProviderAccess(Environment env, ObjectMapper json) {
        this.env=env; this.json=json;
        root=Path.of(env.getProperty("research.runtime.root",".")).toAbsolutePath().normalize();
    }
    public Map<String,Object> search(String query,int limit,int offset) throws Exception {
        if(limit<1 || limit>100 || offset<0 || offset>12000)throw new IllegalArgumentException("Invalid page");
        String q=java.net.URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8);
        return etsy("/listings/active?keywords="+q+"&limit="+limit+"&offset="+offset+"&sort_on=score");
    }
    public Map<String,Object> shop(String id) throws Exception {
        if(!id.matches("[0-9]+"))throw new IllegalArgumentException("Invalid shop");
        return etsy("/shops/"+id);
    }
    public Map<String,Object> shopReviews(String id) throws Exception {
        if(!id.matches("[0-9]+"))throw new IllegalArgumentException("Invalid shop");
        return etsy("/shops/"+id+"/reviews?limit=25");
    }
    public Map<String,Object> listingReviews(String id) throws Exception {
        if(!id.matches("[0-9]+"))throw new IllegalArgumentException("Invalid listing");
        return etsy("/listings/"+id+"/reviews?limit=25");
    }
    public Map<String,Object> etsy(String path) throws Exception {
        if(!env.getProperty("research.runtime.live-enabled",Boolean.class,false))
            throw new ToolFailure("Live providers disabled");
        var gateway=new EtsyGateway(ProviderHttp.client("https://openapi.etsy.com/v3/application",env),json,
            ()->EtsyCredentials.load(path("research.tools.credentials-file"),env));
        var ledger=new QuotaLedger(root.resolve("data/etsy-discovery"),json,System::currentTimeMillis,Thread::sleep);
        return ledger.locked(env.getProperty("research.runtime.etsy-daily-cap",Integer.class,400),2,b->{
            // Recheck provider allowance before each small operation; all attempts share the existing ledger.
            gateway.get("/openapi-ping",b,true);
            return gateway.get(path,b,false);
        });
    }
    public Map<String,Object> reason(String purpose,String instruction,Object evidence) throws Exception {
        if(!env.getProperty("research.runtime.live-enabled",Boolean.class,false))
            throw new ToolFailure("Live providers disabled");
        var settings=new ModelSettings(path("research.models.gemini-credentials-file"),
            path("research.models.open-ai-credentials-file"),
            env.getProperty("research.models.gemini-model","gemini-3.5-flash-lite"),
            env.getProperty("research.models.open-ai-model","gpt-5.4-mini"),
            env.getProperty("research.models.gemini-free-confirmed",Boolean.class,false));
        var models=new ResearchModels(ProviderHttp.client("https://api.openai.com/v1",env),
            ProviderHttp.client("https://generativelanguage.googleapis.com/v1beta",env),settings,json,root);
        Path checked=root.resolve("data/provider-ledgers/model-preflight.json");
        if(!java.nio.file.Files.exists(checked)||java.nio.file.Files.getLastModifiedTime(checked).toInstant()
                .isBefore(java.time.Instant.now().minusSeconds(23*3600)))
            new ModelPreflight(ProviderHttp.client("https://generativelanguage.googleapis.com/v1beta",env),
                ProviderHttp.client("https://api.openai.com/v1",env),json,settings).run(root);
        String route=env.getProperty("research.runtime.model-provider","gemini");
        if(!env.getProperty("research.runtime.paid-enabled",Boolean.class,false)) {
            if("openai".equals(route))throw new ToolFailure("Paid calls are disabled");
            route="gemini";
        }
        String allowance=env.getProperty("research.runtime.openai-allowance-id");
        if(!"gemini".equals(route)&&(allowance==null||allowance.isBlank()))
            throw new ToolFailure("Select a fresh OpenAI allowance explicitly");
        String invocation=java.util.UUID.randomUUID().toString();
        String observationTime=java.time.Instant.now().toString();
        long started=System.nanoTime();boolean succeeded=false;
        try {
            var result=models.askOn(route,purpose+"#"+invocation,
                instruction+"\nResearch observation time (UTC): "+observationTime,evidence,BigDecimal.ONE,750_000,allowance);
            succeeded=true;return result;
        } finally {
            var record=new LinkedHashMap<String,Object>(models.usage());
            record.put("invocationId",invocation);record.put("stage",purpose);record.put("at",java.time.Instant.now().toString());
            record.put("observationTime",observationTime);
            record.put("durationMillis",(System.nanoTime()-started)/1_000_000);record.put("status",succeeded?"SUCCEEDED":"FAILED_OR_DEFERRED");
            lastUsage.set(record);
            PrivateJson.write(root.resolve("data/provider-ledgers/stage-usage/"+invocation+".json"),record,json);
        }
    }
    private Path path(String name) {
        String value=env.getProperty(name);
        return value==null||value.isBlank() ? null : Path.of(value);
    }
}
