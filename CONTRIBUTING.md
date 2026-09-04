# Contributing to StackMate

Thank you for contributing. StackMate `0.1.0-rc.1` is a public alpha; APIs and behavior may change before a stable
release.

## Product boundary

Public product work must preserve the deterministic release chain:

```text
stackmate-core
  <- stackmate-spring-boot-autoconfigure
       <- stackmate-spring-boot-starter
```

Do not introduce inference, LLM/model, downloader, cloud, telemetry, upload, sample, E2E, benchmark, or research
dependencies into these artifacts. `stackmate-core` must remain JDK-only. Public runtime output must remain bounded,
deterministic, and sanitized.

## Development setup

- Use JDK 21 and the checked-in Maven Wrapper (Maven 3.9.x).
- Keep model weights, native runtimes, generated reports, local paths, credentials, incident payloads, and private
  data outside Git.
- Use synthetic data in tests, fixtures, screenshots, and reports.

The full local quality gate is:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Windows PowerShell:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

These commands may resolve Maven dependencies from configured repositories; they do not require a model, API key,
or inference service. Running a command locally is not a compatibility or release-pass claim unless its environment
and result are recorded.

## Changes and pull requests

1. Keep the change focused and explain observable behavior and trade-offs.
2. Add or update tests when behavior changes, including failure, boundary, concurrency, and privacy cases.
3. Preserve original exception and HTTP behavior.
4. Keep all captured and rendered data bounded and sanitized.
5. Keep the 25-property public configuration contract deliberate; unknown properties must remain fail-fast.
6. Update English and Turkish README behavior together when user-facing behavior changes.
7. Update `CHANGELOG.md`, compatibility, security, and notices when their facts change.
8. Record exactly which checks ran and which remain pending; never promote a narrower check to a full release gate.

Do not commit build output, generated coverage/benchmark reports, model or adapter files, native binaries, IDE state,
credentials, or unreviewed incident exports.

## Style and API expectations

- Follow existing Java and Markdown style; review formatter output before committing it.
- Treat throwable, request, MDC, filesystem, and extension-provided text as untrusted.
- Prefer explicit bounds and fail-safe behavior.
- Do not claim unsupported accuracy, performance, platform compatibility, security, or production readiness.
- Public API changes during alpha still require rationale, compatibility notes, and consumer coverage.

Security-sensitive issues follow [SECURITY.md](SECURITY.md), not a public detailed report. General help follows
[SUPPORT.md](SUPPORT.md). By submitting a contribution, you agree that it is licensed under the repository's
[Apache License 2.0](LICENSE) and that you have the right to contribute it.
