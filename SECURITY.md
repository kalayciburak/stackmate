# Security policy

## Supported versions

StackMate `0.1.0-rc.1` is a public alpha, not a production-supported release, security certification, or support
SLA.

| Version | Security status |
| --- | --- |
| `0.1.0-rc.1` source/RC | Best-effort fixes during alpha evaluation |
| Stable releases | None |

The RC has not been published to Maven Central and no remote release has been created.

## Reporting a vulnerability

If this repository's **Security** tab offers **Report a vulnerability**, use GitHub Private Vulnerability Reporting
and include the affected version/revision, impact, reproduction steps, and a minimal sanitized proof of concept.

If that GitHub feature is unavailable, this repository currently documents no alternative confidential address.
Open a public issue only to ask maintainers to enable or provide a private reporting route. Do not put vulnerability
details, exploit code, secrets, tokens, personal/customer data, proprietary code, or unredacted logs in that issue.

There is no guaranteed acknowledgement or remediation timeline. Give maintainers a reasonable opportunity to
confirm and coordinate a fix before public disclosure.

## In scope

- a reproducible bypass of mandatory sanitization or data-minimization boundaries;
- unexpected network, telemetry, download, or upload behavior in a public artifact;
- a way StackMate changes host HTTP/exception behavior;
- unsafe filesystem export or retention behavior;
- denial-of-service behavior that escapes documented bounds;
- dependency or configuration behavior that affects public-artifact confidentiality, integrity, or availability.

Historical model, inference, sample, benchmark, E2E, and research modules are outside the `0.1.0-rc.1` public
product boundary. A report about those modules should clearly identify itself as repository research rather than a
public starter vulnerability.

## Safe reporting data

Prefer a synthetic minimal reproduction. Treat all diagnostic output and incident bundles as potentially sensitive.
Do not send real credentials, model files, customer data, production logs, machine-local paths, or unreviewed export
bundles. If a credential was exposed, rotate or revoke it immediately.

StackMate sanitizes only StackMate-controlled output. In the external-consumer RC `/failure` observation, the
StackMate block contained no raw synthetic secret, but StackMate then rethrew the original exception and Tomcat
independently logged it with the raw synthetic secret. Framework and application logs remain outside the security
boundary. See [docs/privacy-and-threat-model.md](docs/privacy-and-threat-model.md).
