# Provider quota and usage records

Before the first potentially billable call to a provider, create a dated record here containing:

- provider and account/project identity alias, without secrets;
- check timestamp and timezone;
- available quota and current usage, or the provider's documented inability to report them;
- source of the observation;
- local rate or cost guard selected for the application; and
- whether a billable call was authorized.

Current restrictions:

- Prefer Gemini free quota before equivalent OpenAI use.
- Enforce Etsy rate limits locally and reconcile available response headers.
- Do not make image-generation calls.

No provider API calls have been made by this fresh repository baseline.

