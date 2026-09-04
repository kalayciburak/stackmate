# Phase 6.1 remote-run ledger

Three hosted run sets failed and are preserved below. The required final-commit
run IDs, artifact identities and qualification conclusions remain `PENDING` /
`UNVERIFIED` until the focused fixes are pushed and rerun.

## Preserved first failed run set

The draft PR is [#1](https://github.com/kalayciburak/stackmate/pull/1). Its first
run set used commit `9a8f67357a60124fe7ef5e80c0f55a8d049ca7d7`.

| Workflow | Run | Conclusion | Classification and evidence |
|---|---|---|---|
| CI | [33922327479](https://github.com/kalayciburak/stackmate/actions/runs/33922327479) | `FAIL` | `TEST_PORTABILITY_DEFECT`: Linux and macOS exposed Windows path assumptions and macOS system-symlink false positives in historical inference tests; Windows exposed cross-drive relativization in the same test module. Ubuntu reproducibility was skipped because the matrix dependency failed. |
| CodeQL | [33922327492](https://github.com/kalayciburak/stackmate/actions/runs/33922327492) | `FAIL` | Build extraction reached the same Linux inference tests and stopped before analysis; no CodeQL finding was produced. |
| Dependency review | [33922327570](https://github.com/kalayciburak/stackmate/actions/runs/33922327570) | `FAIL` | `WORKFLOW_DEFECT / REPOSITORY_CAPABILITY`: the official action reported that dependency review is unavailable without Dependency Graph and GitHub Advanced Security on this private repository. Repository settings were not changed. |
| Release preflight | no run | `NOT_RUN` | Dispatch returned HTTP 404 because a new `workflow_dispatch` file is not invokable until it exists on the default branch. Merge/default-branch push remain forbidden. Equivalent Windows evidence is moved into the PR qualification path; the manual workflow remains prepared for post-merge use. |

The focused repair keeps all mandatory tests enabled. It does not use retries,
timeout inflation, platform skips, `continue-on-error` or repository-setting
changes.

## Preserved second run set

The second run set used commit
`b6821d68f99e130988324d375fc1a3c0f86725c9`.

| Workflow | Run | Conclusion | Classification and evidence |
|---|---|---|---|
| CI | [33923226270](https://github.com/kalayciburak/stackmate/actions/runs/33923226270) | `FAIL` | `TEST_PORTABILITY_DEFECT`: Windows retained the cross-drive relative-path fixture, Linux exposed a fast-exit scripted-process race, and macOS exposed the `/var` versus `/private/var` canonical path result. Ubuntu reproducibility was skipped because the matrix dependency failed. |
| CodeQL | [33923226567](https://github.com/kalayciburak/stackmate/actions/runs/33923226567) | `FAIL` | The tested build reached the benchmark module. Linux exposed provider-dependent TLS-to-plain root exceptions and a frozen parity corpus whose recorded mixed-line-ending byte identity had not been preserved by Git. Analysis did not run and no finding was produced. |
| Dependency review | [33923226273](https://github.com/kalayciburak/stackmate/actions/runs/33923226273) | `PASS` | The replacement private-repository review completed its public graph build, base/head declaration comparison, runtime denylist audit and seven-day evidence upload. This is historical evidence only; the final rc2 commit must rerun it. |

The second repair keeps the real TLS handshake failure while completing its
platform-stable frozen transport root only inside the two affected fixtures.
It also stores the already-frozen parity corpus bytes without Git line-ending
conversion; the staged blob remains
`fc07d86362b705bee5a493515180ac4daf687b3e820095142dd82bec09540bd8`.
No case, expected answer, ID or model-evaluation result changed.

## Preserved third run set

The third run set used commit
`610600eb2fa4746e7027f9f920ac307cfe930d1f`.

| Workflow | Run | Conclusion | Classification and evidence |
|---|---|---|---|
| CI | [33924591960](https://github.com/kalayciburak/stackmate/actions/runs/33924591960) | `FAIL` | Linux passed the full job in 4m53s, including OSV/performance and evidence upload. Windows passed the full job in 8m40s, including file-repository consume and evidence upload. macOS stopped in four export tests because JUnit supplied its ordinary temporary directory through `/var`, a system symlink alias. Ubuntu reproducibility was skipped by the failed matrix dependency. |
| CodeQL | [33924592005](https://github.com/kalayciburak/stackmate/actions/runs/33924592005) | `FAIL` | Full tested construction passed and CodeQL scanned 261/270 Java files and exported SARIF. Post-analysis workflow-run metadata lookup lacked read-only `actions: read`, so the action ended as a configuration error before artifact upload. No CodeQL query failure was reported. |
| Dependency review | [33924591939](https://github.com/kalayciburak/stackmate/actions/runs/33924591939) | `PASS` | Public dependency graph, base/head declaration review, runtime denylist and seven-day evidence upload all passed in 1m15s. |

The next repair canonicalizes only the JUnit temporary directory before the
existing export tests; the explicit symlink-rejection scenario and production
export policy remain unchanged. CodeQL receives read-only `actions: read` and
moves from the deprecated v3 line to immutable official v4.37.9 commit
`cdf488f595d80d6e07e03d4674febd5ab45fa938`.

## Required runs

| Workflow/job | Commit SHA | Run ID | Immutable URL | Result | Required evidence |
|---|---|---|---|---|---|
| CI / Full qualification (Linux) | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | Full clean verify, public install, consumer, API/bundle/evidence, OSV and performance smoke |
| CI / Full qualification (Windows) | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | Full clean verify, public install, consumer, API/bundle/evidence |
| CI / Full qualification (macOS) | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | Full clean verify, public install, consumer, API/bundle/evidence |
| CI / Ubuntu reproducibility | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | Two clean public builds, comparison report and both manifests |
| CodeQL / Java-Kotlin | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | Full tested construction and analysis conclusion |
| Dependency review / PR | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | PR number, dependency-review conclusion and findings |
| Release preflight / Windows | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` | Manual dispatch, public dry run, clean file-repository consume and uploaded evidence |

## Uploaded artifacts

The workflows are configured for seven-day retention, but no upload is claimed
until the corresponding run is observed.

| Artifact | Run ID | Download/attestation URL | SHA-256 | Retention expiry | Result |
|---|---|---|---|---|---|
| `qualification-Linux-<commit>` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` |
| `qualification-Windows-<commit>` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` |
| `qualification-macOS-<commit>` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` |
| `reproducibility-Ubuntu-<commit>` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` |
| `release-preflight-Windows-<commit>` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `UNVERIFIED` |

## Main completion rules

Main must fill this ledger only from observed runs for one exact final rc2
commit. Record job-level conclusions, exact test counts, API and manifest hashes,
OSV/performance conclusions, artifact checksums and calculated expiry. A
configured job or uploaded-artifact step is not evidence of a passing run.

No entry in this file authorizes signing, Central deployment, release creation,
tag push, merge or any other publication action.
