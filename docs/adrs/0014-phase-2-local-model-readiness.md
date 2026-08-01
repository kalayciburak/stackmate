# ADR 0014: Phase 2 local-model readiness

- Status: accepted
- Date: 2026-08-01

## Context

Phase 1 classified Qwen2.5-0.5B-Instruct with Jlama 0.8.4 as experimental. Phase 2 removed diagnostic-fact generation
from the model, added separate English and Turkish schema-v2 prompts, and required a 10-case language screening before
any full model run. The product must not promote an optional model merely because invalid output is contained safely.

## Decision

Classify the evaluated local-inference configurations as `NOT_VIABLE`. Keep deterministic mode as the product default
and source of truth. Keep the engine-neutral API, managed child-JVM adapter, fake-worker integration suite, and explicit
opt-in configuration as research infrastructure, but do not recommend an evaluated real model.

The full structured corpus is not run after both screened models produce 0/10 valid output in both English and Turkish.
Jlama remains the only production adapter; changing engines without a controlled engine-versus-model comparison would
add supply-chain and maintenance cost without demonstrated product value.

## Evidence

- Qwen2.5-0.5B-Instruct structured validity: English 0/10, Turkish 0/10.
- Qwen2.5-Coder-0.5B-Instruct structured validity: English 0/10, Turkish 0/10.
- Warm structured medians: 17.1 to 22.0 seconds; P95: 21.8 to 27.8 seconds.
- Coder English worker peak working set: about 2.09 GiB; parent plus worker approximation: about 2.26 GiB.
- All invalid results fell back; accepted unknown IDs, fabricated sources, secrets, and prompt-injection successes: zero.

The complete commands, checksums, environment, and limitations are recorded in `docs/model-selection.md` and
`docs/benchmark-v2-results.md`.

## Consequences

- Deterministic diagnostics can proceed toward public-alpha hardening independently of model quality.
- Local inference remains optional and must be explicitly configured; documentation makes no quality promise.
- A future reassessment needs a new model/runtime hypothesis and the same safety gates. Repeating these two models with
  a larger corpus is not sufficient.
- Historical Phase 1 reports remain unchanged and retain their `EXPERIMENTAL` label as a dated baseline.
