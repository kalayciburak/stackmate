# Phase 5.4.1 locked evaluation

Status: **COMPLETE; GATES FAILED**

The fail-closed runner opened the existing external frozen export exactly once
after selection commit `08b5b55adee82a83c3d81e599deca8ca6b3458ca`. It executed
the required 20-case Benchmark V2 holdout, 20-case Phase 5 blind set, and
30-case post-training challenge in order, comparing B0, B1, A1, and A2.

| Condition | Valid structured | Cause | Check | Known restraint | Generic fallback misuse |
|---|---:|---:|---:|---:|---:|
| B0 base + P0 | 47/70 | 44/70 | 44/70 | 0/6 | 2 |
| B1 base + P3 | 42/70 | 34/70 | 38/70 | 5/6 | 1 |
| A1 adapter + P3 | 1/70 | 0/70 | 0/70 | 0/6 | 0 |
| A2 adapter + P2 | 1/70 | 1/70 | 1/70 | 0/6 | 0 |

The selected adapter is an ordinal T1 adapter. The locked production harness
expects schema-v2 cause/check IDs, so most A1/A2 outputs were rejected as
invalid structured output. This is a contract mismatch and a failed alignment
qualification, not evidence of unsafe accepted claims: accepted unsupported
claims, fabricated references, accepted unknown IDs, secrets, prompt-injection
successes, and hidden thinking were all zero.

| Stage | B0 valid/cause/check | B1 valid/cause/check | A1 valid/cause/check | A2 valid/cause/check |
|---|---|---|---|---|
| Benchmark V2 holdout | 15/20, 15/20, 15/20 | 16/20, 11/20, 12/20 | 0/20, 0/20, 0/20 | 1/20, 1/20, 1/20 |
| Phase 5 blind | 11/20, 10/20, 10/20 | 9/20, 8/20, 10/20 | 1/20, 0/20, 0/20 | 0/20, 0/20, 0/20 |
| Post-training challenge | 20/30, 19/30, 19/30 | 17/30, 15/30, 16/30 | 0/30, 0/30, 0/30 | 0/30, 0/30, 0/30 |

Raw output and the authoritative Java-scored report remain outside Git. The
repository contains the sanitized summary at
`research/model-alignment/reports/phase-5-4-1-locked-evaluation-summary.json`.

Because locked gates failed, full confirmation, merge, conversion, and GGUF
compatibility were not run.
