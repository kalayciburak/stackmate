# Phase 6.1 pre-push baseline

Audit date: 2026-09-05

This document preserves the entry-state audit before rc2 implementation. It
separates evidence already reproduced locally from the workflow and attachment
gaps found at that checkpoint. Current implementation status is tracked in
`progress.md`; the gaps below are historical baseline findings.

## Repository checkpoint

```text
Branch                         phase-6-public-alpha-hardening
HEAD                           248476f68ed3a3aa49e9fa1cba33d68a746a5469
Tag at HEAD                    v0.1.0-rc.1
Worktree at audit entry        clean
Origin                         https://github.com/kalayciburak/stackmate.git
Repository visibility          private
Authenticated GitHub account   kalayciburak
Remote default branch          phase-5-4-1-memory-attribution-and-resume
Default branch relationship    ancestor of this checkpoint
Remote qualification branch   absent
Remote v0.1.0-rc.1 tag         absent
Remote write                   NOT PERFORMED
```

The tag and RC are local only. No GitHub Actions result, uploaded artifact,
publication, signing, merge, branch push or tag push is claimed here.

## Locally reproduced evidence

The following results were reproduced from a clean tree in the current audit
turn before this documentation set was created:

| Gate | Result |
|---|---|
| Full clean verification | `PASS`, 12/12 reactor modules |
| Maven tests | 543 Surefire + 5 Failsafe; 0 failures, errors or skips |
| Public-release clean install | `PASS` |
| Local RC bundle | 24 files |
| Bundle manifest SHA-256 | `108dbbb31f207fa1db68355ae536c63572468293dddfc79ffcc3a0557447b3da` |
| `SHA256SUMS` SHA-256 | `286b461c4c35734a306dfbb1341f1ff5a9f360d225f0e24198edd7108ef8a065` |
| Public API | 73 types |
| Public API signature SHA-256 | `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7` |
| External consumer | 6 contract tests + 1 packaged-process test, `PASS` |

These are local Windows results. They do not establish Linux, macOS or remote
provider qualification.

## Audited entry-state gaps

- The qualification branch and rc1 tag have not been pushed, so every remote
  workflow result is `UNVERIFIED`.
- CI performs full clean verification on Ubuntu and Windows, but macOS runs a
  `-DskipTests` verification smoke only.
- CI does not retain qualification artifacts.
- Release preflight is Windows-only, has a broad `v*` push trigger and neither
  uploads its bundle nor publishes anything.
- CodeQL builds with `-DskipTests`.
- The CodeQL action pin is an annotated-tag object SHA rather than the target
  commit SHA.
- The local rc1 bundle deliberately omits sources and Javadoc archives for the
  dependency-only starter. Central's current publication requirements require
  sources and Javadoc for every non-POM JAR, so rc1 is not Central-ready.
- The packaged inference HTTP/child-process lifecycle coverage removed from the
  normal Phase 6 Failsafe run has no equivalent packaged migration. See
  `test-migration-ledger.md`.

## Adopted rc2 qualification plan at entry

Any repository change after the frozen local rc1 checkpoint produces rc2; rc1
will not be moved or reused. The adopted rc2 plan is to:

- remove the release preflight `v*` push trigger;
- pin official actions to verified commit SHAs, including the CodeQL release's
  target commit;
- enable tests in the CodeQL build;
- run full clean qualification plus public install, external consumer, public
  API and bundle checks across Ubuntu, Windows and macOS;
- upload bounded-retention qualification artifacts;
- generate placeholder sources and Javadoc JARs for the dependency-only
  starter so every public non-POM JAR satisfies Central's attachment rule.

This baseline does not retroactively mark the planned changes as implemented;
their later local status is recorded in `progress.md` and `workflow-audit.md`.

## Remote-run placeholders

The main Phase 6.1 status/progress document must be updated after an authorized
push with immutable run links and conclusions for:

- `TODO`: Ubuntu full qualification, consumer, artifact, API and bundle result.
- `TODO`: Windows full qualification, consumer, artifact, API and bundle result.
- `TODO`: macOS full qualification, consumer, artifact, API and bundle result.
- `TODO`: CodeQL result with tests enabled.
- `TODO`: dependency-review result for the pull request.
- `TODO`: release-preflight artifact names, checksums and retention expiry.

Until those entries are backed by observed remote runs, their status remains
`UNVERIFIED`.
