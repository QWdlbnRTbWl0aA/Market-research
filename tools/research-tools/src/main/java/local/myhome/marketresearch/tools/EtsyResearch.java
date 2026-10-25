package local.myhome.marketresearch.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

public final class EtsyResearch {
    private final EtsyGateway gateway;
    private final QuotaLedger ledger;
    private final ObjectMapper json;
    public EtsyResearch(EtsyGateway gateway, QuotaLedger ledger, ObjectMapper json) {
        this.gateway = gateway; this.ledger = ledger; this.json = json;
    }

    public Map<String,Object> collect(Path destination) throws Exception {
        if (Files.exists(destination.resolve("input.json")))
            throw new ToolFailure("A cohort already exists; replay it instead of recollecting.");
        PrivateJson.directory(destination);
        return ledger.locked(QuotaLedger.DAILY_REQUEST_LIMIT,15,budget -> {
            int prior = budget.priorAttempts();
            long start = System.nanoTime();
            var quotaResponse = gateway.get("/openapi-ping",budget,true);
            PrivateJson.write(destination.resolve("quota-probe.json"),quotaResponse,json);
            if (budget.number("x-remaining-today") < 24 || budget.number("x-limit-per-second") < 1)
                throw new ToolFailure("Insufficient observed quota for collection and reserve.");
            Map<String,Object> manifest = new LinkedHashMap<>();
            manifest.put("query","halloween printable"); manifest.put("sort","score");
            manifest.put("limit",100); manifest.put("target_unique",1000);
            manifest.put("started_at",Instant.now().toString());
            manifest.put("before_search_quota",budget.quota());
            manifest.put("prior_local_24h_attempts",prior);
            manifest.put("rolling_local_cap",QuotaLedger.DAILY_REQUEST_LIMIT); manifest.put("max_new_requests",15);
            manifest.put("selection","ranked convenience sample, not a demand estimate");
            Map<Long,Map<?,?>> unique = new LinkedHashMap<>();
            List<Map<String,Object>> pages = new ArrayList<>();
            int fetched = 0;
            for (int page=0; page<14; page++) {
                var body = gateway.get(search("halloween printable",100,page*100),budget,false);
                PrivateJson.write(destination.resolve("page-%02d.json".formatted(page)),body,json);
                if (!(body.get("results") instanceof List<?> results))
                    throw new ToolFailure("Unexpected Etsy results shape; stopped without retry.");
                fetched += results.size();
                Map<String,Object> pageInfo = new LinkedHashMap<>();
                pageInfo.put("offset",page*100); pageInfo.put("returned",results.size());
                pageInfo.put("count",body.get("count")); pageInfo.put("quota",budget.quota());
                pages.add(pageInfo);
                for (var item : results) if (item instanceof Map<?,?> row) {
                    Long id = listingId(row.get("listing_id"));
                    if (id != null && unique.size()<1000) unique.putIfAbsent(id,row);
                }
                manifest.put("pages",pages); manifest.put("fetched_rows",fetched);
                manifest.put("unique_selected",unique.size()); manifest.put("requests",1+pages.size());
                manifest.put("after_quota",budget.quota());
                manifest.put("elapsed_seconds",(System.nanoTime()-start)/1_000_000_000.0);
                PrivateJson.write(destination.resolve("manifest.json"),manifest,json);
                if (unique.size()==1000 || results.isEmpty()) break;
            }
            if (unique.size()!=1000)
                throw new ToolFailure("Collection did not reach 1000 unique listings within budget; no synthetic padding.");
            PrivateJson.write(destination.resolve("input.json"),Map.of("query","halloween printable",
                "observedAt",Instant.now().toString(),"listings",new ArrayList<>(unique.values())),json);
            return Map.of("unique_listings",unique.size(),"fetched_rows",fetched,
                "requests",1+pages.size(),"after_quota",budget.quota());
        });
    }

    public Map<String,Object> discover(Path destination) throws Exception {
        PrivateJson.directory(destination);
        return ledger.locked(QuotaLedger.DAILY_REQUEST_LIMIT,5,budget -> {
            PrivateJson.write(destination.resolve("quota-probe.json"),gateway.get("/openapi-ping",budget,true),json);
            List<Map<String,Object>> observations = new ArrayList<>();
            observations.add(Map.of("operation","quota-probe","quota",budget.quota()));
            int index = 0;
            for (var phrase : List.of("halloween printable","halloween scavenger hunt printable",
                    "halloween classroom games printable","halloween svg")) {
                var response = gateway.get(search(phrase,1,0),budget,false);
                PrivateJson.write(destination.resolve("search-"+index++ +".json"),response,json);
                var observation = new LinkedHashMap<String,Object>();
                observation.put("query",phrase); observation.put("count",response.get("count"));
                if (!(response.get("results") instanceof List<?> results))
                    throw new ToolFailure("Unexpected Etsy results shape.");
                observation.put("returned",results.size()); observation.put("quota",budget.quota());
                Map<?,?> sample = results.isEmpty() ? Map.of() : (Map<?,?>)results.getFirst();
                observation.put("fields",sample.keySet().stream().map(Object::toString).sorted().toList());
                Map<String,String> types = new TreeMap<>();
                sample.forEach((key,value) -> types.put(key.toString(),jsonType(value)));
                observation.put("field_types",types);
                Map<String,Object> selected = new LinkedHashMap<>();
                for (var key : List.of("listing_id","title","listing_type","is_digital","num_favorers","views",
                        "price","tags","original_creation_timestamp","last_modified_timestamp"))
                    selected.put(key,sample.get(key));
                observation.put("sample",selected);
                observations.add(observation);
            }
            var report = Map.of("checked_at",Instant.now().toString(),"requests_this_session",5,"observations",observations);
            PrivateJson.write(destination.resolve("observations.json"),report,json);
            return Map.of("requests",5,"after_quota",budget.quota());
        });
    }
    private static String search(String query,int limit,int offset) {
        return "/listings/active?keywords="+URLEncoder.encode(query,StandardCharsets.UTF_8)
            +"&sort_on=score&limit="+limit+"&offset="+offset;
    }
    private static Long listingId(Object value) {
        if (!(value instanceof Number)) return null;
        try {
            long id = new java.math.BigDecimal(value.toString()).longValueExact();
            return id>0 ? id : null;
        } catch (ArithmeticException | NumberFormatException e) { return null; }
    }
    private static String jsonType(Object value) {
        if (value == null) return "NoneType";
        if (value instanceof Boolean) return "bool";
        if (value instanceof String) return "str";
        if (value instanceof List<?>) return "list";
        if (value instanceof Map<?,?>) return "dict";
        if (value instanceof Float || value instanceof Double || value instanceof java.math.BigDecimal) return "float";
        return value instanceof Number ? "int" : "unknown";
    }
}
