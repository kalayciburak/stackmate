# Compatibility

This matrix describes `0.1.0-rc.1` public-alpha evidence. "Verified" means exercised in the current local release
work; it is not a production-support or SLA statement.

| Surface | Status | Evidence or boundary |
| --- | --- | --- |
| Windows 11 | Locally verified | Current local release environment |
| Amazon Corretto Java 21.0.12.1 | Locally verified | Current local release environment |
| Java 21 language/runtime target | Supported alpha baseline | Build and public contract require Java 21 |
| Spring Boot 4.1.0 | Locally verified | Current project and consumer baseline |
| Spring Framework 7.0.8 | Locally verified | Version managed by the verified Boot baseline |
| Servlet / Spring MVC | Supported alpha baseline | Implemented capture adapter |
| Public-release install | RC passed | Local public-release install completed successfully |
| External consumer RC | RC passed | 6/6 contract checks plus 1/1 packaged-process check |
| Full reactor | Pending | Do not infer a final release-gate pass from the narrower RC checks |
| Linux | Unverified | CI/preflight configuration is not an executed support claim |
| macOS | Unverified | No local or remote release evidence recorded for this RC |
| Containers | Unverified | No container compatibility claim |
| Other Java 21 distributions/patches | Unverified | May work, but not part of current evidence |
| Spring Boot 4.1.x other than 4.1.0 | Unverified | Do not infer patch-line compatibility from coordinates |
| Spring Boot 3.x | Unsupported | Outside the public-alpha contract |
| WebFlux / reactive applications | Unsupported | Auto-configuration is Servlet/Spring MVC only |
| GraalVM native image | Unsupported | No native-image configuration or verification |
| Production deployment | Unsupported for this alpha | Production profiles are blocked unless explicitly overridden |

The Maven Wrapper targets Maven 3.9.x for building the checkout. Consumers ordinarily use their application's
build tool, but only the Maven dependency declaration is documented for this RC.

Report a reproducible compatibility problem with synthetic data through [SUPPORT.md](../SUPPORT.md). Do not attach
unredacted production logs or incident bundles.
