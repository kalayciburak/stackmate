# ADR 0051: v0.1.0-rc.1 default enablement

- Status: Accepted
- Date: 2026-09-04

## Context

The v0.1.0 public alpha is installed as a Spring Boot starter. Requiring a second
enablement property would make starter installation unexpectedly inert, while
unconditional production activation would be too surprising for a diagnostic
component that observes exception data and writes console output.

The component is deterministic, bounded, sanitized, fail open and does not own
the HTTP response. A production-profile check can reduce accidental activation,
but profile names are conventions and cannot provide an authorization boundary.

## Decision

The deterministic starter is enabled by default when present. Users can make it
fully inert with:

```properties
stackmate.enabled=false
```

When any active profile matches the configured production-profile list
(`prod` and `production` by default), analysis is blocked unless the user makes
this explicit opt-in:

```properties
stackmate.safety.allow-in-production=true
```

Unknown `stackmate.*` properties remain binding errors. The production guard is
a convenience safeguard only and is not a security boundary.

## Consequences

- Adding the starter has useful deterministic behavior in development without
  another property.
- Applications retain a single explicit global opt-out.
- Known production profiles require deliberate enablement and otherwise emit at
  most the bounded guard warning behavior.
- Custom production profile names must be listed under
  `stackmate.safety.production-profiles`.
- Documentation and consumer verification must cover default, disabled and
  production-guarded modes.
