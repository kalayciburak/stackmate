# Third-party notices

StackMate source and public artifacts are licensed under the Apache License 2.0; see [LICENSE](LICENSE) and
[NOTICE](NOTICE).

This notice is limited to the `0.1.0-rc.1` public artifact chain inspected in the project POMs:

```text
stackmate-core
  <- stackmate-spring-boot-autoconfigure
       <- stackmate-spring-boot-starter
```

## Public dependency families

| Dependency | Version managed for this RC | Use | Declared license |
| --- | --- | --- | --- |
| Spring Boot auto-configuration | 4.1.0 | Auto-configuration and property binding | Apache License 2.0 |
| Spring Framework Web MVC and transaction APIs | 7.0.8 | Servlet MVC integration and optional transaction evidence | Apache License 2.0 |
| Jakarta Servlet API | 6.1.0 | Optional servlet integration API | EPL 2.0 or GPL 2 with Classpath Exception |
| SLF4J API | 2.0.18 | Delivery through the host application's logging facade | MIT License |
| Spring Boot configuration processor | 4.1.0 | Optional build-time configuration metadata generation | Apache License 2.0 |

`stackmate-core` has no third-party runtime dependency. The starter adds only StackMate auto-configuration; the
consumer application's Spring Boot dependency management supplies the framework versions.

The release process generates an SBOM for the resolved public artifacts. That generated inventory, public POMs,
and upstream license files are authoritative for the exact transitive graph of a built RC bundle.

## Excluded repository dependencies and artifacts

Inference modules, Jlama, Jackson-based inference IPC, models, GGUF/SafeTensors files, native runtimes, research
Python environments, sample/E2E dependencies, benchmark dependencies, container images, and test/build tools are
not part of the public release artifacts and are not redistributed by StackMate `0.1.0-rc.1`.

No model weights are included. Historical model research and provenance notes are retained separately in
[docs/research/local-llm-findings.md](docs/research/local-llm-findings.md) and older research records; they do not
grant model, training-data, input, or output rights.

## Publication status

The public-release install passed locally, but no artifact has been published to Maven Central, no remote release
has been created, and no GPG signing has been performed. CI publication and signing remain preflight-only.
