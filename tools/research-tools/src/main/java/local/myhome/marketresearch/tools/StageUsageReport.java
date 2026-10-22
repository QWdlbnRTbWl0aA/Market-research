package local.myhome.marketresearch.tools;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

final class StageUsageReport {
    static Map<String,Object> read(Path root,ObjectMapper json) throws Exception {
        Path directory=root.resolve("data/provider-ledgers/stage-usage");
        var stages=new TreeMap<String,Map<String,Object>>();
        if(Files.isDirectory(directory))try(var files=Files.list(directory)) {
            for(Path file:files.filter(path->path.getFileName().toString().endsWith(".json")).toList()) {
                var event=json.readValue(Files.readString(file),Map.class);
                String stage=Objects.toString(event.get("stage"),"unknown");
                var total=stages.computeIfAbsent(stage,key->new LinkedHashMap<>(Map.of("invocations",0L,"knownUsageCalls",0L,
                    "unknownUsageAttempts",0L,"inputTokens",0L,"outputTokens",0L,"cachedInputTokens",0L,"durationMillis",0L,"estimatedUsd",BigDecimal.ZERO,"requestAttempts",0L)));
                add(total,"invocations",1);
                if(Boolean.TRUE.equals(event.get("requestSent"))||Boolean.TRUE.equals(event.get("tokenUsageKnown")))add(total,"requestAttempts",1);
                if(Boolean.TRUE.equals(event.get("tokenUsageKnown"))) {
                    add(total,"knownUsageCalls",1);
                    for(String field:List.of("inputTokens","outputTokens","cachedInputTokens"))add(total,field,((Number)event.getOrDefault(field,0)).longValue());
                }else add(total,"unknownUsageAttempts",1);
                add(total,"durationMillis",((Number)event.getOrDefault("durationMillis",0)).longValue());
                if(event.get("estimatedUsd")!=null)total.put("estimatedUsd",new BigDecimal(total.get("estimatedUsd").toString()).add(new BigDecimal(event.get("estimatedUsd").toString())));
            }
        }
        for(var stage:stages.values()) {
            stage.put("knownEstimatedUsd",stage.get("estimatedUsd"));
            if(((Number)stage.get("unknownUsageAttempts")).longValue()>0)stage.put("estimatedUsd",null);
        }
        return Map.of("stages",stages,"costBasis","Estimates, not provider invoices. Unknown usage/uncertain reservations are not zero cost.");
    }
    private static void add(Map<String,Object> total,String field,long value){total.put(field,((Number)total.get(field)).longValue()+value);}
}
