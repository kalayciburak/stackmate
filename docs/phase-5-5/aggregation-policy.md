# Phase 5.5 aggregation policy

Status: `DESIGN_ONLY_NOT_IMPLEMENTED`.

The intended deterministic boundary remains:

- only `SUPPORTED` candidates above validation-frozen score and margin thresholds
  are eligible;
- `CONTRADICTED` and `UNKNOWN` candidates are not selected;
- a check requires either its selected linked cause or independent direct
  support;
- no selected cause/check yields deterministic `EVIDENCE_INSUFFICIENT`;
- Java, not the model, would render any final explanation.

The native corpus does not encode candidate-level cause/check links, explicit
negative predicates, or calibrated thresholds. Implementing aggregation now
would therefore hard-code unvalidated semantics. No Java source, production
module, worker, endpoint, configuration property, or runtime dependency changed.
