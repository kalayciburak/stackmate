# Phase 6 runtime hardening

## Enforced invariants

- The auto-configuration is Servlet-only and requires the Servlet and Spring
  MVC classes.
- The original application failure path is preserved; StackMate observes and
  rethrows rather than owning the HTTP response.
- The built-in `DefaultSanitizer` is the mandatory `@Primary` sanitizer. User
  literal redactions are additive.
- Throwable traversal, suppressed exceptions, messages, stack frames,
  application-frame selection, analyzer contributions, deduplication state and
  local-export retention are bounded.
- Unknown `stackmate.*` properties fail binding instead of being silently
  ignored.
- Custom analyzers are extension points and fail open: one analyzer failure must
  not replace the original application failure or prevent other bounded
  analysis.
- Internal rendering/export failures fail open and may expose only bounded
  failure-class diagnostics when debug logging is enabled.
- No supported path starts a model, child inference process, remote/cloud call,
  telemetry client or runtime network request.

The sanitizer boundary covers StackMate-controlled rendering and export only.
Because StackMate rethrows the original exception, the application server,
framework, application code or observability agents may independently log its
original message. StackMate does not rewrite those events and must not be
treated as global log sanitization.

## Enablement and production guard

The deterministic starter is enabled when present unless
`stackmate.enabled=false`. Matching `prod` or `production` blocks analysis by
default; `stackmate.safety.allow-in-production=true` is required to allow it.
Profile matching is case-insensitive and configurable. The guard is a
convenience safeguard, not authorization or a security boundary.

## Evidence status

The implementation and targeted tests cover the controls above. The completed
Phase 6 tree also passed the full reactor with 543 Surefire and 5 Failsafe tests;
failures, errors, and skips were all zero.
