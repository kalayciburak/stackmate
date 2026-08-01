# StackMate architecture

## Architectural intent

StackMate adds bounded, sanitized, evidence-backed diagnostics to a Spring Boot servlet application without
owning its exception response or logging configuration. Deterministic extraction is authoritative. Local model
inference is optional, isolated, asynchronous, and allowed to reference only known facts and candidates.

```mermaid
flowchart TD
    A["Escaping MVC exception"] --> B["Servlet capture adapter"]
    B --> C["Immutable incident snapshot"]
    C --> D["Sanitizer"]
    D --> E["Deterministic analyzers"]
    E --> F["Evidence-backed diagnosis"]
    F --> G["Quick console renderer"]
    F --> H["Fingerprint and deduplication"]
    H --> I["Bounded inference executor"]
    I --> J["Managed local JVM"]
    J --> K["Output validator"]
    K --> L["Local explanation renderer"]
    F --> M["Optional sanitized exporter"]
```

## Module boundaries

```text
stackmate-sample
  -> stackmate-spring-boot-starter-local
       -> stackmate-spring-boot-starter
            -> stackmate-spring-boot-autoconfigure
                 -> stackmate-core
                 -> stackmate-inference-jvm (optional)
                      -> stackmate-inference-api
                           -> stackmate-core
       -> stackmate-inference-api
       -> stackmate-inference-jvm

stackmate-benchmark
  -> stackmate-core
  -> stackmate-inference-api
```

### `stackmate-core`

JDK-only immutable incident data, defensive throwable traversal, privacy sanitization, application-frame
selection, evidence and analyzers, diagnosis aggregation, fingerprinting, bounded deduplication, and
renderer-neutral results. It imports no Spring, servlet, SLF4J, JSON, database, security, or inference runtime.

### `stackmate-inference-api`

Stable engine-neutral request/result, availability, model descriptor/capability, failure, validation, and circuit
breaker contracts. It may use core domain types but exposes no Jlama type or raw engine JSON.

### `stackmate-inference-jvm`

The experimental Jlama adapter and managed Java 21 worker protocol. Jlama is runtime-scoped here and absent from
the deterministic starter. Local files only; no model downloader, HTTP client, server, or telemetry.

### `stackmate-spring-boot-autoconfigure`

Typed properties, conditions, servlet capture, Spring runtime context, SLF4J console output, safe production
profile guard, application custom analyzer discovery, optional inference orchestration, and future startup
analysis. Optional dependencies are isolated behind class-presence conditions.

### Starters

The base starter aggregates deterministic auto-configuration only. The local starter explicitly adds the
inference API and Jlama adapter. Neither contains model weights.

### Sample and benchmark

The sample is intentionally faulty demonstration code only. The benchmark owns controlled ground truth,
deterministic evaluation, raw-versus-structured inference comparison, schema/leakage/injection checks, and JMH
microbenchmarks. Generated machine-specific reports remain under `target/`.

## Runtime invariants

- Capture performs no unbounded recursion, collection, string copy, or queueing.
- StackMate catches failures only at its own boundaries and always rethrows/preserves the application exception.
- Every printable or exportable runtime string is sanitized; model output is sanitized again.
- Model work never runs on the request thread, event dispatcher, or common ForkJoinPool.
- Duplicate incidents reserve at most one inference within the TTL window.
- No runtime component downloads models, calls a cloud provider, opens a network listener, or uploads incidents.
- Severe JVM errors receive only a minimal best-effort message and never trigger inference.

## Extension points

Interfaces exist only at real boundaries: diagnostic analyzers, sanitizer, deduplication store, console renderer,
local inference engine, circuit breaker, and incident exporter. Concrete internal algorithms remain concrete.
Application Spring beans override defaults through conditional auto-configuration without a service locator.

## Exception visibility

The servlet filter sees exceptions escaping the filter chain and reads handler metadata from standard request
attributes when available. It does not see every exception that an application logs and does not intercept
resolved `@ExceptionHandler` flows. See ADR 0009 for the explicit visibility contract.

## Startup failures

Startup diagnostics are a separate optional Boot `FailureAnalyzer` integration registered through
`META-INF/spring.factories`. Runtime MVC completion does not depend on it.
