# Phase 5.3 output-extraction audit

Date: 2026-08-22

## Extraction boundaries

| Path | Extraction before authoritative validation |
|---|---|
| llama.cpp | Strip surrounding whitespace and a terminal `[end of text]` marker only |
| Transformers | Decode only generated suffix tokens; no JSON-span or fence extraction |
| Authoritative scorer | Parse the entire resulting string with duplicate, unknown-field, and trailing-token rejection |

No parity path repairs output. Correct JSON embedded in surrounding prose is not
silently accepted.

## Controlled findings

- Schema-constrained third-party GGUF produced 80/80 structured outputs.
- With grammar removed, the same GGUF still produced 80/80 syntactically valid
  JSON values. P0 produced 0/40 schema-v2 objects because fields were missing or
  typed differently, especially missing `schemaVersion` and scalar rather than
  array limitations. These are generation/contract failures, not extraction
  failures.
- Unconstrained GGUF P3 produced 40/40 structured outputs but only 29/40
  validator-valid outputs; 11 contained unknown candidate IDs.
- Official Transformers BF16 P0 produced 40/40 JSON and 25/40 structured objects.
- Official Transformers NF4 P0 produced only 19/40 JSON values and 0/40 structured
  objects. The remaining 21 were genuine parse failures.
- F1 P3 restored 40/40 structure, but 9/40 outputs still generated unknown IDs.

The low Transformers scores are therefore not primarily a parser artifact. Strict
whole-output parsing exposes three distinct model behaviors: malformed/truncated
JSON, valid JSON with the wrong schema, and schema-valid output with invalid or
incorrect references.

## Production decision

The production validator remains unchanged. Phase 5.3 does not add permissive JSON
substring extraction, Markdown-fence repair, unknown-ID correction, or silent field
coercion.
