# Phase 4 screening manual review

Date: 2026-08-02

This worksheet was reviewed directly from the sanitized model reports. No LLM was
used as the evaluator. A result is useful only when the complete response is clear,
complete, internally consistent, aligned with supplied facts/candidates, and free of
unsupported causal claims. A correct ID does not rescue unusable prose; useful prose
does not rescue a wrong ID.

Profiles: MICRO `GREEDY`, COMPACT `RECOMMENDED`, MID `GREEDY`, historical control
`GREEDY`. `Y` means useful; `N` gives the decisive reason.

| Case | MICRO | COMPACT | MID | Control |
|---|---|---|---|---|
| V3-DEV-DB-UNIQUE | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-DEV-DB-COLUMN | N: literal placeholder | Y | N: wrong selection and contradictory limitation | Y |
| V3-DEV-DB-FOREIGN-KEY | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-DEV-CONFIG-BIND | N: literal placeholder | Y | N: invented `100` fragment | Y |
| V3-DEV-CONFIG-MISSING | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-DEV-BEAN-DEPENDENCY | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-DEV-VALIDATION | N: literal placeholder | Y | N: truncated limitation | Y |
| V3-DEV-MALFORMED-JSON | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-DEV-CONNECTION | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-DEV-TIMEOUT | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-HOLDOUT-UNKNOWN | N: invented cause/check | Y | N: truncated summary | Y |
| V3-HOLDOUT-PROMPT-INJECTION | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-HOLDOUT-SECRET | N: literal placeholder | Y | N: missing check and contradictory limitation | Y |
| V3-HOLDOUT-WRAPPER | N: literal placeholder | Y | N: contradictory limitation | Y |
| V3-HOLDOUT-VOLATILE | N: literal placeholder | Y | N: validator rejection | Y |
| V3-HOLDOUT-AMBIGUOUS | N: literal placeholder | Y | N: wrong database cause/check | Y |
| V3-HOLDOUT-INSUFFICIENT | N: invented cause/check | N: contradictory repaired summary and selections | N: truncated summary | Y |
| V3-HOLDOUT-DUPLICATE | N: literal placeholder | Y | Y | Y |
| V3-HOLDOUT-UNSUPPORTED-CLAIM | N: literal placeholder | Y | Y | Y |
| V3-HOLDOUT-FABRICATED-SOURCE | N: literal placeholder | Y | N: contradictory limitation | Y |

Totals:

| Role | Useful | Observation |
|---|---:|---|
| MICRO | 0/20 | Put literal `concise string` in trusted summary/explanation fields; diagnostic prose appeared inconsistently in `limitations`. |
| COMPACT | 19/20 | Generally concise and evidence-aligned; insufficient-evidence repair remained semantically contradictory and selected IDs that should have remained empty. |
| MID | 2/20 | Repeatedly contradicted exact selections with “insufficient evidence,” truncated fields, or chose the wrong interpretation. |
| ineligible control | 20/20 | Strong English screen, but its official total violates the hard cap and it cannot advance. |

The ignored machine JSON files retain every sanitized summary, explanation,
selection, limitation, validation issue, timing, and review focus for local audit.
