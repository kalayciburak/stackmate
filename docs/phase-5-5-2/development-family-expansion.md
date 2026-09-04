# Phase 5.5.2 development family expansion

The repair preserves both frozen Phase 5.3 source files and builds a new
development-only family pool around the reviewed ontology.

| Source | TRAIN | VALIDATION |
|---|---:|---:|
| Retained Phase 5.3 families | 45 | 10 |
| New controlled atomic families | 15 | 15 |
| Final model-dataset families | 60 | 25 |

All G53-S-SEL-* probe families were removed. Other source families without a
reviewed ontology predicate were retained only when they formed a deliberately
near same-domain negative; distant fallback contexts were excluded. In total,
35 of the 90 source development families do not enter the repaired dataset.

Exactly one new controlled family per cause predicate was created in each split.
The 30-family expansion is within the authorized cap and target. These are
deterministic controlled synthetic development contexts, not production
incidents, runtime reproductions or a future pristine final set.

The two counterparts use distinct bounded operation contexts. SQLState-class
witnesses use different concrete values; exact-state and exception predicates
use different operation methods and wrapper topology. Neither family ID, split,
recipe, category nor ontology ID enters model input.

New IDs were checked against the existing 318-record exposure inventory. No
family-ID collision was found, and no exposed expected label was accessed.
