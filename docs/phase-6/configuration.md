# Phase 6 configuration contract

Unknown properties below the `stackmate` prefix are rejected
(`ignoreUnknownFields=false`). Adding the deterministic starter enables
StackMate by default; `stackmate.enabled=false` makes it inert. Under a matched
production profile, analysis requires the explicit
`stackmate.safety.allow-in-production=true` opt-in.

| Property | Default | Bound or meaning |
|---|---|---|
| `stackmate.enabled` | `true` | global deterministic auto-configuration switch |
| `stackmate.application-packages` | empty | at most 32 nonblank prefixes, 256 characters each |
| `stackmate.capture.mvc-unhandled` | `true` | capture failures escaping the Servlet MVC chain |
| `stackmate.capture.selected-mdc-keys` | `traceId, spanId, correlationId` | at most 16 nonblank keys, 64 characters each |
| `stackmate.diagnostics.maximum-cause-depth` | `32` | 1..64 |
| `stackmate.diagnostics.maximum-suppressed-exceptions` | `8` | 0..16 per throwable |
| `stackmate.diagnostics.maximum-stack-frames` | `120` | 0..256 per throwable |
| `stackmate.diagnostics.maximum-application-frames` | `8` | 1..32 |
| `stackmate.diagnostics.maximum-message-length` | `2000` | 1..4096 code points |
| `stackmate.privacy.redact-values` | empty | literal values only; 32 entries, 3..256 code points each |
| `stackmate.deduplication.enabled` | `true` | bounded in-memory duplicate tracking |
| `stackmate.deduplication.ttl` | `10m` | positive, at most one day |
| `stackmate.deduplication.maximum-entries` | `512` | 1..10000 |
| `stackmate.console.enabled` | `true` | StackMate diagnostic logger output |
| `stackmate.console.style` | `BOX` | `BOX` or `COMPACT` |
| `stackmate.console.maximum-width` | `100` | 60..200 characters |
| `stackmate.console.show-application-trace` | `true` | show sanitized selected frames |
| `stackmate.console.show-technical-details` | `true` | show bounded technical evidence |
| `stackmate.console.show-cause-chain` | `false` | show bounded sanitized cause chain |
| `stackmate.console.debug-logging` | `false` | log bounded internal failure classes only |
| `stackmate.export.enabled` | `false` | optional local sanitized export |
| `stackmate.export.directory` | `.stackmate/incidents` | local export root |
| `stackmate.export.maximum-incidents` | `100` | 1..10000 retained recognized bundles |
| `stackmate.safety.allow-in-production` | `false` | explicit production-profile opt-in |
| `stackmate.safety.production-profiles` | `prod, production` | at most 32 names, 64 characters each |

`privacy.redact-values` never accepts regular expressions. It augments the
mandatory built-in sanitizer; it does not replace or weaken it. No inference,
model, remote-provider, API-key or cloud configuration is part of the public
starter.
