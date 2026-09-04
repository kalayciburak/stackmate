# Phase 6.1 Maven Central readiness

Status: artifact and metadata dry-run prerequisites pass for rc2; actual Central
publication remains unauthorized and blocked on namespace, credentials, PGP
signing and Portal validation.

## Requirement mapping

| Central requirement | rc2 evidence/status |
|---|---|
| Every non-POM JAR must have sources and Javadoc attachments; a README-based placeholder is explicitly allowed when an artifact has no source or Javadoc content ([Central requirements](https://central.sonatype.org/publish/requirements/)) | `PASS` locally and hosted: the dependency-only starter builds and installs README-containing placeholder sources and Javadoc JARs; other public JARs retain normal attachments |
| MD5 and SHA1 checksums are required; SHA256 and SHA512 are optional ([Central requirements](https://central.sonatype.org/publish/requirements/)) | `PASS` locally and in hosted Windows dry run: 13 required payloads and 26 generated MD5/SHA1 files were recalculated and matched |
| Every deployed file must have a PGP `.asc` signature ([Central GPG guidance](https://central.sonatype.org/publish/requirements/gpg/)) | `PENDING` / not performed: no signing key or signature operation is part of this pre-push work |
| POM metadata must include name, description, URL, license, developer and SCM information ([Central requirements](https://central.sonatype.org/publish/requirements/)) | Present in the rc2 project metadata; final portal/preflight acceptance remains `UNVERIFIED` |
| The publishing namespace must be verified before deployment ([Central Portal registration](https://central.sonatype.org/register/central-portal/)) | `PENDING`: no namespace-verification evidence or Central credential has been supplied |
| Portal publishing still requires all prerequisites; the Maven Portal plugin does not generate every prerequisite ([Portal Maven publishing](https://central.sonatype.org/publish/publish-portal-maven/)) | Official plugin `0.11.0` is isolated in the explicit `central-publish` profile with `autoPublish=false`; no workflow activates it and credentials remain absent |

## Local dry-run evidence

The public-release build/install passed for parent, core, auto-configuration and
starter. A local `file://` deployment and clean Maven repository consumption
also passed; the consumer resolved from `stackmate-file` and passed 6 Surefire
plus 1 Failsafe test with no failures, errors or skips. Starter sources and
Javadoc attachments were present, and every required MD5/SHA1 sidecar matched
a fresh digest calculation.

Final hosted Windows job `101198969235` in run `33927462896` repeated the public
install, external consumer and local file-repository path. Its downloaded report
records 13 required payloads, 26 verified MD5/SHA1 sidecars, clean-repository
resolution, 6 contract tests, 1 packaged-process test, no credentials and no
publication. Report SHA-256 is
`74e8b8334950d9dbb028241c9e9faa55b4f63cedf577d9ae5ca6abe9a78f65f4`.

## Readiness blockers

- Hosted qualification passed. The manual release-preflight remains unavailable
  until the workflow exists on the default branch; equivalent Windows PR gates
  passed without merging.
- Namespace verification, Central credentials and PGP signing are absent.
- No Central deployment or Portal validation has been attempted.

## Publication prerequisites still outside this phase

Future manual publication preparation must record namespace proof, credential
availability, signing-key identity, `.asc` coverage and Portal validation.

This document is not publication approval. No signing, Central credential use,
Central upload, package publication, GitHub Release, remote tag, merge or
release has occurred.
