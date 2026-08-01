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

## Model

No model is distributed by StackMate. The first benchmark candidate is the user-supplied
`Qwen2.5-0.5B-Instruct` model, whose official model card declares Apache License 2.0. The exact source,
quantization, files, and SHA-256 values will be recorded only for files actually evaluated.

Users are responsible for reviewing the license and provenance of any supplied model. StackMate never enables
remote-code execution while loading a model.

## Development and test dependencies

JUnit, AssertJ, JaCoCo, Maven plugins, JMH, and other test/build tooling are not runtime dependencies of the
deterministic starter. Their exact resolved versions and licenses are reviewable through the Maven dependency
tree and generated build reports.
