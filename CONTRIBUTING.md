# Contributing to StackMate

Thank you for helping improve StackMate. The project is currently an unreleased `0.1.0-SNAPSHOT`; behavior and APIs
may change while the initial design is validated.

## Development setup

- Use JDK 21. The build enforces Java 21 specifically.
- Use the checked-in Maven Wrapper, which downloads Apache Maven 3.9.16. A system Maven installation is not needed.
- Do not add model weights, generated benchmark reports, local paths, credentials, API keys, incident payloads, or
  other sensitive data to the repository.

Run the complete quality gate from the repository root:

```bash
chmod +x ./mvnw # only needed if the checkout did not preserve the executable bit
./mvnw --batch-mode --no-transfer-progress clean verify
```

On Windows PowerShell, use the Windows wrapper:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

`verify` runs compilation, unit and integration-test phases, the configured coverage checks, and Spotless checks.
Normal verification does not require a model, an API key, or network inference. Maven may access configured artifact
repositories to resolve the wrapper, plugins, and dependencies.

For a focused module test during development, include required reactor dependencies:

```bash
./mvnw --batch-mode --no-transfer-progress -pl stackmate-core -am test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl stackmate-core -am test
```

Local-model evaluation is optional, requires a user-supplied model outside the repository, and is deliberately not a
normal CI job. Follow [the benchmark methodology](docs/benchmark.md), label machine-specific results accurately, and
never present a local observation as a production-readiness claim.

## Changes and pull requests

1. Keep changes focused and explain the behavior and trade-offs in the pull request.
2. Add or update tests for behavior changes. Cover failure, boundary, concurrency, and privacy cases where relevant.
3. Preserve deterministic, model-free fallback behavior. Tests must not download weights or call a remote inference
   service.
4. Keep outputs bounded and sanitized. Use synthetic incident data; never place real secrets, tokens, personal data,
   or customer stack traces in tests, fixtures, logs, screenshots, or reports.
5. Run the complete `clean verify` command for your operating system before requesting review.
6. Update user-facing documentation and `CHANGELOG.md` when behavior or compatibility changes.

Do not commit build output, generated coverage or benchmark reports, model files, IDE state, or credentials. Security
issues should be reported privately as described in [SECURITY.md](SECURITY.md), not through a public issue.

## Style and design expectations

- Follow the existing Java style enforced by Spotless; use `spotless:apply` only as an intentional local action and
  review its diff before committing.
- Keep the deterministic core independent of Spring and inference runtimes.
- Treat captured exception text and model output as untrusted input.
- Avoid unsupported accuracy, performance, compatibility, or production-readiness claims.

## License

By submitting a contribution, you agree that it is licensed under the repository's
[Apache License 2.0](LICENSE). You must have the right to contribute the submitted material and must preserve required
third-party notices and attribution.
