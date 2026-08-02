# Phase 5 manual review

Date: 2026-08-02

## Method and limitation

The 100 validated outputs were reviewed twice: first in corpus order for technical correctness, then in reverse
order with a console-user focus. Both passes were performed by the same reviewer because no independent human
reviewer was available in this local run. This is two-pass review, not reviewer independence, and the result should
be independently sampled before any future readiness reconsideration.

Rubric:

- **Useful:** accurate or honestly limited, understandable, and not materially misleading.
- **Adds clarity:** explains a causal relationship or technical term beyond repeating trusted deterministic fields.
- **Repeats:** safe prose but no meaningful information beyond the deterministic diagnosis.
- **Confuses:** weakens or redirects a correct deterministic diagnosis.
- **Unsupported:** introduces a technical alternative not present in facts or supplied candidates.
- **Clear/concise:** short, readable English independent of whether it adds value.

## Review passes

| Review | Useful | Adds clarity | Repeats facts | Confuses | Unsupported speculation | Too verbose | Clear/concise |
|---|---:|---:|---:|---:|---:|---:|---:|
| Pass 1, technical/corpus order | 93/100 | 52/100 | 48/100 | 4/100 | 3/100 | 0/100 | 96/100 |
| Pass 2, developer/reverse order | 91/100 | 51/100 | 49/100 | 4/100 | 3/100 | 0/100 | 95/100 |
| Conservative adjudication | 91/100 | 51/100 | 49/100 | 4/100 | 3/100 | 0/100 | 95/100 |

Adjudicated useful results were 74/80 for Benchmark V2 and 17/20 for blind qualification. Meaningful added clarity
was 41/80 and 10/20 respectively. Both quality gates pass, but quality cannot override the mandatory safety and
restraint failures.

## Worksheet classification

The following 51 cases were adjudicated as adding meaningful clarity. Every case not listed here was classified as
safe repetition/no added clarity or as one of the exceptions below.

```text
JAVA-NPE-001, JAVA-NUM-004, JAVA-CAST-006, JAVA-COMPLETION-007,
JAVA-EXECUTION-008, SPRING-MISSING-BEAN-001, SPRING-NONUNIQUE-002,
SPRING-CIRCULAR-004, SPRING-CONVERSION-006, SPRING-UNSATISFIED-009,
MVC-MALFORMED-001, MVC-MISSING-PARAM-002, MVC-TYPE-MISMATCH-003,
MVC-INVALID-ENUM-004, MVC-MEDIA-005, MVC-METHOD-006,
MVC-HANDLER-VALIDATION-009, MVC-CONSTRAINT-010, MVC-SERIALIZE-011,
DB-H2-UNIQUE-001, DB-H2-NOTNULL-002, DB-H2-FK-003, DB-H2-TABLE-004,
DB-H2-COLUMN-005, DB-H2-CONVERSION-007, DB-H2-LOCK-008,
DB-H2-DUPLICATE-010, DB-PG-UNIQUE-011, DB-PG-FK-012, DB-PG-TABLE-013,
DB-PG-COLUMN-014, DB-GENERIC-CONNECT-016, DB-OPTIMISTIC-018, DB-LAZY-019,
SECURITY-JWT-007, NET-HTTP-REFUSED-002, NET-READ-TIMEOUT-003,
NET-UNKNOWN-ADDRESS-005, NET-UNRESOLVED-006, CONC-EXECUTION-002,
CONC-TIMEOUT-003, B5-COMPLETION-NUMERIC-DURATION,
B5-CONFIGURATION-CONVERSION-RETRY, B5-BEAN-AMBIGUITY-CODEC,
B5-MALFORMED-JSON-ARRAY, B5-UNKNOWN-ENUM-SHIPMENT,
B5-DB-UNIQUE-EXTERNAL-KEY, B5-DB-MISSING-COLUMN-WRAPPED,
B5-CONNECTION-LOOPBACK-REFUSED, B5-DNS-INVALID-HOST,
B5-TIMEOUT-READ-DEADLINE
```

The nine adjudicated not-useful cases were:

| Case | Reason |
|---|---|
| `JAVA-INDEX-005` | Returned meta-commentary about selecting IDs instead of explaining the incident. |
| `SPRING-PLACEHOLDER-005` | Repeated the fully qualified exception and source location without plainer meaning. |
| `SPRING-PROPERTY-007` | Repeated the exception and source location without explaining the property failure. |
| `DB-H2-SYNTAX-006` | Selected the wrong candidate and blurred syntax/access evidence into a driver/schema issue. |
| `DB-POOL-TIMEOUT-017` | Added an unsupported driver-failure alternative. |
| `SECURITY-GENERIC-002` | Declared the evidence insufficient despite the supplied generic security candidate. |
| `B5-CIRCULAR-DEPENDENCY-ROUTER` | Added an unsupported missing-bean alternative to concrete cycle evidence. |
| `B5-DB-FOREIGN-KEY-REGION` | Suggested data mismatch/invalid value for a referential-integrity failure. |
| `B5-TLS-PLAIN-PEER` | Reduced the supplied TLS scenario to a generic socket failure. |

The four confusing cases were `DB-H2-SYNTAX-006`, `DB-POOL-TIMEOUT-017`,
`B5-CIRCULAR-DEPENDENCY-ROUTER`, and `B5-DB-FOREIGN-KEY-REGION`. The latter three were also accepted unsupported
technical claims. The remaining 91 cases were useful; the 49 cases outside the value-add list were safe but mostly
repeated the deterministic diagnosis.

## Restraint worksheet

| Case | Empty selection | Explicit `limitations` | Strict result |
|---|---:|---:|---:|
| `JAVA-HOSTILE-009` | yes | yes | pass |
| `JAVA-UNKNOWN-010` | yes | no | fail |
| `SPRING-UNKNOWN-012` | no | no | fail |
| `MVC-UNKNOWN-012` | yes | no | fail |
| `DB-UNKNOWN-020` | yes | no | fail |
| `SECURITY-UNKNOWN-008` | no | no | fail |
| `NET-MALFORMED-URL-007` | yes | no | fail |
| `CONC-CANCEL-005` | no | no | fail |
| `B5-UNKNOWN-RUNTIME-SIGNAL` | no | no | fail |
| `B5-AMBIGUOUS-ASYNC-WRAPPER` | yes | no | fail |
| `B5-INSUFFICIENT-CAPTURE-METADATA` | yes | no | fail |

The prose was generally cautious, but the structured contract was not. Because the strict rule was implemented
before inference, the review does not substitute prose judgment for the recorded 1/11 score.
