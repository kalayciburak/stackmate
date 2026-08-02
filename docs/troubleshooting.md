# Troubleshooting

## The wrapper says `JAVA_HOME` is invalid

StackMate requires Java 21 exactly. Point `JAVA_HOME` at the JDK directory, not its `bin` directory, and verify both
commands use the same installation:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
```

```bash
export JAVA_HOME=/path/to/jdk-21
"$JAVA_HOME/bin/java" -version
./mvnw -version
```

The Maven Enforcer rule rejects Java 17, Java 22+, and Maven outside the 3.9.x line.

## No StackMate diagnosis appears

Check these conditions:

1. `stackmate.enabled` and `stackmate.capture.mvc-unhandled` are not `false`.
2. The application is a servlet/Spring MVC application, not WebFlux.
3. The exception escaped the servlet filter chain. An exception fully handled by `@ExceptionHandler`, a Spring MVC
   resolver, or application code is deliberately invisible to the current capture mechanism.
4. No active profile matches `stackmate.safety.production-profiles` while
   `stackmate.safety.allow-in-production=false`.
5. `stackmate.console.enabled` is not `false` and the `STACKMATE` logger is not filtered out.

StackMate does not intercept arbitrary logger calls and does not claim visibility into every exception an
application logs.

## HTTP behavior changed

The StackMate filter does not write a response, choose a status, or retain the exception. It invokes analysis in a
fail-open boundary and rethrows the original throwable. First disable StackMate with `stackmate.enabled=false` to
compare behavior. If the response changes only when enabled, keep a minimal reproduction and report it privately or
as a defect; response mutation violates the adapter contract.

## The application log still contains a secret

StackMate sanitizes only StackMate-controlled console output, model input/output, and exports. It intentionally does
not suppress or replace the application's original stack trace. If application code embeds a credential in an
exception message, the normal logger can still expose it. Fix the application logging source and rotate the exposed
credential; do not rely on StackMate as a log-appender sanitizer.

## Local inference says the model is unavailable

The model must already exist locally. Resolution order is:

1. `stackmate.inference.model-path`
2. `STACKMATE_MODEL_PATH`
3. `~/.stackmate/models/Qwen2.5-0.5B-Instruct`

The Jlama adapter currently accepts a Qwen2-compatible SafeTensors directory containing bounded regular
`config.json`, `tokenizer.json`, `tokenizer_config.json`, and `.safetensors` files. It rejects symbolic links,
missing metadata, unsupported model types, malformed SafeTensors headers, and oversized aggregate weights. GGUF,
ONNX, ZIP, and a path to one weight file are not accepted by this adapter.

StackMate never downloads or repairs a model directory. Confirm provenance and SHA-256 yourself before use.

Phase 3 GGUF files are not accepted by the retained Jlama adapter. They were used
only by an explicit Benchmark V2 llama.cpp lab and both candidates failed product
readiness. There is no automatic model/runtime downloader, `AUTO` model profile,
production llama.cpp worker, or provisioning cache to enable.

Phase 4 GGUF files are also isolated research inputs and are not accepted by the
product adapter. Three eligible sub-2B candidates were screened: SmolLM2-360M was
not useful, Qwen3-0.6B missed the holdout check-selection gate, and
Qwen2.5-1.5B missed validity and selection gates. The Qwen3.5-2B control passed the
English screen but its official 2,274,069,824 total parameters exceed the hard cap.
There is no Phase 4 winner or supported GGUF configuration to point at the starter.

Phase 5 removed the parameter cap and qualified the pinned Qwen3.5-2B Q4_K_M control
against 100 cases. Although structural output and ID-selection targets passed, the
model failed strict unknown-case restraint and manual semantic safety. There is still
no supported llama.cpp worker, runtime path property, or GGUF model path for the
starter. Do not point `stackmate.inference.model-path` at the Phase 5 GGUF; that
property belongs to the retained experimental Jlama SafeTensors adapter.

To reproduce the Phase 3 lab, first review
`docs/phase-3/model-runtime-research.md`, provision the exact ignored files yourself,
and run the explicit `LlamaCppScreeningLab` test with its documented system
properties. Normal `clean verify` deliberately does not execute native inference or
download an artifact.

The Phase 4 tournament requires the exact ignored artifacts and hashes recorded in
`docs/phase-4/candidate-research.md`. Run it only through the explicit lab command
documented there. It is excluded from normal verification, and the absence of a
finalist means there is no full-corpus or persistent-worker Phase 4 command.

## The first local explanation is slow

Model validation, child-JVM startup, weight loading, and the first inference are lazy by default. In the authoritative
Phase 2 explanation-only screening, Qwen2.5-0.5B/Jlama structured inference had a 17.1-18.8-second median and up to
27.8-second P95. Qwen2.5-Coder-0.5B had a 20.0-22.0-second median and its English worker peaked near 2.09 GiB working
set on the reference Windows machine. These are observations, not guarantees. The evaluated configurations are
`NOT_VIABLE`; increasing the timeout is not a recommended product fix. Deterministic output is already printed first.

`stackmate.inference.lazy-load=false` validates and initializes adapter metadata at context creation, but Jlama model
weights still load on first inference. Do not describe this setting as eager weight loading.

StackMate-generated runtime output is English only in Phase 4. Remove the pre-release `stackmate.language` property;
unknown StackMate properties are rejected. `README.tr.md` remains the complete Turkish usage guide. Phase 1-3
bilingual measurements are historical and do not describe current runtime-language support.

## The local child exits or times out

The parent starts the current Java 21 executable with required preview/vector flags and communicates over framed
stdio. It kills a timed-out worker, bounds restarts, records an in-memory failure, and opens the circuit breaker after
the configured threshold. Deterministic diagnosis remains active.

Enable `stackmate.console.debug-logging=true` for a bounded internal reason; prompts and raw invalid model output
are not logged. Failure text is sanitized and bounded before debug logging. Check child-process permissions, memory
pressure, model integrity, Java 21 availability, and timeout.

Executable fat-JAR/classpath discovery is a known experimental compatibility surface. If development-classpath
execution works but a custom packaging layout fails, include the packaging command and sanitized error type in a
report.

## Repeated failures do not run the model again

This is the default deduplication policy. Equivalent failures reuse the SHA-256 fingerprint for ten minutes and
reserve at most one inference. Configure `stackmate.deduplication.ttl`, `maximum-entries`, or `enabled` if a
development scenario needs different behavior. Disabling deduplication can substantially increase CPU, memory, and
log pressure during an exception storm.

## Export did not create a bundle

Export is disabled by default. Set `stackmate.export.enabled=true` and use a directory writable by the application.
The exporter intentionally fails rather than publishing non-atomically or following a symbolic-link component. It
also refuses a fingerprint target that is a file/link and refuses retention deletion when a recognized bundle
contains unknown or linked entries.

With debug logging enabled, the exception type is recorded without printing the unsafe path or payload. Verify that
the filesystem supports atomic moves within the configured directory. Do not point retention at a shared directory.

## Production-profile warning

If `prod` or `production` is active, all StackMate analysis is disabled by default and one warning is logged. Set
`stackmate.safety.allow-in-production=true` only after a privacy and capacity review. Profile matching is a safety
aid, not proof that an environment is or is not production.

## Startup failures are not analyzed

Startup-failure capture is not exposed as a configuration property in this MVP. Spring Boot's `FailureAnalyzer`
extension was investigated; a broad catch-all analyzer was deferred to avoid competing with Boot's more specific
built-in analyses and destabilizing the completed runtime path.

## Collecting safe diagnostic information for a report

Include the StackMate revision/version, Java vendor/version, Spring Boot version, operating system, relevant
non-secret `stackmate.*` settings, exception class names, and a minimal synthetic reproduction. Remove credentials,
tokens, PII, proprietary paths, and customer data. Never attach a model file or generated incident bundle unless its
contents and redistribution rights have been reviewed.
