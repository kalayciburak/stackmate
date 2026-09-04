# Release process

This is the public-alpha release contract for `0.1.0-rc.1`. It documents the intended maintainer procedure; it does
not assert that a remote release or publication occurred.

## Public coordinates

The release allowlist is limited to:

- `io.github.kalayciburak:stackmate-parent:0.1.0-rc.1`
- `io.github.kalayciburak:stackmate-core:0.1.0-rc.1`
- `io.github.kalayciburak:stackmate-spring-boot-autoconfigure:0.1.0-rc.1`
- `io.github.kalayciburak:stackmate-spring-boot-starter:0.1.0-rc.1`

Reactor membership is not publication authorization. Inference, local starter, sample, E2E, startup-baseline,
benchmark, and research modules must not enter the public bundle or published dependency graph.

## Required gates

1. Confirm the approved revision and a reviewed working tree.
2. Run the repository's normal verification and public-release preflight on the approved clean revision.
3. Verify the frozen public API/property inventory and the four-coordinate allowlist.
4. Inspect public POMs and JARs for forbidden inference, model, prompt, native, dataset, report, credential, or local
   path content.
5. Run the external consumer checks against locally installed RC artifacts.
6. Assemble two local RC bundles and compare their allowed deterministic contents.
7. Review source, Javadoc, license, notice, SBOM, checksum, and release-note outputs.
8. Only after all gates pass, separately authorize signing, publication, tagging, and remote release creation.

The preflight workflow is a dry run. Its presence does not prove that CI ran, that a signing key exists, or that an
artifact reached a repository.

## Current handoff status

- Public-release install: passed for `0.1.0-rc.1`.
- External consumer RC: 6/6 contract checks and 1/1 packaged-process check passed.
- Full reactor: 543 Surefire plus 5 Failsafe tests passed; failures/errors/skips `0/0/0`.
- Reproducibility: two clean builds, 18 compared payload files, zero differences.
- Local bundle: `target/release/stackmate-0.1.0-rc.1`, audited with SHA-256 manifest.
- Local annotated tag: `v0.1.0-rc.1` on the final release checkpoint.
- Maven Central publication: not performed.
- Remote release creation: not performed.
- GPG signing: not performed.
- CI signing/publication: preflight-only.

Remote CI and cross-platform qualification remain unverified and must not be inferred from the local Windows RC.

## Failure handling

Any mismatch in coordinates, public API, property count, dependency boundary, legal files, checksums, bundle
comparison, consumer behavior, or sanitization blocks release. Fix the source, generate fresh local evidence, and
restart the affected gate; do not edit generated evidence to make it pass.
