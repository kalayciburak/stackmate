# Phase 6.1 security qualification

## Supply-chain controls configured for rc2

| Control | Local configuration/evidence | Remote result |
|---|---|---|
| Immutable official-action pins | Checkout `3d3c42e5aac5ba805825da76410c181273ba90b1` (official v7.0.1); setup-java `dd06d9cba3e5552c54d9f8ea23572deb30010f7c` (official v6.0.0); CodeQL `cdf488f595d80d6e07e03d4674febd5ab45fa938` (official v4.37.9); upload-artifact `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (official v7.0.1) | `PASS` |
| Current CodeQL line | Migrated from the deprecated Node 20-based v3 line to immutable official v4.37.9 after the hosted warning | `PASS` |
| Tested CodeQL construction | Full clean verification runs before analysis; Code Scanning upload is disabled because private-repository GHAS is unavailable, and controlled SARIF is retained for seven days | `PASS` |
| Dependency review | Repository-local PR base/head declaration diff and public runtime denylist audit | `PASS`: run `33927462908`, zero policy violations |
| Public SBOM scan | Ubuntu uses official OSV Scanner `2.5.1` Linux asset with expected SHA-256 `f9f25499a2c8cc367b3af45df2ea7eeca7fbccceab9c35079968f4b3652194be` | `PASS`: zero results |
| Evidence retention | Qualification uploads use seven-day retention | `PASS` |
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

## Evidence requirements

The final section records the exact rc2 commit, immutable run URLs, finding
disposition, OSV result and artifact digests/expiry. No finding is left without
an explicit disposition.

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

The fourth CodeQL run
[33925474078](https://github.com/kalayciburak/stackmate/actions/runs/33925474078)
passed full tests, v4.37.9 analysis and seven-day SARIF artifact upload. The
replacement dependency review
[33925474039](https://github.com/kalayciburak/stackmate/actions/runs/33925474039)
also passed. Expected read-only warnings state that private-repository Code
Scanning API features are unavailable without GHAS; no upload to Code Scanning,
setting change or write permission was introduced. Both workflows must rerun
after exact-head checkout is pinned.

Exact-head CodeQL run
[33926362151](https://github.com/kalayciburak/stackmate/actions/runs/33926362151)
passed tested construction, analysis and SARIF upload. Artifact digest was
`b8b668ee3897517c481f157477803bff2c8c1a99c33d3c40d2055530ab6fd7a6`;
downloaded `java.sarif` SHA-256 was
`341cf6d3537826a6f028e0fde010fd9cea7ce304eb64acf7d3758cf2e6a0ca1d`.
Its six `java/xss` paths all terminate in controlled echo endpoints inside two
benchmark test-fixture classes. Those classes are under `src/test/java`, are
absent from all four public coordinates and intentionally reproduce MVC input
behavior. Disposition: `TEST_FIXTURE_ONLY / NOT_RELEASE_SURFACE`; no suppression
or product security relaxation was added.

The same head's dependency-review artifact passed with base/head declaration
counts 58/64, 20 additions, 14 removals and zero runtime-policy violations.
Linux OSV report contained zero results. Private-repository Code Scanning API
warnings remain a known capability limitation; local SARIF evidence is retained
for seven days and no security write permission is present.

## Final security qualification

Source commit `357ca1418eb3786e861e4436fe55005c8c6456cd` passed CodeQL run
[33927462894](https://github.com/kalayciburak/stackmate/actions/runs/33927462894)
and dependency review
[33927462908](https://github.com/kalayciburak/stackmate/actions/runs/33927462908).
CodeQL artifact digest is
`e8c0e1c12eeb129d2cd6f5cfdaab7e3fb32594a5a3bdd5742924ac5d7fe18a87`;
downloaded SARIF SHA-256 is
`38b9ef75c58ec00351f8319fcc9529809b8c097ce591ec29d23049833c5e762f`.
The same six test-fixture-only `java/xss` results were reproduced and retain the
`TEST_FIXTURE_ONLY / NOT_RELEASE_SURFACE` disposition.

Dependency-review artifact digest is
`4979ef8ecc939b578ef489e0cdb16ffeec4fdd97c3f99566a56aca1cfd15b4a0`;
its report records 20 additions, 14 removals and zero policy violations. OSV
report SHA-256 is
`54345d5194d238b6017d0f3181f392109ce35023cc6b4bdfa8c134943dcb578e`
with zero results. No secret, credential, model, adapter, dataset or incident
was uploaded.
