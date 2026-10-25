package local.myhome.marketresearch.tools;

import java.util.Map;

/** Safe structured diagnostics. Provider bodies, credentials and URLs are deliberately excluded. */
public class ProviderFailure extends ToolFailure {
    private final String code,parameter,bodyShape,requestId;
    private final int httpStatus;
    private final boolean retryable;
    private final long retryAt;
    public ProviderFailure(String code,int httpStatus,boolean retryable,long retryAt,String parameter,String bodyShape,String requestId) {
        super("ChatGPT request failed: "+code+" (HTTP "+httpStatus+")");
        this.code=code;this.httpStatus=httpStatus;this.retryable=retryable;this.retryAt=retryAt;
        this.parameter=parameter;this.bodyShape=bodyShape;this.requestId=requestId;
    }
    public String code(){return code;}
    public boolean retryable(){return retryable;}
    public long retryAt(){return retryAt;}
    public Map<String,Object> diagnostic(){return Map.of("code",code,"httpStatus",httpStatus,"retryable",retryable,
        "retryAt",retryAt,"parameter",parameter,"bodyShape",bodyShape,"requestId",requestId);}
}
