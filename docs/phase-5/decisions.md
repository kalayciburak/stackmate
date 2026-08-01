# Phase 5 decisions

## D1: Gate ordering is enforced

Gate A owns model feasibility. Persistent-worker experiments are deferred until Gate A passes; the production
adapter is deferred until Gates A and B pass; packaged Spring integration is deferred until Gate C passes. A failed
gate leaves the deterministic product unchanged and stops dependent implementation.

## D2: Exactly one model is qualified

Phase 5 qualifies `Qwen3.5-2B` in Q4_K_M GGUF form. No smaller replacement, larger fallback, prompt tournament,
fine-tune, distillation, or second inference engine is introduced.

## D3: llama.cpp `b10223` is the qualification runtime

The Phase 4 control used `b10221`. At Phase 5 execution time the current official release is `b10223`, commit
`11924d4c17abc27383376a1ac6a24fa3e36c1c0c`. Its release delta is a CI fix after the `b10221` BoringSSL update; it
retains Qwen3.5, CPU, and JSON-schema support while avoiding a knowingly stale pin. The exact archive digest is
recorded in `provenance.md`.

## D4: The model remains untrusted

Grammar-constrained JSON is syntax control, not factual validation. The Phase 2 explanation-only contract and
semantic validator remain unchanged: facts and source locations are deterministic; unknown IDs, invented
identifiers, hidden thinking, secrets, prompt-injection compliance, and unsupported claims are rejected.

## D5: The blind corpus is immutable before evaluation

Twenty new cases will be written from independent recipes and ground truth, with at least fourteen executable
runtime/integration captures. The manifest digest and commit are recorded before the model receives a blind prompt.
Evaluation code may map independent benchmark keys to supplied candidate IDs, but benchmark resources must not read
production analyzer constants or derive expected answers from production output.

## D6: No provisioning is part of Phase 5

Even if all gates pass, the first production adapter uses explicit local executable and GGUF paths. Downloading,
catalog activation, cache management, or zero-friction provisioning remains a later, evidence-dependent decision.
