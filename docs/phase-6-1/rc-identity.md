# Phase 6.1 release-candidate identity

## Frozen rc1

| Field | Value |
|---|---|
| Tag | `v0.1.0-rc.1` |
| Commit | `248476f68ed3a3aa49e9fa1cba33d68a746a5469` |
| Remote presence | Absent |
| Disposition | Frozen local-only checkpoint; will not be moved, reused or pushed |

Workflow hardening, release metadata changes and Central-compatible starter
attachments were made after the rc1 checkpoint. Those changes require rc2 and
cannot be attached to rc1 without falsifying its immutable identity.

## Qualified rc2

| Field | Value |
|---|---|
| Maven version | `0.1.0-rc.2` |
| Final qualified source commit SHA | `357ca1418eb3786e861e4436fe55005c8c6456cd` |
| Local tag | `v0.1.0-rc.2`, annotated, points to the qualified source commit |
| Remote branch/ref | Qualified source commit pushed on `origin/phase-6-public-alpha-hardening`; evidence-only closure commit follows |
| Remote tag | Absent; tag push withheld by policy |
| Remote workflow identity | CI `33927462896`, CodeQL `33927462894`, dependency review `33927462908`: all `PASS` |
| Pre-push local bundle manifest | `61a806934c50c8c89eb0d4b5f5f8eeab816ac9ce0a304c55b9d3adbb89eb3a1f` |

The pre-push local bundle remains local evidence; the hosted bundle identities
are recorded separately because Windows and Unix line endings differ.

The frozen public API contains 73 types with signature SHA-256
`41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.

## Interim exact-checkout identity

Commit `6d6be9c0cf165d8bc53a62688c170b3a5d5af0de` passed every workflow,
and all six artifact names used that exact SHA. The embedded qualification
summaries nevertheless recorded PR merge SHA `977a7ab...` from `GITHUB_SHA`.
The collector is corrected and locally verified, so this commit is preserved
as a successful intermediate run rather than declared final rc2 identity.

## Qualified rc2 identity

The sixth run set bound checkout, artifact names and all three embedded
qualification summaries to
`357ca1418eb3786e861e4436fe55005c8c6456cd`. The local annotated tag was
created only after that green result. It was not pushed because tag publication
is optional and the manual default-branch release-preflight could not run
without a merge. RC1 remains immutable and local-only.

## Identity rule

All evidence is bound to one exact qualified source commit. The local tag target
was verified before creation; a future tag push would require a separate
authorization and preflight decision.

No tag push, merge, signing, Central upload, publication or GitHub Release is
authorized or claimed here.
