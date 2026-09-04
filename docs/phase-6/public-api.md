# Phase 6 public API

The public-alpha API is the public bytecode surface of the three public JARs:
`stackmate-core`, `stackmate-spring-boot-autoconfigure` and
`stackmate-spring-boot-starter`. The parent POM is publishable but contributes no
Java types.

## Snapshot inventory

| Classification | Types |
|---|---:|
| Stable API | 56 |
| Extension SPI | 6 |
| Configuration API | 11 |
| Total | 73 |

The module split is 50 types in core and 23 in deterministic
auto-configuration. The dependency-only starter contributes no public class.

The six supported extension interfaces are:

- `DiagnosticAnalyzer`
- `ServletExceptionCaptureHandler`
- `DeduplicationStore`
- `IncidentExporter`
- `ConsoleRenderer`
- `Sanitizer`

The complete type-by-type classification is machine-readable in
`release/public-api-inventory.json`; the corresponding `javap -public
-constants -s` signature is `release/public-api-baseline.txt`.

## Compatibility policy

For the v0.1.0 public alpha, removing a listed public type/member, changing a
descriptor, narrowing visibility, or moving a type between artifacts is an API
change that requires explicit review and baseline regeneration. Additive API
changes also require classification review. Internal implementation details and
nonpublishable inference/research modules are outside this compatibility set.

The inventory was regenerated from the freshly built `0.1.0-rc.1` JARs and
matched the reviewed signature: 73 types and SHA-256
`41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.
