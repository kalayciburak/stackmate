# StackMate

> Deterministic, local diagnostics for Spring MVC failures.

StackMate `0.1.0-rc.3` is a public alpha for Java 21 Spring Boot applications. It observes exceptions that escape
the Spring MVC servlet chain, creates a bounded and sanitized snapshot, derives evidence-backed diagnostics,
deduplicates repeated incidents, and writes a concise English diagnosis to the application log. Optional export
writes the same sanitized deterministic evidence to the local filesystem.

This release contains no LLM, inference runtime, model, downloader, cloud call, telemetry, or upload path. Historical
local-model experiments are isolated in [docs/research/local-llm-findings.md](docs/research/local-llm-findings.md)
and are not product capabilities.

Turkish documentation: [README.tr.md](README.tr.md)

## Alpha status

The public API and behavior may change before a stable release. RC2 passed local verification and exact-commit hosted
qualification on Ubuntu x64, Windows x64, and macOS arm64. Each hosted platform reported 549 Surefire plus 6 Failsafe
tests including the external consumer, with zero failures, errors, or skips. RC1 remains a frozen local-only
checkpoint and is superseded by RC2's CI and Central-metadata corrections. No artifact has been published to Maven
Central, no remote release has been created, no RC tag has been pushed, and no GPG signing has been performed.

## What it does

- Captures unhandled exceptions that escape the Spring MVC servlet request chain without swallowing or replacing them.
- Traverses bounded throwable graphs and selects application-owned frames.
- Produces deterministic categories, evidence strength, causes, and next checks.
- Sanitizes StackMate-controlled console and export content with mandatory secret and PII rules.
- Generates a stable SHA-256 fingerprint and tracks duplicates in a bounded in-memory TTL store.
- Renders bounded `box` or `compact` console output.
- Optionally creates a four-file sanitized incident bundle on local disk.

## What it does not do

StackMate does not change HTTP status or response bodies, replace application logging, inspect source code, capture
request or response bodies, capture query values or header values, upload incidents, emit telemetry, call cloud
services, download models, run an LLM, edit code, or start a coding agent. Exceptions resolved inside Spring MVC,
including many `@ExceptionHandler` flows, do not cross the current capture boundary.

Spring Boot 3, WebFlux, GraalVM native image, production deployment, containers, and operating-system versions beyond
the documented hosted runners are not verified for this alpha. See [compatibility.md](docs/compatibility.md).

## Requirements

- Java 21
- Spring Boot 4.1.0
- Spring Framework 7.0.8
- Servlet-based Spring MVC
- Maven 3.9.x when building this checkout

The local environment is Windows 11 with Amazon Corretto 21.0.12.1. Hosted qualification used Temurin 21.0.12.1 on
Ubuntu 24.04 x64, Windows Server 2025 x64, and macOS 26 arm64. Containers remain unverified.

## Add the starter

The public Maven coordinates are:

```xml
<dependency>
  <groupId>io.github.kalayciburak</groupId>
  <artifactId>stackmate-spring-boot-starter</artifactId>
  <version>0.1.0-rc.3</version>
</dependency>
```

The RC is not on Maven Central. Until publication, this dependency resolves only after the artifacts have been
installed or supplied locally.

For a local evaluation from this checkout, install only the public artifact chain first:

```powershell
$env:JAVA_HOME = "<path-to-jdk-21>"
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -Ppublic-release `
  -pl stackmate-core,stackmate-spring-boot-autoconfigure,stackmate-spring-boot-starter `
  -am clean install
```

Then add the dependency above to a separate Spring MVC application. The repository also contains the standalone
`release/consumer` project used to verify the installed coordinates; it is not a reactor module.

The starter is enabled by default. No configuration is required for a servlet Spring MVC application. To make the
starter inert:

```yaml
stackmate:
  enabled: false
```

To help identify application frames:

```yaml
stackmate:
  application-packages:
    - com.example.myapp
```

## Production-profile guard

If an active Spring profile matches `prod` or `production`, StackMate blocks analysis by default and logs a bounded
warning. Explicit opt-in requires:

```yaml
stackmate:
  safety:
    allow-in-production: true
```

This guard is a convenience check, not a security boundary or a production-readiness claim. Review privacy,
filesystem, logging, and capacity policy before opting in.

## Representative output

The values below illustrate fields emitted by the deterministic renderer; they are representative, not a transcript
of a claimed RC test run:

```text
+--------------------------------------------------------------------------------------------------+
| StackMate Quick Diagnosis                                                                        |
| Incident: STM-7A3C1D2E                                                                           |
| Category: DATABASE_CONSTRAINT                                                                    |
| Evidence: HIGH                                                                                   |
| Summary: A database unique constraint was violated                                               |
| Root: java.sql.SQLException: duplicate key value violates unique constraint                      |
| First app location: OrderService#create(OrderService.java:42)                                    |
| Details: The database rejected a value that must be unique.                                      |
| Request: POST /orders                                                                            |
| First checks:                                                                                    |
|   - Check whether the value already exists before retrying.                                      |
| Application trace:                                                                              |
|   OrderService#create(OrderService.java:42)                                                      |
| Source: Deterministic rules                                                                      |
| Runtime: Java 21.0.12.1 | Spring Boot 4.1.0 | Network disabled                                  |
+--------------------------------------------------------------------------------------------------+
```

Incident IDs and diagnosis text depend on captured evidence. The host application's normal exception handling and
raw logs remain unchanged. More representative layouts are in [console-gallery.md](docs/console-gallery.md).

## Configuration

StackMate exposes exactly 25 `stackmate.*` properties in this RC. Unknown fields are rejected. The only user
redaction extension is `stackmate.privacy.redact-values`: it accepts bounded literal values, not regular
expressions. There are no public body, query-value, or header-capture switches.

See [configuration.md](docs/configuration.md) for every property, default, and bound.

## Local export

Export is disabled by default. Enabling it writes one sanitized bundle per first-seen fingerprint:

```yaml
stackmate:
  export:
    enabled: true
    directory: .stackmate/incidents
```

```text
.stackmate/incidents/STM-XXXXXXXX/
|-- incident.json
|-- diagnosis.md
|-- stacktrace.txt
`-- agent-prompt.md
```

The export is local and optional. `agent-prompt.md` is inert context; StackMate does not invoke an agent. Review all
files before sharing them.

## Architecture and limits

The public artifact chain is:

```text
stackmate-core
  <- stackmate-spring-boot-autoconfigure
       <- stackmate-spring-boot-starter
```

`stackmate-core` is JDK-only. The auto-configuration owns the Servlet/Spring MVC adapter, typed configuration,
console integration, and optional local exporter. Research, sample, benchmark, local-inference, and E2E modules in
this repository are not part of the public release.

Known limits:

- only exceptions escaping the servlet filter chain are visible;
- startup failures and logged-only failures are not captured;
- mandatory sanitization reduces risk but cannot guarantee arbitrary text is safe;
- the application's unchanged raw logs can still disclose data;
- duplicate state is local and in-memory;
- export retention applies only to recognized StackMate bundles.

Read [architecture.md](docs/architecture.md), [privacy-and-threat-model.md](docs/privacy-and-threat-model.md), and
[troubleshooting.md](docs/troubleshooting.md) before wider evaluation.

## Project status and participation

- [Changelog](CHANGELOG.md)
- [Compatibility](docs/compatibility.md)
- [Support](SUPPORT.md)
- [Security policy](SECURITY.md)
- [Contributing](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Release process](docs/release-process.md)

## License

StackMate is licensed under the [Apache License 2.0](LICENSE). See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the public runtime dependency notice.
