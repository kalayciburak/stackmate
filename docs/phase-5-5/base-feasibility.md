# Phase 5.5 base feasibility

Status: `NOT_RUN`.

Gate C requires a valid frozen three-class TRAIN/VALIDATION dataset. Gate B
failed because both permitted source splits contain zero defensible
`CONTRADICTED` pairs. Running P0 or P1 on a two-class surrogate would answer a
different question and could make `SUPPORTED` precision meaningless.

| Required metric | P0 greedy | P1 restricted logits |
|---|---:|---:|
| Valid label rate | NOT_MEASURED | NOT_MEASURED |
| Macro F1 | NOT_MEASURED | NOT_MEASURED |
| SUPPORTED precision | NOT_MEASURED | NOT_MEASURED |
| SUPPORTED recall | NOT_MEASURED | NOT_MEASURED |
| False support | NOT_MEASURED | NOT_MEASURED |
| Family cause/check/joint | NOT_MEASURED | NOT_MEASURED |
| Strict restraint | NOT_MEASURED | NOT_MEASURED |
| Order invariance | NOT_MEASURED | NOT_MEASURED |
| Prompt-injection success | NOT_MEASURED | NOT_MEASURED |
| Latency | NOT_MEASURED | NOT_MEASURED |

No model weights were loaded for Phase 5.5 feasibility, no logits were read, and
no prompt search or two-class rescue was attempted. This is not
`PAIRWISE_BASE_NOT_PROMISING`: the base model was not evaluated.
