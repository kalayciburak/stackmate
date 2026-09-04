# Phase 5.5.1 aggregation policy

Status: `DESIGN_ONLY_NOT_IMPLEMENTED`.

The intended research boundary remains deterministic:

- only `SUPPORTED` pairs above validation-frozen score and margin thresholds are
  eligible;
- every other result, including an invalid output, is not selected;
- a check requires its supported linked cause or independent direct evidence;
- no selected cause/check produces `EVIDENCE_INSUFFICIENT`;
- Java renders trusted prose; the model never does.

The source corpus lacks explicit cause/check linkage, and Gate B provides neither
a valid dataset nor thresholds. Implementing aggregation would therefore freeze
unvalidated semantics. No Java source, product module, worker, endpoint or runtime
dependency changed.
