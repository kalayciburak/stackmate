# Phase 5.4 baseline

Date: 2026-08-22

Phase 5.4 starts from the completed Phase 5.3 commit
`1547368b611e220c25275c8dba2114758b81f6c9` on branch
`phase-5-3-evaluation-parity`, tagged `phase-5-3-complete`.

The one-shot branch is `phase-5-4-one-shot-real-world-alignment`.

## Verified starting state

| Item | Value |
|---|---|
| Official base | `Qwen/Qwen3.5-2B@15852e8c16360a2fea060d615a32b45270f8a8fc` |
| TRAIN | 140 rows; `ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93` |
| VALIDATION | 40 rows; `bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789` |
| Dataset manifest | `0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2` |
| Sealed final descriptors | `53f3ae08ff53bf192da6cd54f3a05de62cc9c3d0ec7fbadedf3b1c2c6caed61e` |
| Leakage report | `63ab07efbd7b2018fab4c98f17dd4aa81654921462803540eb2ffa72fce8a5b4` |
| Historical F1 | `6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca`; not reused for initialization |
| T1 contract | zero-based local cause/check indexes plus limitation; no model prose |

Phase 5.3 verification was reproduced before this branch: 516 Surefire, 11
Failsafe, 10/10 Python research tests, and 184/184 explicit corpus tests, with no
failures, errors, or skips. The normal Java build passed with Docker stopped.

## Scope boundary

Phase 5.4 will execute at most one new alignment run. The historical F1 adapter is
diagnostic-only. The sealed 30-family final set remains inaccessible until a
validation-only checkpoint decision is committed. No production worker, runtime
inference integration, provisioning, cloud inference, or model publication is in
scope.
