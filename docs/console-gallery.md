# Console gallery

These are sanitized excerpts produced on 2026-08-01 by the current renderer and packaged sample on Windows with
Amazon Corretto 21.0.12. Box width was 100 and ANSI was disabled. Timestamps and PID noise are omitted; incident IDs
remain because they contain no incident data. The gallery deliberately includes capture-boundary and fallback output,
not only successful diagnoses.

## English unique constraint

Packaged sample, `GET /demo/database/duplicate` (HTTP 500 unchanged):

```text
| StackMate Quick Diagnosis
| Incident: STM-C46673C7
| Category: DATABASE_CONSTRAINT
| Evidence: HIGH
| Summary: A database unique constraint was violated
| Root: java.sql.SQLException: duplicate key value violates unique constraint
| "uk_sample_reference"
| First app location: SampleDatabaseException#create(SampleDatabaseException.java:47)
| Source: Deterministic rules
| Runtime: Java 21.0.12 | Spring Boot 4.1.0 | Network disabled
```

## Turkish unique constraint

Packaged sample with profile `turkish`, same HTTP behavior:

```text
| StackMate Hızlı Tanı
| Olay: STM-C46673C7
| Kategori: VERİTABANI KISITI
| Kanıt gücü: YÜKSEK
| Özet: Benzersizlik kısıtı ihlal edildi
| Kök hata: java.sql.SQLException: duplicate key value violates unique constraint
| "uk_sample_reference"
| İlk uygulama konumu: SampleDatabaseException#create(SampleDatabaseException.java:47)
| Kaynak: Deterministik kurallar
| Çalışma ortamı: Java 21.0.12 | Spring Boot 4.1.0 | Ağ devre dışı
```

## English configuration binding

Actual deterministic renderer output for the controlled `ConfigurationPropertiesBindException` fixture:

```text
| StackMate Quick Diagnosis
| Incident: STM-A727D174
| Category: SPRING_CONFIGURATION
| Evidence: HIGH
| Summary: Spring configuration properties could not be bound
| Root: org.springframework.boot.context.properties.bind.ConfigurationPropertiesBindException:
| order configuration could not be bound
| First app location: OrderService#execute(OrderService.java:42)
| First checks:
|   - Check property names, value types, active profiles, and configuration sources.
| Source: Deterministic rules
```

## Turkish validation

Actual deterministic renderer output for the controlled validation fixture:

```text
| StackMate Hızlı Tanı
| Olay: STM-EE38E1B2
| Kategori: DOĞRULAMA
| Kanıt gücü: YÜKSEK
| Özet: İstek doğrulaması başarısız oldu
| Kök hata: org.springframework.web.bind.MethodArgumentNotValidException: order request was invalid
| İlk uygulama konumu: OrderService#execute(OrderService.java:42)
| İlk kontroller:
|   - Reddedilen alanları ve bu alanlardaki doğrulama kısıtlarını inceleyin.
| Kaynak: Deterministik kurallar
```

The packaged `POST /demo/validation` returned the normal Spring HTTP 400 body. Spring's
`DefaultHandlerExceptionResolver` resolved the exception and the current escaping-servlet-chain capture boundary
printed no StackMate card. The renderer supports this incident when supplied through the API, but the current MVC
adapter does not claim to observe it.

## Malformed JSON capture boundary

Packaged request and real Spring log:

```text
HTTP 400
{"status":400,"error":"Bad Request","path":"/demo/malformed-json"}

Resolved [org.springframework.http.converter.HttpMessageNotReadableException:
JSON parse error: Unexpected end-of-input within/between Object entries]
```

No StackMate card was printed. This is expected because the exception was resolved inside Spring MVC rather than
escaping the servlet chain.

## Missing column

Packaged `GET /demo/database/missing-column`, HTTP 500 unchanged:

```text
| StackMate Quick Diagnosis
| Category: DATABASE_SCHEMA
| Summary: A referenced database column was not found
| Root: java.sql.SQLException: column "missing_sample_column" does not exist
| First app location: SampleDatabaseException#create(SampleDatabaseException.java:47)
| Request: GET /demo/database/missing-column
| Source: Deterministic rules
```

## Unknown exception restraint

Packaged `GET /demo/unknown`, HTTP 500 unchanged:

```text
| StackMate Quick Diagnosis
| Category: UNKNOWN
| Evidence: UNKNOWN
| Summary: No supported deterministic pattern matched
| Root: DemoFailureController$UnknownSampleException:
| Controlled exception with no StackMate-specific rule
| First app location: DemoFailureController#unknown(DemoFailureController.java:106)
| Note: Insufficient context for a conclusive diagnosis.
| Source: Deterministic rules
```

No local inference was submitted for this insufficient diagnosis.

## Duplicate occurrence

```text
| StackMate duplicate STM-2CE4E3E5 (count 2)
```

The second matching incident did not submit another inference job within the TTL.

## Redaction

The packaged sample used controlled fake credentials and PII. The card contained:

```text
| Root: java.lang.IllegalStateException: Controlled fake data: password=[REDACTED],
| Authorization: [REDACTED] [REDACTED],
| jdbc:postgresql://demo.invalid:5432/sample?user=[REDACTED]&password=[REDACTED],
| email=[REDACTED], tcKimlikNo=[REDACTED]
```

The original password, bearer token, database password, email address, and identity-like value were absent from the
StackMate card.

## Invalid model fallback

Packaged sample with `invalid-model` and trigger policy `always`:

```text
| StackMate Quick Diagnosis
...
[StackMate] Local inference is unavailable; configure a local JVM model.
Deterministic diagnosis remains active.
```

The request returned its original HTTP 500. No local-explanation block was printed.

## Invalid output fallback

The packaged fake child returned a malformed protocol frame:

```text
| StackMate Quick Diagnosis
...
[debug] StackMate local inference skipped safely: ENGINE_ERROR: malformed protocol frame
```

The debug reason is bounded and sanitized, appears only with debug logging enabled, and the raw child output is not
printed.

## Model timeout fallback

The packaged fake child deliberately exceeded the configured timeout:

```text
| StackMate Quick Diagnosis
...
[debug] StackMate local inference skipped safely: TIMED_OUT
```

The HTTP request completed before inference; no local-explanation block was printed.

## Hybrid enhanced explanation

This is actual validated output from the deterministic packaged fake worker. It exercises the same process framing,
schema, validation, executor, and renderer as a real engine without requiring model weights:

```text
| StackMate Local Explanation
| Summary: This failure is explained only by the supplied evidence.
| Explanation: Only deterministic candidates were prioritized.
| Prioritized causes:
|   - An operation ran in an invalid state. An exact IllegalStateException was captured.
| Prioritized checks:
|   - Check lifecycle and state transitions before the first application frame.
| Evidence used: 1
| Duration: 529 ms
| Source: Experimental local JVM model StackMate packaged E2E fake child 1
| StackMate made no external network call
```

This fake-worker example validates integration behavior, not real-model quality. Real-model readiness is `NOT_VIABLE`
for the Phase 2 candidates; see `docs/model-selection.md`.
