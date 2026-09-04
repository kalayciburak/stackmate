# Compatibility

This matrix describes `0.1.0-rc.2` public-alpha qualification. "Verified" means exercised in the stated environment;
it is not a production-support or SLA statement. Hosted results remain unverified until the draft-PR jobs finish.

| Surface | Status | Evidence or boundary |
| --- | --- | --- |
| Windows 11 | Locally verified | Current local release environment |
| Amazon Corretto Java 21.0.12.1 | Locally verified | Current local release environment |
| Java 21 language/runtime target | Supported alpha baseline | Build and public contract require Java 21 |
| Spring Boot 4.1.0 | Locally verified | Current project and consumer baseline |
| Spring Framework 7.0.8 | Locally verified | Version managed by the verified Boot baseline |
| Servlet / Spring MVC | Supported alpha baseline | Implemented capture adapter |
| Public-release install | Locally verified | RC2 main, sources, Javadoc and SBOM artifacts built and installed |
| External consumer RC | Locally verified | 6/6 contract checks plus 1/1 packaged-process check |
| Full reactor | Locally verified | 543 Surefire plus 5 Failsafe; zero failures, errors or skips |
| Linux | Unverified | Full hosted qualification is configured but has not run |
| macOS | Unverified | Full hosted qualification is configured but has not run |
| Containers | Unverified | No container compatibility claim |
| Other Java 21 distributions/patches | Unverified | May work, but not part of current evidence |
| Spring Boot 4.1.x other than 4.1.0 | Unverified | Do not infer patch-line compatibility from coordinates |
| Spring Boot 3.x | Unsupported | Outside the public-alpha contract |
| WebFlux / reactive applications | Unsupported | Auto-configuration is Servlet/Spring MVC only |
| GraalVM native image | Unsupported | No native-image configuration or verification |
| Production deployment | Unsupported for this alpha | Production profiles are blocked unless explicitly overridden |

The Maven Wrapper targets Maven 3.9.x for building the checkout. Consumers ordinarily use their application's
build tool, but only the Maven dependency declaration is documented for this RC. RC1 is a frozen local-only
checkpoint and is not the active qualification candidate.

Report a reproducible compatibility problem with synthetic data through [SUPPORT.md](../SUPPORT.md). Do not attach
unredacted production logs or incident bundles.
