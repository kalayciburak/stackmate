# Phase 6.1 pre-push progress

Audit date: 2026-09-05

Status: local rc2 qualification is complete. Two failed hosted run sets are
preserved; final-commit remote gates remain `PENDING` / `UNVERIFIED`.

## Release-candidate boundary

- `v0.1.0-rc.1` is frozen at
  `248476f68ed3a3aa49e9fa1cba33d68a746a5469` and is absent from the remote.
- Workflow, release metadata and Central attachment changes require
  `0.1.0-rc.2`. The rc1 tag will not be moved, reused or pushed.
- The working version is `0.1.0-rc.2`. No final rc2 commit or tag identity is
  claimed before the authorized remote checkpoint is created.

## Local rc2 evidence

| Gate | Result | Evidence summary |
|---|---|---|
| Full clean verification | `PASS` | Root: 543 Surefire + 5 Failsafe; separate consumer: 6 Surefire + 1 Failsafe; 0 failures, errors or skips |
| Public release build/install | `PASS` | Parent, core, auto-configuration and starter qualified locally |
| Starter attachments | `PASS` | Dependency-only starter produced README-containing placeholder sources and Javadoc JARs |
| Public API | `PASS` | 73 types; SHA-256 `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7` |
| Qualification evidence collector | `PASS` | Combined 549 Surefire + 6 Failsafe; 0 failures, errors or skips; platform/environment, dependency trees and main-JAR entry hashes produced |
| Local file repository | `PASS` | Deploy and clean-repository consume passed; 13 required payloads and 26 MD5/SHA1 checksum files verified |
| Local reproducibility | `PASS` | Two clean builds; 23 compared payload files; zero differences; both manifests `bb35cc3becdd7bfdbcbda43756f652fc8f8809b73c9a332ea68259dd4d71e408` |
| Local rc2 bundle | `PASS` | 26 files; manifest `61a806934c50c8c89eb0d4b5f5f8eeab816ac9ce0a304c55b9d3adbb89eb3a1f`; SHA256SUMS `3dfbb4ce2048bc095cd715d08a9e159aa112c98cfec8859f12a6147b2480151e` |
| Workflow static validation | `PASS` | Official action pins verified; actionlint 1.7.12 passed all four workflows |
| Performance portability smoke | `PASS` | Windows packaged HTTP and fresh-JVM scripts passed after cross-platform path/process changes |

The local results above do not establish hosted Linux, Windows or macOS
qualification and do not authorize publication.

## Implemented workflow scope, remotely unverified

- Linux, Windows and macOS: full clean verify, public install, external
  consumer, API/bundle checks and qualification evidence.
- Ubuntu: OSV Scanner `2.5.1`, bounded performance smoke and two-clean-build
  reproducibility.
- Seven-day uploaded evidence retention.
- CodeQL construction with full tests.
- Manual-only `workflow_dispatch` release preflight, including local
  `file://` repository verification; no publish path.

Configuration inspection is not a remote result. All workflow run IDs, URLs,
commit conclusions, uploaded artifact identities and security conclusions are
still `PENDING` / `UNVERIFIED`.

## First hosted run

Draft PR [#1](https://github.com/kalayciburak/stackmate/pull/1) triggered the
first run set on commit `9a8f67357a60124fe7ef5e80c0f55a8d049ca7d7`.
CI run `33922327479`, CodeQL run `33922327492` and dependency-review run
`33922327570` failed for preserved, diagnosed reasons. No product-release
artifact failed: all failures occurred before artifact qualification, in
historical test portability or unavailable repository capability. Focused
repairs are locally verified and the final hosted rerun is pending.

## Second hosted run

Commit `b6821d68f99e130988324d375fc1a3c0f86725c9` produced CI run
`33923226270`, CodeQL run `33923226567` and dependency-review run
`33923226273`. Dependency review passed. CI exposed the remaining Windows
relative-path fixture, Linux scripted-process race and macOS canonical-path
result. CodeQL's tested build then exposed Linux TLS exception-chain variation
and a parity corpus byte-serialization defect. The narrow fixes preserve all
frozen cases and expected answers. Focused tests passed 77/77 and the full
reactor passed 543 Surefire + 5 Failsafe locally with zero skips.

## Publication boundary

No signing, Central credential use, Central deployment, GitHub Release, merge,
remote tag or package publication has occurred. Only the qualification branch
and draft PR have been pushed. Phase 6.1 evidence is qualification material
only.

## Main update after authorized remote runs

Main must replace the placeholders in `remote-runs.md` and then record here:

| Field | Value |
|---|---|
| Final rc2 commit SHA | `PENDING` |
| Remote branch/ref | `PENDING` |
| Three-OS qualification conclusion | `UNVERIFIED` |
| Ubuntu reproducibility conclusion | `UNVERIFIED` |
| CodeQL conclusion | `UNVERIFIED` |
| Dependency-review conclusion | `UNVERIFIED` |
| Release-preflight conclusion | `UNVERIFIED` |
| Final artifact names, checksums and expiry | `PENDING` |
| Final go/no-go conclusion | `PENDING` |
