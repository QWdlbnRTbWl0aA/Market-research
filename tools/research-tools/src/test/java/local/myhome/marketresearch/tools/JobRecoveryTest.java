package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JobRecoveryTest {
    @Test void retriesKnownTransientFailuresTwiceAndRespectsProviderWait() {
        long wait=System.currentTimeMillis()+60000;
        var failure=new ProviderFailure("HTTP_503",503,true,wait,"","detail_object","");
        assertEquals(wait,JobRecovery.retryAt(failure,Map.of(),0));
        assertEquals(wait,JobRecovery.retryAt(failure,Map.of(),1));
        assertEquals(0,JobRecovery.retryAt(failure,Map.of(),2));
        assertEquals(0,JobRecovery.retryAt(new ProviderFailure("STREAM_INTERRUPTED",200,false,0,"","stream",""),Map.of(),0));
    }
    @Test void correctiveRetryRequiresAccountedOutputAndIsLimitedToOne() {
        var failure=new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Invalid output");
        var accounted=Map.<String,Object>of("requestSent",true,"tokenUsageKnown",true);
        assertTrue(JobRecovery.retryAt(failure,accounted,0)>System.currentTimeMillis());
        assertEquals(0,JobRecovery.retryAt(failure,accounted,1));
        assertEquals(0,JobRecovery.retryAt(failure,Map.of("requestSent",true),0));
    }
}
