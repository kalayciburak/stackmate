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

`Qwen2.5-1.5B-Instruct` is the quality-bound comparison if its local memory measurement stays within the product
budget. Qwen3-0.6B is retained for a future ONNX/Jlama-support comparison because it has stronger explicit Turkish
evidence.

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
