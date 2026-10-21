package local.myhome.marketresearch.tools;

import java.nio.channels.*;
import java.nio.file.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** A stage-wide $2 ceiling. Ambiguous failures retain their entire worst-case reservation. */
final class ModelBudget {
    record Outcome(Map<String,Object> value,long inputTokens,long outputTokens) {}
    @FunctionalInterface interface Call {Outcome execute() throws Exception;}
    private final Path directory; private final ObjectMapper json;
    ModelBudget(Path directory,ObjectMapper json){this.directory=directory;this.json=json;}
    @SuppressWarnings("unchecked")
    Map<String,Object> openai(String purpose,int inputByteBound,int outputCap,Call call) throws Exception {
        if(inputByteBound>150_000 || outputCap>8000) throw new ToolFailure("Model request exceeds local token budget.");
        PrivateJson.directory(directory);
        try(var channel=FileChannel.open(directory.resolve("openai-stage.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=channel.tryLock()) {
            if(lock==null) throw new ToolFailure("Another model operation holds the spending lock.");
            Path file=directory.resolve("openai-stage.json");
            Map<String,Object> state=Files.exists(file)?json.readValue(Files.readString(file),Map.class):new LinkedHashMap<>();
            BigDecimal committed=new BigDecimal(state.getOrDefault("committedUsd",0).toString());
            BigDecimal reservation=cost(inputByteBound+4096,outputCap);
            if(committed.add(reservation).compareTo(new BigDecimal("2"))>0)
                throw new ToolFailure("OpenAI stage budget would exceed USD 2; no call sent.");
            List<Map<String,Object>> calls=new ArrayList<>((List<Map<String,Object>>)state.getOrDefault("calls",List.of()));
            Map<String,Object> event=new LinkedHashMap<>();
            event.put("purpose",purpose);event.put("model","gpt-5.4-mini");event.put("at",Instant.now().toString());
            event.put("reservedUsd",reservation);event.put("status","RESERVED_OR_UNCERTAIN");
            calls.add(event);state.put("calls",calls);state.put("budgetUsd",2);
            state.put("committedUsd",committed.add(reservation));PrivateJson.write(file,state,json);
            Outcome result=call.execute();
            if(result.inputTokens()<0 || result.outputTokens()<0) throw new ToolFailure("Provider usage unavailable; reservation retained.");
            BigDecimal actual=cost(result.inputTokens(),result.outputTokens());
            if(actual.compareTo(reservation)>0) throw new ToolFailure("Provider usage exceeded conservative reservation.");
            event.put("inputTokens",result.inputTokens());event.put("outputTokens",result.outputTokens());
            event.put("actualUsd",actual);event.put("status","SETTLED");
            state.put("committedUsd",committed.add(actual));
            PrivateJson.write(file,state,json);
            return result.value();
        } catch(OverlappingFileLockException e){throw new ToolFailure("Another model operation holds the spending lock.");}
    }
    static BigDecimal cost(long input,long output) {
        return BigDecimal.valueOf(input).multiply(new BigDecimal("0.75"))
            .add(BigDecimal.valueOf(output).multiply(new BigDecimal("4.50"))).divide(new BigDecimal("1000000"));
    }
}
