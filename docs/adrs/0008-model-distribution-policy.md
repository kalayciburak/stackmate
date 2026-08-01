# ADR 0008: User-supplied model distribution

- Status: Accepted
- Date: 2026-08-01

## Context

Model weights are large, license-sensitive, mutable supply-chain inputs. Bundling them would enlarge starter
artifacts and could create redistribution or attribution obligations.

## Decision

Do not commit or package model weights. The initial release accepts an explicit local path, an environment
variable, or a documented file under the user's `.stackmate/models` directory. No model is downloaded at
application startup. Model name, license, source, quantization, and checksum are documented only for models
actually evaluated.

## Consequences

- The deterministic starter has no inference runtime or model dependency.
- The local starter falls back safely when a model is absent or invalid.
- Any future installer must be an explicit command with license/source/size disclosure, SHA-256 verification,
  atomic writes, partial-file rejection, and safe-path enforcement.
