# Third-party notices

StackMate is licensed under Apache License 2.0. Maven dependencies are resolved separately and are not model
weights or source code embedded in this repository.

## Production dependency policy

| Component | Module/scope | License | Notes |
|---|---|---|---|
| Spring Boot 4.1.0 and Spring Framework 7.0.8 | Spring adapter and sample | Apache-2.0 | Framework adapter only; absent from `stackmate-core`. |
| SLF4J | Spring adapter | MIT | Uses the logging facade already present in Spring applications. |
| Jackson | JVM inference adapter | Apache-2.0 | Used for bounded local IPC and model output parsing; absent from core. |
| Jlama 0.8.4 | Local starter only | Apache-2.0 | Experimental local CPU inference; model weights are not included. |

Jlama's optional module brings its Maven-declared transitive libraries only into the local starter, including
Guava, JCTools, Jackson, and Jinjava. They are absent from `stackmate-core` and the deterministic starter. Release
work must refresh the dependency tree, license inventory, and vulnerability review rather than treating this
development-time notice as a final SBOM.

## Model

No model is distributed by StackMate. The evaluated user-supplied model is the official
`Qwen/Qwen2.5-0.5B-Instruct` SafeTensors repository, whose model card declares Apache License 2.0. Commercial use
and redistribution are permitted by that license subject to its notice, patent, and trademark terms; StackMate
does not grant rights in training data, user inputs, or generated outputs.

- Source: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct
- Format/quantization: official SafeTensors files; no file conversion or additional quantization was applied.
  Jlama reported BF16 model storage, F32 working memory, and BF16 quantized working memory for the executed run
- Evaluated `model.safetensors` size: 988,097,824 bytes
- Evaluated `model.safetensors` SHA-256: `FDF756FA7FCBE7404D5C60E26BFF1A0C8B8AA1F72CED49E7DD0210FE288FB7FE`
- Redistribution by StackMate: none; users provision files explicitly
- Remote code: not enabled or required

Users are responsible for reviewing the license and provenance of any supplied model. StackMate never enables
remote-code execution while loading a model.

## Development and test dependencies

JUnit, AssertJ, JaCoCo, Maven plugins, JMH, and other test/build tooling are not runtime dependencies of the
deterministic starter. Their exact resolved versions and licenses are reviewable through the Maven dependency
tree and generated build reports.
