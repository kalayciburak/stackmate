# ADR 0009: Capture escaping MVC exceptions with a servlet filter

- Status: Accepted
- Date: 2026-08-01

## Context

An early ordered `HandlerExceptionResolver` can observe exceptions before `@ExceptionHandler`, but cannot know
whether a later resolver handles them. A late resolver sees only still-unresolved exceptions but misses exceptions
converted by standard MVC resolvers. Returning a non-null `ModelAndView` would take ownership of error handling,
which StackMate must not do.

## Decision

Use a servlet-only `OncePerRequestFilter` that observes `IOException`, `ServletException`, `RuntimeException`, and
`Error` escaping the dispatcher, snapshots them, and rethrows the exact same object. The filter never writes to the
response, never changes status, and skips error dispatches to avoid duplicate capture. Handler metadata is read
from standard `HandlerMapping` request attributes when present.

Deterministic analysis and console rendering happen before the original exception continues to the container;
optional model inference is submitted to a dedicated executor later. A recursion guard prevents StackMate failures
from re-entering capture.

## Visibility contract

The initial adapter observes exceptions that escape the servlet filter chain. It does not claim to capture:

- Exceptions fully handled by an application `@ExceptionHandler` or `@ControllerAdvice`.
- Standard MVC exceptions fully resolved before they escape the dispatcher.
- Exceptions logged by arbitrary background threads, schedulers, appenders, service AOP, WebFlux, or other
  non-servlet paths.
- Startup failures, which use Spring Boot's separate `FailureAnalyzer` extension when implemented.

Deterministic analyzers may still support exception types that reach StackMate through custom analyzers, exported
snapshots, benchmarks, or future official capture adapters.

## Consequences

- The exact original exception and normal container/Boot HTTP error handling are preserved.
- User-defined exception handlers remain authoritative and are not double-reported by the MVP filter.
- This narrower, honest visibility is preferred to claiming that every logged Spring exception is observable.
- Integration tests must prove same exception identity, unchanged HTTP status/body behavior, normal requests,
  handled exceptions, disabled mode, error-dispatch deduplication, and concurrent failures.

## Evidence

- Spring `OncePerRequestFilter` API:
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html
- Spring MVC `HandlerExceptionResolver` contract:
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/HandlerExceptionResolver.html
- Spring MVC `HandlerMapping` request attributes:
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/HandlerMapping.html
