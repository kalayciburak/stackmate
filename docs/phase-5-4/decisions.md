# Phase 5.4 decisions

Date: 2026-08-22

## D1: One new run only

Use exactly one new real-world T1 alignment run. Do not reuse the historical F1
adapter as initialization and do not perform a sweep.

## D2: BF16 first

Run a short BF16 LoRA feasibility smoke test first. Use NF4 QLoRA only if BF16
fails for a measured hardware or framework reason and the required NF4 base parity
guard passes.

## D3: Validation-only checkpoint lock

Select one epoch using only the frozen 40-row VALIDATION split and commit its
metadata before opening LOCKED_FINAL.

## D4: Deterministic boundary

The model emits only T1 local indexes and limitation. Java remains responsible for
index bounds, trusted-object mapping, evidence semantics, and prose.

## D5: Stop after precision guard failure

BF16 LoRA failed the one-GiB practical VRAM-headroom requirement. NF4 failed the
pre-training base guard because restraint and fallback safety degraded. Do not spend
the one permitted real training run on either path; preserve Phase 5.3 and classify
the Phase 5.4 adapter as `NOT_VIABLE` without opening LOCKED_FINAL.
