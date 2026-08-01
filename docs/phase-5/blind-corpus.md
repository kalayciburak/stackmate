# Phase 5 blind qualification corpus

Frozen: 2026-08-02

## Identity

| Field | Value |
|---|---|
| Corpus ID | `stackmate-phase-5-blind` |
| Schema | 1 |
| Prompt contract | 2, English only |
| Cases | 20 |
| Final pre-model manifest SHA-256 | `abadadd2216b3c429696a53b0be662abca5c0e46a5b90cf007108b5847ba20bc` |
| Initial freeze commit | `b0a104deff769047f4ec7b5dfd60c374bb11c940` |

The manifest and recipes were committed before any blind prompt was sent to Qwen. A pre-model type check found that
three independently authored category labels used conceptual names rather than the existing enum names; those labels
were corrected without changing a recipe, expected selection, forbidden claim, or model prompt. Historical V2 and
Phase 4 reports were not changed.

## Composition

| Provenance | Count |
|---|---:|
| `REAL_RUNTIME` | 11 |
| `REAL_INTEGRATION` | 8 |
| `SYNTHETIC_EDGE_CASE` | 1 |
| Total real execution | 19 (95%) |

The set covers async wrappers, dependency injection, placeholder resolution, malformed JSON, validation, four
database failures, connection refusal, read timeout, future timeout, executor rejection, authorization, prompt
injection, credential redaction, unknown-error restraint, ambiguity, and fabricated-source traps.

## Independence controls

- Every recipe has a new `blind.*` identity and the manifest test rejects reuse of a V2 recipe.
- The executable recipes use new values, endpoints, table/column names, wrapper messages, and adversarial sentinels.
- Expected categories, root types, facts, dispositions, selection counts, forbidden claims, and manual-review focuses
  were authored in the manifest before model evaluation.
- The manifest does not import analyzer constants, production cause IDs, or production check IDs.
- Model scoring maps the frozen expected selection count onto the deterministic candidate list supplied for that
  incident. The model is never credited for generating a candidate.
- Two restraint cases require empty selections and an honest limitation instead of an invented solution.

## Real-execution validation

The pre-model command was:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl stackmate-benchmark -am `
  '-Dtest=QualificationBlindManifestTest,QualificationBlindRuntimeRecipeTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: 21 tests passed (2 manifest checks and 19 executable recipe invocations). The first run correctly discovered
that Spring Framework 7 uses `PlaceholderResolutionException` as the captured root; the independently observable
root expectation was corrected, and the second run passed. A separate pre-model inference-input check then verified
that every diagnostic case exposes the frozen number of deterministic cause/check candidates and that the controlled
credentials are absent. No model was invoked by these checks.

## What this corpus does not prove

Twenty cases cannot represent all developer failures, and 19 executable cases still run in a controlled local test
environment. This corpus is a blind qualification supplement to V2, not a replacement for packaged Spring E2E,
cross-platform execution, or long-lived worker testing.
