package local.myhome.marketresearch.tools;
/** Deliberately safe user-facing failures: no credentials, HTTP bodies or machine details. */
public final class ToolFailure extends RuntimeException {
    public ToolFailure(String message) { super(message); }
}
