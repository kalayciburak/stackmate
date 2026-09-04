# Support

StackMate `0.1.0-rc.1` is a public alpha with best-effort community support. There is no production support, response
time, remediation time, compatibility guarantee, or SLA.

## Before asking for help

Review the [README](README.md), [configuration reference](docs/configuration.md),
[compatibility matrix](docs/compatibility.md), and [troubleshooting guide](docs/troubleshooting.md). Confirm that the
application uses Java 21, Spring Boot 4.1.0, Spring Framework 7.0.8, and Servlet/Spring MVC.

## General questions and defects

Use the repository's GitHub Issues page for reproducible, non-sensitive usage questions and defects. Include:

- StackMate version or commit;
- operating system and Java vendor/version;
- Spring Boot and Spring Framework versions;
- relevant non-secret `stackmate.*` configuration;
- a minimal synthetic reproduction;
- expected and observed behavior;
- whether `stackmate.enabled=false` changes the result.

Do not present success on an unverified platform as a support claim. The currently verified local baseline is Windows
11 with Amazon Corretto 21.0.12.1, Spring Boot 4.1.0, Spring Framework 7.0.8, and Spring MVC.

## Sensitive reports

Follow [SECURITY.md](SECURITY.md) for vulnerabilities. Do not include secrets, tokens, exploit details, personal or
customer data, proprietary source, production logs, machine-local paths, model files, or unreviewed incident bundles
in a public issue. The repository documents no email or other secret support channel.

## Out of scope for alpha support

- production deployment and incident-response operations;
- Spring Boot 3, WebFlux, and GraalVM native image;
- Linux, macOS, containers, or other Java distributions as verified platforms;
- LLM, inference, Jlama, llama.cpp, models, local starters, adapters, and research experiments;
- application-specific debugging that requires private source or production data.

Historical research modules may remain in the repository, but they are not public-alpha product support surfaces.
