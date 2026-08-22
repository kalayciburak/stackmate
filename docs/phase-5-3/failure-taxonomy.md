# Phase 5.3 locked failure taxonomy

Date: 2026-08-22

## Scope and evidence boundary

The 70 Phase 5.2 locked cases are already exposed, so this analysis classifies the
210 B0, B1, and A1 outputs for diagnosis only. It does not create a new final gate,
alter an expected answer, or rerun a model.

The original locked input export SHA-256 was
`b036f1cc18e4ef0bd99f3de68466fb4e35cb0229855360be592afb1020af0f87`, but
that external temporary file no longer exists. A fresh JBR 25 export has SHA-256
`bd4c7c9e137e26361dd072062f56eace216bffcaa65f28201b617073d073bae9`.
The diagnostic report records both hashes and marks the input as regenerated.

This regeneration is bounded evidence rather than byte parity. Independent Java
21, Corretto 25, and JBR 25 exports agreed for 70/70 expected cause/check sets,
70/70 candidate orders, and 70/70 fact-ID sets. Full requests agreed in 68/70 and
rendered user prompts in 69/70; runtime exception text caused the remaining drift.
The classifier therefore uses regenerated data only for stable expected IDs,
candidate order, and list length, never to replace original generated outputs.

## Dominant classifications

Categories are multi-label, so counts do not sum to 210.

| Category | Count |
|---|---:|
| Invalid structure | 95 |
| Missing cause | 69 |
| Extra prose | 65 |
| Missing check | 58 |
| Extra cause | 53 |
| Wrong cause | 52 |
| Unknown cause ID | 47 |
| Extra check | 42 |
| Wrong check | 41 |
| Unknown check ID | 35 |
| Prompt-dependent failure | 22 |
| Over-abstention | 10 |
| EOS/stop failure | 5 |
| Truncated output | 4 |
| Generic fallback | 1 |
| Unsupported claim | 1 |

No unknown evidence ID, under-abstention, confirmed validator false positive,
confirmed validator false negative, or measured candidate-order bias was assigned.
Candidate-order bias remains unclassified until the controlled permutation
experiment is complete.

## Condition breakdown

| Condition | Cases with diagnostics | Invalid structure | Unknown cause/check | Missing cause/check |
|---|---:|---:|---:|---:|
| B0 base + P0 | 69/70 | 69 | 1 / 1 | 8 / 7 |
| B1 base + P3 | 48/70 | 24 | 26 / 14 | 35 / 26 |
| A1 F1 + P3 | 26/70 | 2 | 20 / 20 | 26 / 25 |

The adapter almost eliminated structural failure but did not eliminate candidate
reference or semantic-selection failure. This is consistent with F1 learning the
synthetic output format more strongly than unseen candidate semantics.

## Candidate position and list length

All 192 non-restraint expected cause selections and all 192 expected check
selections were at one-based candidate position 1 in the exposed locked corpus.
That is a label-distribution concern, but not proof of order bias. Actual known
cause/check selections were overwhelmingly also at position 1; 49 cause selections
and 37 check selections used references absent from the request.

Candidate-list lengths were 1-4. Diagnostic rates remained high at every length;
the report preserves per-length and per-position multi-label counts. A controlled
permutation is required before attributing any error causally to order.

## Interpretation

- B0 is dominated by serialization/contract failure.
- P3 removes many structural failures but exposes unknown-ID and wrong-selection
  behavior instead of solving it.
- F1 strongly learns schema shape, yet 20 unknown cause and 20 unknown check
  references remain among its exposed locked outputs.
- Parsing failures and semantic failures are separate; neither is counted as the
  other without raw-output evidence.
- The production validator accepted none of the generated unknown IDs. Safety
  containment remained intact even though task quality failed.
