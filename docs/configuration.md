# Configuration reference

StackMate `0.1.0-rc.1` exposes exactly 25 public `stackmate.*` properties. Binding is strict:
`@ConfigurationProperties(ignoreUnknownFields = false)` rejects stale, misspelled, and unsupported fields.

The starter is enabled by default. Set `stackmate.enabled=false` for a complete opt-out.

## Complete property list

| Property | Default | Accepted value or bound |
| --- | --- | --- |
| `stackmate.enabled` | `true` | Boolean |
| `stackmate.application-packages` | `[]` | At most 32 nonblank package prefixes, each at most 256 characters |
| `stackmate.capture.mvc-unhandled` | `true` | Boolean; capture exceptions escaping the MVC servlet chain |
| `stackmate.capture.selected-mdc-keys` | `[traceId, spanId, correlationId]` | At most 16 nonblank keys, each at most 64 characters |
| `stackmate.diagnostics.maximum-cause-depth` | `32` | `1..64` |
| `stackmate.diagnostics.maximum-suppressed-exceptions` | `8` | `0..16` per throwable |
| `stackmate.diagnostics.maximum-stack-frames` | `120` | `0..256` per throwable |
| `stackmate.diagnostics.maximum-application-frames` | `8` | `1..32` |
| `stackmate.diagnostics.maximum-message-length` | `2000` | `1..4096` code points |
| `stackmate.privacy.redact-values` | `[]` | At most 32 literal values; each 3..256 characters |
| `stackmate.deduplication.enabled` | `true` | Boolean |
| `stackmate.deduplication.ttl` | `10m` | Positive duration, at most `1d` |
| `stackmate.deduplication.maximum-entries` | `512` | `1..10000` |
| `stackmate.console.enabled` | `true` | Boolean |
| `stackmate.console.style` | `box` | `box` or `compact` |
| `stackmate.console.maximum-width` | `100` | `60..200` characters |
| `stackmate.console.show-application-trace` | `true` | Boolean |
| `stackmate.console.show-technical-details` | `true` | Boolean |
| `stackmate.console.show-cause-chain` | `false` | Boolean |
| `stackmate.console.debug-logging` | `false` | Boolean; bounded internal failure classes only |
| `stackmate.export.enabled` | `false` | Boolean |
| `stackmate.export.directory` | `.stackmate/incidents` | Local filesystem path |
| `stackmate.export.maximum-incidents` | `100` | `1..10000` recognized bundles |
| `stackmate.safety.allow-in-production` | `false` | Boolean |
| `stackmate.safety.production-profiles` | `[prod, production]` | At most 32 nonblank profile names, each at most 64 characters |

## Default configuration

```yaml
stackmate:
  enabled: true
  application-packages: []
  capture:
    mvc-unhandled: true
    selected-mdc-keys: [traceId, spanId, correlationId]
  diagnostics:
    maximum-cause-depth: 32
    maximum-suppressed-exceptions: 8
    maximum-stack-frames: 120
    maximum-application-frames: 8
    maximum-message-length: 2000
  privacy:
    redact-values: []
  deduplication:
    enabled: true
    ttl: 10m
    maximum-entries: 512
  console:
    enabled: true
    style: box
    maximum-width: 100
    show-application-trace: true
    show-technical-details: true
    show-cause-chain: false
    debug-logging: false
  export:
    enabled: false
    directory: .stackmate/incidents
    maximum-incidents: 100
  safety:
    allow-in-production: false
    production-profiles: [prod, production]
```

## Privacy contract

Mandatory built-in secret and PII sanitization cannot be disabled through configuration.
`stackmate.privacy.redact-values` adds exact literal values after validation; entries are quoted before matching, so
regular-expression syntax has no special meaning. This property is suitable for known bounded values, not for
defining a general regex policy.

There are no public properties for request body, response body, query-value, cookie, authorization, or arbitrary
header capture. Adding such property names causes strict binding to fail instead of silently enabling collection.

## Production guard

Active profile matching is case-insensitive. If any active profile matches `stackmate.safety.production-profiles`
and `allow-in-production` is `false`, analysis and StackMate incident output are blocked. Changing the profile-name
list can weaken this convenience guard; it does not prove whether an environment is production.

## Console and deduplication

`show-technical-details` controls deterministic explanation and request summary fields. `show-application-trace`
and `show-cause-chain` control their corresponding bounded sections. `debug-logging` reports safe internal failure
classes and must not expose raw incident values.

Deduplication uses a process-local bounded in-memory store. Disabling it can increase log and analysis work during an
exception storm. It is not a distributed rate limiter.

## Export

Export writes sanitized `incident.json`, `diagnosis.md`, `stacktrace.txt`, and `agent-prompt.md` files under a
fingerprint-derived directory. The exporter rejects symbolic-link paths and requires atomic publication. Retention
deletes only complete recognized StackMate bundles; do not use a shared directory.
