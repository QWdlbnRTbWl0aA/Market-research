package local.myhome.marketresearch.tools;
/** A returned, usage-accounted model answer with invalid JSON. Never print the raw text in errors. */
final class MalformedModelResponse extends RuntimeException {
    final String raw;
    MalformedModelResponse(String raw){super("Model returned malformed JSON; private response retained.");this.raw=raw;}
}
