# Phase 6 decisions

- Ship v0.1.0 as a deterministic Spring Boot Servlet/MVC diagnostics starter.
- Do not expose local or remote inference as a supported v0.1.0 feature.
- Publish only the parent, core, deterministic auto-configuration and
  deterministic starter coordinates.
- Preserve inference, benchmark and alignment history in the repository while
  excluding it from release artifacts and public configuration metadata.
- Keep the full reactor for local historical verification; publication is
  controlled by an explicit machine-readable allowlist and fail-closed audit.
- Keep the default enablement convention for now: adding the starter enables
  deterministic diagnostics unless `stackmate.enabled=false`; freeze the final
  policy only after Gate C tests its safety implications.
- Keep the production-profile guard as a convenience safeguard, never as a
  security boundary.
- Permit pre-release breaking cleanup of internal leaks and unsupported
  inference properties, but document every consumer-visible change.
- Keep version `0.1.0-SNAPSHOT` until Gates A-H pass. A local
  `0.1.0-rc.1` assembly and tag are Gate I outputs only.
- Do not push, publish, download a model, add an inference runtime or claim
  remote cross-platform verification in this phase.
