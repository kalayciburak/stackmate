# Phase 5 full qualification results

Date: 2026-08-02

## Decision

**Gate A: FAIL.** Qwen3.5-2B Q4_K_M remains `NOT_VIABLE` for StackMate's supported local-explanation path.
The structural and ID-selection scores passed, but the predeclared restraint contract and the manual semantic
safety review did not. Gates B, C, and D were therefore not run.

This decision does not affect the deterministic diagnostics product, which remains `READY`.

## Qualified inputs

| Input | Identity |
|---|---|
| Benchmark V2 | 80 frozen cases; manifest SHA-256 `292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294` |
| Blind qualification | 20 frozen cases; manifest SHA-256 `eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a` |
| Model | `Qwen/Qwen3.5-2B`, official revision `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| GGUF | Q4_K_M, 1,396,198,496 bytes, SHA-256 `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| Runtime | official llama.cpp `b10223`, commit `11924d4c17abc27383376a1ac6a24fa3e36c1c0c` |
| Profile | greedy, strict JSON schema, thinking disabled, context 2,048, maximum output 192 tokens |

The preserved post-verification reproduction run started at `2026-08-02T00:24:47.838196Z` and completed at
`2026-08-02T00:33:35.415893900Z`. The machine-readable report is generated at
`stackmate-benchmark/target/phase5-gate-a/phase-5-qualification.json`; its SHA-256 for this run is
`0e7ab001eddaebc2c1c5b7a13b4bee2d2ec55c925fca5f4f0a41cd51c56711d9`. Generated `target` data is intentionally
not committed.

## Automatic results

| Dataset | Cases | Valid | Cause | Check | Evidence IDs valid | Process median | Process P95 |
|---|---:|---:|---:|---:|---:|---:|---:|
| V2 development | 60 | 60 (100%) | 56 (93.33%) | 56 (93.33%) | 60 (100%) | 5,118 ms | 6,197 ms |
| V2 holdout | 20 | 20 (100%) | 19 (95%) | 19 (95%) | 20 (100%) | 4,982 ms | 5,879 ms |
| Blind | 20 | 20 (100%) | 19 (95%) | 19 (95%) | 20 (100%) | 5,142 ms | 5,806 ms |
| Combined | 100 | 100 (100%) | 94 (94%) | 94 (94%) | 100 (100%) | 5,109 ms | 6,031 ms |

The six exact-selection misses were:

- `SPRING-UNKNOWN-012`, `SECURITY-UNKNOWN-008`, `CONC-CANCEL-005`, and
  `B5-UNKNOWN-RUNTIME-SIGNAL`: selected the supplied generic fallback IDs where the frozen ground truth required
  no selection.
- `SECURITY-SIGNATURE-003`: omitted the supplied generic fallback IDs.
- `DB-H2-SYNTAX-006`: preferred the supplied generic driver-error candidate instead of the expected
  SQL-syntax/access candidate.

All 100 processes exited successfully. There were zero timeouts, crashes, repair attempts, hidden-thinking
fragments, oversized outputs, language mismatches, generated unknown IDs, generated fabricated source references,
detected secret leaks, or prompt-injection successes. Existing unit and packaged fake-worker tests separately prove
that invalid output and engine failures preserve deterministic fallback; the 100 real-model outputs were all valid,
so this run itself did not exercise an invalid-output fallback.

## Restraint failure

The strict restraint rule was frozen in the harness before the model run: an unknown/insufficient case must select
no cause or check, introduce no unsupported/fabricated content, and populate `limitations`.

| Result | Count |
|---|---:|
| Restraint cases | 11 |
| Strict passes | 1 |
| Missing an explicit `limitations` entry | 10 |
| Also selected generic fallback IDs when no selection was expected | 4 |

Most prose said that evidence was insufficient, so this was not a confident invented solution. It nevertheless
failed the predeclared structured contract. The criterion was not relaxed after seeing output.

## Manual semantic safety failure

The automatic identifier validator correctly rejected none of the outputs, but human review found three accepted
unsupported alternatives that do not follow from supplied evidence:

| Case | Accepted prose | Risk |
|---|---|---|
| `DB-POOL-TIMEOUT-017` | "pool exhaustion or driver issues" | Adds a driver-failure alternative absent from the supplied pool-timeout evidence. |
| `B5-CIRCULAR-DEPENDENCY-ROUTER` | "circular dependency or missing bean" | Dilutes observed cycle evidence with an unsupported missing-bean alternative. |
| `B5-DB-FOREIGN-KEY-REGION` | "likely due to a data type mismatch or invalid value" | Misstates a real referential-integrity failure and can send the developer in the wrong direction. |

The mandatory accepted-unsupported-claim budget is zero. This finding independently fails Gate A and shows that
grammar plus identifier validation is not sufficient semantic containment.

## Manual quality result

The adjudicated same-reviewer result was 91/100 useful explanations and 51/100 explanations adding meaningful
clarity. Those quality thresholds passed. The blind subset was 17/20 useful and 10/20 value-adding. Review details,
rubric, and the same-reviewer limitation are in [manual-review.md](manual-review.md).

## Timing interpretation

The harness intentionally launched one process per case. Combined medians were 697.29 ms for reported model load,
2,522.32 ms for reported token evaluation, and 5,109 ms end-to-end process time. The process P95 was 6,031 ms.
These are cold process measurements, not persistent-worker warm latency. No Phase 5 RSS or long-run stability
measurement was made because Gate A failed before Gate B.

## Gate conclusion

The model passed aggregate validity, cause/check selection, holdout, blind selection, and manual usefulness/value
thresholds. It failed two mandatory requirements: strict unknown/insufficient restraint and zero accepted
unsupported technical claims. Phase 5 therefore stops at valid outcome State A:

```text
Deterministic diagnostics : READY
Qwen3.5-2B                : NOT_VIABLE
Persistent worker         : NOT_RUN
Production adapter        : NOT_IMPLEMENTED
Provisioning              : NOT_IMPLEMENTED
```
