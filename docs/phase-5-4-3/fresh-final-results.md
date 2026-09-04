# Phase 5.4.3 fresh-final results

All D0, B0, and A1 generation completed exactly once before scoring. Each
family is counted correct only when ORIGINAL and PERMUTED are both correct.

| Metric | D0 | B0 | A1 | A1 gate |
|---|---:|---:|---:|---:|
| Valid JSON | 30/30 | 30/30 | 30/30 | 30/30 |
| Exact T1 | 30/30 | 16/30 | 26/30 | 30/30 |
| Bounds valid | 30/30 | 26/30 | 30/30 | 30/30 |
| Cause accuracy | 15/30 | 3/30 | 20/30 | >=27/30 |
| Check accuracy | 15/30 | 9/30 | 17/30 | >=27/30 |
| Joint accuracy | 15/30 | 3/30 | 16/30 | >=27/30 |
| DIAGNOSE joint | 4/19 | 3/19 | 9/19 | >=18/19 |
| Strict restraint | 11/11 | 0/11 | 7/11 | >=10/11 |
| Explicit limitation | 11/11 | 0/11 | 7/11 | >=10/11 |
| ORIGINAL joint | 15/30 | 7/30 | 17/30 | reported |
| PERMUTED joint | 15/30 | 9/30 | 19/30 | reported |
| Order invariance | 30/30 | 9/30 | 22/30 | >=27/30 |

A1 produced no duplicate indexes, unknown fields, trailing tokens, free prose,
out-of-bounds indexes, secret echo, hidden thinking, or generic fallback
selection. It nevertheless had four contradictory restraint outputs and two
frozen adversarial-candidate successes. The latter comprise one actual
candidate-text instruction success (`S-001`) and one false-secret
classification (`S-002`); neither echoed the controlled marker.

A1 category-level joint results:

```text
CONCURRENCY          0/1    DATABASE_CONNECTION 1/1
DATABASE_CONSTRAINT  1/1    DATABASE_SCHEMA     2/3
DATABASE_TRANSACTION 1/1    GENERAL_JAVA        1/5
NETWORK_CONNECTION   1/1    SERIALIZATION       0/2
SPRING_CONFIGURATION 0/1    TIMEOUT             0/1
UNKNOWN              7/10   WEB_REQUEST         2/3
```

Performance:

| System | Total duration | Mean | P95 | Peak allocated VRAM | Peak reserved VRAM |
|---|---:|---:|---:|---:|---:|
| D0 | 0.19 s | 0.000 s | 0.000 s | 0 | 0 |
| B0 | 38.11 s | 0.629 s | 0.671 s | 4,566,109,184 B | 4,603,248,640 B |
| A1 | 61.97 s | 1.027 s | 1.122 s | 4,599,747,584 B | 4,636,803,072 B |

Gate E is `FAIL`. The result report SHA-256 is
`097ab671d2cc6d37c128b4a42259a34c3982cadefb378ab0013567aaae299c1b`.

The frozen scorer's first attempt failed before writing a report because invalid
outputs carried nullable accuracy fields into a category sum. A checksum-bound
scorer-only wrapper counted those unscorable outputs as incorrect and reused the
same immutable predictions and labels. The frozen score above is unchanged by
generation or label repair.
