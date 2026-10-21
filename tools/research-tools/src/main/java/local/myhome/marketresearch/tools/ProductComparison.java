package local.myhome.marketresearch.tools;

import java.math.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Evaluation CLI pipeline, not a live durable orchestrator workflow or deterministic business recommender. */
final class ProductComparison {
    @FunctionalInterface interface ModelCall {Map<String,Object> ask(String purpose,String instructions,Object evidence) throws Exception;}
    private final FrozenCorpus corpus;
    private final ObjectMapper json;
    private final ModelCall model;
    private final Path output;
    private final List<Map<String,Object>> calls=new ArrayList<>();
    ProductComparison(FrozenCorpus corpus,ObjectMapper json,ModelCall model,Path output){this.corpus=corpus;this.json=json;this.model=model;this.output=output;}
    static BigDecimal spendingCeiling(Path root,ObjectMapper json) throws Exception {
        Path directory=root.resolve("data/provider-ledgers"),file=directory.resolve("agent-comparison-budget.json");
        PrivateJson.directory(directory);
        // Serialize initialization too: a restart/another provider run cannot allocate another USD 1.63.
        try(var channel=java.nio.channels.FileChannel.open(directory.resolve("agent-comparison-budget.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=channel.tryLock()) {
            if(lock==null)throw new ToolFailure("Comparison budget initialization is already running.");
            if(Files.exists(file)) {
                var state=json.readValue(Files.readString(file),Map.class);
                return new BigDecimal(state.get("ceilingUsd").toString());
            }
            var ledger=json.readValue(Files.readString(directory.resolve("openai-stage.json")),Map.class);
            BigDecimal start=new BigDecimal(ledger.get("committedUsd").toString());
            BigDecimal ceiling=start.add(new BigDecimal("1.63")).min(new BigDecimal("2"));
            PrivateJson.write(file,Map.of("at",Instant.now().toString(),"startingCommittedUsd",start,
                "maximumAdditionalUsd",new BigDecimal("1.63"),"ceilingUsd",ceiling),json);
            return ceiling;
        }
    }
    Map<String,Object> run(String provider) throws Exception {
        PrivateJson.directory(output);
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("origin","API_PIPELINE");manifest.put("provider",provider);
        manifest.put("protocol",ProductReasoning.VERSION);manifest.put("sourceHashes",corpus.hashes);
        manifest.put("startedAt",Instant.now().toString());manifest.put("calls",calls);manifest.put("status","RUNNING");
        try {
            var rows=corpus.compactListings();List<Map<String,Object>> scans=new ArrayList<>();
            for(int offset=0;offset<rows.size();offset+=1667) {
                var shard=rows.subList(offset,Math.min(offset+1667,rows.size()));
                Set<String> allowed=new HashSet<>();shard.forEach(row->allowed.add(row.get("id").toString()));
                corpus.reviews.values().stream().filter(r->Boolean.TRUE.equals(r.get("listingInCorpus")))
                    .forEach(r->allowed.add(r.get("listingId").toString()));
                var scan=checked("scan-"+offset,ProductReasoning.SCAN,Map.of("context",corpus.context(),"listings",shard),value->{
                    corpus.citations(value);
                    if(!(value.get("candidates") instanceof List<?> candidates)||candidates.isEmpty()||candidates.size()>5)
                        throw new ToolFailure("Invalid candidate scan.");
                    for(var raw:candidates)if(!(raw instanceof Map<?,?> candidate)||!(candidate.get("listingIds") instanceof List<?> ids)
                            ||ids.isEmpty()||ids.size()>5||ids.stream().anyMatch(id->id==null||!allowed.contains(id.toString())))
                        throw new ToolFailure("Candidate needs a listing from the shard or explicit in-corpus review context.");
                });
                scans.add(scan);
            }
            var dossier=corpus.dossier(scans);PrivateJson.write(output.resolve("retrieved-evidence.json"),dossier,json);
            var draft=checked("product-definition",ProductReasoning.DEFINE,Map.of("scans",scans,"evidence",dossier),this::validateReport);
            var audited=checked("critical-review",ProductReasoning.AUDIT,Map.of("draft",draft,"evidence",dossier),value->{
                corpus.citations(value);
                if(!(value.get("audit") instanceof List<?>)||!(value.get("final") instanceof Map<?,?> report))throw new ToolFailure("Invalid critical review.");
                Map<String,Object> mapped=new LinkedHashMap<>();report.forEach((key,item)->mapped.put(key.toString(),item));validateReport(mapped);
            });
            var value=(Map<?,?>)audited.get("final");
            Map<String,Object> report=new LinkedHashMap<>();value.forEach((key,item)->report.put(key.toString(),item));validateReport(report);
            var accepted=Map.of("origin","API_PIPELINE","provider",provider,"protocol",ProductReasoning.VERSION,
                "sourceHashes",corpus.hashes,"validation","Schema/evidence identity/arithmetic only; claims require analytical review, not code scoring.","report",report);
            PrivateJson.write(output.resolve("accepted-report.json"),accepted,json);manifest.put("status","SUCCEEDED");
            manifest.put("products",((List<?>)report.get("products")).size());return manifest;
        } finally {
            manifest.put("finishedAt",Instant.now().toString());if(!"SUCCEEDED".equals(manifest.get("status")))manifest.put("status","FAILED");
            PrivateJson.write(output.resolve("manifest.json"),manifest,json);
        }
    }
    private Map<String,Object> checked(String purpose,String instructions,Object evidence,java.util.function.Consumer<Map<String,Object>> validate) throws Exception {
        Map<String,Object> response=null;Object previous;Object sources;String failure;
        try {response=call(purpose,instructions,evidence);validate.accept(response);return response;}
        catch(MalformedModelResponse malformed) {previous=FrozenCorpus.clip(malformed.raw,40_000);sources=evidence;failure="Invalid JSON syntax";}
        catch(ToolFailure invalid) {
            if(response==null)throw invalid; // Provider/quota/transport errors never trigger automatic retries.
            previous=response;sources=corpus.repairEvidence(response);failure=invalid.getMessage();
        }
        var repaired=call(purpose+"-repair",instructions+"\nCorrect the previous response's validation failure. Return the same schema. Remove unsupported claims/citations; do not invent replacements.",
            Map.of("previousResponse",previous,"validationFailure",failure,"sources",sources));
        validate.accept(repaired);return repaired;
    }
    private Map<String,Object> call(String purpose,String instructions,Object evidence) throws Exception {
        byte[] input=json.writer().with(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsBytes(Map.of("instructions",instructions,"evidence",evidence));
        String digest=FrozenCorpus.sha(input);long start=System.nanoTime();
        Map<String,Object> event=new LinkedHashMap<>();event.put("purpose",purpose);event.put("inputSha256",digest);
        event.put("startedAt",Instant.now().toString());event.put("inputBytes",input.length);event.put("status","SENT_OR_UNCERTAIN");calls.add(event);
        PrivateJson.write(output.resolve(purpose+"-request.json"),Map.of("instructions",instructions,"evidence",evidence),json);
        try {
            System.out.println("{\"phase\":\""+purpose+"\"}");
            var response=model.ask("comparison-"+purpose,instructions,evidence);
            PrivateJson.write(output.resolve(purpose+"-raw.json"),response,json);event.put("status","RETURNED");return response;
        } finally {event.put("elapsedMillis",(System.nanoTime()-start)/1_000_000);}
    }
    void validateReport(Map<String,Object> report) {
        corpus.citations(report);
        if(!report.keySet().equals(Set.of("products","declinedAlternatives","limitations"))
                ||!(report.get("products") instanceof List<?> products)||products.isEmpty()||products.size()>3
                ||!(report.get("declinedAlternatives") instanceof List<?>)||!(report.get("limitations") instanceof List<?>))
            throw new ToolFailure("Invalid product report schema.");
        Set<String> ids=new HashSet<>();
        for(var raw:products) {
            if(!(raw instanceof Map<?,?> product))throw new ToolFailure("Invalid product definition.");
            for(String key:List.of("id","name","audience","useCase","differentiation","seasonality","automationPlan","manualWorkAssumptions","confidence"))
                if(!(product.get(key) instanceof String text)||text.isBlank())throw new ToolFailure("Incomplete product definition.");
            if(!ids.add(product.get("id").toString()))throw new ToolFailure("Duplicate product identity.");
            for(String key:List.of("deliverables","qaPlan","evidence","counterevidence"))
                if(!(product.get(key) instanceof List<?> list)||list.isEmpty())throw new ToolFailure("Missing product evidence/requirements.");
            if(!(product.get("priceUsd") instanceof Number)||!(product.get("monthlySalesNeeded") instanceof Number))throw new ToolFailure("Missing price scenario.");
            BigDecimal price=new BigDecimal(product.get("priceUsd").toString());
            if(price.signum()<=0||price.compareTo(new BigDecimal("1000"))>0
                    ||new BigDecimal("30").divide(price,0,RoundingMode.CEILING).compareTo(new BigDecimal(product.get("monthlySalesNeeded").toString()))!=0)
                throw new ToolFailure("Product target arithmetic does not match proposed price.");
            if(!(product.get("experiment") instanceof Map<?,?> experiment)||!experiment.keySet().containsAll(
                Set.of("hypothesis","budgetAssumptions","measurements","stopOrIterateRule")))throw new ToolFailure("Missing bounded experiment plan.");
            boolean cited=false;
            for(var e:(List<?>)product.get("evidence"))if(e instanceof Map<?,?> claim&&claim.get("listingIds") instanceof List<?> list&&!list.isEmpty())cited=true;
            if(!cited)throw new ToolFailure("Product lacks listing evidence.");
        }
    }
}
