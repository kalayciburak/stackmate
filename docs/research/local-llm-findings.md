# Historical local-LLM findings

## Status and isolation

This page is a historical research index. It does **not** describe a StackMate `0.1.0-rc.1` product capability.
The public alpha ships no LLM, inference API or implementation, local starter, Jlama integration, llama.cpp runtime,
GGUF or SafeTensors file, model downloader, prompt, adapter, benchmark harness, or model configuration property.

No model or native runtime was published, bundled, uploaded, or selected for the public release. The public runtime
does not make cloud or local inference calls.

## Outcome

Earlier phases evaluated user-provisioned local models and increasingly constrained evidence-selection contracts.
Those experiments produced useful containment and evaluation lessons, but no candidate established the quality,
restraint, compatibility, and operational evidence required for product inclusion. Later research also showed that
one bounded atomic classification task was deterministically solvable, reducing the case for an LLM in that path.

The product decision for `0.1.0-rc.1` is therefore explicit: ship only deterministic capture, diagnosis,
sanitization, deduplication, console output, and optional local export.

## How to read older material

Documents under historical phase and research directories may mention inference modules, local starters, model-path
settings, Jlama, llama.cpp, Qwen checkpoints, adapters, training, E2E profiles, or benchmark commands. Treat every
such reference as repository history or a controlled lab description. It is not install, configuration, support, or
compatibility guidance for the public alpha.

Historical scores are scoped to their frozen corpora, prompts, model artifacts, and machines. They do not support a
general accuracy, production-readiness, hardware, operating-system, or product-capability claim.

## Product documentation authority

For public-alpha behavior, use [../../README.md](../../README.md),
[../architecture.md](../architecture.md), [../configuration.md](../configuration.md), and
[../compatibility.md](../compatibility.md). If an older research page conflicts with those documents, the public
product documents control.
