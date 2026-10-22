package local.myhome.marketresearch.tools;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Read-only adapter; no HTTP retry and no provider error body reaches logs/output. */
public final class EtsyGateway {
    private static final Set<String> HEADERS = Set.of("x-limit-per-second","x-limit-per-day",
        "x-remaining-today","x-remaining-this-second","x-remaining-this-secon","retry-after");
    private final RestClient client;
    private final ObjectMapper json;
    private final Supplier<String> credentials;

    public EtsyGateway(RestClient client, ObjectMapper json, Supplier<String> credentials) {
        this.client = client; this.json = json; this.credentials = credentials;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> get(String path, QuotaLedger.Budget budget, boolean probe) throws Exception {
        String key = credentials.get(); // Configuration failures must not reserve a provider request.
        budget.reserve(probe);
        try {
            return client.get().uri(path).header("x-api-key",key).exchange((request,response) -> {
                Map<String,String> headers = new TreeMap<>();
                HEADERS.forEach(name -> {
                    String value = response.getHeaders().getFirst(name);
                    if (value != null) headers.put(name,value);
                });
                int status = response.getStatusCode().value();
                budget.reconcile(headers,status);
                if (!response.getStatusCode().is2xxSuccessful())
                    throw new ToolFailure("Etsy returned HTTP "+status+"; stopped without retry.");
                return json.readValue(response.getBody(),Map.class);
            });
        } catch (ToolFailure e) { throw e; }
        catch (RuntimeException e) { throw new ToolFailure("Etsy transport or response failed; stopped without retry."); }
    }
}
