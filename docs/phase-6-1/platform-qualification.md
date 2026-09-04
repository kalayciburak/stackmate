# Phase 6.1 platform qualification

## Local evidence

| Platform | Scope | Result |
|---|---|---|
| Local Windows | Root full clean verify | `PASS`: 543 Surefire + 5 Failsafe; 0 failures, errors or skips |
| Local Windows | Separate clean consumer verify | `PASS`: 6 Surefire + 1 Failsafe; 0 failures, errors or skips |
| Local Windows | Public install and starter attachments | `PASS`: README-containing placeholder sources and Javadoc JARs produced and installed |
| Local Windows | Evidence collection | `PASS`: combined 549 Surefire + 6 Failsafe; platform/environment, runtime dependency trees and main-JAR entry hashes produced |
| Local Windows | Local `file://` repository deploy/consume | `PASS`: parent/core/auto-configuration/starter deployed; clean Maven repository consumer resolved from `stackmate-file` and passed 6 + 1 |

The final local file-repository run also recalculated and verified MD5 and SHA1
sidecars for every required payload. A short Windows portability smoke passed
for both packaged HTTP and fresh-JVM startup harnesses after removing their
Windows-only executable, CIM and hidden-window assumptions.

## Hosted platform matrix

The final exact-head matrix passed the following scope.

| Hosted platform | Full clean verify | Public install | Consumer | API/bundle/evidence | Extra gates | Result |
|---|---|---|---|---|---|---|
| Linux / Ubuntu 24.04 x64 | `PASS` | `PASS` | `PASS` | `PASS` | OSV 2.5.1 zero results; bounded performance smoke passed | `PASS` |
| Windows Server 2025 x64 | `PASS` | `PASS` | `PASS` | `PASS` | File-repository deploy/consume passed | `PASS` |
| macOS 26 arm64 | `PASS` | `PASS` | `PASS` | `PASS` | Full qualification, not smoke-only | `PASS` |

Every row is observed evidence from run `33927462896` at source commit
`357ca1418eb3786e861e4436fe55005c8c6456cd`.

## Recorded remote evidence

The exact commit, job URLs, Java/Maven environments, test counts, API and
bundle hashes, artifact digests/expiry, OSV and performance conclusions are
recorded in this file and `remote-runs.md`.

This qualification does not publish, sign, tag, merge or release artifacts.

## First hosted failure disposition

- Linux: one separator-dependent command assertion and one missing intermediate
  fixture directory.
- Windows: the hosted workspace and temporary directory were on different
  drives, making `Path.relativize` invalid.
- macOS: the two issues above plus `/var` being a system symlink caused a false
  rejection of every otherwise valid temporary model directory.

The repair checks the selected executable file name, creates the intended
normalization parent, constructs the relative-path fixture beneath module
`target`, and rejects the configured model directory itself plus required model
files when they are symlinks. Canonical root/file containment remains enforced.
The focused Windows suite passed 12/12 with no skips; hosted rerun is pending.

## Second hosted failure disposition

- Windows: the relative-path fixture still crossed the hosted `D:` workspace
  and `C:` temporary drive; the fixture now lives below module `target`.
- Linux: a scripted child emitted its one response and exited before the
  parent liveness check; the controlled script now serves until shutdown.
- macOS: the configured path was correctly accepted but returned `/var/...`
  instead of its canonical `/private/var/...`; accepted roots now return
  `toRealPath()` after the final-component symlink check.
- CodeQL/Linux: TLS-to-plain can end at `SSLException` without an underlying
  `SocketException`, and Git converted the frozen parity corpus's historical
  mixed line endings. Fixture-local root completion and exact blob-byte
  preservation address those two deterministic harness defects.

The second fix set passed the affected 77-test benchmark subset and the full
Windows reactor. No hosted success is claimed until the exact pushed commit
passes.

## Third hosted failure disposition

Run [33924591960](https://github.com/kalayciburak/stackmate/actions/runs/33924591960)
proved the complete Linux and Windows jobs. Linux passed in 4m53s, including
OSV, performance smoke and evidence upload. Windows passed in 8m40s, including
clean file-repository consumption and evidence upload. macOS reached
`FileSystemIncidentExporterTest`; four normal exports were rejected because
JUnit exposed the real temporary directory through the leading `/var` system
alias. The existing test class now canonicalizes only its injected temp root
before each case. Its deliberate user-created symlink rejection remains active,
and the focused class passed 6/6 locally.

## Fourth hosted behavior result

Run [33925474151](https://github.com/kalayciburak/stackmate/actions/runs/33925474151)
passed Linux, Windows, macOS and dependent Ubuntu reproducibility. Every OS
completed full clean verification, public install, consumer, API/bundle and
evidence collection; Linux additionally passed OSV/performance and Windows
passed file-repository deployment/clean consumption. The evidence commit is
the synthetic PR merge `8b521134...`, so this remains behavior proof rather
than the final exact-head platform qualification.

## Final platform qualification

Run [33927462896](https://github.com/kalayciburak/stackmate/actions/runs/33927462896)
passed the complete matrix with all summaries bound to exact source commit
`357ca1418eb3786e861e4436fe55005c8c6456cd`. Each platform reported
Temurin 21.0.12.1, Maven 3.9.16, 549 Surefire + 6 Failsafe including the
external consumer, and zero failures/errors/skips. This establishes only the
tested hosted environments, not every Linux, Windows or macOS version.

The bounded Ubuntu smoke retained product behavior and recorded HTTP success
P95 values of 1.98 ms without StackMate, 1.58 ms disabled and 1.25 ms enabled.
Unhandled-failure P95 values were 5.58 ms, 7.78 ms and 22.64 ms respectively.
Fresh-JVM startup medians were 2756 ms, 2727 ms and 2848 ms. These short hosted
measurements are regression smoke evidence, not a performance SLA.
