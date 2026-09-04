# Phase 5.5.2 official base-model feasibility

Gate E evaluated the exact official Qwen3.5-2B BF16 checkpoint with no adapter.
The evaluator used frozen token IDs 15/16, batch size 8, maximum sequence 1024,
direct next-token logits, no generation and two deterministic batch orders.

| Candidate metric | TRAIN | VALIDATION | Combined |
|---|---:|---:|---:|
| Valid label | 426/426 | 186/186 | 612/612 |
| Accuracy | 60.33% | 54.84% | 58.66% |
| Macro F1 | 48.67% | 45.20% | 47.62% |
| SUPPORTED precision | 79.41% | 80.00% | 79.59% |
| SUPPORTED recall | 14.29% | 12.90% | 13.83% |
| NOT_SUPPORTED precision | 58.67% | 52.63% | 56.84% |
| NOT_SUPPORTED recall | 97.05% | 96.77% | 96.97% |
| False support | 7 | 3 | 10 |
| Missed support | 162 | 81 | 243 |
| Injection success | 2 | 3 | 5 |

Family aggregation excluded security variants:

| Family metric | TRAIN | VALIDATION | Combined |
|---|---:|---:|---:|
| Cause exact | 21/60 | 11/25 | 32/85 |
| Check exact | 18/60 | 5/25 | 23/85 |
| Joint exact | 13/60 | 5/25 | 18/85 |
| Strict restraint | 12/17 | 5/5 | 17/22 |
| Over-abstention | 31 | 13 | 44 |
| Under-abstention | 5 | 0 | 5 |

The model predicted SUPPORTED for only 12 of 93 supported VALIDATION pairs,
while accepting three unsupported security pairs. Precision exactly met 80%,
but recall collapsed and macro F1 missed the 70% gate.

The two batch orders agreed on 606/612 predictions. Six changed class and the
maximum absolute label-logit difference was 0.25, above the frozen 0.05
tolerance. The original report did not retain second-pass pair identities, so
that diagnostic limitation is preserved rather than repaired through another
model run.

~~~text
Valid label gate          PASS
Unknown label gate        PASS
Prompt injection gate     FAIL
Order invariance gate     FAIL
SUPPORTED precision gate  PASS exactly at 80%
Macro F1 gate             FAIL at 45.20%
~~~

Gate E result: BINARY_BASE_NOT_PROMISING. Gate F training is prohibited.

Report SHA-256:
cd01c7696ffa05b2382af6a71c12a194baa2c24c5741a18fae392de712af57ea.
