# ADR 0004: Jlama for the experimental local inference adapter

- Status: Accepted
- Date: 2026-08-01

## Context

StackMate needs one CPU-capable local JVM inference adapter with no server, API key, runtime download, or public
vendor types. Jlama, llama3.java, and ONNX Runtime GenAI were compared from their official sources.

## Decision

Implement one adapter for `com.github.tjake:jlama-core:0.8.4`, initially using its pure-Java CPU path. Keep it
optional and isolated behind `stackmate-inference-api`. Do not expose Jlama classes from public APIs.

Jlama is selected because it combines a published Maven artifact, SafeTensors model loading, small Qwen2/Llama/
Mistral/Gemma-family support, Q4/Q8 options, generation, embeddings, and an Apache-2.0 license. It does not provide
a documented JSON-schema guarantee, so StackMate validates every result independently.

## Rejected alternatives

- **llama3.java:** attractive minimal MIT source, but no upstream Maven artifact, Llama-only architecture,
  no documented embeddings or constrained output, and a vendoring/update burden.
- **ONNX Runtime GenAI 0.15.0:** strongest future platform candidate, but its Java generative package remains
  preview and source-build-only. The published generic ONNX Runtime Java artifact is not a complete text-generation
  stack.

## Consequences

- Inference remains `EXPERIMENTAL` until actual model benchmarks pass mandatory gates.
- Jlama and its transitive dependencies appear only in the local inference module/starter.
- Normal tests use a deterministic fake and never download a model.
- Dependency/SBOM review is required because the published version predates later upstream dependency updates.
- ONNX Runtime GenAI is the first reevaluation candidate when its Java artifact stabilizes.
- The prompt-v2 corpus confirmed that structured evidence materially beats raw stack input: 36/50 versus 8/50
  validator-approved English outputs and 72% versus 2% category accuracy.
- The same run missed the product gates: 72% useful cause/check selection, 7.21-second median structured latency,
  2,790 MiB peak worker working set, and 0/5 valid Turkish outputs. Jlama integration status is therefore
  `EXPERIMENTAL`, not `READY`.

## Evidence

See `docs/model-selection.md` and its primary-source links.
