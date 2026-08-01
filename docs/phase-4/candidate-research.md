# Phase 4 candidate and runtime research

Date: 2026-08-02

This note freezes the inputs for the English-only screening tournament. It is a
research inventory, not a production model catalog. No model is bundled, downloaded
by application code, or made available to the Spring starter by this work.

## Parameter-cap finding

Phase 4 defines the cap as 2,000,000,000 total parameters, including vision
parameters. Parameter totals below come from the official Hugging Face repository
metadata for the pinned SafeTensors revisions, not from model names.

The exact Phase 3 reference, `Qwen/Qwen3.5-2B`, contains 2,274,069,824 parameters.
Its conditional-generation configuration also contains a vision model. It therefore
violates the Phase 4 cap and cannot be an eligible candidate or finalist. Because the
prompt separately requires the exact Phase 3 model as a mandatory reference, it is
retained as an **ineligible historical control** and will receive the same screen for
comparison. It cannot pass Gate B regardless of its score.

`Qwen/Qwen3-1.7B` was considered for MID but rejected before download: its official
total is 2,031,739,904 parameters. `Qwen/Qwen2.5-1.5B-Instruct` replaces it as the
credible MID candidate.

## Eligible candidates

| Tier | Official model and revision | Architecture | Total parameters | Context | License | Thinking behavior |
|---|---|---|---:|---:|---|---|
| MICRO | `HuggingFaceTB/SmolLM2-360M-Instruct` at `a10cc1512eabd3dde888204e902eca88bddb4951` | `LlamaForCausalLM` | 361,821,120 | 8,192 | Apache-2.0 | No thinking mode in the pinned template |
| COMPACT | `Qwen/Qwen3-0.6B` at `c1899de289a04d12100db370d81485cdf75e47ca` | `Qwen3ForCausalLM` | 751,632,384 | 40,960 | Apache-2.0 | Template supports thinking; screening explicitly disables it |
| MID | `Qwen/Qwen2.5-1.5B-Instruct` at `989aa7980e4cf806f80c7fef2b1adb7bc71aa306` | `Qwen2ForCausalLM` | 1,543,714,304 | 32,768 | Apache-2.0 | No thinking mode in the pinned template |

Each model is instruction-tuned or post-trained, ungated, CPU-capable through the
selected llama.cpp runtime, and usable without remote-code trust. StackMate uses only
English, text-only, one-turn prompts. Candidate-specific chat envelopes are permitted
only to follow the official tokenizer template; the system instruction, structured
request, schema, scoring, context, and output limits remain identical.

Official sources:

- <https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct>
- <https://huggingface.co/Qwen/Qwen3-0.6B>
- <https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct>
- <https://huggingface.co/Qwen/Qwen3.5-2B>

## Screening GGUF artifacts

The first pass uses Q4_K_M consistently. Expected sizes and hashes are the LFS
metadata returned for the pinned GGUF repository revisions; the downloaded bytes
must be verified again before native parsing.

| Role | GGUF repository revision | Artifact | Expected bytes | Expected SHA-256 | Provenance |
|---|---|---|---:|---|---|
| MICRO | `bartowski/SmolLM2-360M-Instruct-GGUF` at `7be6f65f1db715fe5dc5a4634c0d459b4eed42ec` | `SmolLM2-360M-Instruct-Q4_K_M.gguf` | 270,590,880 | `2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2` | Third-party feasibility quantization |
| COMPACT | `bartowski/Qwen_Qwen3-0.6B-GGUF` at `60b85c0e3d8fe0f6474f406922a26d12aca4550d` | `Qwen_Qwen3-0.6B-Q4_K_M.gguf` | 484,220,320 | `9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14` | Third-party feasibility quantization |
| MID | `Qwen/Qwen2.5-1.5B-Instruct-GGUF` at `91cad51170dc346986eccefdc2dd33a9da36ead9` | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1,117,320,736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` | Official Qwen GGUF repository |
| INELIGIBLE CONTROL | `bartowski/Qwen_Qwen3.5-2B-GGUF` at `7d26695454df6de5fbcce2e58681e62dae06ce43` | `Qwen_Qwen3.5-2B-Q4_K_M.gguf` | 1,396,198,496 | `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` | Existing Phase 3 third-party artifact |

The three new artifacts total 1,872,131,936 bytes. The historical control already
exists in the ignored Phase 3 research cache. The third-party artifacts are suitable
only for feasibility screening: their cards do not cryptographically bind the GGUF
bytes to the official upstream revision.

The machine-readable candidate inventory is
`stackmate-benchmark/src/main/resources/phase4-candidates/manifest.json`, SHA-256
`a79c5f359b4bcb05f927eb8921f5fc32109db9abc6c3b9b706a47590ad0f68d2`. Its strict
loader rejects unknown fields, duplicate IDs, unsafe filenames, mutable-looking
revisions, unpinned checksums, non-Apache licenses, and eligible entries above the
parameter cap.

## Runtime pin

The isolated screen uses official llama.cpp release `b10221`, commit
`815a2a5915f22ce6a760c676389c5dfe8535c08f`. It was the latest stable release when
this research was executed.

| Field | Value |
|---|---|
| Windows artifact | `llama-b10221-bin-win-cpu-x64.zip` |
| Expected bytes | 18,352,727 |
| Expected SHA-256 | `61dd281e1c5be8a7ae0b403efd0feee9da126e40605c27df35f9d5bef49caaf9` |
| Local execution target | Windows x86_64 CPU |
| Other published targets | Not executed and therefore not claimed as verified |

The runtime supports the selected architectures, GBNF/JSON-schema constrained
generation, file-based prompts, bounded context/output, and explicit Qwen reasoning
control. No HTTP server or RPC backend is used.

Sources:

- <https://github.com/ggml-org/llama.cpp/releases/tag/b10221>
- <https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md>
- <https://github.com/ggml-org/llama.cpp/security/advisories>

## Security review

Release `b10221` postdates the published fixes for the reviewed high-severity GGUF
and tokenizer issues, including the `b8146` fix for `GHSA-3p4r-fq3f-q74v` and the
Qwen3.5 tokenizer-stack fix merged by upstream pull request 22110. The critical RPC
advisory remains a residual in the wider project; StackMate does not build, enable,
or invoke the RPC backend in this experiment.

Checksums and expected byte counts are verified before every new native-parser input
is accepted. That reduces substitution risk but does not make native parsing safe in
an absolute sense. The worker remains an isolated research process, model paths are
operator-controlled, and no application exception value can choose a model or URL.

The previously rejected `java-llama.cpp` artifact is not reconsidered: no current,
verified evidence established that its embedded llama.cpp revision meets this
experiment's architecture and security floor.

## Screening decision

Proceed in ascending size order: MICRO, COMPACT, MID, then the ineligible historical
control. Every role receives the frozen 20-case V3 screen, at most two bounded
generation profiles, strict grammar, and unchanged Phase 2 semantic validation. A
profile must return 20/20 valid trusted outputs to advance. The control is reported
but cannot advance because of the parameter-cap violation.

## Local artifact verification

The machine had 590,282,543,104 bytes free before Phase 4 downloads. The three new
model downloads total 1,872,131,936 bytes. Every activated local file matched the
expected byte count and SHA-256 above. The b10221 archive also matched its expected
18,352,727 bytes and checksum; the extracted CLI reported:

```text
version: 10221 (815a2a591)
built with Clang 20.1.8 for Windows x86_64
```

All files remain under ignored `.stackmate/phase4/` research paths. No model or
native binary is tracked, packaged, provisioned, or downloaded by library code.
