# Phase 5.3 real-world corpus

Date: 2026-08-22

## Result

`stackmate-real-world-v1` is frozen for research use. It contains 120 incident
families and no private or production incident data.

| Classification | Future train | Future validation | Sealed final | Total |
|---|---:|---:|---:|---:|
| Controlled real runtime/integration | 55 | 15 | 20 | 90 |
| Controlled synthetic/adversarial | 15 | 5 | 10 | 30 |
| Total | 70 | 20 | 30 | 120 |

The real recipes cover Java runtime, Spring bean/configuration, MVC/Jackson,
loopback HTTP/TLS, filesystem, concurrency, Spring Security, and PostgreSQL.
Synthetic recipes are limited to throwable topology, conflicting evidence, and
selection-contract edge cases that cannot be reproduced safely as ordinary runtime
failures.

## Generated development data

Each TRAIN and VALIDATION family has exactly two variants. Variant zero preserves
deterministic candidate order; variant one applies a deterministic permutation.
Targets use contract T1 and contain only zero-based request-local cause/check
indexes plus a limitation. They contain no model-written explanation prose.

| File | Records | Bytes | SHA-256 |
|---|---:|---:|---|
| `train.jsonl` | 140 | 4,676,681 | `ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93` |
| `validation.jsonl` | 40 | 1,151,826 | `bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789` |
| `manifest.json` | 1 | 1,569 | `0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2` |
| `sealed-final-families.json` | 30 descriptors | 3,955 | `53f3ae08ff53bf192da6cd54f3a05de62cc9c3d0ec7fbadedf3b1c2c6caed61e` |

The source catalog SHA-256 is
`42d2bab37173a9b8a7579203e270a02fbe9ab795b12b9a731f39fa035a8244ee`.
Two independent exports after the final correction produced all four hashes
identically.

## Execution evidence

- Non-PostgreSQL development recipes: 94/94 executions passed.
- Controlled synthetic development recipes: 40/40 executions passed.
- PostgreSQL development recipes: 46/46 executions passed against the local
  ephemeral `postgres:17-alpine` container.
- Catalog/inventory checks: 4/4 passed, including complete fixture coverage.
- Combined targeted confirmation: 184/184 JUnit executions passed.
- LOCKED_FINAL recipe execution: 0.

PostgreSQL verification remains an explicit `RealWorldPostgresRecipeLab`; its
`Lab` suffix excludes it from normal Surefire discovery. PostgreSQL,
Testcontainers, and Spring Security are benchmark test-scope dependencies only.
The normal product has no Docker or database-container requirement.

## Ground-truth review

All 120 families completed both required passes:

1. Lexical family order, backed by two development executions per family and the
   exact deterministic request/export path. Review-order SHA-256:
   `56ae1b42159360456af4a444da630d09094949f6d60e6b57e8824136b8f262c9`.
2. Independent SHA-256 family order, static-only and without reading generated
   JSONL or executing final fixtures. Review-order SHA-256:
   `d1bc05be8783226476a998b5827b91697f8c74d1995342c4abb66ccc82db1ea2`.

Both passes found zero remaining root/wrapper, expected-selection, split,
reproducibility, or private-data anomalies. The machine report is
`research/model-alignment/reports/ground-truth-review.json`.

## Pre-freeze corrections

Fail-closed generation exposed and corrected catalog categories, one unmapped
Spring target, a two-node PostgreSQL failed-transaction chain, an asynchronous
fixture interrupt leak, and one `StackOverflowError` frame nondeterminism. The
first leakage pass also exposed a SocketException model-facing overlap and two
cross-split synthetic semantic near-matches; balanced family swaps removed them
before freezing. No expected answer was changed after the final hashes were set.

## Remaining limitations

- 33/140 TRAIN and 9/40 VALIDATION rows have an identical model-facing user prompt
  to their same-family second variant when the exception has no varying message
  and candidate permutation is a no-op. The family cap remains two and no duplicate
  crosses a split.
- Runtime captures naturally include laboratory stack frames in the stored audit
  fields. Training consumes the explicit `messages` field; application-frame facts
  are bounded by the deterministic request factory.
- This corpus establishes a materially stronger data basis, not model quality or
  production readiness. The 30 final families remain untouched in Phase 5.3.

```text
Catalog: FROZEN
Development recipe execution: PASS
Generated development dataset: FROZEN
Ground-truth two-pass review: PASS
Leakage audit: PASS
Final family execution: 0
```
