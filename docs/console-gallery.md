# Console gallery

## Observed external-consumer RC output

The following sanitized StackMate block was captured from the external consumer's `GET /failure` scenario. The host
response remained HTTP 500. This is `OBSERVED` RC evidence, not representative hand-written output:

```text
StackMate Quick Diagnosis
Incident: STM-45DE77DA
Category: GENERAL_JAVA
Evidence: HIGH
Summary: An operation ran in an invalid state
Root: java.lang.IllegalStateException: consumer-unhandled password=[REDACTED]
First app location: ConsumerApplication#failure(ConsumerApplication.java:31)
Details: An exact IllegalStateException was captured.
Request: GET /failure
First checks:
  - Check lifecycle and state transitions before the first application frame.
Cause chain:
  - jakarta.servlet.ServletException: Request processing failed: java.lang.IllegalStateException:
consumer-unhandled password=[REDACTED]
  - java.lang.IllegalStateException: consumer-unhandled password=[REDACTED]
Application trace:
  ConsumerApplication#failure(ConsumerApplication.java:31)
Source: Deterministic rules
Runtime: Java 21.0.12.1 | Spring Boot 4.1.0 | Network disabled
```

This StackMate-rendered block contained zero occurrences of the raw synthetic secret. StackMate then rethrew the
original exception to preserve application behavior, and Tomcat independently logged that original exception with
the synthetic raw secret. StackMate sanitizes only StackMate-controlled output; it is not a sanitizer or suppression
control for framework or application logs.

The remaining examples are **representative output**, assembled from deterministic renderer fields. They are not
claimed transcripts of the pending final full-reactor run. Text, wrapping, incident IDs, stack frames, and optional
sections vary with evidence and configuration. Runtime output is English.

## Box layout

```text
+----------------------------------------------------------------------------------+
| StackMate Quick Diagnosis                                                        |
| Incident: STM-7A3C1D2E                                                           |
| Category: DATABASE_CONSTRAINT                                                    |
| Evidence: HIGH                                                                   |
| Summary: A database unique constraint was violated                               |
| Root: java.sql.SQLException: duplicate key value violates unique constraint      |
| First app location: OrderService#create(OrderService.java:42)                    |
| Details: The database rejected a value that must be unique.                      |
| Request: POST /orders                                                            |
| First checks:                                                                    |
|   - Check whether the value already exists before retrying.                      |
| Application trace:                                                              |
|   OrderService#create(OrderService.java:42)                                      |
| Source: Deterministic rules                                                      |
| Runtime: Java 21.0.12.1 | Spring Boot 4.1.0 | Network disabled                  |
+----------------------------------------------------------------------------------+
```

`box` is the default. Borders use plain ASCII; captured text is not allowed to inject terminal control sequences.

## Compact layout

```text
StackMate Quick Diagnosis
Incident: STM-7A3C1D2E
Category: DATABASE_CONSTRAINT
Evidence: HIGH
Summary: A database unique constraint was violated
Root: java.sql.SQLException: duplicate key value violates unique constraint
First checks:
  - Check whether the value already exists before retrying.
Source: Deterministic rules
Runtime: Java 21.0.12.1 | Spring Boot 4.1.0 | Network disabled
```

Select it with `stackmate.console.style=compact`.

## Unknown diagnosis

```text
StackMate Quick Diagnosis
Incident: STM-4D5E6F70
Category: UNKNOWN
Evidence: UNKNOWN
Summary: No supported deterministic pattern matched
Root: com.example.UnknownFailure: Controlled synthetic message
Checks: No evidence-backed next check is available.
Source: Deterministic rules
Runtime: Java 21.0.12.1 | Spring Boot 4.1.0 | Network disabled
Note: Insufficient context for a conclusive diagnosis.
```

The fallback note makes uncertainty explicit; StackMate does not use an LLM to fill the gap.

## Duplicate occurrence

```text
StackMate duplicate STM-7A3C1D2E (count 2)
```

This line represents a matching fingerprint seen within the configured process-local TTL window.

## Redaction

```text
Root: java.lang.IllegalStateException: password=[REDACTED], email=[REDACTED]
```

This illustrates StackMate-controlled output after mandatory sanitization. The host application's own raw exception
log is separate and may still contain the original message.

## Severe JVM failure

```text
StackMate severe JVM failure STM-1A2B3C4D: java.lang.OutOfMemoryError
```

Severe JVM failures receive only a minimal best-effort line. They do not trigger an additional inference or network
path; neither exists in the public release.

## Visibility boundary

If Spring MVC resolves an exception internally, no StackMate card may appear. For example, validation or malformed
JSON errors commonly become normal Spring 400 responses before they can escape the servlet filter chain. This is an
expected capture boundary, not evidence that all MVC failures are observable.
