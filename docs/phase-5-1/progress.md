# Phase 5.1 progress

Updated: 2026-08-02

## Objective

Determine through a controlled prompt ablation whether the pinned Qwen3.5-2B
Q4_K_M model can meet StackMate's semantic-restraint standard without changing the
model, runtime, schema, corpus, ground truth, generation profile, hardware, or
primary-comparison validator.

## Entry state

- Reviewed Phase 5 completion commit:
  `d29d789933a6394db942435e2c5ef607cda53f2f`.
- Created local annotated tag `phase-5-complete` at that commit.
- Created local branch `phase-5-1-prompt-alignment`.
- Confirmed the starting tree was clean and no remote is configured.
- Read the current product, Phase 4/5, model-selection, security, prompt, repair,
  schema, validator, corpus, and llama.cpp qualification materials.

## Baseline reproduction

- Java 21 `clean verify`: 11/11 reactor projects, 332 Surefire plus 11 Failsafe
  tests, zero failures/errors/skips.
- Pinned 100-case P0 run: 100/100 valid, 94/100 exact cause and check selection,
  V2 holdout 19/20, blind 19/20.
- Strict restraint reproduced at 1/11: ten missing limitations and four generic
  fallback selections.
- The same three unsupported technical alternatives were reproduced verbatim.
  The existing automatic counter still reports zero, confirming the semantic
  detector gap rather than resolving it.
- No repair, timeout, crash, leakage, injection success, unknown ID, fabricated
  reference, hidden thinking, or residual workspace process occurred.

## Frozen prompt-development split

- Prompt development: 40 V2 development cases.
- Selection validation: remaining 20 V2 development cases.
- Locked final: 20 existing V2 holdout plus 20 existing blind cases.
- Split SHA-256:
  `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07`.
- Combined source-ground-truth SHA-256:
  `0a5c9c748e187fdd2c52d506f592d30b04606d59242e3660b7a15f1f7d829d6a`.

## Prompt ablation

- P0-P3 and the process-per-case ablation harness were committed before model
  evaluation.
- Development evaluation completed for 160 outputs. P3 achieved 40/40 valid,
  36/40 exact cause/check selection, 4/4 strict abstention, and zero fallback
  misuse.
- Selection validation completed for 80 outputs. P3 achieved 20/20 valid, 18/20
  exact cause/check selection, 4/4 strict abstention, and zero fallback misuse.
- The first selection-time manual pass missed one unsupported `high rejection rate`
  claim in P1 and P3. The reverse-order pass corrected this after Gate B without
  changing the prompt or ground truth.
- P3 was checksum-locked and remained the only Gate B prompt.

## Locked and follow-up results

- Gate B ran once: 40/40 valid, 34/40 exact cause/check, and 3/3 strict locked
  abstentions.
- V2 holdout was 16/20 (80%) and failed the mandatory 85% gate. Blind was 18/20
  (90%). Gate C was therefore skipped by protocol.
- The complete second manual pass found one P3 accepted unsupported claim across
  the already-observed 100 disjoint outputs.
- Production restraint validation rejects seven Phase 5 empty-selection outputs
  lacking a limitation. A research-only lexical check catches the three known
  Phase 5 claims but creates seven false or over-conservative rejections and is not
  adopted.
- The separate eight-case adversarial suite produced 8/8 valid outputs, zero
  injection success, leakage, unknown IDs, source claims, or hidden thinking, but
  over-abstained in three cases.
- Clause-level evidence objects preserved 20/20 validity and 18/20 selection on
  selection validation, but retained the unsupported claim and increased latency.

## Final state

Phase 5.1 is `PARTIAL`. Prompt restraint improved materially, but no prompt passed
all development gates and locked holdout accuracy failed. Qwen3.5-2B is
`EXPERIMENTAL`, not ready for worker or product integration.

## Final verification

- Java 21 `mvnw verify`: 11/11 reactor projects passed.
- Surefire: 346; Failsafe: 11; total: 357; failures/errors/skips: 0/0/0.
- Spotless ran in check-only mode; no formatter apply command was used.
- The first final verify exposed one obsolete coordinator fixture whose empty
  selection lacked the newly required limitation. The fixture was corrected to
  state `EVIDENCE_INSUFFICIENT`; the targeted test and full verify then passed.
- Normal verification used fixture outputs only and did not execute or download a
  real model/native runtime.

## Out-of-scope status

Persistent worker: `NOT_RUN`. Production adapter: `NOT_IMPLEMENTED`.
Provisioning: `NOT_IMPLEMENTED`. Remote push: none.
