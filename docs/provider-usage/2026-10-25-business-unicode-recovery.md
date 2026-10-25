# Business-analysis HTTP 400 diagnosis

The operator reported renewed ChatGPT plan quota. The app-owned connection remained enabled with
the selected `gpt-6.1-sol` model. Successful diagnostic completions confirmed usable plan access.
The named API allowance was unchanged at USD 4 authorized and USD 3.89756175 committed before
production recovery. No paid fallback was enabled for diagnostics and no API allowance was reset.

Six bounded ChatGPT-only transport probes used saved evidence: a small schema request, full JSON
output, full static schema, full cohort-constrained schema, the original observation-time suffix,
and a reconstructed malformed description. Five completed, reporting 142,764 input and 6,568
output tokens in total. The deliberately malformed control received HTTP 400 before completion;
its token usage is unknown. All attempts remain in the existing plan ledger. Diagnostic answers
were kept private and were not accepted as production recommendations or injected into jobs.

The failing cohort contains a description whose emoji straddles the 160-UTF-16-unit preview
boundary. A plain substring retained only its high surrogate. Reconstructing this exact boundary
failure reproduced the generic HTTP 400. Stored/exported call evidence had replaced the invalid
surrogate with `?`, so earlier saved-evidence replay accidentally repaired the input and concealed
the problem. The original full listing description remained intact.

The correction makes listing and prior-context previews preserve surrogate pairs. Both model
adapters reject invalid Unicode before sending requests or reserving paid usage. Citation schemas,
semantic validation, job IDs, original requests, source evidence and historical ledgers are preserved.
Regression tests cover the actual preparation boundary, request admission and preservation of the
original description.

Full `mvn verify` passed. The fix was deployed using immutable jars and stopped-service database
backups. The existing blocked job retained its original ID and request, then completed generation
and review successfully for its 100 uncached listings. The orchestrator subsequently started the
next business cohort. Hourly reporting, spending ceilings and historical usage ledgers remain intact.
