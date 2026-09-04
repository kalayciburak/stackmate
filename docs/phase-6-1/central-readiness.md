# Phase 6.1 Maven Central readiness

Status: locally improved for rc2, but not ready for or authorized to publish.

## Requirement mapping

| Central requirement | rc2 evidence/status |
|---|---|
| Every non-POM JAR must have sources and Javadoc attachments; a README-based placeholder is explicitly allowed when an artifact has no source or Javadoc content ([Central requirements](https://central.sonatype.org/publish/requirements/)) | `PASS` locally: the dependency-only starter now builds and installs README-containing placeholder sources and Javadoc JARs; other public JARs retain their normal attachments |
| MD5 and SHA1 checksums are required; SHA256 and SHA512 are optional ([Central requirements](https://central.sonatype.org/publish/requirements/)) | `PASS` locally: 13 required payloads and 26 generated MD5/SHA1 files were recalculated and matched |
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

## Readiness blockers

- All hosted qualification, release-preflight and uploaded-artifact results are
  `UNVERIFIED`.
- Namespace verification, Central credentials and PGP signing are absent.
- No Central deployment or Portal validation has been attempted.

## Main update after remote/manual prerequisites

Main must add the exact rc2 commit, release-preflight run URL, file-repository
report/checksums, final bundle identity and attachment inventory. Future manual
publication preparation must separately record namespace proof, credential
availability, signing-key identity, `.asc` coverage and Portal validation.

This document is not publication approval. No signing, Central credential use,
Central upload, package publication, GitHub Release, remote tag, push, PR, merge
or release has occurred.
