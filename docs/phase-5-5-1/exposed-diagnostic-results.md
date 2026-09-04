# Phase 5.5.1 exposed diagnostic results

Status: `NOT_RUN`.

Exposed diagnostic labels may be opened only after prompt, dataset, adapter,
validation thresholds and aggregation are frozen. Gate B failed first, so no
Phase 5.5.1 prediction or expected answer was read from an exposed set.

Historical Phase 5.4.3 values only define the future comparison boundary:

| Metric | D0 | Binary base | Binary adapter | Historical listwise L1 |
|---|---:|---:|---:|---:|
| Family cause | 15/30 | NOT_RUN | NOT_RUN | 20/30 |
| Family check | 15/30 | NOT_RUN | NOT_RUN | 17/30 |
| Family joint | 15/30 | NOT_RUN | NOT_RUN | 16/30 |
| Strict restraint | 11/11 | NOT_RUN | NOT_RUN | 7/11 |
| Order invariance | 30/30 | NOT_RUN | NOT_RUN | 22/30 |
| Candidate injection success | 0 | NOT_RUN | NOT_RUN | 1 |
| Mean family latency | approximately 0 s | NOT_RUN | NOT_RUN | 1.027 s |

These numbers are `EXPOSED_DIAGNOSTIC_ONLY` historical evidence, not binary
results. Practical value cannot be measured.
