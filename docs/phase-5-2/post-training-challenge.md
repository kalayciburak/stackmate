# Phase 5.2 post-training challenge

Date: 2026-08-02

## Frozen corpus

| Item | Value |
|---|---|
| Corpus ID | `stackmate-phase-5-2-post-training-challenge` |
| Manifest | `/corpus-v4-post-training-challenge/manifest.json` |
| Cases | 30 |
| Real runtime/integration | 20 |
| Synthetic hard-negative | 5 |
| Adversarial or insufficient | 5 |
| Strict-restraint cases | 3 |
| Manifest SHA-256 | `9b5477bf2e1ea1593b3559a2696a0f5e9043f8d34577fbc86be85663d3d6f5e1` |
| Freeze commit | `730f269` |

The lock resource stores the exact manifest hash and marks the set inaccessible to
training. Case IDs are disjoint from Benchmark V2 and the Phase 5 blind corpus.

## Composition

The 20 executed cases cover Java wrapper chains, Spring dependency/configuration,
MVC serialization/request validation, H2 constraints/schema errors, JDK security,
loopback networking, and concurrency. They use new values, class names, table
names, messages, and request shapes rather than copying locked exception text.

The five hard negatives separate:

- pool exhaustion from unsupported driver failure;
- dependency cycles from unsupported missing dependencies;
- missing referenced rows from unsupported type mismatch;
- TLS evidence from unsupported DNS alternatives;
- request validation from unsupported database constraints.

The final five cases contain three unclassified incidents requiring abstention and
two new prompt-injection shapes embedded in incident data.

## Pre-freeze corrections

Runtime verification found that the malformed-list fixture produces a Jackson
`MismatchedInputException` classified as `SERIALIZATION`; the draft expectation was
corrected before freeze. Two draft JDK signature/certificate cases exposed a known
deterministic analyzer coverage gap and would have required the model to select an
unsupported answer. They were replaced before freeze with executable JDK
authorization cases that have direct supplied evidence.

No adapter output was generated or inspected during these corrections.

## Verification

```powershell
$env:JAVA_HOME='C:\Users\Burak\.jdks\corretto-21.0.12'
.\mvnw -pl stackmate-benchmark -am "-Dtest=PostTrainingChallengeManifestTest,PostTrainingChallengeRuntimeRecipeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: 24 tests, zero failures/errors/skips; 20/20 real recipes reproduced their
frozen root exception and all 30 cases satisfied the deterministic category and
candidate-count contract.
