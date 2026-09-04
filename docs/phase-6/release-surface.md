# Phase 6 release surface

Gate B uses a fail-closed coordinate allowlist. Reactor membership is not
publication authorization.

| Module | Classification | Deploy |
|---|---|---:|
| `stackmate-parent` | `PUBLIC_RELEASE` | yes |
| `stackmate-core` | `PUBLIC_RELEASE` | yes |
| `stackmate-spring-boot-autoconfigure` | `PUBLIC_RELEASE` | yes |
| `stackmate-spring-boot-starter` | `PUBLIC_RELEASE` | yes |
| `stackmate-spring-boot-autoconfigure-local` | `HISTORICAL_EXPERIMENT` | no |
| `stackmate-inference-api` | `HISTORICAL_EXPERIMENT` | no |
| `stackmate-inference-jvm` | `HISTORICAL_EXPERIMENT` | no |
| `stackmate-spring-boot-starter-local` | `HISTORICAL_EXPERIMENT` | no |
| `stackmate-sample` | `SAMPLE` | no |
| `stackmate-e2e` | `INTERNAL_TEST` | no |
| `stackmate-startup-baseline` | `BENCHMARK` | no |
| `stackmate-benchmark` | `BENCHMARK` | no |
| `research/model-alignment` | `RESEARCH` | no |

## Required dependency direction

```text
stackmate-core
  <- stackmate-spring-boot-autoconfigure
       <- stackmate-spring-boot-starter
```

`stackmate-core` remains JDK-only. The deterministic auto-configuration may use
only core, required Spring/Servlet APIs and SLF4J. Inference modules may depend
on public product modules for historical tests; no dependency may flow back
into an allowlisted artifact.

## Artifact denials

Public JARs must contain no inference class, prompt, model metadata, Python,
dataset, report, native binary, GGUF, SafeTensors, adapter, benchmark corpus,
credential or machine-local path. Published POMs must contain no Jlama,
inference-module, Spring AI or remote-provider dependency.

The source and release-policy snapshot implements this boundary: deterministic
auto-configuration no longer exposes the historical inference surface and the
machine-readable allowlist contains exactly the four public coordinates. The
final RC JAR, POM, sources, Javadoc and SBOM audit passed during local assembly;
the bundle audit reports `PASS` with no forbidden entry.

No release coordinate is authorized for remote publication by this document.
