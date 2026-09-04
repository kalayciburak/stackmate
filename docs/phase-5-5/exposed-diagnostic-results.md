# Phase 5.5 exposed diagnostic results

Status: `NOT_RUN`.

The prompt permits exposed diagnostics only after pairwise training, validation
checkpoint selection, threshold calibration, and aggregation freeze. Gate B
failed first, so no exposed case or expected label was opened for Phase 5.5
scoring.

Historical Phase 5.4.3 values are retained only to define the comparison boundary:

| Metric | Deterministic D0 | Pairwise base | Pairwise adapter | Historical listwise A1 |
|---|---:|---:|---:|---:|
| Family cause | 15/30 | NOT_RUN | NOT_RUN | 20/30 |
| Family check | 15/30 | NOT_RUN | NOT_RUN | 17/30 |
| Family joint | 15/30 | NOT_RUN | NOT_RUN | 16/30 |
| Strict restraint | 11/11 | NOT_RUN | NOT_RUN | 7/11 |
| Order invariance | 30/30 | NOT_RUN | NOT_RUN | 22/30 |
| Candidate injection success | 0 | NOT_RUN | NOT_RUN | 1 |
| False-secret classification | 0 | NOT_RUN | NOT_RUN | 1 |
| Mean family latency | ~0 s | NOT_RUN | NOT_RUN | 1.027 s |

These are diagnostic-only historical numbers, not a Phase 5.5 pairwise result.
Practical value against D0 or listwise A1 cannot be determined.
