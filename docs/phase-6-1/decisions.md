# Phase 6.1 decisions

## D1 - Freeze rc1 as a local-only checkpoint

Status: accepted.

`v0.1.0-rc.1` remains fixed at
`248476f68ed3a3aa49e9fa1cba33d68a746a5469`. It is not on the remote. Any
repository change, including workflow or release-attachment hardening, requires
rc2; the rc1 tag will not be moved.

## D2 - Separate current evidence from planned fixes

Status: accepted.

Local Windows results may be reported as reproduced local evidence. Linux,
macOS, GitHub Actions, CodeQL, dependency review and uploaded-artifact outcomes
remain `UNVERIFIED` until observed remotely. Planned rc2 behavior must not be
worded as implemented or passing.

## D3 - Treat fake-child reduction as product-scope removal

Status: accepted.

The six normal `PackagedFakeChildIT` cases were excluded, not equivalently
migrated. Lower-level tests preserve validator, protocol, queue and simulated
process checks, but the packaged inference HTTP/child lifecycle seam is gone.
This is intentional because inference/local worker behavior is
`RESEARCH_ONLY` / `NOT_IN_RELEASE`; it is not a claim of equal packaged
coverage.

## D4 - Keep deterministic packaged and consumer qualification

Status: accepted.

The normal root qualification keeps the four deterministic packaged cases and
the disabled-mode case. The independent consumer remains a separate release
gate covering six public contract tests and one packaged process test, including
HTTP behavior, redaction, deduplication and shutdown.

## D5 - Replace broad tag automation with deliberate preflight

Status: implemented locally for rc2; remote behavior unverified.

Remove the release-preflight `v*` tag-push trigger. Retain deliberate execution,
least-privileged read access and no publish/sign/merge authority.

## D6 - Require full three-OS release qualification

Status: implemented locally for rc2; remote behavior unverified.

Ubuntu, Windows and macOS must each run full clean qualification and the public
install, external consumer, frozen API and bundle audit. Uploaded evidence must
use explicit bounded retention. A compile-only macOS smoke is insufficient for
the cross-platform claim.

## D7 - Run tests during CodeQL construction

Status: implemented locally for rc2; remote behavior unverified.

The CodeQL Maven build will no longer use `-DskipTests`. This closes the current
gap between analyzed construction and the tested application build.

## D8 - Pin official actions to commit objects

Status: implemented locally for rc2; actionlint passed, remote behavior unverified.

Official actions remain commit-pinned. The existing checkout, setup-java and
dependency-review pins map to official releases. The CodeQL release pin must use
target commit `6f5948dfacef28e207b48d0905cf90c03365536d`, not the annotated-tag object
SHA.

## D9 - Supply attachments for every public non-POM JAR

Status: implemented and verified locally for rc2.

Central requires sources and Javadoc attachments for every non-POM JAR. The
dependency-only starter therefore needs valid placeholder sources and Javadoc
JARs. The rc1 “not applicable” exception is acceptable for its local bundle
audit but is not Central-ready.

## D10 - Do not publish, sign or merge in Phase 6.1 pre-push work

Status: accepted.

This phase authorizes audit documentation and later qualification hardening,
not repository publication, artifact publication, signing or branch merge.
Remote writes require separate authorization. After authorized runs, the main
Phase 6.1 status/progress document must be updated with immutable run links,
exact commits, artifact checksums and retention information.

## D11 - Replace unavailable GitHub dependency review without changing settings

Status: accepted after failed run `33922327570`.

The private repository does not provide the Dependency Graph/GitHub Advanced
Security capability required by the official action. Repository settings remain
unchanged. The PR instead records base/head POM declaration differences and
fails closed on forbidden public runtime dependencies, with a seven-day report.

## D12 - Accept system-level path aliases outside the model boundary

Status: accepted after Linux, Windows and macOS failures in run `33922327479`.

Historical model validation rejects a symlink at the configured model directory
and every required model file, then canonicalizes the root and enforces file
containment. It no longer rejects an operating-system ancestor such as macOS
`/var -> /private/var`, which is outside the configured model directory and
caused false rejection of all temporary paths.

## D13 - Normalize only the TLS-to-plain benchmark boundary

Status: accepted after CodeQL run `33923226567`.

The plain peer still performs a real TLS protocol violation, but JDK providers
may expose either an underlying `SocketException` or a terminal `SSLException`.
Only the two affected runtime fixtures complete a missing socket root while
preserving the observed SSL failure. Global throwable capture and every frozen
case, expectation and ID remain unchanged.

## D14 - Preserve the frozen parity corpus byte identity

Status: accepted after CodeQL run `33923226567`.

The historical parity corpus was generated with mixed CRLF records and a final
LF, and its frozen SHA-256 is
`fc07d86362b705bee5a493515180ac4daf687b3e820095142dd82bec09540bd8`.
Git had normalized the repository blob to LF (`543da525...`), so fresh Linux
checkouts could never match the manifest. A path-specific `.gitattributes`
rule now stores the existing frozen bytes verbatim; semantic content and
historical checksum references are not rewritten.
