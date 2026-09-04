# Troubleshooting

## No StackMate diagnosis appears

Check the following:

1. `stackmate.enabled` is not `false`.
2. The application is Servlet-based Spring MVC, not WebFlux.
3. `stackmate.capture.mvc-unhandled` is not `false`.
4. The exception escaped the servlet filter chain. Resolved `@ExceptionHandler` and Spring MVC resolver flows are
   intentionally invisible.
5. No active profile matches `stackmate.safety.production-profiles` while
   `stackmate.safety.allow-in-production=false`.
6. `stackmate.console.enabled` is not `false` and the application's logging configuration does not suppress the
   StackMate logger.

StackMate does not intercept arbitrary log calls, startup failures, or every exception handled by Spring.

## The application fails during configuration binding

The 25 public `stackmate.*` properties are strict. Remove old, misspelled, or research-only settings and compare the
configuration with [configuration.md](configuration.md). In particular, this release has no `mode`, `language`,
`inference`, `model`, request-body, query-value, header-capture, custom-regex, or ANSI property.

`stackmate.privacy.redact-values` accepts literal values only. Each value must contain 3 through 256 characters,
with at most 32 entries.

## Production-profile warning appears

`prod` and `production` are blocked by default. Prefer evaluating the alpha outside production. If an explicit risk
review authorizes production-profile analysis, set `stackmate.safety.allow-in-production=true`. The switch is an
override, not a production-readiness guarantee.

## HTTP behavior appears to change

The capture filter must rethrow the original exception and must not write the response. Compare behavior after
setting `stackmate.enabled=false`. If the difference is reproducible, prepare a minimal synthetic application and
report it through [../SUPPORT.md](../SUPPORT.md). Do not attach production data.

## The application's raw log still contains a secret

StackMate sanitizes StackMate-controlled output and exports only. It deliberately does not replace the host
application's raw exception logging. Remove the sensitive value at its source, adjust application logging, and
rotate or revoke the exposed credential. Do not rely on StackMate as a general log sanitizer.

## A StackMate line is truncated or a section is missing

Review the `diagnostics.maximum-*` bounds and console switches. `show-technical-details`,
`show-application-trace`, and `show-cause-chain` independently control optional sections. A real application frame
appears only when frame selection finds one; configure `stackmate.application-packages` with your package prefixes.

## Repeated incidents show only a duplicate line

This is the default bounded deduplication behavior. Equivalent fingerprints are tracked for ten minutes. Adjust
`stackmate.deduplication.ttl` or `maximum-entries` only for a measured need. Disabling deduplication can increase
analysis and log cost during an exception storm.

## Export did not create a bundle

Export is disabled by default. Confirm `stackmate.export.enabled=true`, a writable dedicated directory, and a
filesystem that supports atomic moves. The exporter refuses symbolic-link components, an unsafe existing target,
or retention deletion when a recognized incident directory contains unknown or linked entries.

With `stackmate.console.debug-logging=true`, StackMate may log a bounded internal failure class without raw path or
incident values. Do not point retention at a shared directory.

## The wrapper says `JAVA_HOME` is invalid

This checkout requires Java 21 and Maven 3.9.x. Point `JAVA_HOME` at the JDK directory, not its `bin` directory:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
```

```bash
export JAVA_HOME=/path/to/jdk-21
"$JAVA_HOME/bin/java" -version
./mvnw -version
```

Only Windows 11 with Amazon Corretto 21.0.12.1 is locally verified for this RC. A failure elsewhere is not evidence
of claimed platform support; see [compatibility.md](compatibility.md).

## Collecting safe report information

Include the StackMate version, Java vendor/version, Spring Boot version, operating system, relevant non-secret
`stackmate.*` settings, exception class names, and a minimal synthetic reproduction. Remove credentials, tokens,
PII, proprietary paths, customer data, and unredacted incident content. Follow [../SECURITY.md](../SECURITY.md) for
security-sensitive reports.
