# ADR 0001: Spring Boot 4.1 and Java 21

- Status: Accepted
- Date: 2026-08-01

## Context

StackMate is a Spring Boot library with a strict Spring Boot 4.1.x and Java 21 baseline.
The selected versions must be stable releases and must not introduce Spring Boot 3 compatibility code.

## Decision

Use Spring Boot 4.1.0, Spring Framework 7.0.8 as managed by that release, Maven, and
`maven.compiler.release=21`. Use Maven Wrapper with a current Maven 3.9.x distribution.

Spring Boot's official system-requirements page listed 4.1.0 as stable and 4.1.1 as a snapshot on
2026-08-01. It states that Boot 4.1.0 requires Java 17 or later and Spring Framework 7.0.8 or later.
StackMate deliberately chooses the stricter Java 21 product baseline.

## Consequences

- Consumers must run Java 21 or later.
- No Spring Boot 3 compatibility branches, reflection shims, or multi-release artifacts are provided.
- CI and local verification explicitly select Java 21 even when the host default is older.
- Boot 4 auto-configuration uses `@AutoConfiguration` and
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Evidence

- https://docs.spring.io/spring-boot/system-requirements.html
- https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- https://spring.io/blog/2026/06/10/spring-boot-4/
