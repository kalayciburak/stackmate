# Phase 6.1 workflow audit

This audit starts from the workflows at local tag `v0.1.0-rc.1` and records the
first hosted failure set plus the focused rc2 corrections.

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
| `actions/checkout` | Entry SHA mapped to official `v4.4.0` but emitted a hosted Node 20 deprecation warning | Updated to official v7.0.1 commit `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | Entry SHA mapped to official `v4.9.1` but emitted a hosted Node 20 deprecation warning | Updated to official v6.0.0 commit `dd06d9cba3e5552c54d9f8ea23572deb30010f7c` |
| `actions/dependency-review-action` | Official v4.9.0 ran but the private repository lacks required GitHub capability | Replaced with a repository-local base/head declaration and public runtime policy audit |
| `github/codeql-action` | Existing SHA was an annotated v3 tag object; the subsequent v3 target emitted Node 20 and December 2026 deprecation warnings | Pin init/analyze to official v4.37.9 target commit `cdf488f595d80d6e07e03d4674febd5ab45fa938` |

This pin mapping is audit input, not evidence that GitHub fetched or executed
the actions during this documentation task.

## rc2 workflow changes implemented locally

- Release preflight is now `workflow_dispatch` only; no tag event can invoke it.
- Ubuntu, Windows and macOS are configured for full clean verification.
- On all three operating systems, qualify the public-release install, external
  consumer, frozen API and assembled/audited bundle.
- Controlled summaries and bundles use seven-day artifact retention.
- The CodeQL Maven build runs full tests.
- CodeQL has read-only `actions: read` for workflow-run metadata; no write
  permission is added.
- CodeQL writes SARIF locally with `upload=false` and seven-day artifact
  retention because this private repository does not expose GHAS Code Scanning;
  no `security-events: write` permission remains.
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

The second run set is also retained: CI
[33923226270](https://github.com/kalayciburak/stackmate/actions/runs/33923226270)
and CodeQL
[33923226567](https://github.com/kalayciburak/stackmate/actions/runs/33923226567)
failed on additional deterministic test-harness portability defects, while the
replacement dependency review
[33923226273](https://github.com/kalayciburak/stackmate/actions/runs/33923226273)
passed. These intermediate results do not fill the final-commit placeholders.

The third set is also intermediate. CI
[33924591960](https://github.com/kalayciburak/stackmate/actions/runs/33924591960)
proved complete Linux and Windows jobs but failed the macOS temp-path fixture;
CodeQL
[33924592005](https://github.com/kalayciburak/stackmate/actions/runs/33924592005)
passed tested construction and SARIF generation but lacked `actions: read` for
metadata; dependency review
[33924591939](https://github.com/kalayciburak/stackmate/actions/runs/33924591939)
passed.

The fourth set passed all configured jobs: CI
[33925474151](https://github.com/kalayciburak/stackmate/actions/runs/33925474151),
CodeQL
[33925474078](https://github.com/kalayciburak/stackmate/actions/runs/33925474078)
and dependency review
[33925474039](https://github.com/kalayciburak/stackmate/actions/runs/33925474039).
Downloaded artifact names and summaries exposed standard PR merge SHA
`8b521134...`; therefore checkout refs and artifact suffixes are now explicitly
bound to the PR head SHA. The green merge-ref set is not used as final identity
evidence.

The first manual release-preflight dispatch returned HTTP 404 because GitHub
only exposes new `workflow_dispatch` definitions after they exist on the
default branch. The phase will not merge or push directly to that branch to
bypass this rule. Equivalent Windows checks are included in PR qualification;
manual preflight remains post-merge preparation only.
