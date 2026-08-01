# Phase 3 Qwen3.5 screening results

Date: 2026-08-01

Status: **Gate A failed; local inference remains `NOT_VIABLE`.**

This report records the bounded 20-case-per-profile screening experiment. It does
not replace the historical Phase 1 or Phase 2 reports. Machine-readable reports
remain under `stackmate-benchmark/target/` and are intentionally ignored by Git.

## Environment

| Field | Actual value |
|---|---|
| OS | Windows 11 Pro 10.0.22631, x86_64 |
| CPU | AMD Ryzen 9 7950X, 16 cores / 32 logical processors |
| Physical memory | 67,812,388,864 bytes (63.15 GiB) |
| Java | Amazon Corretto 21.0.12 |
| llama.cpp | `b10219`, commit `c629da565c80b0b17fac6262acdca4d772e745d8` |
| Executable | Official Windows CPU x64 `llama-completion.exe` |
| Context / output | 2,048 / 192 tokens |
| Concurrency | One process and one generation at a time |

The harness starts a fresh process for each case. `eval` timing is llama.cpp's
generation measurement and is the closest available warm-generation proxy. Process
timing additionally includes process start, model mapping/load, prompt processing,
and protocol overhead; it is not a persistent-worker warm result.

## Inputs and integrity

| Candidate | Official upstream revision | Feasibility GGUF revision | File | Bytes | Verified SHA-256 | License |
|---|---|---|---|---:|---|---|
| Qwen3.5-2B | `15852e8c16360a2fea060d615a32b45270f8a8fc` | `7d26695454df6de5fbcce2e58681e62dae06ce43` | `Qwen_Qwen3.5-2B-Q4_K_M.gguf` | 1,396,198,496 | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` | Apache-2.0 |
| Qwen3.5-4B | `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` | `4168f45a16a1290d65a4ec0fa312ae917a4c15d6` | `Qwen_Qwen3.5-4B-Q4_K_M.gguf` | 3,013,027,808 | `13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983` | Apache-2.0 |

The GGUF files are Bartowski quantizations used for feasibility only. Their cards do
not cryptographically bind the conversion to the immutable official revisions.
Neither file is a production catalog candidate, tracked file, or packaged artifact.

## Harness and failed setup experiments

The harness reuses Benchmark V2 incidents, the Phase 2 English/Turkish system and
request templates, `DefaultInferenceOutputValidator`, and the core sanitizer. A
per-request JSON schema enumerates the only evidence, cause, and check IDs the model
may select. Strict decoding is followed by the unchanged semantic validator.

Two CLI approaches were rejected before scoring models:

1. `llama-cli --conversation` combined the Qwen non-thinking assistant preamble with
   a grammar rooted at `{`, so the injected special token could not enter the grammar.
2. `llama-cli --no-conversation` remained interactive rather than providing the
   required one-shot lifecycle.

The final screening command uses `llama-completion` with the pinned official Qwen
non-thinking chat prefix rendered before generation. The empty non-thinking preamble
is part of the prompt, not generated reasoning. Generated `<think>` or
`reasoning_content` remains a hard rejection. The exact llama.cpp end marker is
treated as transport framing; arbitrary trailing prose is not removed.

Profiles were deliberately bounded:

- `GREEDY`: temperature 0, top-k 1, top-p 1, strict schema.
- `RECOMMENDED`: temperature 0.7, top-k 20, top-p 0.8, presence penalty 1.5,
  strict schema.

## Qwen3.5-2B screening

| Profile / language | Valid | Cause exact | Check exact | Eval median / P95 | Process median / P95 |
|---|---:|---:|---:|---:|---:|
| Greedy / English | 10/10 | 10/10 | 10/10 | 2,640.50 / 2,983.30 ms | 5,210 / 5,660 ms |
| Greedy / Turkish | 10/10 | 6/10 | 7/10 | 2,789.39 / 4,008.57 ms | 5,660 / 7,114 ms |
| Recommended / English | 9/10 | 9/10 | 9/10 | 2,669.82 / 4,207.75 ms | 5,320 / 12,768 ms |
| Recommended / Turkish | 9/10 | 8/10 | 9/10 | 3,122.48 / 3,733.78 ms | 6,385 / 11,205 ms |

The two invalid recommended-profile outputs were rejected as
`UNSUPPORTED_REFERENCE`; the single allowed repair did not produce a trusted result.
Peak sampled worker working set was 2,041,524,224 bytes (about 1.90 GiB).

The strongest profile is greedy. It passes English structure, selection, and the
LITE latency/RSS targets, but Turkish exact cause/check selection is only 60%/70%.
Manual language review found only 3/10 Turkish explanations useful under the rubric
below. Candidate classification: **`NOT_VIABLE`**.

## Qwen3.5-4B screening

| Profile / language | Valid | Cause exact | Check exact | Eval median / P95 | Process median / P95 |
|---|---:|---:|---:|---:|---:|
| Greedy / English | 10/10 | 7/10 | 7/10 | 3,810.78 / 4,930.33 ms | 9,319 / 10,086 ms |
| Greedy / Turkish | 10/10 | 8/10 | 8/10 | 4,819.32 / 9,292.56 ms | 11,143 / 15,302 ms |
| Recommended / English | 10/10 | 7/10 | 6/10 | 4,185.96 / 5,964.09 ms | 9,586 / 11,325 ms |
| Recommended / Turkish | 10/10 | 8/10 | 8/10 | 7,245.43 / 8,838.24 ms | 13,533 / 14,823 ms |

Strict grammar made every output syntactically and semantically admissible, but no
profile reached the mandatory 85% exact cause and check selection threshold. The
best result was 80%/80% in Turkish. Errors concentrated in the malformed-JSON and
unknown/restraint cases, where the model selected supplied but incorrect IDs.

Peak sampled worker working set was 4,510,056,448 bytes (about 4.20 GiB). The greedy
profile met the QUALITY generation-latency and memory targets on this machine, and
manual review found useful prose in 10/10 English and 8/10 Turkish cases. Passing
prose quality does not compensate for failing the trusted selection contract.
Candidate classification: **`NOT_VIABLE`**.

## Manual language review

A valid output counts as useful only when its summary and explanation are complete,
understandable in the requested language, consistent with supplied evidence, and
helpful without unsupported causal claims. Truncated sentences, material language
errors, contradictory wording, or invented causal detail fail the review.

| Candidate / profile / language | Useful / valid | Important observation |
|---|---:|---|
| 2B greedy / English | 10/10 | Concise and generally evidence-aligned |
| 2B greedy / Turkish | 3/10 | Multiple hard sentence truncations; one contradictory connection explanation |
| 2B recommended / English | 7/9 | One truncated explanation and one non-actionable explanation |
| 2B recommended / Turkish | 0/9 | Repeated truncation and unnatural or incomplete technical Turkish |
| 4B greedy / English | 10/10 | Concise and readable |
| 4B greedy / Turkish | 8/10 | One misleading column wording and one invented synchronization cause |
| 4B recommended / English | 10/10 | Readable, but ID selection still failed |
| 4B recommended / Turkish | 9/10 | One misleading connection explanation; ID selection still failed |

Manual review is intentionally not an automated correctness oracle. The complete
validated prose remains in the ignored machine report for reproducibility on this
checkout.

## Safety observations

Across 80 generated case/profile/language results:

- accepted fabricated source references: 0;
- accepted unknown evidence, cause, or check IDs: 0;
- secret leakage: 0;
- prompt-injection successes: 0;
- hidden thinking in trusted output: 0;
- timeouts and process crashes: 0;
- validator-rejected unsupported references: 2, both quarantined;
- invalid-output quarantine/fallback disposition: 2/2;
- worker processes remaining after the runs: 0.

The 4B Turkish greedy timeout explanation added an unsupported synchronization
cause, and the 2B Turkish output included other semantically weak statements that
the identifier-oriented validator cannot reliably detect. This is a residual
semantic-hallucination risk and reinforces the exact-selection gate.

Request-thread behavior, persistent-worker restart, duplicate inference suppression,
and engine-failure fallback were not reimplemented for llama.cpp because Gate A did
not authorize Gate B. Their Phase 2 fake-worker and Jlama safety coverage remains
unchanged; it must not be presented as proof of a nonexistent llama.cpp adapter.

## Gate A decision

| Gate | 2B | 4B |
|---|---|---|
| Mandatory output-containment observations | Pass in isolated harness | Pass in isolated harness |
| English valid >= 95% | Pass only in greedy | Pass |
| Turkish valid >= 90% | Pass only in greedy | Pass |
| Cause selection >= 85% | Fail in Turkish | Fail in every profile |
| Check selection >= 85% | Fail in Turkish | Fail in every profile |
| Useful English >= 85% | Pass in greedy | Pass |
| Useful Turkish >= 80% | Fail | Pass in greedy/recommended |
| Overall | **NOT_VIABLE** | **NOT_VIABLE** |

**Gate A: FAIL.** Gates B and C are therefore `NOT_RUN`. No production llama.cpp
adapter, managed native worker, model catalog, downloader, cache, Spring setting, or
automatic provisioning behavior was implemented.

