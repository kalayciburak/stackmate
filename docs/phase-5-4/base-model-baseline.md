# Phase 5.4 base-model baseline

The official base was evaluated on the frozen 40-row VALIDATION/T1 split before any
alignment training. No adapter and no LOCKED_FINAL data were loaded.

| Metric | BF16 B0 | NF4 B0 |
|---|---:|---:|
| Valid JSON | 40/40 | 40/40 |
| Exact T1 structure | 40/40 | 39/40 |
| Cause accuracy | 34/40 | 30/40 |
| Check accuracy | 33/40 | 29/40 |
| Joint accuracy | 33/40 | 29/40 |
| DIAGNOSE joint | 11/18 | 11/18 |
| Strict restraint | 22/22 | 18/22 |
| Order invariance | 14/20 | 13/20 |
| Unknown indexes | 0 | 1 |
| Generic fallback misuse | 0 | 3 |

The comparison rejects NF4 as a Phase 5.4 training fallback. The BF16 report is
also the B0 reference if a future separately authorized run changes the hardware
budget.
