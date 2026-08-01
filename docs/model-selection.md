# Local inference and model selection

Last reviewed: 2026-08-01

This document separates source-backed capabilities from StackMate measurements. No performance or model-quality
number in this document is an unexecuted target presented as a result.

## Inference runtime decision matrix

| Criterion | Jlama 0.8.4 | llama3.java | ONNX Runtime GenAI 0.15.0 |
|---|---|---|---|
| Java baseline | Java 21 practical baseline | Java 21+ | Java 11+ build; Java 8+ generated JAR claim |
| Preview flags | Required by documented setup | Required | No |
| Incubator modules | `jdk.incubator.vector` | `jdk.incubator.vector` | No |
| Pure Java | Java CPU fallback; optional native SIMD | Yes | No, JNI/C++ runtime |
| Published Maven consumption | `com.github.tjake:jlama-core:0.8.4` | No upstream artifact | GenAI Java package not published; source build required |
| Windows x86_64 | Java path; optional native classifier documented | Plausible, no official matrix | Release assets available |
| Linux x86_64 | Java path; optional native classifier documented | Plausible, no official matrix | Release assets available |
| macOS x86_64 | Java path; optional native classifier documented | Plausible, no official matrix | Platform combinations vary |
| macOS ARM64 | Java path; optional native classifier documented | Plausible, no official matrix | Release assets available |
| Model format | Hugging Face SafeTensors/tokenizers | GGUF | ONNX model directory plus `genai_config.json` |
| Quantization | F32/F16/BF16 and Q8/Q4 | F16/BF16/F32, Q4/Q5/Q6/Q8 variants | FP, INT8, INT4; builder paths vary |
| Small-model support | Llama, Mistral, Gemma, Qwen2, Granite, GPT-2 and others | Llama 3 family only | Broadest model matrix |
| Structured output | No documented grammar/schema guarantee | No documented constrained output | Native capability advertised; Java exposure unproven |
| Embeddings | Supported | Not documented | Separate generic ORT model possible; no high-level GenAI Java API found |
| Cold/warm/RSS evidence | No comparable official dataset found | No usable official numeric table found | No comparable end-to-end Java dataset found |
| Maintenance | Latest Central 0.8.4; source main is newer beta | Small source project, no release discipline | Active Microsoft project; 0.15.0 released 2026-07-30 |
| License | Apache-2.0 | MIT | MIT |
| Supply-chain notes | Published version predates later dependency fixes; scan SBOM | Vendoring shifts provenance/update duty to StackMate | Source build requires CMake/Python/Gradle/native toolchain |
| Offline/serverless runtime | Yes after local provisioning | Yes | Yes after local provisioning |
| Embedding complexity | Moderate | High | High until Java artifact is published |
| Process isolation | Strong fit for a warm child JVM | Possible | Recommended because native runtime can crash |

## Engine selection

StackMate selects **Jlama 0.8.4** for one experimental production adapter because it is the only evaluated option
with a current published Maven artifact, local CPU generation, small-model support, quantization support, and a
pure-Java fallback. The initial adapter does not require `jlama-native`; platform-specific native acceleration can
be evaluated later.

The runner is placed in a long-lived managed child JVM. This contains preview/vector flags, model heap/off-heap
usage, and crashes outside the application JVM. IPC uses framed standard input/output, not HTTP or a socket.
This is an architectural inference from the runtime constraints, not an upstream performance claim.

ONNX Runtime GenAI remains the preferred future reevaluation candidate once its Java generative artifact and
structured-generation APIs are published and stable. The mature `com.microsoft.onnxruntime:onnxruntime` artifact
is not itself a complete tokenization/search/KV-cache generation runtime.

## Model candidate matrix

| Candidate | Parameters | License | Turkish evidence | Structured/code evidence | Selected-engine compatibility | Official quantized evidence |
|---|---:|---|---|---|---|---|
| Qwen2.5-0.5B-Instruct | 0.49B | Apache-2.0 | Family states 29+ languages; Turkish not named | Family explicitly calls out JSON improvements | Qwen2 architecture is documented by Jlama | Official GGUF Q4_K_M, 491 MB; Jlama requires SafeTensors rather than GGUF |
| Qwen2.5-Coder-0.5B-Instruct | 0.49B | Apache-2.0 | Model card is English-tagged; Turkish unproven | Code generation/reasoning/fixing focus | Qwen2 architecture is documented by Jlama | Official GGUF Q4_K_M, 491 MB; Jlama requires SafeTensors |
| Qwen2.5-1.5B-Instruct | 1.54B | Apache-2.0 | Family states 29+ languages; Turkish not named | Model card explicitly states structured JSON improvement | Qwen2 architecture is documented by Jlama | Official GGUF Q4_K_M, 1.12 GB; Jlama requires SafeTensors |
| Qwen3-0.6B | 0.6B | Apache-2.0 | Turkish is explicitly in the 119-language list | Coding/agent and JSON examples; no schema guarantee | Not in Jlama 0.8.4 documented support list | Official GGUF Q8_0, 639 MB |
| mT0-small | 0.3B | Apache-2.0 | Underlying mT5 explicitly includes Turkish | Weak code/strict JSON evidence | Not supported by Jlama; ONNX comparison only | Official repository includes ONNX files |

Qwen3.5-0.8B was also reviewed. It reports Apache-2.0 and broad multilingual/code capability, but its newer
hybrid architecture requires newer/specialized runtimes and is not a safe Jlama 0.8.4 choice.

## Initial model choice

The first compatibility and structured-output benchmark uses **Qwen2.5-0.5B-Instruct** in its official
SafeTensors form. It fits the requested small-model range, has an Apache-2.0 license, requires no gated access or
remote code, and uses the Qwen2 architecture documented by Jlama. It is not declared READY until the A/B/C corpus,
English/Turkish quality, invalid-output, leakage, latency, and RSS gates are actually measured.

### Executed compatibility and corpus measurements

The official Qwen2.5-0.5B-Instruct SafeTensors directory was supplied explicitly and loaded by Jlama 0.8.4 in the
managed Java 21 worker on Windows 11. A five-call prompt-v1 compatibility run returned five validator-approved
outputs with a 7,350 ms cold call and 5,576-5,723 ms warm calls. The later prompt-v2 corpus run is the authoritative
quality measurement:

| Measurement | Actual prompt-v2 result |
|---|---:|
| English structured valid outputs | 36/50 |
| English structured category/cause/check accuracy | 36/50 (72%) |
| English structured median / P95 latency | 7,213 / 18,933 ms |
| English raw valid outputs | 8/50 |
| English raw category accuracy | 1/50 (2%) |
| Turkish structured valid outputs | 0/5 |
| Peak worker working set | 2,790.19 MiB |
| Peak parent + worker working set | 2,972.36 MiB |
| Model directory size | 988,097,824 bytes |
| Sampled process-tree network endpoints | 0 |

Environment: AMD Ryzen 9 7950X (16 cores/32 threads), 63.16 GiB RAM, Windows 11 Pro, Amazon Corretto 21.0.12,
Jlama 0.8.4. Working set was sampled and is not a cross-platform RSS guarantee. Jlama reported model type BF16,
working memory F32, and quantized working memory BF16. The official unmodified `model.safetensors` checksum was
`FDF756FA7FCBE7404D5C60E26BFF1A0C8B8AA1F72CED49E7DD0210FE288FB7FE` (SHA-256).

The adapter is **EXPERIMENTAL**. Structured evidence clearly improves over raw traces and every invalid result is
rejected, but the model misses the quality, under-five-second warm, under-1.5-GB memory, and Turkish-language gates.
During Phase 1 the selected descriptor therefore advertised English support and did not advertise Turkish support.
See `docs/benchmark.md` for complete A/B/C metrics and measurement limitations.

`Qwen2.5-1.5B-Instruct` is not tested because the smaller 0.5B model already exceeds the memory target. Qwen3-0.6B
is retained for a future ONNX/Jlama-support comparison because it has stronger explicit Turkish evidence.

## Phase 2 model Lab V2 candidate record

This record was written before downloading a new model. Phase 2 first reuses the existing user-provisioned
Qwen2.5-0.5B-Instruct directory, then permits only one new candidate artifact. The Qwen2 family documentation
explicitly includes Turkish among its multilingual training languages; the adapter can therefore execute the
separate Turkish prompt for measurement. This is a protocol capability, not a claim that the 0.5B model passes
StackMate's Turkish quality gates.

| Candidate | Source revision | Weight format | File-level quantization | Weight bytes | Expected weight SHA-256 | License | Selection reason |
|---|---|---|---|---:|---|---|---|
| Qwen2.5-0.5B-Instruct | existing local official directory | SafeTensors BF16 | None | 988,097,824 | `FDF756FA7FCBE7404D5C60E26BFF1A0C8B8AA1F72CED49E7DD0210FE288FB7FE` | Apache-2.0 | Historical baseline, multilingual general model, JSON-oriented instruction tuning |
| Qwen2.5-Coder-0.5B-Instruct | `ea3f2471cf1b1f0db85067f1ef93848e38e88c25` | SafeTensors BF16 | None | 988,097,824 | `F9523886352217DED3AEEEF552B381AF79D568C6D49A4B9E423288CEA56B0A44` | Apache-2.0 | Same supported Qwen2 architecture and size; tests whether technical/code specialization improves evidence selection |

The production worker requests Jlama `DType.I8` quantized working memory while consuming the official BF16
SafeTensors weights. This is runtime working-memory quantization, not a quantized model artifact and must not be
reported as file-level INT8. Official GGUF/AWQ/GPTQ artifacts are not consumed by the selected SafeTensors adapter.
Qwen3-0.6B remains source-reviewed but is not downloaded: its `qwen3` architecture is rejected by the deliberately
bounded Jlama adapter, so it cannot provide a comparable result without an engine change.

## Phase 2 screening result and decision

The reduced schema-v2 contract did not make either screened 0.5B model reliable. Each cell below is an actual
10-case structured run. A model response that could not be parsed and validated after the one allowed repair is
counted as invalid, not as an engine crash.

| Candidate / language | Valid | Invalid | Cause/check exact | Warm median | P95 | Unsupported claims accepted |
|---|---:|---:|---:|---:|---:|---:|
| Qwen2.5-0.5B-Instruct / English | 0/10 | 10/10 | 0/10 | 18,808 ms | 23,278 ms | 0 |
| Qwen2.5-0.5B-Instruct / Turkish | 0/10 | 10/10 | 0/10 | 17,103 ms | 27,846 ms | 0 |
| Qwen2.5-Coder-0.5B-Instruct / English | 0/10 | 10/10 | 0/10 | 20,032 ms | 21,779 ms | 0 |
| Qwen2.5-Coder-0.5B-Instruct / Turkish | 0/10 | 10/10 | 0/10 | 21,999 ms | 23,016 ms | 0 |

The general model raw-stack research baseline was also 0/10 valid, with a 30,653 ms median and 34,352 ms P95.
Every structured failure was a bounded invalid-JSON result. Accepted unknown evidence IDs, fabricated source
references, secrets, forbidden claims, and prompt-injection successes were all zero because no invalid output crossed
the validator. These are containment results, not model-quality successes.

The Coder English worker peaked at 2,243,895,296 bytes working set (about 2.09 GiB); sampled parent plus worker peak
was 2,426,294,272 bytes (about 2.26 GiB). The process-tree sampler observed zero established network endpoints during
that run. Working set and endpoint sampling are Windows observations, not universal OS guarantees.

Neither candidate passed the screening gate, so the planned 50-case full structured benchmark was intentionally not
run. A longer run would not turn 0% schema validity into a credible product claim. No third candidate and no second new
artifact were downloaded. Jlama remains the single adapter because this evidence does not isolate the engine from the
model/prompt combination well enough to justify a production-engine migration.

Phase 2 local inference decision: **NOT_VIABLE** for the evaluated model/engine configurations. The engine-neutral SPI,
managed-process safety boundary, fake-child E2E tests, and deterministic fallback remain useful experimental
infrastructure. No evaluated real model is recommended or enabled by default. This decision supersedes the Phase 1
`EXPERIMENTAL` product classification without rewriting the historical Phase 1 section above.

## Model distribution and integrity

- Model weights are never committed or packaged.
- Runtime startup never downloads a model.
- The model path must be explicit, normalized, a regular file/directory as required by the engine, and must not
  traverse an unsafe symlink boundary.
- Checksums are recorded only for the exact evaluated files. GGUF checksums do not verify separate SafeTensors
  files.
- Apache-2.0 model licensing permits use and redistribution subject to license/notice/patent/trademark terms, but
  does not guarantee training-data, input, or output rights.

## Sources

- Jlama repository and license: https://github.com/tjake/Jlama
- Jlama Maven artifact: https://central.sonatype.com/artifact/com.github.tjake/jlama-core/0.8.4
- llama3.java: https://github.com/mukel/llama3.java
- ONNX Runtime GenAI: https://github.com/microsoft/onnxruntime-genai
- ONNX Runtime GenAI Java API: https://onnxruntime.ai/docs/genai/api/java.html
- ONNX Runtime GenAI build: https://onnxruntime.ai/docs/genai/howto/build-from-source.html
- Qwen2.5-0.5B-Instruct: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct
- Qwen2.5-Coder-0.5B-Instruct: https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct
- Qwen2.5-1.5B-Instruct: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct
- Qwen3-0.6B: https://huggingface.co/Qwen/Qwen3-0.6B
- mT0-small: https://huggingface.co/bigscience/mt0-small
