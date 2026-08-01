# ADR 0005: Run local inference in a managed child JVM

- Status: Accepted
- Date: 2026-08-01

## Context

Jlama's documented runtime uses preview features and `jdk.incubator.vector`. Model loading also introduces large,
hardware-dependent heap/off-heap pressure and may optionally use native libraries. Requiring every consuming
Spring Boot application to accept those flags and risks would weaken the starter experience.

## Decision

Use one long-lived StackMate-managed Java 21 child JVM for local inference. Launch it with the exact required
preview/vector flags, keep one model warm, and use length-bounded framed messages over standard input/output.
Do not open an HTTP server or network socket.

The parent-side public inference API remains deployment-neutral. The child supports bounded health/load/generate/
shutdown operations. The parent owns timeouts, circuit breaking, output validation, restart limits, and graceful
termination.

## Considered option: in-process

In-process inference has lower process-start and packaging complexity, but it leaks preview/vector requirements,
model memory, GC pressure, and crash risk into the user's application. Those costs outweigh the simpler first call.

## Consequences

- Model load is not on the servlet request thread or application startup critical path by default.
- A child crash cannot directly replace application exception handling; deterministic diagnosis continues.
- Process startup, cross-platform classpath packaging, cancellation, shutdown, and console separation require
  explicit tests.
- The production worker uses no shell interpolation; paths and arguments are passed as discrete process arguments.
- Packaged executable-JAR discovery/extraction must be treated as a security-sensitive compatibility surface and
  may limit the experimental adapter until verified.
- During the prompt-v2 corpus run, the measured worker reached 2,790.19 MiB peak working set while the benchmark
  parent peaked at 182.17 MiB. Keeping that footprint outside the application heap validates the isolation rationale
  even though the 2,972.36 MiB combined footprint exceeds the initial memory target.

## Evidence

See `docs/model-selection.md`. Comparable upstream cold/warm/RSS measurements were not available, so the process
choice is based on isolation and runtime-flag evidence rather than fabricated performance numbers.
