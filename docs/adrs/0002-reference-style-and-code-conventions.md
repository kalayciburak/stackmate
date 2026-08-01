# ADR 0002: Reference style and code conventions

- Status: Accepted
- Date: 2026-08-01

## Context

The public `kalayciburak/auth-service` repository was inspected at commit
`6e03087dfae923ae1940361486fea88ae509fc09` as a readability and naming reference,
not as an architectural template. A semantic `jbcontext` lookup was attempted first but the service
returned HTTP 404, so representative source files were then inspected directly.

## Adopted conventions

- Keep framework entry points thin and delegate to intention-revealing operations. `AuthController`
  demonstrates direct request validation and service delegation.
- Split orchestration into short verbs with explicit names. `AuthService` uses focused operations such as
  `authenticateUser`, `generateAuthTokens`, and `validateRefreshToken`.
- Prefer immutable Java records for data carriers. `RegisterRequest` combines a record with field-level
  validation constraints.
- Isolate coherent validation rules when they are reusable. `UserValidator` uses small named guards rather
  than embedding every check in an orchestration method.
- Prefer clear, specific error names and centralized framework error mapping where StackMate itself owns the
  boundary.

## Intentionally rejected conventions

- **Lombok:** the reference project uses constructor, accessor, and builder generation. StackMate uses
  explicit constructors/factories and records so its public library API has no annotation-generated behavior.
- **Horizontal application layering:** the reference repository uses broad controller/service/repository/model
  packages and application-domain coupling. StackMate uses package-by-capability and separates its JDK-only
  core from Spring and inference adapters.
- **Persistence and authentication coupling:** StackMate does not adopt the reference project's JPA entity,
  eager relationship, repository, JWT, or Spring Security dependencies.
- **Raw generic failures:** a raw `RuntimeException` in the reference login flow and a stale exception Javadoc
  demonstrate why StackMate will use explicit failure contracts and keep documentation aligned with behavior.
- **Framework types in core:** Spring response DTOs, security context, repositories, and application context
  never cross into `stackmate-core`.

## Consequences

StackMate adopts the reference project's readable naming and small-method bias while deliberately rejecting
its Lombok use, application layering, authentication domain, database coupling, and framework-heavy runtime.

## Inspected evidence

- `src/main/java/com/kalayciburak/authservice/controller/AuthController.java`
- `src/main/java/com/kalayciburak/authservice/service/AuthService.java`
- `src/main/java/com/kalayciburak/authservice/model/dto/request/RegisterRequest.java`
- `src/main/java/com/kalayciburak/authservice/service/validator/UserValidator.java`
- `src/main/java/com/kalayciburak/authservice/model/entity/User.java`
- `src/main/java/com/kalayciburak/authservice/security/authorization/UserAuthorizationHelper.java`
- `pom.xml`
- `LICENSE.txt`
