# Phase 3 entry baseline

## Identity

- Date: 2026-08-01
- OS: Microsoft Windows 11 Pro 10.0.22631, x86_64
- CPU: AMD Ryzen 9 7950X, 16 cores / 32 logical processors
- RAM: approximately 63.16 GiB
- JVM: Amazon Corretto OpenJDK 21.0.12+8-LTS
- Spring Boot: 4.1.0
- Spring Framework: 7.0.8
- Maven Wrapper: 3.9.16
- Phase 2 commit: `cf1518c5dde5af940740e0eb35ce7bd808caaa64`
- Corpus V2 manifest SHA-256: `292B395F6024837A52878B38A6C0170A62CB29FDFCC89DF68B845CED7732F294`

## Verification

The clean normal build passed all 11 projects. Maven Enforcer reported that the Java, Maven, dependency convergence,
and banned-dependency rules passed. Surefire ran 314 tests and Failsafe ran 12 packaged-process tests, with zero
failures, errors, or skips.

The dependency tree reconfirmed:

```text
stackmate-core
    no runtime dependencies

stackmate-inference-api
    -> stackmate-core

stackmate-spring-boot-starter
    -> stackmate-spring-boot-autoconfigure
    -> stackmate-core
    no inference engine

stackmate-spring-boot-starter-local
    -> deterministic starter
    -> inference API
    -> inference JVM adapter
    -> Jlama 0.8.4
```

## Deterministic sample evidence

The English and Turkish packaged runs invoked `/demo/database/duplicate`. Both returned the unchanged Spring Boot
response shape with status 500. StackMate rendered `DATABASE_CONSTRAINT` / `VERİTABANI KISITI`, `HIGH` / `YÜKSEK`,
the observed `java.sql.SQLException`, and the real application location
`DemoFailureController#duplicateDatabaseValue(DemoFailureController.java:52)`.

These executions reproduce the deterministic product baseline only. They make no claim about Qwen3.5 or llama.cpp.

## Entry risks

- Real inference has only been executed locally on Windows so far.
- The current Jlama/Qwen2.5 configurations remain `NOT_VIABLE`; they are historical controls, not Phase 3 candidates.
- llama.cpp is a native parser/executor for untrusted GGUF input. A pinned source revision and verified model artifact
  reduce supply-chain risk but do not provide an OS sandbox.
- Existing ADR numbers 0009 through 0014 belong to Phase 2. Phase 3 decisions will use the next unused numbers instead
  of overwriting history.

