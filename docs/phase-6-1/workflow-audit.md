# Phase 6.1 workflow audit

This is a static audit of the workflows at local tag `v0.1.0-rc.1`. No remote
workflow was run or observed.

## Current workflow inventory

| Workflow | Triggers | Platform and work | Permissions | Artifacts/publication | Current gap |
|---|---|---|---|---|---|
| CI | push to `main`; standard `pull_request` | Ubuntu and Windows full `clean verify`; macOS `verify` with `-DskipTests -Djacoco.skip=true` | `contents: read` | No upload | macOS is compile/metadata/format smoke, not full qualification; no retained evidence |
| CodeQL | push to `main`; standard `pull_request`; weekly schedule | Ubuntu Java/Kotlin analysis; Maven `package` with `-DskipTests` | workflow `contents: read`; job adds `security-events: write` | Security analysis only | Tests are disabled; CodeQL pin uses an annotated-tag object SHA |
| Dependency review | standard `pull_request` only | Ubuntu dependency diff review | `contents: read` | No upload | No result exists before a remote PR run |
| Release preflight | manual `workflow_dispatch`; any `v*` tag push | Windows public-release install, consumer, API hash and local bundle assembly | `contents: read` | No upload and no publish | Broad tag trigger, one OS only, and bundle evidence is discarded with the runner |

All pull-request triggers are the standard `pull_request` event. No workflow
uses `pull_request_target`, repository secrets or a publication credential.
Java setup enables the Maven cache.

## Action pin audit

| Action | Current pin assessment | rc2 action |
|---|---|---|
| `actions/checkout` | Existing SHA maps to official `v4.4.0` | Keep or replace only with a verified official commit pin |
| `actions/setup-java` | Existing SHA maps to official `v4.9.1` | Keep or replace only with a verified official commit pin |
| `actions/dependency-review-action` | Existing SHA maps to official `v4.9.0` | Keep or replace only with a verified official commit pin |
| `github/codeql-action` | Existing SHA is the release's annotated-tag object, not its commit | Pin init/analyze to target commit `6f5948dfacef28e207b48d0905cf90c03365536d` |

This pin mapping is audit input, not evidence that GitHub fetched or executed
the actions during this documentation task.

## rc2 workflow changes implemented locally

- Release preflight is now `workflow_dispatch` only; no tag event can invoke it.
- Ubuntu, Windows and macOS are configured for full clean verification.
- On all three operating systems, qualify the public-release install, external
  consumer, frozen API and assembled/audited bundle.
- Controlled summaries and bundles use seven-day artifact retention.
- The CodeQL Maven build runs full tests.
- Official actions use verified commit objects; CodeQL uses the release target
  commit and upload-artifact uses official v7.0.1 commit
  `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`.
- Ubuntu additionally runs OSV 2.5.1, bounded HTTP/startup smoke and two-clean-build
  reproducibility. Windows release preflight includes clean `file://` deploy/consume.
- Keep permissions least-privileged and keep publish/sign/merge absent.

All four workflow files passed official actionlint 1.7.12 locally. These are
configuration facts only; hosted behavior remains unverified until the PR runs.

## Remote evidence placeholders

After the first authorized remote runs, the main Phase 6.1 document must record:

| Evidence | Status now | Required update |
|---|---|---|
| CI/qualification run URL and commit | `UNVERIFIED` | Add immutable run URL and exact commit SHA |
| Ubuntu result | `UNVERIFIED` | Record full verify, consumer, API and bundle conclusions |
| Windows result | `UNVERIFIED` | Record full verify, consumer, API and bundle conclusions |
| macOS result | `UNVERIFIED` | Record full verify, consumer, API and bundle conclusions |
| CodeQL | `UNVERIFIED` | Record run URL, tested build result and findings conclusion |
| Dependency review | `UNVERIFIED` | Record PR/run URL and conclusion |
| Uploaded artifacts | `UNVERIFIED` | Record names, checksums, retention days and expiry |

No row may be changed to `PASS` from configuration inspection alone.
