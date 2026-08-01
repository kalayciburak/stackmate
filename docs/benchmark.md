# Benchmark methodology and status

## Purpose

The benchmark tests the product hypothesis rather than assuming a local model is useful. It compares the same
controlled incidents in three modes:

- **Mode A:** bounded sanitized raw stack trace to the selected local model.
- **Mode B:** bounded structured StackMate evidence/candidates to the same local model and prompt version.
- **Mode C:** deterministic StackMate diagnosis without a model.

Mode C is the product baseline. Model integration is retained only as experimental when it improves useful
explanation or prioritization without violating mandatory safety gates.

## Ground truth

Every case has a stable ID, expected category and root type, expected fact types/values where deterministic,
allowed cause/check IDs, and forbidden claims. Synthetic throwables are allowed only when reproducing the real
framework condition would add disproportionate infrastructure. Case text is untrusted and includes controlled
secret, terminal, and prompt-injection probes.

The initial corpus contains at least 50 cases distributed across general Java, Spring dependency injection and
configuration, MVC/validation/serialization, JDBC/persistence, security, external infrastructure, and
concurrency/resource failures.

## Deterministic metrics

- Category accuracy.
- Root-cause type accuracy.
- Required fact precision and recall.
- Cause/check selection accuracy.
- Forbidden-claim count.
- Fabricated source reference count.
- Secret-leakage count.
- Prompt-injection policy failure count.
- Input/output character counts.

## Model metrics

The model output validator provides machine-checkable results for JSON/schema validity, known IDs, known source
locations, output bounds, terminal controls, leakage, and forbidden claims. English and Turkish readability use a
separate human worksheet; no second LLM is the sole evaluator.

Cold latency, warm latency, peak RSS, model load, and token throughput must include CPU, cores, RAM, OS, Java,
engine, model, quantization, and prompt version. JMH is used only for deterministic microbenchmarks; model
end-to-end measurements use a separately documented harness because they include process and model lifecycle.

## Mandatory safety gates

- Zero runtime network calls.
- Zero fabricated class, method, file, or line references accepted.
- Zero controlled secret leakage.
- Zero unknown evidence/cause/check IDs accepted.
- Zero request-thread model inference.
- 100% deterministic fallback on invalid output, timeout, and engine failure.
- 100% duplicate inference suppression within the configured window.

## Reports

Generated reports are machine-specific and ignored by Git:

```text
stackmate-benchmark/target/stackmate-benchmark-report.json
stackmate-benchmark/target/stackmate-benchmark-report.md
```

A curated baseline may be committed later only with hardware/model/prompt provenance and without local paths.

## Current status

No model benchmark has been executed yet. Engine and model capability statements in
`docs/model-selection.md` come from primary sources, not StackMate measurements. Actual A/B/C numbers will be
added only after the corpus, validator, and local model path are available.
