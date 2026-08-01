# StackMate implementation progress

Last updated: 2026-08-01 (Europe/Istanbul)

This is the live, evidence-oriented implementation log. Target values are not reported as measurements.

## Phase 0 - Discovery and evidence

### Completed

- Inspected the workspace: it was empty and was not yet a Git repository.
- Verified the host toolchain:
  - Git 2.55.0.windows.3 is available.
  - The default `java` command resolves to Amazon Corretto 17.0.20.
  - Amazon Corretto 21.0.12 is installed at `C:\Users\Burak\.jdks\corretto-21.0.12`.
  - No system `mvn` command is installed, so the Maven Wrapper is required for all builds.
- Verified from the current official Spring Boot documentation that:
  - Spring Boot 4.1.0 is the latest stable 4.1.x release visible on 2026-08-01.
  - Spring Boot 4.1.1 is still listed as a snapshot.
  - Spring Boot 4.1.0 requires Java 17 or later and Spring Framework 7.0.8 or later.
  - StackMate will nevertheless compile and run on the explicitly required Java 21 baseline.
- Confirmed the Boot 4.1 auto-configuration registration contract:
  `@AutoConfiguration` plus
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Started primary-source research for the reference repository, JVM inference runtimes,
  small model candidates, Maven Wrapper version, and basic name/artifact collisions.
- Inspected `kalayciburak/auth-service` at commit
  `6e03087dfae923ae1940361486fea88ae509fc09` and recorded adopted/rejected style decisions in ADR 0002.
- Verified Apache Maven 3.9.16 as the current stable Maven 3.9.x release and Maven Wrapper Plugin 3.3.4
  as the current official wrapper plugin.
- Found an existing unrelated `stackmate-io/stackmate` infrastructure product and `docs.stackmate.io`.
  The required project name is retained, but it is not treated as a collision-free global brand.
- Found no public Maven Central artifact at `io.github.kalayciburak:stackmate` and no positively visible public
  repository at `github.com/kalayciburak/stackmate`; these negative checks do not reserve either name.
- Compared Jlama 0.8.4, llama3.java, and ONNX Runtime GenAI 0.15.0 from primary sources.
- Selected Jlama 0.8.4 as the single experimental adapter and a managed Java 21 child JVM using stdin/stdout IPC.
- Compared current small model candidates and selected Qwen2.5-0.5B-Instruct as the first Jlama-compatible
  benchmark candidate. No model was downloaded or run in Phase 0.
- Verified current stable build-plugin versions from official Maven metadata and recorded the Boot-managed
  JUnit Jupiter 6.0.3, AssertJ 3.27.7, and Testcontainers 2.0.5 versions.

### Commands and results

```text
Get-ChildItem -Force
Result: WORKSPACE_EMPTY

git status --short --branch
Result: fatal: not a git repository

git --version
Result: git version 2.55.0.windows.3

java -version
Result: openjdk version "17.0.20" (default PATH; not suitable for this build)

C:\Users\Burak\.jdks\corretto-21.0.12\bin\java.exe -version
Result: openjdk version "21.0.12" 2026-07-21 LTS

mvn -version
Result: command not found
```

### Initial decisions

- Build against Spring Boot 4.1.0 and Java 21.
- Keep `stackmate-core` independent of Spring, logging, JSON, servlet, and inference runtimes.
- Make deterministic evidence the only source of diagnostic strength.
- Prohibit runtime network access and automatic model downloads.
- Keep model weights user-supplied and outside all artifacts.
- Make the real inference adapter optional; all normal verification must work with a deterministic fake.
- Keep Jlama and all of its transitive dependencies outside the deterministic starter.
- Run inference in a managed child JVM so preview/vector flags and model memory do not leak into the application JVM.
- Keep inference status `EXPERIMENTAL` until an actual model passes the mandatory A/B/C benchmark gates.
- Pin the wrapper distribution to Apache Maven 3.9.16.
- Retain the mandated StackMate name while documenting the existing unrelated brand collision and avoiding
  claims of global uniqueness.

### Tests executed

None yet. Production implementation and Maven project bootstrap have not started.

### Remaining risks

- Jlama requires preview/vector runtime flags, and packaged executable-JAR worker discovery is not yet verified.
- A useful permissively licensed sub-1.5B model may still fail the quality gates.
- The currently selected shell Java is 17; every verification command must explicitly select Java 21.
- Real local-model benchmarks require an explicit user-supplied model file and may remain unexecuted if none is available.
- Runtime no-network behavior needs both architectural enforcement and verification.

### Next phase

Complete Phase 0 research and ADRs, then bootstrap the Maven reactor and run the first Java 21 wrapper build.

## Phase 1 - Repository bootstrap

### Completed

- Initialized a new Git repository on branch `main`; there was no existing user history to rewrite.
- Created the requested eight-module Maven reactor with one-way internal dependency management.
- Generated Maven Wrapper 3.3.4 scripts pinned to Apache Maven 3.9.16.
- Added Java 21 compiler release, compiler warnings, Maven/Java enforcer gates, dependency convergence,
  banned Lombok/Spring AI/LangChain4j dependencies, Surefire/Failsafe, JaCoCo, Spotless, and reproducible timestamp metadata.
- Added Apache License 2.0, `NOTICE`, initial third-party notices, model-weight ignores, and English/Turkish README entry points.
- Kept Jlama as a runtime dependency of `stackmate-inference-jvm`; it is optional from auto-configuration and is
  not a declared dependency of the deterministic starter.

### Commands and results

```text
Maven Wrapper Plugin 3.3.4 with -Dmaven=3.9.16
Result: BUILD SUCCESS; wrapper URL pinned to apache-maven-3.9.16-bin.zip

.\mvnw.cmd clean verify  (Java 21)
First result: BUILD FAILURE at Spotless because Markdown files contained an extra final blank line.

.\mvnw.cmd spotless:apply  (Java 21)
Result: BUILD SUCCESS; 13 Markdown files and reactor POM formatting normalized.

.\mvnw.cmd clean verify  (Java 21)
Result: BUILD SUCCESS; 9/9 reactor projects succeeded in 4.304 s.
```

The last duration is a build observation on this machine, not a product performance benchmark.

### Tests executed

No Java tests existed at the bootstrap milestone. Maven reported `No tests to run`; the successful build proves
the reactor/tooling baseline only. Empty-JAR warnings are expected temporarily and must disappear as implementation
lands in the next phases.

### Remaining risks

- Module dependency trees must be re-inspected after production classes and optional auto-configuration land.
- Coverage gates are configured for core but have not exercised any code yet.
- Starter artifacts are temporarily empty aggregators; their meaningful dependency boundaries are implemented next.

### Next phase

Implement the tested Spring-independent incident pipeline, then integrate it through a non-resolving servlet MVC adapter.

## Evidence sources

- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot custom auto-configuration: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- Spring Boot 4.1 release notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes
- Apache Maven downloads: https://maven.apache.org/download.cgi
- Maven Wrapper: https://maven.apache.org/tools/wrapper/index.html
- Sonatype namespace registration: https://central.sonatype.org/register/namespace/
- Existing unrelated Stackmate product: https://docs.stackmate.io/
