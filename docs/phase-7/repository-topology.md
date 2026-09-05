# Phase 7 repository topology

Status: `PASS` for canonical-branch normalization and the observed protection configuration.

## Canonical branch

- `main` was fast-forwarded to `56dc1f8452da90f558807790c20d7cf1eac0fc00` without rewriting history.
- Local and remote `main` were observed equal at that revision.
- The repository default branch is `main`.
- The former default branch is retained at
  `phase-5-4-1-memory-attribution-and-resume@8d12ca3ee740a87bf438b5ba3b6726040bbbfc73`.
- Existing private branches and tags are retained. No force update or deletion was performed.

Draft PR #1 was closed as superseded rather than merged. The explanatory comment is `5551257535`.

## Hosted checks and protection

At canonical `main`, CI run `33961539960` and CodeQL run `33961539942` passed. The five required status contexts are:

1. `Full qualification (Linux)`
2. `Full qualification (Windows)`
3. `Full qualification (macOS)`
4. `Ubuntu reproducibility`
5. `Analyze Java`

Observed protection settings: strict status checks are enabled; force pushes and branch deletion are disabled;
`enforce_admins` is false; no approving-review requirement is configured.

## Current Phase 7 line

The current Phase 7 source is `8b40bac6dfb940324db638195766a67d5cdf7415`; PR #2 is draft. This records identity
only and is not an RC3 qualification claim.
