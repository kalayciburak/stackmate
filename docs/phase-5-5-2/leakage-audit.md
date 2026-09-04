# Phase 5.5.2 leakage audit

Gate C and D audits passed on the frozen atomic dataset:

| Check | Result |
|---|---:|
| Family overlap | 0 |
| Full model-input overlap | 0 |
| Normalized model-input overlap | 0 |
| Same-input conflicting labels | 0 |
| Duplicate TRAIN model inputs | 0 |
| Duplicate VALIDATION model inputs | 0 |
| Candidate-text dual-label coverage | 100% / 100% |
| Minimum families per semantic and label | 2 |

Candidate semantics and exact canonical text intentionally cross splits. The
incident evidence and controlled topology differ; family IDs, split names,
semantic IDs, category, list position, label and review rationale are absent
from model input.

The first controlled draft had 16 repeated TRAIN model inputs. Review added one
bounded sanitized runtime message or one method-only application frame and then
regenerated all files. The final 612 model inputs are unique.

The 15 controlled TRAIN and 15 controlled VALIDATION counterparts remain
development templates. Their exact and normalized complete inputs do not
overlap, but this does not create a claim of semantic independence or pristine
generalization.

Dataset audit SHA-256:
48e03e8ab1d124735371723a359738337e3867fb29a31144b7d6791c9214848d.
