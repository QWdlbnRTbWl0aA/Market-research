# ADR 0009: Reuse guarded provider transport from services

Accepted, 2026-10-22.

Move the existing Etsy/text-model transport, credential readers and persistent quota/spending ledgers from the experimental CLI into a small Maven infrastructure library. Preserve their package names and existing ledger paths so historical usage and test coverage remain valid.

Each service supplies its own provider-neutral application behavior. No service shares another service's jobs, domain decisions or database tables. A common infrastructure library is preferable to duplicating spending guards or adding a provider gateway service merely to centralize transport.

All local processes must use the same configured runtime root. Locks protect cross-process reservations. Local lock contention is safe to defer because no request was sent. Known free-quota waits have durable retry timestamps; ambiguous provider outcomes require explicit intervention and retain reservations. Explicit Gemini mode never switches to paid OpenAI.

Non-generative model access/usage preflight refreshes before generation when stale. Account usage that cannot be read remains unknown. The development spending ceiling is not a recurring production authorization. Images remain disabled.
