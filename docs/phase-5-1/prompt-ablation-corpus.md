# Phase 5.1 prompt-ablation corpus

Frozen: 2026-08-02

## Identity

| Field | Value |
|---|---|
| Corpus ID | `stackmate-phase-5-1-prompt-ablation` |
| Split manifest | `stackmate-benchmark/src/main/resources/prompt-ablation/split-manifest.json` |
| Split manifest SHA-256 | `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07` |
| V2 source SHA-256 | `292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294` |
| Blind source SHA-256 | `eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a` |
| Ground-truth SHA-256 | `0a5c9c748e187fdd2c52d506f592d30b04606d59242e3660b7a15f1f7d829d6a` |
| Cases | 100 unique IDs |

The ground-truth digest is SHA-256 over the exact V2 manifest bytes followed by the
exact blind manifest bytes. It does not normalize or reinterpret either source.

## Split policy

The V2 corpus already had 60 development and 20 holdout cases. Before prompt
variants were created, the first 40 V2 development cases in manifest order were
assigned to prompt development and the remaining 20 to selection validation. The
locked final is exactly the pre-existing 20 V2 holdout cases plus all 20 frozen
Phase 5 blind cases.

| Split | Cases | Visibility during prompt editing |
|---|---:|---|
| Prompt development | 40 | visible |
| Selection validation | 20 | visible only for bounded P0-P3 comparison |
| Locked final | 40 | inaccessible to development mode; run only after prompt lock |

No case, expected selection, fact, or ground-truth disposition was changed.

## Prompt development IDs

```text
JAVA-NPE-001, JAVA-ARG-002, JAVA-STATE-003, JAVA-INDEX-005,
JAVA-CAST-006, JAVA-COMPLETION-007, JAVA-HOSTILE-009,
JAVA-UNKNOWN-010, SPRING-MISSING-BEAN-001, SPRING-CREATION-003,
SPRING-CIRCULAR-004, SPRING-PLACEHOLDER-005, SPRING-PROPERTY-007,
SPRING-FACTORY-008, SPRING-UNSATISFIED-009, SPRING-CONDITION-011,
SPRING-UNKNOWN-012, MVC-MALFORMED-001, MVC-TYPE-MISMATCH-003,
MVC-INVALID-ENUM-004, MVC-MEDIA-005, MVC-VALIDATION-007,
MVC-MISSING-BODY-008, MVC-HANDLER-VALIDATION-009, MVC-SERIALIZE-011,
MVC-UNKNOWN-012, DB-H2-UNIQUE-001, DB-H2-FK-003, DB-H2-TABLE-004,
DB-H2-COLUMN-005, DB-H2-CONVERSION-007, DB-H2-LOCK-008,
DB-H2-TRANSACTION-009, DB-PG-UNIQUE-011, DB-PG-FK-012,
DB-PG-TABLE-013, DB-PG-DEADLOCK-015, DB-GENERIC-CONNECT-016,
DB-POOL-TIMEOUT-017, DB-LAZY-019
```

## Selection-validation IDs

```text
DB-UNKNOWN-020, SECURITY-DENIED-001, SECURITY-SIGNATURE-003,
SECURITY-CERT-004, SECURITY-SPRING-DENIED-005, SECURITY-JWT-007,
SECURITY-UNKNOWN-008, NET-SOCKET-REFUSED-001, NET-READ-TIMEOUT-003,
NET-TLS-PLAIN-004, NET-UNKNOWN-ADDRESS-005, NET-MALFORMED-URL-007,
NET-RESET-008, CONC-COMPLETION-001, CONC-TIMEOUT-003,
CONC-REJECTED-004, CONC-CANCEL-005, ADV-INJECTION-002,
ADV-TERMINAL-003, ADV-UNICODE-004
```

## Locked-final IDs

```text
JAVA-NUM-004, JAVA-EXECUTION-008, SPRING-NONUNIQUE-002,
SPRING-CONVERSION-006, SPRING-BIND-010, MVC-MISSING-PARAM-002,
MVC-METHOD-006, MVC-CONSTRAINT-010, DB-H2-NOTNULL-002,
DB-H2-SYNTAX-006, DB-H2-DUPLICATE-010, DB-PG-COLUMN-014,
DB-OPTIMISTIC-018, SECURITY-GENERIC-002, SECURITY-CREDENTIALS-006,
NET-HTTP-REFUSED-002, NET-UNRESOLVED-006, CONC-EXECUTION-002,
ADV-SECRET-001, ADV-VOLATILE-005,
B5-COMPLETION-NUMERIC-DURATION, B5-CONFIGURATION-CONVERSION-RETRY,
B5-MISSING-PROPERTY-ENDPOINT, B5-BEAN-AMBIGUITY-CODEC,
B5-CIRCULAR-DEPENDENCY-ROUTER, B5-VALIDATION-NESTED-ADDRESS,
B5-MALFORMED-JSON-ARRAY, B5-UNKNOWN-ENUM-SHIPMENT,
B5-DB-UNIQUE-EXTERNAL-KEY, B5-DB-FOREIGN-KEY-REGION,
B5-DB-MISSING-COLUMN-WRAPPED, B5-CONNECTION-LOOPBACK-REFUSED,
B5-DNS-INVALID-HOST, B5-TLS-PLAIN-PEER,
B5-TIMEOUT-READ-DEADLINE, B5-UNKNOWN-RUNTIME-SIGNAL,
B5-AMBIGUOUS-ASYNC-WRAPPER, B5-INSUFFICIENT-CAPTURE-METADATA,
B5-PROMPT-DELIMITER-INJECTION, B5-SECRET-FABRICATED-SOURCE-TRAP
```

## Qualification flags

The manifest records three immutable case-ID sets:

- `abstentionRequiredCaseIds`: the 11 existing `RESTRAIN` cases;
- `unknownOrInsufficientCaseIds`: the same 11 cases under the product wording;
- `unsupportedClaimTrapCaseIds`: 23 predeclared cases emphasizing unsupported
  alternatives, generic fallback, fabricated sources, secrets, or injection.

Flags are reporting metadata. They do not expose expected cause/check IDs to the
prompt and are not case-specific validator rules.

## Contamination control

- P3 examples use abstract scenarios and contain no case ID, exception message,
  source identifier, or exact candidate text.
- The locked set contains categories beyond the three known Phase 5 failures,
  including validation, malformed JSON, DNS, TLS, timeout, configuration, security,
  wrapper, secret, volatile-value, and delimiter attacks.
- Locked results cannot authorize another prompt edit. A technical harness failure
  may be fixed without changing prompt bytes and must be documented.
