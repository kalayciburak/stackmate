# Phase 7 publication authorization

Status: `NOT_AUTHORIZED`.

No evidence in Phase 7 authorizes any of the following:

- creating or pushing a final tag;
- creating a GitHub release;
- signing artifacts;
- deploying to Maven Central or validating a Central publication;
- changing repository visibility;
- deleting or force-updating retained branches or tags; or
- publishing an article or announcement pack.

RC2 and the qualified RC3 annotated tags remain local and unpushed. Gate G is
`BLOCKED_USER_DOGFOOD`, Gates H-L are `NOT_RUN`, and `KEEP_ENABLED` is unanswered. Publication requires a later,
explicit user authorization after all prerequisite gates pass; readiness evidence alone is not authorization.

Until then, all release work remains dry-run or evidence-only as described in
[the release process](../release-process.md).
