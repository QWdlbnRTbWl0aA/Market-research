package local.myhome.marketresearch.tools;

/** A definitive rejection or pre-request condition where a guarded API fallback is safe. */
final class ChatGptUnavailable extends ProviderFailure {
    final String reason;
    ChatGptUnavailable(String reason) { this(reason,0,0,"",""); }
    ChatGptUnavailable(String reason,int httpStatus,long retryAt,String bodyShape,String requestId) {
        super(reason,httpStatus,false,retryAt,"",bodyShape,requestId);this.reason=reason;
    }
}
