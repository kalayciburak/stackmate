# Phase 5.5.3 decisions

- Preserve Phase 5.5.2 datasets, ontology, prompt, label mapping, model revision
  and reports byte-for-byte.
- Treat the weak untrained base result as a comparator, not as the Phase 5.5.3
  training gate.
- Audit deterministic solvability before loading model weights.
- Implement D0, D1 and D2 without family-ID rules, expected-label lookup,
  validation-specific conditions or raw-message answer lookup.
- Treat equality caused by the frozen label-generation predicate as evidence
  that the published task is deterministic, not as model success.
- Stop before model-eligible splitting, logit qualification and training when
  D1 or D2 reproduces reviewed labels by construction or reaches the frozen
  deterministic-solvability gate.
- Keep all new implementation research-only. Do not add Maven runtime ML
  dependencies, production integration, provisioning or automatic downloads.
- Keep model and adapter artifacts outside Git and do not push this branch.
- Record D0 as `NOT_APPLICABLE` on the atomic pair corpus. The product selector
  has no frozen product-candidate-to-atomic-semantic crosswalk, so assigning
  atomic check decisions would invent behavior. Its historical 30-family result
  remains a scope-limited reference only.
- Classify D1 as `CONSTRUCTION_ORACLE` and D2 as
  `CONSTRUCTION_EQUIVALENT`. D2 uses an independent predicate interpreter but
  consumes the same ontology relation that generated the labels.
- Close Gate B as `TASK_DETERMINISTIC` and do not create a model-eligible
  subset, nested validation split, evaluator matrix, adapter or calibration.
