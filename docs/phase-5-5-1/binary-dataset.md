# Phase 5.5.1 binary dataset audit

Gate B audited every Phase 5.3 TRAIN and VALIDATION family using canonical
variant zero. The original files and listwise targets were not changed. A raw
proposal mapped reviewed selected IDs to `SUPPORTED` and unselected IDs to
`NOT_SUPPORTED`; no proposed label was accepted without candidate-level review.

## Raw proposal

| Split | Families | Cause | Check | Total | Supported | Not supported |
|---|---:|---:|---:|---:|---:|---:|
| TRAIN | 70 | 103 | 103 | 206 | 102 | 104 |
| VALIDATION | 20 | 27 | 27 | 54 | 18 | 36 |

The following optimistic minimum exclusions were applied. Counts are disjoint in
this corpus:

| Reason | TRAIN | VALIDATION |
|---|---:|---:|
| Check contains an explicit list/comma or standalone `or` | 58 | 10 |
| Probe/placeholder semantics depend on hidden persistent IDs | 12 | 4 |
| Unselected cause still carries direct positive evidence | 25 | 5 |
| Hedged `may` cause is candidate-locally ambiguous | 7 | 1 |
| Identical model input has conflicting labels | 2 | 0 |
| Total excluded | 104 | 20 |

## Conservative upper bound

| Split | Represented families | Cause | Check | Total | Supported | Not supported |
|---|---:|---:|---:|---:|---:|---:|
| TRAIN | 64 | 65 | 37 | 102 | 57 (55.882%) | 45 (44.118%) |
| VALIDATION | 19 | 19 | 15 | 34 | 12 (35.294%) | 22 (64.706%) |

The ratio targets pass, but the validation absolute minimum does not:
`SUPPORTED` has 12 defensible pairs and requires at least 15. This is an
optimistic upper bound because checks joined only by `and` and other subtle
partial-support cases were not additionally removed once the hard gate failed.

Retained candidate text is 53–178 characters in TRAIN and 62–178 in VALIDATION.
Candidate-linked evidence has 1–6 items per TRAIN pair and 1–5 per VALIDATION
pair. Seven source families retain no defensible pair under this screen.

No ambiguous pair received a forced label. No binary dataset JSONL or manifest
was created. The machine audit is
`reports/phase-5-5-1-binary-source-audit.json`, SHA-256
`18a489fbcd86f41e9d8d8e111fd192a0a1e1e336452e1286b37ece5ae6db6c7d`.

Gate B result: `BINARY_VALIDATION_CLASS_INSUFFICIENT`.
