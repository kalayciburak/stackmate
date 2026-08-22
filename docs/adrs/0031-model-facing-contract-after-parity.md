# ADR 0031: Separate model selection from deterministic prose

- Status: Accepted for future alignment research
- Date: 2026-08-22

## Context

Phase 5.2 F1 achieved perfect synthetic validation but generated unknown IDs and
incorrect selections on locked data. Phase 5.3 showed that request-scoped grammar
had materially inflated the previous GGUF baseline and that opaque ID generation,
selection, and prose authoring were entangled.

Development-only ablations compared opaque IDs, local ordinals, per-candidate
decisions, selection-only output, and prose-only output under original and
deterministically permuted candidate order.

## Decision

Adopt `SEPARATE_SELECTION_AND_PROSE` for one future bounded experiment:

- the model outputs only zero-based request-local cause/check indexes and either
  null or `EVIDENCE_INSUFFICIENT`;
- Java validates index bounds, maps indexes to the current request candidates, and
  derives evidence references;
- Java renders trusted existing cause/check text and limitations;
- the model does not author summary or explanation prose in the selected path;
- candidate order is deterministically varied in future training data and measured
  in validation.

This decision does not modify the production inference schema or product runtime in
Phase 5.3. It selects a research contract for future training only.

## Evidence

BF16 selection-only T1 produced 40/40 valid output, 38/40 cause/check, 8/8
restraint, and zero unknown references in original order. Under deterministic
permutation it remained 40/40 valid, 34/40 cause/check, 8/8 restraint, and zero
unknown references. Selection invariance was 36/40.

C1 and C2 eliminated unknown references but performed materially worse. F1 did not
generalize to their new output formats. F1 prose-only T2 was structurally perfect,
yet manual review found four unsupported technical claims, seven confusing cases,
and no measurable clarity beyond deterministic text.

## Consequences

- Unknown generated IDs become structurally impossible after ordinal bound checks.
- The LLM retains only bounded prioritization responsibility.
- Existing deterministic descriptions remain the sole rendered technical claims.
- Candidate-order sensitivity remains a measured risk and must be addressed in
  corpus construction.
- A future adapter must be trained directly on T1; existing F1 remains research-only.

## Rejected alternatives

- Keep C0: better than C1/C2 for F1, but unknown-ID generation and mixed prose
  responsibility remain.
- Use C1 combined prose: no unknown references, but poor accuracy and restraint.
- Use per-candidate C2: explicit decisions did not compensate for unfamiliar format.
- Use model prose only: strict structure did not prevent unsupported or confusing
  explanations.
