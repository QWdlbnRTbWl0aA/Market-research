# Deterministic Etsy cohort analysis — v1

`POST /v1/etsy-cohort-analyses` on the analysis service is a small synchronous business operation. It performs local computation only. Long-running provider work continues to use asynchronous jobs.

Request fields:

- `query`: nonblank search phrase, at most 300 characters.
- `observedAt`: ISO-8601 observation timestamp; required and not more than one minute in the future.
- `listings`: 1..1000 raw Etsy listing objects. This is an explicit import boundary; provider-specific mapping remains separate from the pure analyzer.

The 200 response is `CohortReport`: schema/methodology version, received/invalid/duplicate/unique counts, listing-type classification, digital shop coverage and concentration, digital price distributions by currency, engagement summaries with missingness, original-creation age buckets, frequent normalized tags, repeated normalized titles and an automation-oriented experiment queue. API calls and model work are absent.

Invalid request envelopes return 400. Invalid identifiers within a valid cohort are counted and skipped. First occurrence wins for duplicate listing IDs. Unknown listing types remain unknown. Missing/invalid/negative engagement values remain unknown rather than zero. Missing/bad money (including nonpositive divisor) is excluded from monetary summaries and counted. Future/missing creation times are excluded from age buckets and counted.

Price percentiles use linear interpolation at `(n-1)*p`; currencies never mix. Tags count each normalized tag at most once per listing. Title overlap is exact after Unicode/case/punctuation normalization, not semantic similarity. Shop HHI and largest share use only digital listings with known shops; missing shop count is explicit.

STANDARD_DOWNLOAD means both customization flags are explicitly false. Any true flag yields TEMPLATE_REVIEW; missing flags yield AUTOMATION_UNKNOWN. The queue prioritizes those workload hints and uses stable IDs for ties. It is not an expected-return ranking and imposes no sample-size gate on cheap publication experiments.

The endpoint does not archive or retain the submitted cohort and is not yet called by the orchestrator. The collection/replay scripts retain local evidence and the report for this test. No secrets or machine metadata belong in input or output.
