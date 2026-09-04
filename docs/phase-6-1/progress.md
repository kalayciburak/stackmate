# Phase 6.1 progress

Audit date: 2026-09-05

Status: Phase 6.1 remote qualification is complete for source commit
`357ca1418eb3786e861e4436fe55005c8c6456cd`.

## Release-candidate boundary

- `v0.1.0-rc.1` is frozen at
  `248476f68ed3a3aa49e9fa1cba33d68a746a5469` and is absent from the remote.
- Workflow, release metadata and Central attachment changes require
  `0.1.0-rc.2`. The rc1 tag will not be moved, reused or pushed.
- Version `0.1.0-rc.2` is qualified at source commit `357ca141...`; the local
  annotated tag was created only after the green exact-commit run.

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

At the pre-push checkpoint, configuration inspection was not treated as a
remote result; all remote fields were still `PENDING` / `UNVERIFIED`.

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

## Third hosted run

Commit `610600eb2fa4746e7027f9f920ac307cfe930d1f` produced CI run
`33924591960`, CodeQL run `33924592005` and dependency-review run
`33924591939`. Linux and Windows completed every configured job step; macOS
found that its normal JUnit temp directory traverses the `/var` system alias.
CodeQL passed the full tested build and produced SARIF after scanning 261/270
Java files, but lacked `actions: read` for post-analysis workflow metadata.
Dependency review passed again. The macOS fixture canonicalization passed 6/6
locally; CodeQL is updated to immutable official v4.37.9 with read-only
workflow metadata access. Final hosted rerun remains pending.

## Fourth hosted run

Head `e087961eb05c28d2e3fa667b43c6bdba5d5698be` produced successful CI
run `33925474151`, CodeQL run `33925474078` and dependency-review run
`33925474039`. All three OS jobs, Ubuntu reproducibility, CodeQL v4/SARIF and
dependency review passed. The downloaded artifacts revealed that standard PR
checkout recorded synthetic merge commit
`8b5211345d272c97d52d6193303c1b7a38ef7432`, not the exact branch head.
Workflows now explicitly check out and name evidence with the PR head SHA. A
new exact-head run is mandatory before final qualification.

## Fifth hosted run

Exact checkout head `6d6be9c0cf165d8bc53a62688c170b3a5d5af0de` passed CI
`33926362199`, CodeQL `33926362151` and dependency review `33926362200`.
All downloaded artifact names carried the head SHA, but qualification summaries
still recorded the PR merge SHA through `GITHUB_SHA`. Independent payload audit
passed test/API/dependency/JAR-entry/reproducibility/OSV/file-repository gates,
and all six CodeQL XSS results were test-fixture-only. The collector now uses
validated checkout HEAD; the final identity rerun remains pending.

## Final exact-head result

CI `33927462896`, CodeQL `33927462894` and dependency review `33927462908`
all passed with checkout, artifact names and embedded summaries bound to
`357ca1418eb3786e861e4436fe55005c8c6456cd`. The local annotated
`v0.1.0-rc.2` tag points to that commit and remains unpushed. Remote artifacts
were downloaded and audited outside Git. No publication, merge, signing or
remote tag occurred.

## Publication boundary

No signing, Central credential use, Central deployment, GitHub Release, merge,
remote tag or package publication has occurred. Only the qualification branch
and draft PR have been pushed. Phase 6.1 evidence is qualification material
only.

## Final remote summary

| Field | Value |
|---|---|
| Final rc2 source commit SHA | `357ca1418eb3786e861e4436fe55005c8c6456cd` |
| Remote branch/ref | `origin/phase-6-public-alpha-hardening` contained the qualified source commit; an evidence-only closing commit follows |
| Three-OS qualification conclusion | `PASS` |
| Ubuntu reproducibility conclusion | `PASS`: 23/23 identical |
| CodeQL conclusion | `PASS`: six test-fixture-only XSS paths triaged |
| Dependency-review conclusion | `PASS`: zero policy violations |
| Release-preflight conclusion | `NOT_RUN`: unavailable before default-branch merge; equivalent Windows PR gate passed |
| Final artifact names, checksums and expiry | Recorded in `remote-runs.md`; six artifacts, seven-day retention |
| Final go/no-go conclusion | `REMOTE_CI_QUALIFIED_RC_READY` |
