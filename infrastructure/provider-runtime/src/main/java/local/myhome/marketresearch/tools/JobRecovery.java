package local.myhome.marketresearch.tools;

import java.util.Map;
import org.springframework.web.server.ResponseStatusException;

/** Bounded stage recovery: no retry of uncertain I/O and no new billing authorization. */
public final class JobRecovery {
    private JobRecovery() {}
    public static long retryAt(Throwable failure,Map<String,Object> usage,int priorRetries) {
        if(priorRetries>=2)return 0;
        long now=System.currentTimeMillis();
        if(failure instanceof ProviderFailure provider&&provider.retryable())
            return Math.max(provider.retryAt(),now+5000L*(1L<<priorRetries));
        // A rejected, usage-accounted output can receive one corrective retry in its current phase.
        if(priorRetries==0&&failure instanceof ResponseStatusException&&usage!=null
                &&Boolean.TRUE.equals(usage.get("requestSent"))&&Boolean.TRUE.equals(usage.get("tokenUsageKnown")))
            return now+1000;
        return 0;
    }
    public static String detail(Throwable failure) {
        String detail=failure instanceof ResponseStatusException rejection?rejection.getReason():
            failure instanceof ToolFailure safe?safe.getMessage():failure.getClass().getSimpleName();
        if(detail==null)detail="Unknown failure";
        return detail.substring(0,Math.min(300,detail.length()));
    }
}
