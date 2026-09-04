# ADR 0049: v0.1.0 public-alpha scope

- Status: Accepted
- Date: 2026-09-04

## Context

The deterministic core and Servlet/MVC capture path have repeatedly passed the
normal build and behavioral suites. Multiple local-model phases produced useful
research but did not qualify a safe, generalizable product model. Phase 5.5.3
also showed that its final binary corpus was equivalent to executable ontology
predicates.

Shipping inference code as a supported feature would therefore broaden the
release beyond the strongest evidence. Public users need a small starter with a
clear capture boundary, deterministic output and no model provisioning burden.

## Decision

The v0.1.0 public alpha supports Java 21, Spring Boot 4.1 and Spring MVC/Servlet
applications. It captures only exceptions escaping the documented filter
boundary, performs bounded deterministic evidence analysis and prints sanitized
English console diagnostics without changing application responses.

Local inference, remote inference, model/runtime provisioning, WebFlux, logging
capture, startup capture, service AOP, dashboards, IDE/agent integration and
automatic fixes are outside v0.1.0. Historical research remains in the
repository but outside publishable artifacts and primary quickstarts.

## Consequences

- The public starter has no inference transitive, prompt, model property or
  model runtime.
- Release documentation leads with deterministic behavior and explicit limits.
- Pre-release API/configuration cleanup may remove unsupported inference-facing
  surfaces.
- Research can continue after v0.1.0 only under a separately authorized phase.
- This decision does not claim that every real-world incident is solved.
