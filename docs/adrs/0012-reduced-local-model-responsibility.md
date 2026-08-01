# ADR 0012: Reduced local-model responsibility

- Status: Accepted
- Date: 2026-08-01

## Context

The inference schema used in Phase 1 let a model rewrite deterministic cause and check text and attach source-location
IDs to those generated structures. Validation rejected unknown IDs, but this still treated the model as a second
diagnostic author and made model quality depend on repeating facts StackMate already knew. Phase 2 requires facts and
diagnosis to have one owner.

## Decision

Adopt inference and prompt schema version 2. The deterministic pipeline exclusively owns category, evidence strength,
root type, facts, candidate causes, candidate checks, and application locations. Application locations are supplied as
typed facts; they are never output fields.

The model may produce only a bounded summary, a bounded explanation, limitations, and ordered selections of supplied
cause, check, and evidence IDs. Validation resolves selected IDs back to the deterministic candidates before rendering.
It rejects unknown IDs, unsupported selections, new source-like references, new technical identifiers, sensitive data,
terminal controls, Markdown fences, destructive commands, source-inspection claims, and language or schema mismatch.
English and Turkish use separate versioned system and repair instructions.

This is an intentional breaking change to the experimental `0.1.0-SNAPSHOT` inference API. The removed
`StructuredCause`, `StructuredCheck`, and `SourceLocation` output-authoring types have no compatibility adapter because
retaining them would preserve the unsafe authority boundary. Migration details are recorded in
`docs/migrations/phase-2-inference-schema-v2.md`.

## Consequences

- Rendered facts and suggested checks always come from trusted deterministic objects.
- Model benchmarks measure explanation validity and supplied-ID ranking, not diagnostic fact generation.
- Existing custom inference engines and fake engines must emit schema version 2.
- A valid model response cannot add a new class, method, file, line, database object, property, or diagnostic category.
- Strict rejection may increase fallback frequency for small models; deterministic diagnosis remains the product.

## Rejected alternatives

- Keep generated structured causes and validate their text: this still makes the model a diagnostic author.
- Silently remove unsupported fields during parsing: this can turn a non-compliant response into apparently trusted
  output.
- Parse Markdown-fenced JSON: the schema explicitly requires a single JSON object and repair is already bounded to one
  attempt.
