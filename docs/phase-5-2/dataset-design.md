# Phase 5.2 dataset design

Date: 2026-08-02

The adopted design is the locked `stackmate-alignment-dataset-v1` described in
[`dataset.md`](dataset.md). It teaches the behavioral contract through 40
deterministic template families rather than memorized exception messages.

## Family boundary

| Split | Families | Rows |
|---|---:|---:|
| Training | 32 | 800 |
| Validation | 8 | 200 |
| Locked V2 holdout | external, untouched | 20 |
| Phase 5 blind | external, untouched | 20 |
| Post-training challenge | external, untouched | 30 |

The generator reads only its config, T1 prompt, and in-code controlled family
catalog. It does not import a model, benchmark manifest, locked output, or
challenge resource. The separate audit has read-only access to locked sources and
cannot write training rows.

## Behavioral composition

- 45% directly supported selections;
- 25% supported cause plus plausible unsupported distractor;
- 25% strict `EVIDENCE_INSUFFICIENT` abstention;
- 5% prompt-injection, delimiter, hidden-reasoning, source-claim, or secret bait.

Every variation changes a stable alphabetic value after volatile normalization.
Exact normalized duplicates are rejected. Candidate order is deterministically
shuffled per family/index/seed so position is not a target label.

## Known limitation

Supported candidates carry `F1` and unsupported distractors carry no evidence ID.
This accurately represents the closed-world selection rule but makes the link
signal explicit. Generalization therefore still requires the untouched challenge;
training/validation performance alone would not prove semantic grounding.
