# Market Research

A local system for researching Etsy digital-product opportunities. It will gather marketplace evidence, refine searches, analyze demand and competition, define potential products, and eventually assemble listing packages.

## Current status

This repository is an architecture baseline. It contains no live Etsy, Gemini, OpenAI, persistence, or image-generation integrations.

## Planned system

| Component | Ownership |
| --- | --- |
| Research orchestrator | Research workflow, canonical run state, retries, and dashboard API |
| Analysis service | Initial metrics, business analysis, ranking, and product-definition jobs |
| Content service | Listing text, visuals, mock-ups, prompts, and provider selection |
| Research archive | Business-oriented archival of evidence, reports, assets, and listing packages |
| Dashboard | React user interface through the orchestrator |

All backend services are independently deployable but live in one repository. Long-running work uses asynchronous HTTP job resources; no message broker is currently planned.

## Development model

- `main`: protected release branch; agents must not push directly.
- `dev`: integration, testing, and staging branch.
- Feature branches: normal development, merged into `dev` with significant commits preserved.

Architecture decisions live under `docs/adr/`. Provider quota checks will be recorded under `docs/provider-usage/` before the first potentially billable calls.

## Build

Requirements: JDK 21 and Maven 3.6.3 or newer.

```bash
mvn verify
```

Each service produces its own executable Spring Boot JAR and uses a distinct default local port. Provider integrations are disabled in the baseline configuration.

