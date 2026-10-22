package local.myhome.marketresearch.tools;
/** Local contention only: no HTTP request was sent; retrying later cannot incur duplicate cost. */
public final class ProviderBusy extends ToolFailure {
    public ProviderBusy(){super("Another worker holds the provider ledger; waiting without sending a request.");}
}
