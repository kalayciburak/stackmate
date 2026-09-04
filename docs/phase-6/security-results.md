# Phase 6 security results

## Implemented controls

- Mandatory primary sanitizer with built-in secret/PII rules.
- Additional user redactions are bounded literal values only; regex input is
  not accepted.
- Bounded throwable parsing, snapshot sizes, analyzer contributions,
  deduplication entries and export retention.
- Strict rejection of unknown `stackmate.*` configuration.
- Fail-open custom analyzers and internal diagnostics, preserving the original
  application failure.
- No remote/cloud/model dependency or runtime path in public artifacts.
- CI actions are pinned to commit SHAs. CodeQL, dependency review, Dependabot
  and a release-preflight workflow are present.

## Final RC dependency scan

The public `0.1.0-rc.1` CycloneDX SBOMs were scanned with OSV Scanner. The
scanner exited successfully and reported zero known vulnerability groups.
The scanner was the official OSV-Scanner `2.5.1` Windows binary with verified
SHA-256 `25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.

| Artifact | Packages scanned | Known vulnerability groups |
|---|---:|---:|
| `stackmate-core` | 0 | 0 |
| `stackmate-spring-boot-autoconfigure` | 18 | 0 |
| `stackmate-spring-boot-starter` | 14 | 0 |

```text
OSV exit code               0
CodeQL remote run           UNVERIFIED
Dependency review run       UNVERIFIED
Dependabot remote state     UNVERIFIED
Release preflight run       UNVERIFIED
```

Zero findings means no vulnerability was identified in the scanned SBOM inputs
by that scanner snapshot; it is not a guarantee that dependencies are free of
unknown vulnerabilities.

The JSON scan report is stored outside Git with SHA-256
`54345d5194d238b6017d0f3181f392109ce35023cc6b4bdfa8c134943dcb578e`.

## Runtime redaction evidence and known boundary

In the RC `/failure` probe, the diagnostic block rendered by StackMate contained
zero occurrences of the synthetic raw secret and rendered the password value as
`password=[REDACTED]`. This verifies the mandatory sanitizer and the absence of
an unsafe raw fallback in StackMate's own rendering path. The same rule applies
to StackMate-controlled export: only sanitized incident data may be rendered or
published by that path.

StackMate then rethrew the original exception to preserve application behavior.
Tomcat independently logged that original exception, including the synthetic
raw secret. StackMate does not intercept or sanitize framework/application log
events and is not a global log-security boundary. Applications must configure
their logging, exception messages and observability pipeline to prevent secrets
from reaching those independent sinks. This is a known limitation, not a
sanitizer failure inside StackMate's rendering/export boundary.

The Maven signing profile exists, but no signing was performed and no private
key, passphrase or signing credential was requested.
