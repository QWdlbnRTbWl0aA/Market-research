package local.myhome.marketresearch.tools;

/** A definitive rejection or pre-request condition where a guarded API fallback is safe. */
final class ChatGptUnavailable extends ToolFailure {
    final String reason;
    ChatGptUnavailable(String reason) { super("ChatGPT unavailable: " + reason); this.reason = reason; }
}
