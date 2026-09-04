# Phase 5.5.1 decisions

- Preserve the Phase 5.5 tri-class contract, audit, reports, commits and negative
  result as historical evidence.
- Define `NOT_SUPPORTED` as “the supplied trusted evidence does not directly
  justify selecting this candidate,” never as a universal falsehood.
- Use only existing Phase 5.3 real-world TRAIN and VALIDATION families for
  development; exposed diagnostic labels remain closed until all training and
  validation-only choices are frozen.
- Review ambiguous, compound, generic and check candidates before deriving rows;
  exclude uncertainty rather than force a binary label.
- Keep exactly one candidate per model input and exclude sibling candidates,
  indexes, persistent IDs, family IDs, labels, dispositions, source code,
  request bodies, headers, secrets and private paths.
- Freeze label tokens, prompt, serialization, dataset, scoring and the one-run
  configuration before base-model execution.
- Proceed to the one rank-8 BF16 LoRA run only if Gate B and Gate C pass exactly
  as written. Do not use historical listwise weights for initialization.
- Keep aggregation in research scope and deterministic Java logic; the model may
  neither select by list position nor generate prose.
- Create local commits at phase boundaries and perform no remote push.
