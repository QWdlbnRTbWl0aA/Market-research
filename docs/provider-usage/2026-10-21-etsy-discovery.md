# Etsy quota and usage observation — 2026-10-21

Historical record: the Python discovery implementation described below has since been replaced by the Java research-tools CLI. Its quota ledger and recorded observations remain compatible; see ADR 0006 and the current README for commands.

Checked at 11:42:41 UTC. Identity alias: configured Etsy research application. Credentials and machine/network identifiers are deliberately omitted.

## Before research searches

Reviewed Etsy's [rate-limit documentation](https://developers.etsy.com/documentation/essentials/rate-limits/) and [API reference](https://developers.etsy.com/documentation/reference/). The documentation directs clients to response headers or the developer portal for application-specific limits; no separate usage API was identified. A single authenticated, read-only `openapi-ping` request established the current allocation before any listing search.

Observed headers after that probe:

- `x-limit-per-second`: 5
- `x-limit-per-day`: 5000
- `x-remaining-today`: 4999
- `x-remaining-this-second`: 4

The pre-probe counter was not independently observed. No billing or free-use entitlement is inferred from these rate counters.

## Local guard

`scripts/etsy_probe.py` reserves requests in a persisted rolling-24-hour ledger before network I/O, locks the ledger across processes, spaces requests by at least two seconds, caps local attempts at six, and requires observed positive quota with a ten-request daily reserve before searching. It stops on any HTTP failure without automatic retry. These are discovery-session controls, not yet the production orchestrator's Etsy adapter.

All requests used HTTPS and only read public marketplace data. Secrets were loaded in memory from the configured credential file; raw responses and ledger remain in ignored local data storage.

## Actual use

Five requests: one quota probe and four searches. Each search used `limit=1`, `sort_on=score`, no pagination, and the phrase shown below.

| Search phrase | API count | Returned | Remaining daily quota |
| --- | ---: | ---: | ---: |
| halloween printable | 431979 | 1 | 4998 |
| halloween scavenger hunt printable | 5613 | 1 | 4997 |
| halloween classroom games printable | 17998 | 1 | 4996 |
| halloween svg | 219211 | 1 | 4995 |

These counts describe matching active listings at observation time, including any irrelevant or physical matches. They are neither digital-only totals nor measures of demand, unique sellers, sales, or unique products. Lower counts for longer queries do not independently establish an opportunity.

No Gemini, OpenAI, or image-generation calls were made. Their account quota and usage have not been checked because no first call is planned in this increment. Those checks remain mandatory before their use.
