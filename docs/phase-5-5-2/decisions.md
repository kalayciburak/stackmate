# Phase 5.5.2 decisions

- Preserve the Phase 5.5 and 5.5.1 blocked reports and all frozen source hashes.
- Keep binary `SUPPORTED`/`NOT_SUPPORTED` semantics, label tokens, tokenizer,
  chat template and prompt unchanged unless a dataset-independent defect is
  proven before model execution.
- Build a reviewed atomic ontology before creating pair rows. Semantic IDs are
  metadata and must never enter model input.
- Admit a model-dataset candidate only when the exact canonical text has honest
  support and non-support contexts in distinct TRAIN and VALIDATION families.
- Additional development families are allowed only within the 40-family cap,
  must be controlled and reproducible, and cannot use any exposed diagnostic or
  future-final family.
- Counterfactual labels come from explicit evidence predicates, never listwise
  omission, model output, candidate position or renamed duplicates.
- Freeze family splits, ontology, pair reviews, shortcut baselines and security
  wrappers before any model weight is loaded.
- Stop immediately if ontology, dataset or shortcut gates fail; do not rescue a
  failed gate by relaxing thresholds or changing text after model results.
- Keep all implementation in research scope; no production worker, Spring
  integration, provisioning, model artifact, remote push or publication.
- Freeze ontology SHA-256 f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793
  and dataset-manifest SHA-256
  3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a
  before Gate E.
- Treat lexical/hash pair review as deterministic self-consistency only; bind
  the separate independent semantic review before declaring Gates B-D passed.
- Freeze maximum sequence 1024 from the measured 489-token maximum and required
  538-token safety margin.
- Stop permanently before Gate F because the frozen base run failed macro F1,
  prompt-injection and order-invariance gates. Do not use training to rescue a
  base path that did not satisfy the predeclared feasibility gate.
