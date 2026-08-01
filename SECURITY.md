# Security policy

## Supported versions

StackMate has no released, production-supported version yet.

| Version | Status |
| --- | --- |
| `main` / `0.1.0-SNAPSHOT` | Receives best-effort security fixes during development |
| Published releases | None |

This status is not a security certification, support SLA, or production-readiness claim.

## Reporting a vulnerability

Use this repository's **Security** tab and choose **Report a vulnerability** to open a private GitHub Security
Advisory when private vulnerability reporting is enabled. Include the affected revision, impact, reproduction steps,
and a minimal sanitized proof of concept.

If private reporting is not enabled, ask the repository owner for a private reporting channel without including
vulnerability details. Do not open a public issue containing an exploit, secret, token, personal data, customer data,
or unredacted incident/stack-trace content.

Please allow maintainers time to confirm the report and coordinate a fix before public disclosure. There is currently
no guaranteed response or remediation timeline.

## Scope and handling guidance

Security reports may cover the deterministic diagnostic pipeline, sanitization and privacy boundaries, Spring Boot
integration, local child-process isolation, output validation, dependency risks, or a reproducible way StackMate
changes host-application behavior.

Keep the following out of reports and reproductions unless explicitly requested through a trusted private channel:

- real credentials, API keys, tokens, personal or customer data;
- proprietary application code, production logs, and unredacted stack traces;
- model weights or model files whose license or redistribution rights are unclear;
- generated machine-specific benchmark and coverage reports containing local paths or environment details.

StackMate is designed for local, bounded diagnostics, but no sanitizer can guarantee that arbitrary input is safe.
Treat all diagnostic output as potentially sensitive, review it before sharing, and use synthetic data whenever
possible. The project does not require remote inference or API keys; unexpected runtime network inference is a
security-relevant behavior worth reporting privately.

Model quality, inaccurate explanations, and unsupported production-readiness claims are not by themselves security
vulnerabilities. They may become security-relevant when they cause data disclosure, unsafe execution, boundary
bypass, or a reproducible integrity or availability impact.
