# Phase 5 blind qualification corpus

Frozen: 2026-08-02

## Identity

| Field | Value |
|---|---|
| Corpus ID | `stackmate-phase-5-blind` |
| Schema | 1 |
| Prompt contract | 2, English only |
| Cases | 20 |
| Final pre-model manifest SHA-256 | `eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a` |
| Initial freeze commit | `b0a104deff769047f4ec7b5dfd60c374bb11c940` |

The manifest and recipes were committed before any blind prompt was sent to Qwen. A pre-model type check found that
three independently authored category labels used conceptual names rather than the existing enum names. A second
requirements audit also found that the initial set did not cover every mandatory blind purpose. Before any model
execution, the set was corrected to the exact 20-purpose composition below, revalidated, and committed again.
Historical V2 and Phase 4 reports were not changed; the initial freeze remains in history.

## Composition

| Provenance | Count |
|---|---:|
| `REAL_RUNTIME` | 8 |
| `REAL_INTEGRATION` | 10 |
| `SYNTHETIC_EDGE_CASE` | 2 |
| Total real execution | 18 (90%) |

## Cases and frozen ground truth

| Case | Source | Category | Purpose |
|---|---|---|---|
| `B5-COMPLETION-NUMERIC-DURATION` | real runtime | `GENERAL_JAVA` | `CompletionException` wrapper |
| `B5-CONFIGURATION-CONVERSION-RETRY` | real integration | `SPRING_CONFIGURATION` | configuration conversion |
| `B5-MISSING-PROPERTY-ENDPOINT` | real integration | `SPRING_CONFIGURATION` | missing property |
| `B5-BEAN-AMBIGUITY-CODEC` | real integration | `SPRING_DEPENDENCY_INJECTION` | bean ambiguity |
| `B5-CIRCULAR-DEPENDENCY-ROUTER` | real integration | `SPRING_DEPENDENCY_INJECTION` | circular dependency |
| `B5-VALIDATION-NESTED-ADDRESS` | real integration | `VALIDATION` | nested validation |
| `B5-MALFORMED-JSON-ARRAY` | real integration | `SERIALIZATION` | unfamiliar malformed JSON |
| `B5-UNKNOWN-ENUM-SHIPMENT` | real integration | `WEB_REQUEST` | unknown enum |
| `B5-DB-UNIQUE-EXTERNAL-KEY` | real integration | `DATABASE_CONSTRAINT` | alternate unique wording |
| `B5-DB-FOREIGN-KEY-REGION` | real integration | `DATABASE_CONSTRAINT` | foreign key |
| `B5-DB-MISSING-COLUMN-WRAPPED` | real integration | `DATABASE_SCHEMA` | wrapped missing column |
| `B5-CONNECTION-LOOPBACK-REFUSED` | real runtime | `NETWORK_CONNECTION` | connection refusal |
| `B5-DNS-INVALID-HOST` | real runtime | `DNS` | DNS resolution failure |
| `B5-TLS-PLAIN-PEER` | real runtime | `TLS` | TLS against a plain peer |
| `B5-TIMEOUT-READ-DEADLINE` | real runtime | `TIMEOUT` | read timeout |
| `B5-UNKNOWN-RUNTIME-SIGNAL` | real runtime | `UNKNOWN` | unknown exception restraint |
| `B5-AMBIGUOUS-ASYNC-WRAPPER` | synthetic edge | `UNKNOWN` | ambiguous evidence restraint |
| `B5-INSUFFICIENT-CAPTURE-METADATA` | synthetic edge | `UNKNOWN` | insufficient evidence restraint |
| `B5-PROMPT-DELIMITER-INJECTION` | real runtime | `GENERAL_JAVA` | prompt injection |
| `B5-SECRET-FABRICATED-SOURCE-TRAP` | real runtime | `GENERAL_JAVA` | secret plus fabricated source |

The manifest holds the full expected root type, facts, disposition, cause/check selection count, forbidden claims,
and manual-review focus for each row.

## Independence controls

- Every recipe has a new `blind.*` identity and the manifest test rejects reuse of a V2 recipe.
- The executable recipes use new values, endpoints, table/column names, wrapper messages, and adversarial sentinels.
- Expected categories, root types, facts, dispositions, selection counts, forbidden claims, and manual-review focuses
  were authored in the manifest before model evaluation.
- The manifest does not import analyzer constants, production cause IDs, or production check IDs.
- Model scoring maps the frozen expected selection count onto the deterministic candidate list supplied for that
  incident. The model is never credited for generating a candidate.
- Three restraint cases require empty selections and an honest limitation instead of an invented solution.

## Real-execution validation

The pre-model command was:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl stackmate-benchmark -am `
  '-Dtest=QualificationBlindManifestTest,QualificationBlindRuntimeRecipeTest,QualificationBlindInferenceInputTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: 21 tests passed (2 manifest checks, 18 executable recipe invocations, and one inference-input check). The
first run correctly discovered
that Spring Framework 7 uses `PlaceholderResolutionException` as the captured root; the independently observable
root expectation was corrected, and the second run passed. A separate pre-model inference-input check then verified
that every diagnostic case exposes the frozen number of deterministic cause/check candidates and that the controlled
credentials are absent. No model was invoked by these checks.

## What this corpus does not prove

Twenty cases cannot represent all developer failures, and 18 executable cases still run in a controlled local test
environment. This corpus is a blind qualification supplement to V2, not a replacement for packaged Spring E2E,
cross-platform execution, or long-lived worker testing.
