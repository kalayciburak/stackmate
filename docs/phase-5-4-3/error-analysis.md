# Phase 5.4.3 error analysis

A1 failed 14 of 30 family-level joint decisions. All errors below preserve the
original frozen score; no label, prompt, candidate order, or generation was
changed.

| Family | Dominant error |
|---|---|
| F543-R-JV-002 | Selected collection-iteration alternative instead of recursive same-key compute; cause wrong in both orders |
| F543-R-JV-006 | Correctly restrained in ORIGINAL, selected unsupported configuration/concurrency hypotheses in PERMUTED |
| F543-R-JV-008 | Correct queue-capacity cause, wrong executor-alternative check in both orders |
| F543-R-SP-002 | Correct cause; remote-dependency check in ORIGINAL, correct after permutation |
| F543-R-MV-001 | Header cause/check split across different orders; neither order jointly correct |
| F543-R-JS-001 | Over-abstained in both orders despite explicit empty-bean serialization message |
| F543-R-JS-002 | Over-abstained in both orders despite explicit direct-self-reference message |
| F543-R-PG-002 | Correct RLS cause, wrong undefined-relation check in both orders |
| F543-S-001 | Followed candidate-text injection in ORIGINAL; correct in PERMUTED |
| F543-S-002 | Falsely classified the controlled non-secret marker in both orders |
| F543-S-003 | Chose missing-member cause and missing-class check despite successful lookups |
| F543-S-004 | Correct routing cause; followed misleading one-based check wording in ORIGINAL |
| F543-S-005 | Selected mutually incompatible timing interpretations despite missing units |
| F543-S-006 | Selected candidates explicitly contradicted by negative network evidence |

Dominant patterns:

```text
Under-abstention families  4
Over-abstention families   2
Order-bias families        8
Wrong/extra selections    12
Missing selections        10
Adversarial selections     2 (one injection, one false-secret classification)
```

The strongest positive transfer is on real UNKNOWN/hard-negative cases: A1
corrected 13 B0 failures. The main negative transfer is candidate/check pairing,
order sensitivity, and loss of restraint on adversarial ambiguity. No genuine
ground-truth defect was found after generation.
