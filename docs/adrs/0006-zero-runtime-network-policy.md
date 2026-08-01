# ADR 0006: Zero runtime network policy

- Status: Accepted
- Date: 2026-08-01

## Context

StackMate's precise product claim is zero external service dependency. Exception and model data must not
leave the machine, and the library must not silently introduce remote fallback behavior.

## Decision

Production runtime modules contain no cloud provider clients, HTTP model clients, telemetry exporters,
automatic model downloaders, or agent CLI integrations. The inference adapter reads only explicit local files.
The first implementation does not open a localhost model server; managed inference, if selected, uses local
standard input/output IPC.

## Consequences

- No API key configuration exists.
- Model acquisition is a separate explicit user action and never occurs at application startup.
- Build-time dependency resolution is not a runtime product network call and is documented separately.
- Network-call absence must be architecture-reviewed and exercised in tests where practical.
