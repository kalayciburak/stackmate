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

The rc2 workflow is implemented locally to run the following scope. No hosted
runner has executed it yet.

| Hosted platform | Full clean verify | Public install | Consumer | API/bundle/evidence | Extra gates | Result |
|---|---|---|---|---|---|---|
| Linux / Ubuntu | Configured locally | Configured locally | Configured locally | Configured locally | OSV 2.5.1, bounded performance smoke | `UNVERIFIED` |
| Windows | Configured locally | Configured locally | Configured locally | Configured locally | None platform-specific | `UNVERIFIED` |
| macOS | Configured locally | Configured locally | Configured locally | Configured locally | None platform-specific | `UNVERIFIED` |

“Configured locally” in this table is not remote execution evidence. Each
platform must pass against the same final rc2 commit before a cross-platform
qualification claim is made.

## Main update after remote execution

For each platform, main must add the exact commit, run/job URL, runner image,
Java and Maven environment, root and consumer counts, API SHA-256, bundle
manifest SHA-256, evidence artifact name/checksum/expiry and final conclusion.
Linux must additionally record OSV and performance-smoke conclusions.

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
