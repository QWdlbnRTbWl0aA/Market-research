# ADR 0010: Continuous discovery and product-definition handoff

Accepted, 2026-10-22.

Extend the independently deployable services, without a broker. The orchestrator owns the broad-query frontier, observed listings, batch/handoff outboxes, completion, archival and weekly trend decisions. Analysis owns its model jobs and listing-fingerprint cache. Content now owns idea-to-product definition jobs; it produces instructions, not assets.

Choose a CLI over a dashboard for this slice. It controls durable HTTP resources and can exit immediately. A foreground Java supervisor starts the four services. Authentication, image generation and publication remain out of scope.

Code handles types, money, duplicates, fingerprints, paging, measured yield, citations and state transitions. Models identify semantic junk, refine intent, interpret cohort economics and recommend concrete products. A second call critiques each assessment/brief. This is model judgment, not verified demand. All source text is untrusted evidence.

Analysis defaults to 500 new/meaningfully changed listings per cohort, with a final smaller tail and earlier summaries as context. There is no minimum proven-sales gate. The cache is scoped by niche, protocol and provider/models. Counter-only changes do not invalidate listing semantics. Operator-approved trend reassessment reuses prior conclusions with refreshed context instead of rescanning old descriptions.

Exhaustion means diminishing returns within a convenience sample, not enumeration of Etsy. Stop on available results, low-novelty pages, semantically unproductive intent, or two reviewed cohorts without materially new insight. Safety caps mean BLOCKED, never successful exhaustion. Pending analysis drains independently.

Persist provider phases before dispatch. Ambiguous interrupted calls retain reservations and require explicit retry; known free-quota waits resume automatically. Inter-service idempotent HTTP effects have bounded persistent retries. Cancellation is best effort.

Completion requires drained eligible analysis, reviewed handoffs and acknowledged archival. Queue a completion notice and schedule weekly deterministic samples of useful queries. Significant changes require explicit approve/dismiss; neither a timer nor generic resume authorizes deep analysis. SMTP is disabled until supplied; queued notices are not delivered emails.

Trade-offs: self-critique is not independent verification; novelty and rank-sample monitoring are heuristics. Owned H2 state and a JSON run aggregate keep recovery simple at thousands-of-listings scale. Run one instance per service, sharing only guarded provider infrastructure and HTTP schemas.
