# ADR 0011: benchmark V2 independence strategy

- Status: Accepted
- Date: 2026-08-01

## Context

The V1 corpus contains 50 controlled scenarios and reported perfect deterministic category and root-type results. The
same implementation effort created both analyzers and fixtures, so that result cannot by itself distinguish robust
rules from benchmark alignment. Phase 2 needs a frozen, more realistic input before any analyzer or inference-contract
change.

## Decision

Freeze an 80-case JSON manifest in the downstream benchmark module with:

- 60 development and 20 holdout cases;
- explicit provenance, including 54 executable runtime or integration cases;
- independent ground-truth keys rather than production cause/check IDs;
- executable fixtures that use JDK, Spring, MockMvc, validation, JDBC, security, socket/TLS, and concurrency APIs;
- explicit `RESTRAIN` outcomes for cases that should remain unknown;
- a recorded commit and SHA-256 before analyzer behavior changes.

The manifest loader is strict, immutable, and isolated from production analyzers. V2 results are reported separately for
development and holdout sets. Historical V1 results are not overwritten.

## Consequences

- Deterministic accuracy may fall; this is evidence, not a reason to rewrite holdout expectations.
- Real recipes make the benchmark slower and bring test-only Spring validation, MockMvc, and H2 dependencies into the
  benchmark module.
- Root truth means the deepest throwable in the executed chain, while category evaluation can still use wrapper
  evidence. This distinction exposed several invalid initial fixture assumptions before the freeze.
- Cases can only be corrected after freeze through a new corpus version with an explained manifest change.

## Rejected alternatives

- Generate expected values from StackMate output: circular and unable to detect analyzer mistakes.
- Reuse analyzer constants in fixtures: creates direct alignment and hides ID drift.
- Keep only synthetic throwables: fast but insufficient evidence for real framework wrapper behavior.
- Modify holdout cases after seeing failures: invalidates the holdout.

