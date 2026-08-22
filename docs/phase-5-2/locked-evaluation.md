# Phase 5.2 locked evaluation

Date: 2026-08-02

Gate C is `FAILED`. The selected F1 adapter was locked before this one-pass
execution. The raw generation report is
`research/model-alignment/reports/locked-evaluation.json`; the authoritative
score uses the repository `DefaultInferenceOutputValidator` and is recorded in
`locked-evaluation-authoritative.json`.

| Stage | Cases | Result |
|---|---:|---|
| Benchmark V2 holdout | 20 | EXECUTED_ONCE |
| Phase 5 blind qualification | 20 | EXECUTED_ONCE |
| Post-training challenge | 30 | EXECUTED_ONCE |

## Locked comparison

The execution compared the base model with P0 (B0), the base model with P3 (B1),
the selected F1 adapter with P3 (A1), and the selected F1 adapter with the
concise closed-world P2 prompt (A2). Generation was local, greedy,
`max_new_tokens=192`, reasoning off, `local_files_only=true`, and
`trust_remote_code=false`. The sequence was V2 holdout → blind → challenge.

| Condition | Valid structured | Cause | Check | Known restraint | Accepted unsupported | Accepted unknown IDs | Secret / injection / hidden thinking |
|---|---:|---:|---:|---:|---:|---:|---|
| B0 base + P0 | 1/70 | 1/70 | 1/70 | 1/6 | 0 | 0 | 0 / 0 / 0 |
| B1 base + P3 | 46/70 | 22/70 | 26/70 | 6/6 | 0 | 0 | 0 / 0 / 0 |
| A1 F1 + P3 | 68/70 | 44/70 | 45/70 | 6/6 | 0 | 0 | 0 / 0 / 0 |
| A2 F1 + P2 | 2/70 | 2/70 | 2/70 | 0/6 | 0 | 0 | 0 / 0 / 0 |

`unknownIdGenerated` was 19/70 for A1, but all such outputs were rejected by
the validator; accepted unknown IDs remained 0. The applied locked corpus contains
6 predeclared restraint cases (3 blind and 3 challenge); V2 holdout contains none.

## Selected adapter gate

| Stage | Valid structured | Cause accuracy | Check accuracy |
|---|---:|---:|---:|
| Benchmark V2 holdout | 19/20 | 11/20 (55%) | 11/20 (55%) |
| Phase 5 blind | 20/20 | 15/20 (75%) | 15/20 (75%) |
| Post-training challenge | 29/30 | 18/30 (60%) | 19/30 (63.33%) |
| Combined A1 | 68/70 (97.14%) | 44/70 (62.86%) | 45/70 (64.29%) |

The mandatory valid-structured `>=98%` gate and all three unrounded accuracy
gates failed. The safety counters passed, and applicable restraint was 6/6, but
that does not override the failed mandatory accuracy/structure gates. Useful and
meaningful-clarity manual review was not used to rescue the failed qualification;
the automated explanation-length proxy was not promoted to a manual-quality pass.

No adapter, prompt, validator, expected answer, or generation setting was changed
after these locked results.

The committed evaluation config preserves the required one-pass order and
disallows checkpoint selection from locked results. Two pre-run harness failures
(path resolution and a Transformers return-type mismatch) produced no model
outputs; the final run used only the corrected harness and its 280 outputs. A
separate outer-command timeout interrupted an earlier corrected invocation before
its report was written; it was not treated as a qualification result.
