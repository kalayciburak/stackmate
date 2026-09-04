# Phase 5.5.2 shortcut baselines

Every fitted choice used TRAIN only; VALIDATION was scoring-only. Majority ties
fail safe to NOT_SUPPORTED.

| Baseline | Validation accuracy | Macro F1 | Gate |
|---|---:|---:|---:|
| S0 exact candidate model text | 43.55% | 42.14% | <=65% PASS |
| S1 semantic candidate ID | 43.55% | 42.33% | <=65% PASS |
| S2 candidate type | 50.00% | 33.33% | <=60% PASS |
| S3 input-character threshold | 42.47% | 35.54% | <=60% PASS |
| S4 negative-evidence presence | 50.00% | 33.33% | <=60% PASS |
| E0 evidence-type multiset | 51.61% | 50.93% | <=60% PASS |
| E1 evidence-item count | 43.01% | 36.39% | <=60% PASS |

S3 selected its threshold and direction solely on TRAIN. E0 and E1 are stricter
additional checks prompted by adversarial review; they prevent evidence shape
from replacing evidence semantics.

No label-specific delimiter, empty field, category marker or serialization
variant was found. Negative evidence is present in one SUPPORTED and one
NOT_SUPPORTED security row per split and uses the same typed structure.

Shortcut report SHA-256:
8ca37d5fb2acf706d2cde94903256acaaf6a5428c222718c269c1b394547a0b0.
