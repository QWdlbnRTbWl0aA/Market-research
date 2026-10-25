# Shared Etsy request ceiling: 4,500 per rolling 24 hours

The operator explicitly approved 4,500 requests per rolling 24 hours for the current and future runs.
This replaces the former 400-request acceptance-run default. It is a shared ceiling across processes and
investigations, including probes, listing pages, shop profiles and reviews. Independent per-run sample
and request bounds are preserved. Settings above 4,500 are rejected; tighter explicit bounds remain valid.

The provider adapter, operational configuration example, experimental collector defaults and live private
configuration use the new ceiling. Existing file locking, throttling, provider-header reconciliation,
daily provider reserve and Retry-After/cooldown checks remain. No OpenAI allowance or ChatGPT limit changes.

The quota recheck business action evaluates the current shared ledger without making a provider call or
resetting request history. It updates only the discovery's Etsy wait and pending evidence waits that
depended on that exact timestamp. It does not retry model jobs, clear worker errors, reset page pointers
or authorize weekly deep analysis. An actual provider cooldown remains a wait.

Full `mvn verify` passed, including 19 workflow integration tests. Regression coverage checks increased
capacity with 400 preserved attempts, refusal when a probe-plus-request cannot fit the 4,500 ceiling,
rejection of higher settings, retained provider waits, untouched model jobs and preserved source state.
Private quota/configuration/research snapshots are retained for operational integrity checks.
