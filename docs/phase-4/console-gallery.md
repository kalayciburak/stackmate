# Phase 4 console gallery

Date: 2026-08-02

These excerpts are sanitized outputs captured during the Phase 4 baseline and
packaged-process checks. Timestamps are normalized. StackMate-generated runtime text
is English; `README.tr.md` is documentation, not a Turkish console mode.

## Deterministic database diagnosis

```text
[timestamp] ERROR [stackmate-sample] STACKMATE :
+--------------------------------------------------------------------------------------------------+
| StackMate Quick Diagnosis                                                                        |
| Category: DATABASE_CONSTRAINT                                                                    |
| Evidence: HIGH                                                                                   |
| Summary: A database unique constraint was violated                                               |
| Root: java.sql.SQLException: duplicate key value violates unique constraint                      |
| "uk_sample_reference"                                                                            |
| First app location: SampleDatabaseException#create(SampleDatabaseException.java:47)              |
| Request: GET /demo/database/duplicate                                                            |
| Source: Deterministic rules                                                                      |
| Runtime: Java 21.0.12 | Spring Boot 4.1.0 | Network disabled                                     |
+--------------------------------------------------------------------------------------------------+
```

The application still returned its normal HTTP 500 response; StackMate did not
write the status or body.

## Duplicate occurrence

```text
[timestamp] ERROR [stackmate-sample] STACKMATE :
+--------------------------------------------------------------------------------------------------+
| StackMate duplicate STM-2CE4E3E5 (count 2)                                                       |
+--------------------------------------------------------------------------------------------------+
```

## Redaction

```text
[timestamp] ERROR [stackmate-sample] STACKMATE :
+--------------------------------------------------------------------------------------------------+
| StackMate Quick Diagnosis                                                                        |
| Category: GENERAL_JAVA                                                                           |
| Evidence: HIGH                                                                                   |
| Summary: An operation ran in an invalid state                                                    |
| Root: java.lang.IllegalStateException: Controlled fake data: password=[REDACTED],                |
| Authorization: [REDACTED] [REDACTED],                                                            |
| jdbc:postgresql://demo.invalid:5432/sample?user=[REDACTED]&password=[REDACTED],                  |
| email=[REDACTED], tcKimlikNo=[REDACTED]                                                          |
| First app location: DemoFailureController#redaction(DemoFailureController.java:90)               |
| Source: Deterministic rules                                                                      |
+--------------------------------------------------------------------------------------------------+
```

## Disabled mode

```text
HTTP status: 500
HTTP body: {"timestamp":"[timestamp]","status":500,"error":"Internal Server Error","path":"/demo/null-pointer"}
Normal application exception: java.lang.NullPointerException: Controlled null pointer sample
StackMate diagnostic blocks: 0
```

## Validated fake-worker explanation

The packaged fake-worker test exercised the real asynchronous protocol/lifecycle
without a model. It preserved HTTP 500, printed the quick diagnosis, and then printed
the validated local-explanation block. The fake response selected only supplied IDs.
This is integration evidence, not model-quality evidence.

## Rejected real-model output

No eligible Phase 4 model passed the tournament, so there is no production local
explanation to present as a product example. Examples containing an unknown ID,
fabricated source, invalid JSON, oversized output, invalid UTF-8, non-English text,
or unsafe prose were rejected and never rendered as trusted advice. Deterministic
output remained available in every fallback path.
