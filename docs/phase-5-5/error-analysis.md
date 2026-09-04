# Phase 5.5 error analysis

No model prediction exists, so there are no false-support examples to enumerate.
The blocking failure is dataset methodology:

| Taxonomy | Finding |
|---|---|
| `AMBIGUOUS_GROUND_TRUTH` | 140 unselected native pairs have resolved evidence but no candidate-local fact proving contradiction. |
| `CONTRADICTED_CLASS_ABSENT` | TRAIN and VALIDATION each contain zero defensible contradicted pairs. |
| `LABEL_INVENTION_RISK` | Mapping listwise omission to contradiction would turn ranking annotations into unsupported entailment labels. |
| `CROSS_FAMILY_NEGATIVE_RISK` | Transplanting candidates between incidents would create unreviewed semantics and possible co-occurring “negatives.” |
| `TEMPLATE_OVERLAP` | Generic candidate/evidence content repeats across development splits and exposed catalogs. |

The pairwise hypothesis is neither confirmed nor disproved. The available source
annotation is insufficient to test the requested three-way decision boundary.
A valid future experiment would need independently reviewed candidate-local
counterevidence created without reading exposed expected answers.
