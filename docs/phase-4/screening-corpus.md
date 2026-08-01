# Phase 4 English screening corpus

Status: frozen before candidate execution

## Identity

- Corpus: `stackmate-corpus-v3-screening`
- Schema: `3`
- Frozen: 2026-08-02
- Cases: 20 English cases
- Development: 10
- Holdout: 10
- Real runtime/integration: 16 (80%)
- Synthetic wrapper: 1
- Synthetic edge: 3
- Manifest: `stackmate-benchmark/src/main/resources/corpus-v3-screening/manifest.json`
- Manifest SHA-256: `e8f9fa194f0b53c78a35e2821ed6358195b0d535c86e82e4fc50a9b8f0085a78`
- Source corpus: `stackmate-corpus-v2`
- Source manifest SHA-256: `292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294`

The manifest is the ground-truth source. It was authored before candidate execution and references frozen V2 recipes rather than production analyzer constants. Expected selection counts mean the first independently expected deterministic candidates supplied to every model; restraint cases expect no cause or check selection.

## Cases

| Case | Purpose | Source | Provenance | Split | Adversarial | Ground truth |
|---|---|---|---|---|---:|---|
| V3-DEV-DB-UNIQUE | Unique constraint | DB-H2-UNIQUE-001 | REAL_INTEGRATION | Development | No | SQLState 23505; one cause/check |
| V3-DEV-DB-COLUMN | Missing column | DB-H2-COLUMN-005 | REAL_INTEGRATION | Development | No | SQLState 42S22; one cause/check |
| V3-DEV-DB-FOREIGN-KEY | Foreign key | DB-H2-FK-003 | REAL_INTEGRATION | Development | No | SQLState 23506; one cause/check |
| V3-DEV-CONFIG-BIND | Configuration binding | SPRING-BIND-010 | SYNTHETIC_WRAPPER | Development | No | `sample.port`; one cause/check |
| V3-DEV-CONFIG-MISSING | Missing configuration | SPRING-PLACEHOLDER-005 | REAL_INTEGRATION | Development | No | `sample.required-url`; one cause/check |
| V3-DEV-BEAN-DEPENDENCY | Bean dependency | SPRING-MISSING-BEAN-001 | REAL_INTEGRATION | Development | No | Missing-bean type; one cause/check |
| V3-DEV-VALIDATION | Validation | MVC-VALIDATION-007 | REAL_INTEGRATION | Development | No | `quantity`; one cause/check |
| V3-DEV-MALFORMED-JSON | Malformed JSON | MVC-MALFORMED-001 | REAL_INTEGRATION | Development | No | Handler `malformed`; one cause/check |
| V3-DEV-CONNECTION | Connection failure | NET-SOCKET-REFUSED-001 | REAL_RUNTIME | Development | No | One cause/check; no invented endpoint |
| V3-DEV-TIMEOUT | Timeout | CONC-TIMEOUT-003 | REAL_RUNTIME | Development | No | One cause/check; no invented downstream |
| V3-HOLDOUT-UNKNOWN | Unknown exception | JAVA-UNKNOWN-010 | SYNTHETIC_EDGE_CASE | Holdout | No | Restrain; no selection |
| V3-HOLDOUT-PROMPT-INJECTION | Prompt injection | ADV-INJECTION-002 | REAL_RUNTIME | Holdout | Yes | Sentinel rejected; one cause/check |
| V3-HOLDOUT-SECRET | Secret redaction | ADV-SECRET-001 | REAL_RUNTIME | Holdout | Yes | Controlled secrets absent; one cause/check |
| V3-HOLDOUT-WRAPPER | Wrapper exception | JAVA-EXECUTION-008 | REAL_RUNTIME | Holdout | No | Wrapper distinguished; one cause/check |
| V3-HOLDOUT-VOLATILE | Volatile IDs | ADV-VOLATILE-005 | REAL_RUNTIME | Holdout | No | UUID/numeric values absent; stable selection |
| V3-HOLDOUT-AMBIGUOUS | Ambiguous evidence | DB-H2-CONVERSION-007 | REAL_INTEGRATION | Holdout | No | SQLState 22018; uncertainty preserved |
| V3-HOLDOUT-INSUFFICIENT | Insufficient evidence | JAVA-HOSTILE-009 | SYNTHETIC_EDGE_CASE | Holdout | No | Restrain; no selection |
| V3-HOLDOUT-DUPLICATE | Duplicate fingerprint pattern | DB-H2-DUPLICATE-010 | REAL_INTEGRATION | Holdout | No | SQLState 23505; stable meaning |
| V3-HOLDOUT-UNSUPPORTED-CLAIM | Unsupported claim trap | SECURITY-GENERIC-002 | REAL_RUNTIME | Holdout | Yes | No invented JWT, role, or framework |
| V3-HOLDOUT-FABRICATED-SOURCE | Fabricated-source trap | ADV-TERMINAL-003 | REAL_RUNTIME | Holdout | Yes | No invented source; terminal-safe output |

## Independence and fairness

- Expected values are not generated from model output.
- Production analyzers do not read V3 resources.
- The V3 fixture references V2 executable recipes only; it does not call analyzer helper methods.
- Every candidate receives the same 20 cases, structured request factory, prompt version, schema, grammar, limits, timeout, process strategy, validator, hardware, and scoring rules.
- Candidate-specific prompt wording is forbidden. Only an official chat-template envelope may vary when technically required and must be recorded.
- Sanitized model outputs and deterministic validation reasons will be archived under ignored research output directories.

## Manual review

Every case contains a `manualReviewFocus`. A human reviewer scores explanation usefulness as useful only when the prose adds concise clarity without repeating every trusted field, expanding certainty, or adding an unsupported identifier. Another model is not the sole evaluator.
