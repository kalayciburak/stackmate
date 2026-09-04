# Phase 5.5.2 aggregation policy

Status: NOT_RUN for implementation and freeze.

The base evaluator used deterministic set comparison only to measure family
behavior. It did not implement the future Java aggregation contract, thresholds,
cause exclusivity, check linkage or final prose.

The intended policy remains:

- only SUPPORTED candidates above both validation-frozen thresholds are eligible;
- checks require a selected linked cause or independent direct evidence;
- no eligible cause/check produces EVIDENCE_INSUFFICIENT;
- Java owns all final prose.

Gate E failure prevented threshold calibration, so freezing or implementing this
policy would be premature. No Java source or production module changed.
