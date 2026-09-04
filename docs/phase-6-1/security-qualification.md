# Phase 6.1 security qualification

## Supply-chain controls configured for rc2

| Control | Local configuration/evidence | Remote result |
|---|---|---|
| Immutable official-action pins | Checkout `3d3c42e5aac5ba805825da76410c181273ba90b1` (official v7.0.1); setup-java `dd06d9cba3e5552c54d9f8ea23572deb30010f7c` (official v6.0.0); CodeQL `cdf488f595d80d6e07e03d4674febd5ab45fa938` (official v4.37.9); upload-artifact `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (official v7.0.1) | `UNVERIFIED` |
| Current CodeQL line | Migrated from the deprecated Node 20-based v3 line to immutable official v4.37.9 after the hosted warning | `UNVERIFIED` |
| Tested CodeQL construction | Full clean verification is configured instead of a skipped-test build; SARIF upload to Code Scanning is disabled because private-repository GHAS is unavailable, and controlled SARIF is retained for seven days instead | `UNVERIFIED` |
| Dependency review | Repository-local PR base/head declaration diff and public runtime denylist audit | Replacement run `33923226273` passed on intermediate commit `b6821d6`; final-commit rerun pending |
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

## First hosted security-run disposition

The official dependency-review action failed in run
[33922327570](https://github.com/kalayciburak/stackmate/actions/runs/33922327570)
because GitHub Advanced Security/Dependency Graph is unavailable for this
private repository. Enabling repository settings is outside this phase. The
replacement PR job compares base/head POM declarations, reports additions and
removals, builds current public dependency trees and fails on the release
runtime denylist. Its local dry run reported 20 additions, 14 removals and zero
policy violations. Hosted evidence remains pending.

CodeQL run
[33922327492](https://github.com/kalayciburak/stackmate/actions/runs/33922327492)
stopped in the shared Linux test failure before analysis. It reported no
security finding; the final CodeQL conclusion remains unverified.

The second CodeQL run
[33923226567](https://github.com/kalayciburak/stackmate/actions/runs/33923226567)
completed initialization and reached the benchmark module, then stopped on the
Linux TLS/serialization portability defects before analysis. It likewise
produced no finding. The replacement dependency review run
[33923226273](https://github.com/kalayciburak/stackmate/actions/runs/33923226273)
passed, including its bounded evidence upload; it must still rerun for the
final commit.

The repaired CodeQL job uses the official action's documented `upload=false`
and `output` inputs, disables database upload, removes `security-events: write`
and stores SARIF as a bounded workflow artifact. This preserves analysis
without changing repository security settings.

The third CodeQL run
[33924592005](https://github.com/kalayciburak/stackmate/actions/runs/33924592005)
passed full tested construction, scanned 261 of 270 Java files and exported
SARIF. It failed only when the action queried workflow-run metadata without
`actions: read`; no query or build failure was reported and no SARIF artifact
was uploaded. The next run adds only read-only workflow metadata permission and
uses official v4.37.9 commit
`cdf488f595d80d6e07e03d4674febd5ab45fa938`.
