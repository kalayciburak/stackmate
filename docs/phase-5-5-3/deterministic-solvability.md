# Phase 5.5.3 deterministic solvability

Gate B stops the experiment before model loading. The frozen Phase 5.5.2
binary task is already an executable ontology-predicate task.

## Audit boundary

The audit creates label-blind prediction projections first and hashes them
before opening scoring labels or family/pair identities. The projection contains
only D0/D1/D2 decisions and reasons. It cannot contain expected labels, family
IDs, pair IDs, rationale, pair kind or missing-predicate metadata.

```text
TRAIN prediction receipt       0ecec7719bdc3300fde2125efe2a4d273e479d134d31bf035d57c90675a03ee5
VALIDATION prediction receipt  42d2ae12069def236347cbfafbbbb58766c84442678ae30f34031955e1a357ec
Combined prediction receipt    56de9ea7be8495ac3c584013e681134714aa5f34e6b33486220cc07be11e237e
```

D1 uses Java-owned semantic identity and the checked-in ontology predicate
interpreter. D2 receives only candidate type, exact canonical candidate text,
trusted evidence and negative evidence from the bounded input. D2 implements
the flat `EXACT`, `EXACT_ANY` and `PREFIX` language independently and never
calls the reusable predicate evaluator. Exact trailing untrusted-pattern
wrappers are removed as wrappers, never executed as instructions.

Required support may come only from the positive trusted-evidence block.
Negative evidence can disqualify a candidate only when the ontology declares a
matching disqualifier. Every current ontology entry has an empty
`disqualifyingEvidence` list, so that clause is presently vacuous.

## D0 applicability

The historical D0 policy selects one specific product cause and its same-slug
product check. Phase 5.5.2 replaced those candidate semantics with one atomic
cause and three atomic checks per predicate, without freezing a product-ID to
atomic-semantic crosswalk. A faithful D0 projection onto these 612 pair rows is
therefore unavailable.

```text
Atomic TRAIN coverage       0/426
Atomic VALIDATION coverage  0/186
Atomic combined coverage    0/612
Candidate/family metrics    NOT_APPLICABLE
```

For context only, historical D0 on its native 30-family Phase 5.4.3 corpus
reached 15/30 cause, 15/30 check and 15/30 joint accuracy, 11/11 strict
restraint, 30/30 order invariance and zero prompt-injection success. Those
figures are not compared with atomic pair metrics.

## D1 and D2 results

| Baseline | Split | Pairs | SUPPORTED P/R | NOT_SUPPORTED P/R | Macro F1 | False/missed support | Cause/check/joint | Restraint | Security false support |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| D1 construction oracle | TRAIN | 426 | 100% / 100% | 100% / 100% | 100% | 0 / 0 | 60/60 / 60/60 / 60/60 | 17/17 | 0/13 |
| D1 construction oracle | VALIDATION | 186 | 100% / 100% | 100% / 100% | 100% | 0 / 0 | 25/25 / 25/25 / 25/25 | 5/5 | 0/13 |
| D2 independent interpreter | TRAIN | 426 | 100% / 100% | 100% / 100% | 100% | 0 / 0 | 60/60 / 60/60 / 60/60 | 17/17 | 0/13 |
| D2 independent interpreter | VALIDATION | 186 | 100% / 100% | 100% / 100% | 100% | 0 / 0 | 25/25 / 25/25 / 25/25 | 5/5 | 0/13 |

D1 errors: 0. D2 errors: 0. Therefore every required error audit is empty;
all six predeclared error-taxonomy counters are zero.

The frozen Phase 5.5.2 base candidate metrics were also recomputed from all 612
stored pair results. TRAIN, VALIDATION and combined metrics exactly equal the
stored report without loading the model.

## Interpretation and stop

The dataset manifest declares `predicateTruthDeterminesLabels: true`, and the
generator assigns each label from the same support predicate. D1 is therefore a
construction oracle. D2 supplies implementation independence, but because it
consumes the same ontology relation it is construction-equivalent rather than
an independent semantic generalization test.

Both baselines exceed every frozen deterministic-solvability threshold and
equal all reviewed labels by construction. Gate B result:

```text
TASK_DETERMINISTIC
LLM_TASK_DETERMINISTIC
```

This conclusion is deliberately narrow: the frozen atomic binary task does not
justify LoRA. It does not prove that the current product selector solves every
real-world incident, and it does not convert the ontology selector into product
code in this research phase.

Machine-readable report SHA-256:
`43f6ef5de6222e1a93bc29b26544c67c0ff8ec7bae023fd01b7d6d62ffe9d426`.
