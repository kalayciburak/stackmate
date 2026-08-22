# Phase 5.3 old-versus-real dataset audit

Date: 2026-08-22

## Structural comparison

| Metric | Phase 5.2 train | Phase 5.2 validation | Phase 5.3 train | Phase 5.3 validation |
|---|---:|---:|---:|---:|
| Rows | 800 | 200 | 140 | 40 |
| Families | 32 | 8 | 70 | 20 |
| Mean variants/family | 25 | 25 | 2 | 2 |
| Maximum variants/family | 30 | 30 | 2 | 2 |
| Controlled real rows | 0 | 0 | 110 | 30 |
| Controlled synthetic rows | 800 | 200 | 30 | 10 |
| Restraint rows | 200 | 50 | 40 | 22 |
| Candidate-order permutations | 0 | 0 | 70 | 20 |
| Unique exception-chain signatures | not represented | not represented | 46 | 16 |
| Mean user-prompt characters | 637.1 | 643.0 | 2,090+ | 2,140+ |

Phase 5.2 intentionally generated many lexical variants from 40 templates. That
was sufficient for feasibility and validation-only adapter selection, but it did
not resemble the locked runtime distributions that later failed. Phase 5.3 uses 90
development families, two bounded variants each, 70 real runtime/integration
families, actual deterministic request candidates, and explicit original/permuted
orders.

Expected selections are no longer concentrated only in opaque IDs. Phase 5.3 T1
targets use request-local indexes zero through two; 31 TRAIN expected selections
occur outside index zero. The contract removes globally stable model-facing IDs,
while Java retains ownership of mapping and deterministic prose.

## What improved

- Family repetition fell from a mean of 25 to exactly two.
- Validation grew from 8 semantic families to 20 while remaining family-separated.
- Real Java/Spring/PostgreSQL execution supplies exception topology and analyzer
  evidence instead of templated incident sentences.
- Candidate-order perturbation is explicit and balanced.
- Strict restraint represents 22/40 validation rows, directly testing the failure
  mode exposed by F1 under T1.
- Leakage is checked against every prior locked/development source and the sealed
  new final descriptors.

## Limitations

The new data is controlled laboratory data, not production telemetry. Same-family
variants can still produce identical model-facing prompts when candidate order has
only one item and the real exception has no varying message. Stored raw stack
traces contain bounded test-harness frames. These facts are documented rather than
treated as evidence of model generalization.

Machine report:
`research/model-alignment/reports/old-vs-real-dataset-audit.json`, SHA-256
`de1d17ad74144be59eb052c6be122a558e6f60d93f931af4fc9118c643604dad`.
