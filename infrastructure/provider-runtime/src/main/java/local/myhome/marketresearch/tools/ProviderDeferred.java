package local.myhome.marketresearch.tools;
/** A known, non-billable wait boundary, unlike ambiguous transport failures. */
public final class ProviderDeferred extends ToolFailure {
    private final long retryAt;
    public ProviderDeferred(long retryAt){super("Known provider allowance unavailable; work remains queued.");this.retryAt=retryAt;}
    public long retryAt(){return retryAt;}
}
