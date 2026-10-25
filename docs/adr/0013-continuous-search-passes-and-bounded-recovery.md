# Continuous search passes and bounded stage recovery

Date: 2026-10-25

The original global phrase bound left accepted suggestions and useful unrefined branches behind. Treat
the configured query limit as admission capacity per pass, while retaining branch-local marginal-value
stops and explicit depth/page safety boundaries. Continue automatically from saved suggestions after
each pass drains. Do not erase suggestions, page offsets, evidence, job IDs or provider ledgers.

Pause new listing requests at 2,000 pending listings rather than accumulating an unlimited backlog when
analysis is unavailable. Refinements and mixed business cohorts remain schedulable independently.
Archival continues to require drained analysis and completed product handoffs. Weekly monitoring still
requires explicit operator approval before new deep analysis.

Persist a maximum of two retries for confirmed transient model failures per stage, honoring Retry-After.
Permit one corrective retry for an accounted validation failure. Preserve drafts and review phase across
restarts. Never automatically retry an interrupted request with unknown outcome or fall back to a paid
provider after it. Known quota reset times may defer work; unknown reset times remain visible blockers.
Do not change any authorized dollar allowance. Retain safe structured provider diagnostics without raw
provider messages, credentials or infrastructure details.

Normal SMTP progress reports are hourly; health checks remain frequent and significant lifecycle or
attention events may be immediate. This is operational supervision, not another workflow owner.
