# ADR 0007: Evidence-backed model output

- Status: Accepted
- Date: 2026-08-01

## Context

Local model output can contain invented locations, unknown facts, prompt-injection responses, secrets,
terminal control sequences, or malformed structured data.

## Decision

Inference requests assign stable IDs to facts, cause candidates, and check candidates. Model output must use
a bounded structured schema and may reference only supplied IDs. A validator rejects unknown identifiers,
unknown source locations, forbidden claims, excessive output, unsafe control sequences, and language/schema
violations. Rejected output is never rendered as trusted advice.

## Consequences

- The public domain model does not expose raw engine JSON.
- One bounded repair attempt is the maximum.
- Output is sanitized again after validation.
- The deterministic diagnosis remains the authoritative fallback.
