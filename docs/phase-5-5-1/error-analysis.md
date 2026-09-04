# Phase 5.5.1 error analysis

No model prediction exists, so there are no false-support outputs to review. The
blocking failures are annotation and candidate granularity:

| Taxonomy | Finding |
|---|---|
| `AMBIGUOUS_GROUND_TRUTH` | Listwise omission does not prove that a candidate-local evidence pair lacks direct support. |
| `POSITIVE_EVIDENCE_UNSELECTED` | Thirty unselected cause pairs carry evidence that positively states their broad candidate claim. |
| `COMPOUND_CHECK` | At least 68 check pairs combine multiple independently falsifiable actions using lists or `or`. |
| `ID_DEPENDENT_PLACEHOLDER` | Six probe families use candidate-ID/evidence-role semantics hidden behind placeholder prose. |
| `HEDGED_CAUSE` | Eight unselected causes state only that an alternative “may” hold. |
| `SECURITY_COVERAGE_ABSENT` | Natural candidate/evidence fields contain none of the required injection or negative-evidence cases. |

Examples include an unselected outer `HttpMessageNotReadableException` beside a
selected deeper `InvalidFormatException`, an unselected SQLState `22012` beneath
a selected outer `25P02`, and an unselected exact `IllegalStateException` in a
restraint family. Relabeling these without new candidate-level review would teach
hidden listwise specificity, not pairwise support.
