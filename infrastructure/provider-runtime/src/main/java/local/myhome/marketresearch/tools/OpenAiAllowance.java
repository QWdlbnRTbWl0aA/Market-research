package local.myhome.marketresearch.tools;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** An allowance is a new authorization record, never a reset of historical spending. */
public final class OpenAiAllowance {
    private OpenAiAllowance() {}
    static Path file(Path directory,String id) {
        if(id==null||!id.matches("[a-z0-9][a-z0-9-]{0,79}"))throw new ToolFailure("Invalid allowance ID");
        return directory.resolve("allowances").resolve(id+".json");
    }
    public static Map<String,Object> start(Path root,String id,BigDecimal cap,ObjectMapper json) throws Exception {
        Path directory=root.resolve("data/provider-ledgers");
        Path file=file(directory,id);
        PrivateJson.directory(file.getParent());
        try(var channel=java.nio.channels.FileChannel.open(directory.resolve("openai-stage.lock"),
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
        if(lock==null)throw new ProviderBusy();
        if(Files.exists(file))throw new ToolFailure("Allowance already exists; it will not be reset.");
        if(cap!=null&&cap.signum()<=0)throw new ToolFailure("Allowance must be positive.");
        var state=new LinkedHashMap<String,Object>();
        state.put("id",id);state.put("createdAt",Instant.now().toString());
        state.put("status",cap==null?"AWAITING_CAP":"ACTIVE");state.put("budgetUsd",cap);
        state.put("committedUsd",BigDecimal.ZERO);state.put("calls",List.of());
        PrivateJson.write(file,state,json);return state;
        }catch(java.nio.channels.OverlappingFileLockException busy){throw new ProviderBusy();}
    }
    public static Map<String,Object> confirm(Path root,String id,BigDecimal cap,ObjectMapper json) throws Exception {
        Path directory=root.resolve("data/provider-ledgers");
        if(cap==null||cap.signum()<=0)throw new ToolFailure("Confirm an explicit positive USD cap");
        try(var channel=java.nio.channels.FileChannel.open(directory.resolve("openai-stage.lock"),
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
            if(lock==null)throw new ProviderBusy();
            Path path=file(directory,id);
            if(!Files.exists(path))throw new ToolFailure("Allowance is missing");
            Map<String,Object> state=json.readValue(Files.readString(path),Map.class);
            if(!"AWAITING_CAP".equals(state.get("status")))throw new ToolFailure("Allowance is already authorized; it cannot be reset");
            state.put("budgetUsd",cap);state.put("status","ACTIVE");state.put("authorizedAt",Instant.now().toString());
            PrivateJson.write(path,state,json);return state;
        }catch(java.nio.channels.OverlappingFileLockException busy){throw new ProviderBusy();}
    }
    static BigDecimal limit(Path directory,String id,ObjectMapper json) throws Exception {
        Path path=file(directory,id);
        if(!Files.exists(path))throw new ToolFailure("OpenAI allowance is missing.");
        var state=json.readValue(Files.readString(path),Map.class);
        if(!"ACTIVE".equals(state.get("status"))||state.get("budgetUsd")==null)
            throw new ToolFailure("OpenAI allowance awaits an explicitly confirmed cap.");
        BigDecimal amount=new BigDecimal(state.get("budgetUsd").toString());
        if(amount.signum()<=0)throw new ToolFailure("Invalid allowance cap.");
        return amount;
    }
}
