# Phase 6.1 security qualification

## Supply-chain controls configured for rc2

| Control | Local configuration/evidence | Remote result |
|---|---|---|
| Immutable official-action pins | Checkout `11d5960a326750d5838078e36cf38b85af677262` (official v4.4.0); setup-java `cf277c60eb25467037889841efdb72551f06f6c3` (official v4.9.1); dependency review `2031cfc080254a8a887f58cffee85186f0e49e48` (official v4.9.0); CodeQL commit `6f5948dfacef28e207b48d0905cf90c03365536d`; upload-artifact `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (official v7.0.1) | `UNVERIFIED` |
| Correct CodeQL object | Corrected from the annotated tag object to commit `6f5948dfacef28e207b48d0905cf90c03365536d`, behind the current official v3 tag | `UNVERIFIED` |
| Tested CodeQL construction | Full clean verification is configured instead of a skipped-test build | `UNVERIFIED` |
| Dependency review | Standard pull-request review is configured | `PENDING`: no PR/run exists |
| Public SBOM scan | Ubuntu uses official OSV Scanner `2.5.1` Linux asset with expected SHA-256 `f9f25499a2c8cc367b3af45df2ea7eeca7fbccceab9c35079968f4b3652194be` | `UNVERIFIED` |
| Evidence retention | Qualification uploads are configured for seven days | `UNVERIFIED` |
| Release authority | Release preflight is manual-only and read-only; no publish or signing step exists | Static configuration only |

The four workflow files passed official `actionlint` 1.7.12 locally. Its
official Windows archive SHA-256 was
`6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9`.

The action-to-release mappings and OSV checksum are audit inputs. They do not
prove that GitHub downloaded or executed those objects successfully.

## Evidence collector

Local evidence collection passed and produced platform/environment metadata,
public runtime dependency trees, main-JAR entry hashes and reconciled test
counts: 549 Surefire + 6 Failsafe with 0 failures, errors or skips. This is
local qualification evidence, not a CodeQL, dependency-review or hosted OSV
result.

## Main update after remote execution

Main must record the exact final rc2 commit and immutable URLs for CodeQL,
dependency review and Ubuntu OSV execution. Include conclusions and any finding
IDs/severities, the observed OSV binary checksum, scanned SBOM identities, and
the evidence artifact checksum/expiry. Any unresolved finding requires an
explicit disposition before changing the overall result from `UNVERIFIED`.

No security gate in this document authorizes a push, merge, tag, signing,
Central upload or public release.
