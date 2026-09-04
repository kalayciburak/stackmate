# Phase 6 decisions

- Ship v0.1.0 as a deterministic Spring Boot Servlet/MVC diagnostics starter.
- Do not expose local or remote inference as a supported v0.1.0 feature.
- Publish only the parent, core, deterministic auto-configuration and
  deterministic starter coordinates.
- Preserve inference, benchmark and alignment history in the repository while
  excluding it from release artifacts and public configuration metadata.
- Keep the full reactor for local historical verification; publication is
  controlled by an explicit machine-readable allowlist and fail-closed audit.
- Adding the starter enables deterministic diagnostics by default. The explicit
  global opt-out is `stackmate.enabled=false`.
- When an active profile matches `prod` or `production`, analysis remains
  blocked unless `stackmate.safety.allow-in-production=true` is explicitly set.
  This convenience guard is not a security boundary.
- Keep the production-profile guard as a convenience safeguard, never as a
  security boundary.
- Permit pre-release breaking cleanup of internal leaks and unsupported
  inference properties, but document every consumer-visible change.
- Use version `0.1.0-rc.1` across the reactor and external consumer for local RC
  verification. All local gates passed, so the authorized annotated tag is
  `v0.1.0-rc.1`; publication remains separately prohibited.
- Do not push, publish, download a model, add an inference runtime or claim
  remote cross-platform verification in this phase.

The enablement decision is recorded in ADR 0051.
