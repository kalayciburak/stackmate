# Phase 5.5 leakage audit

The audit compared family identities and several deterministic signatures before
any model execution. No individual exposed diagnostic label was loaded into a
pairwise record or written to the report.

## TRAIN versus VALIDATION

| Check | Result |
|---|---:|
| Family ID overlap | 0 |
| Raw pair-signature overlap | 2 |
| Normalized pair-signature overlap | 8 |
| Candidate-text overlap | 14 |
| Candidate-semantic overlap | 8 |
| Evidence-signature overlap | 6 |

Family separation passes. The nonzero content overlaps reflect reused generic
candidate/evidence templates and remain a memorization/confounding risk for a
future pairwise experiment. They do not authorize moving families between
splits or assigning negative labels.

## Exposed diagnostics

The existing inventory supplied 228 eligible records after excluding the same
real-world TRAIN/VALIDATION source records. Their family-ID overlap with the 90
development families was zero. Hash-only comparison found 11 expected-cause and
11 expected-check signature overlaps, consistent with stable candidate concepts
reappearing across historical catalogs. This is a diagnostic overlap finding,
not access to per-case expected labels.

Because no pairwise dataset was frozen and no model was trained, there is no
training-to-diagnostic leakage event in Phase 5.5. Any future dataset repair must
repeat semantic overlap review after adding independently reviewed
counterevidence; it must not mine exposed expected answers.

Source bindings:

```text
TRAIN SHA-256            ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93
VALIDATION SHA-256       bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789
Dataset manifest SHA-256 0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2
Inventory SHA-256        cb4c52669c5369b8443482400311bc0fb578d2f9640ef9bfdb6cca39705467a3
```
