# ADR 0050: Release and research module boundary

- Status: Accepted
- Date: 2026-09-04

## Context

The repository contains a proven deterministic product and a larger body of
historical inference research. The existing reactor verifies both, but the
publishable auto-configuration artifact currently compiles and packages local
inference properties, coordinators and auto-configurations. Optional Maven
dependencies prevent normal transitive resolution but do not make the JAR or
its generated metadata a deterministic-only public surface.

Deleting research modules would damage reproducibility. Publishing them would
misrepresent v0.1.0 support.

## Decision

Use a source-preserving module boundary:

- keep all historical modules in the repository and full local reactor;
- move Spring inference integration out of the publishable deterministic
  auto-configuration module into a non-publishable historical module;
- make the executable sample and default packaged tests deterministic-only;
- mark every non-public module with `maven.deploy.skip=true`;
- enforce the four-coordinate release allowlist in
  `release/release-artifacts.json`;
- audit assembled public JARs and POMs for research/model content.

The publishable coordinates are exactly:

```text
io.github.kalayciburak:stackmate-parent
io.github.kalayciburak:stackmate-core
io.github.kalayciburak:stackmate-spring-boot-autoconfigure
io.github.kalayciburak:stackmate-spring-boot-starter
```

## Consequences

- The deterministic starter cannot expose an inference transitive, property,
  prompt, auto-configuration or model class.
- Historical modules remain buildable but cannot deploy through the normal
  release path.
- Research verification may remain broader than product CI, but public release
  assembly consumes only the allowlist.
- A future supported inference product would require a new explicit release
  decision and compatibility boundary.
