# ADR 0010: dedicated packaged-process E2E module

- Status: Accepted
- Date: 2026-08-01

## Context

Phase 1 unit and Spring context tests did not start the repackaged sample JAR. They therefore could not prove Boot loader
packaging, real HTTP behavior, parent/child command construction, pipe framing, process cleanup, or normal disabled and
language profiles as one deployed unit.

Putting Failsafe tests in `stackmate-sample` would run its integration-test phase before a downstream consumer could
depend on the completed repackaged artifact, and would mix production demonstration code with process-harness concerns.

## Decision

Add `stackmate-e2e` after `stackmate-sample` in the reactor. It has a test-scoped dependency on the sample to enforce
reactor order, contains no production code, and uses Maven Failsafe `*IT` tests to:

- start the executable sample with `ProcessBuilder` and discrete arguments;
- allocate a local ephemeral port and poll a bounded readiness endpoint;
- call real HTTP endpoints with the JDK HTTP client;
- capture stdout and stderr without shell interpolation;
- shut down through an E2E-only sample profile and assert that observed descendants are dead;
- exercise the real managed child protocol with a sample-only deterministic child;
- keep real-model execution behind the explicit `real-model-e2e` Maven profile.

The fake child lives in the sample artifact because it must be loadable from the exact Boot JAR under test. It is only
created by the `e2e-fake-child` profile and is not part of either StackMate starter. It speaks the production framed
protocol but never loads model weights or contacts a service.

The explicit real-model lifecycle profile is:

```bash
./mvnw -pl stackmate-e2e -am -Preal-model-e2e \
  -Dstackmate.model.path=/absolute/path/to/local-model \
  verify
```

It accepts either a validator-approved explanation or the bounded safe fallback. Requiring explanation success would
turn a lifecycle/safety test into an unsupported model-quality claim.

## Consequences

- Normal `verify` gains genuine packaged-process coverage and needs no model or network service.
- The reactor grows from eight to nine child modules (ten projects including the parent).
- Tests take roughly 30 seconds on the reference Windows machine because several isolated JVMs are deliberately used.
- Sample test-fixture code is packaged but inert unless an explicitly named E2E profile is activated.
- Linux and macOS behavior remains a CI claim only after those workflows actually execute; local Phase 2 evidence is
  Windows Java 21.

## Rejected alternatives

- Mocking `ProcessBuilder`: does not verify packaging or lifecycle.
- Calling a fake engine in-process: does not verify framing, stderr, timeout, restart, or orphan behavior.
- Localhost HTTP inference service: violates the chosen pipe protocol and adds an unnecessary network surface.
- Real model in normal CI: slow, platform-sensitive, and requires an external artifact.
