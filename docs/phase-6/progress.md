# Phase 6 progress

| Gate | State |
|---|---|
| Gate A: repository and deterministic baseline | PASS |
| Gate B: release surface | PASS; final RC artifact audit excludes research/inference |
| Gate C: API and configuration | PASS; RC inventory 73 types with matching signature |
| Gate D: runtime hardening | PASS; full reactor and focused hardening tests pass |
| Gate E: consumer and packaging | PASS; RC consumer 6 contract plus 1 packaged-process test |
| Gate F: security and performance | PASS locally; final RC JMH and OSV evidence recorded |
| Gate G: artifacts and supply chain | PASS locally; remote CI remains unverified |
| Gate H: documentation and DX | PASS |
| Gate I: local RC assembly | PASS; local bundle and annotated tag `v0.1.0-rc.1` |

Phase 6 starts from the exact Phase 5.5.3 completion commit. The normal Windows
reactor build and the separately isolated WSL research checks pass without a
model. Public-alpha scope is frozen around deterministic Servlet/MVC diagnostics;
local inference remains historical research and is not a v0.1.0 feature.

The release boundary now identifies only parent, core, deterministic
auto-configuration and deterministic starter as publishable. Inference, Jlama,
local starter, sample, E2E, benchmark and research modules remain in the
repository but are nonpublishable. Final built-artifact inspection and local RC
assembly passed against the frozen allowlist.

## Recorded evidence

- Baseline checkpoint: `c14cbfe270f8520d379712209afd46fc8e4d5f1d`.
- Baseline full build: 517 Surefire plus 11 Failsafe tests passed.
- Final RC full reactor: 543 Surefire plus 5 Failsafe tests passed; failures,
  errors and skips were all zero.
- Public RC API: 73 types; signature SHA-256
  `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.
- Public-release clean install passed for all four public coordinates and
  produced RC sources/Javadoc where applicable plus public SBOMs.
- External RC consumer: 6/6 unit/contract tests plus 1/1 packaged-process
  integration test passed.
- Final RC public SBOM OSV scan: exit 0 and zero known vulnerability groups.
- Final RC JMH raw report SHA-256:
  `ba13b7865e67ed80cfe4896018c50ec7edb2f471045eb0b91e754e659c9bae6a`.
- The fresh-JVM full dedup-cache retained-heap probe passed at 10,000 entries;
  approximate median 510,936 bytes and p95/max 1,226,608 bytes. Raw report
  SHA-256: `cf9eab70a0cc53ed531bbc5e60e0a8458fc2d8074d7edd2170915632fe9ceda1`.
- HTTP behavior hashes were identical across without, disabled and enabled
  modes. Enabled failure handling measured 5.44 ms median and 7.77 ms p95.
- Fresh-JVM enabled startup measured 1,633 ms median and 1,685 ms p95, a 54 ms
  median increase (about 3.4%) over the without-StackMate mode.
- Two independent clean public-release builds produced 18 byte-identical
  payload files with zero differences. The finalized 21-file payload comparison
  also passed with zero differences.
- Final local bundle: `target/release/stackmate-0.1.0-rc.1`, 24 files,
  1,166,290 bytes; manifest SHA-256
  `108dbbb31f207fa1db68355ae536c63572468293dddfc79ffcc3a0557447b3da`.

## Remaining external qualification

GitHub CI, CodeQL, dependency-review and release-preflight runs have not been
observed on a remote provider. Linux and macOS remain unverified. These limits
do not invalidate the local Windows RC, but they block a cross-platform claim.

No artifact, tag or branch has been pushed or published. The RC tag is local
only.
