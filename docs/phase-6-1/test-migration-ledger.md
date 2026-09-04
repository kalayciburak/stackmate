# Phase 6.1 test migration ledger

This ledger compares the normal packaged-test surface at the Phase 5.5.3
checkpoint with the current Phase 6 root verification and the separate public
consumer. “Excluded” does not mean “migrated.”

## Count reconciliation

| Suite | Normal Failsafe composition | Count |
|---|---|---:|
| Phase 5.5.3 root | `PackagedDeterministicIT` (4) + `PackagedFakeChildIT` (6) + `PackagedModesIT` (1) | 11 |
| Phase 6 root | `PackagedDeterministicIT` (4) + `PackagedModesIT` (1) | 5 |
| Phase 6 external consumer | `PackagedConsumerIT` | 1 |

`PackagedRealModelIT` was opt-in and excluded from the normal Phase 5.5.3
Failsafe count. Its test class and `real-model-e2e` profile remain as historical
material, but the profile is statically incompatible with the deterministic
Phase 6 sample. It is not part of clean verification and is not a current
qualification path.

## Packaged fake-child disposition

The six `PackagedFakeChildIT` methods still exist in source but are explicitly
excluded by `stackmate-e2e/pom.xml` from the normal Phase 6 Failsafe run.

| Historical packaged case | Phase 6 disposition | Equivalent packaged migration? |
|---|---|---|
| Valid protocol output while child stderr remains private | Excluded; protocol and process behavior has lower-level Surefire coverage | No |
| Reject unknown evidence and fabricated source | Excluded; validators have lower-level Surefire coverage | No |
| Malformed, oversized and invalid UTF-8 responses | Excluded; protocol bounds/parsing have lower-level Surefire coverage | No |
| Child timeout and unexpected exit fallback | Excluded; simulated process behavior has lower-level Surefire coverage | No |
| Bounded queue and inference never blocking HTTP requests | Excluded; queue behavior has lower-level Surefire coverage | No |
| Shutdown during in-flight inference terminates the child without an orphan | Excluded; child lifecycle has lower-level Surefire coverage | No |

The lower-level tests cover validators, protocol framing, queue rules and
simulated process behavior. They do not exercise the removed end-to-end seam:
a packaged Spring application receiving HTTP traffic while managing an
inference child process. Therefore the six-case reduction is not presented as
one-for-one migration or unchanged packaged coverage.

## Intentional scope removal

The gap is accepted as intentional product-scope removal for the public alpha,
not as a successful test migration. Inference and the local worker are
`RESEARCH_ONLY` / `NOT_IN_RELEASE`; they are excluded from the public artifact
allowlist and are not supported runtime behavior.

The public deterministic packaged suite remains responsible for:

- successful HTTP 200 behavior and unhandled HTTP 500 behavior;
- handled malformed input without a false StackMate capture;
- unknown-exception restraint without an invented resolution;
- sanitization/redaction and duplicate reuse;
- disabled mode; and
- packaged sample shutdown.

The independent consumer repeats the release-facing packaged boundary with HTTP
200, 500 and 418 behavior, redaction, deduplication and process shutdown. Its six
contract tests separately verify starter defaults, disabled/non-web behavior,
strict configuration, extension use and absence of inference classes.

## Follow-up rule

No rc2 task should describe `PackagedFakeChildIT` as migrated unless a new
packaged test actually crosses the equivalent HTTP/child lifecycle boundary.
Restoring such coverage would be research-surface work and is not required for
the deterministic public-alpha release qualification.
