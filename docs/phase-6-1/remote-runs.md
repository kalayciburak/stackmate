# Phase 6.1 remote-run ledger

Three failed sets and two green intermediate sets are preserved below. The
sixth set is the final exact-head qualification for source commit
`357ca1418eb3786e861e4436fe55005c8c6456cd`.

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

## Preserved fourth green behavior run

The fourth run set reports branch head
`e087961eb05c28d2e3fa667b43c6bdba5d5698be`, but the standard
`pull_request` checkout and `${{ github.sha }}` artifact suffix used synthetic
merge commit `8b5211345d272c97d52d6193303c1b7a38ef7432`.

| Workflow | Run | Conclusion | Evidence and limitation |
|---|---|---|---|
| CI | [33925474151](https://github.com/kalayciburak/stackmate/actions/runs/33925474151) | `PASS` | Linux 5m23s, macOS 2m34s and Windows 6m57s passed every configured qualification step. Ubuntu reproducibility passed in 1m26s. Four platform artifacts plus reproducibility evidence were retained through 2026-09-11 UTC. |
| CodeQL | [33925474078](https://github.com/kalayciburak/stackmate/actions/runs/33925474078) | `PASS` | v4.37.9 passed tested construction, analysis and bounded SARIF upload in 7m03s. Private-repository Code Scanning API warnings remain expected because GHAS is unavailable; no write permission or repository-setting change was made. |
| Dependency review | [33925474039](https://github.com/kalayciburak/stackmate/actions/runs/33925474039) | `PASS` | Public graph, declaration diff, runtime denylist and bounded evidence upload passed in 1m01s. |

This set proves the behavior of the corrected tree, but its artifacts claim the
synthetic merge commit rather than the requested exact branch head. The next
workflow revision checks out `${{ github.event.pull_request.head.sha }}` on PRs
and uses the same head SHA in every qualification artifact name. No final
qualification claim is derived from the fourth set.

## Preserved fifth exact-checkout run

Commit `6d6be9c0cf165d8bc53a62688c170b3a5d5af0de` passed every hosted
job: CI [33926362199](https://github.com/kalayciburak/stackmate/actions/runs/33926362199),
CodeQL [33926362151](https://github.com/kalayciburak/stackmate/actions/runs/33926362151)
and dependency review
[33926362200](https://github.com/kalayciburak/stackmate/actions/runs/33926362200).
Checkout logs and artifact names used the exact head SHA. However, the three
qualification summaries still preferred inherited `GITHUB_SHA` and recorded
synthetic merge commit `977a7abfabc055682c6d815c0957091d3cdddb41`.

The downloaded evidence otherwise passed independent audit: all OS summaries
reported 549 Surefire + 6 Failsafe with zero failures/errors/skips; API hash
`41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`;
OSV zero results; Ubuntu 23/23 byte-identical payloads; Windows 13 required
file-repository payloads, 26 checksum sidecars and 6+1 consumer tests. CodeQL
reported six XSS paths, all confined to controlled benchmark test controllers
under `stackmate-benchmark/src/test/java`; none is part of a public coordinate.

The collector now resolves and validates checked-out `git HEAD` directly and
ignores `GITHUB_SHA`. A local run with an injected all-zero `GITHUB_SHA` still
reported exact HEAD `6d6be9c...`, 549+6 tests and `publicationPerformed=false`.
One more hosted run was required before final identity could be accepted.

## Final exact-head qualification run

All mandatory jobs passed with checkout, artifact names and embedded summaries
bound to `357ca1418eb3786e861e4436fe55005c8c6456cd`.

## Required runs

| Workflow/job | Commit SHA | Run ID | Immutable URL | Result | Required evidence |
|---|---|---|---|---|---|
| CI / Full qualification (Linux) | `357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [job 101198969478](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/job/101198969478) | `PASS` | 4m14s; full clean verify, public install, 6+1 consumer, API/bundle/evidence, OSV zero results and bounded performance smoke |
| CI / Full qualification (Windows) | `357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [job 101198969235](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/job/101198969235) | `PASS` | 7m02s; full clean verify, public install, 6+1 consumer, API/bundle/evidence and clean file-repository consume |
| CI / Full qualification (macOS) | `357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [job 101198969500](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/job/101198969500) | `PASS` | 1m49s; full clean verify, public install, 6+1 consumer and API/bundle/evidence |
| CI / Ubuntu reproducibility | `357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [job 101200359697](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/job/101200359697) | `PASS` | 1m37s; two clean public builds, 23/23 byte-identical payloads and uploaded comparison evidence |
| CodeQL / Java-Kotlin | `357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462894` | [job 101198969102](https://github.com/kalayciburak/stackmate/actions/runs/33927462894/job/101198969102) | `PASS` | 5m58s; full tested construction, v4.37.9 analysis and SARIF upload; six test-fixture-only XSS paths triaged |
| Dependency review / PR | `357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462908` | [job 101198968939](https://github.com/kalayciburak/stackmate/actions/runs/33927462908/job/101198968939) | `PASS` | 1m06s; 20 additions, 14 removals, zero public runtime policy violations |
| Release preflight / Windows | `357ca1418eb3786e861e4436fe55005c8c6456cd` | no run | [workflow](https://github.com/kalayciburak/stackmate/actions/workflows/release-preflight.yml) | `NOT_RUN` | New manual workflow is unavailable until present on the default branch. Equivalent public dry-run and file-repository gates passed in the Windows PR job; no merge was made. |

## Uploaded artifacts

All six observed artifacts use seven-day retention.

| Artifact | Run ID | Download/attestation URL | SHA-256 | Retention expiry | Result |
|---|---|---|---|---|---|
| `qualification-Linux-357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [artifact 9957402406](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/artifacts/9957402406) | `3578e1f17ec5ad82704c947acc99aa4aebe4954a4f3dd557dd2e45a56363c2ee` | `2026-09-11T22:59:10Z` | `PASS` |
| `qualification-Windows-357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [artifact 9957464948](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/artifacts/9957464948) | `155766be3c10c4a73692dacbb8ab8b13b3dd473eb1f842edd76427eb3ce90785` | `2026-09-11T23:01:57Z` | `PASS` |
| `qualification-macOS-357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [artifact 9957348021](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/artifacts/9957348021) | `e6d2f3fb4299338e81872b2645819683fd20752da2570ec2a4a2db9dfdce3250` | `2026-09-11T22:56:45Z` | `PASS` |
| `reproducibility-Ubuntu-357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462896` | [artifact 9957504760](https://github.com/kalayciburak/stackmate/actions/runs/33927462896/artifacts/9957504760) | `f28edbaabed02a50a250eed27127c733bf07b1698516e6ae9d45588a495e7cef` | `2026-09-11T23:03:39Z` | `PASS` |
| `codeql-sarif-357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462894` | [artifact 9957434975](https://github.com/kalayciburak/stackmate/actions/runs/33927462894/artifacts/9957434975) | `e8c0e1c12eeb129d2cd6f5cfdaab7e3fb32594a5a3bdd5742924ac5d7fe18a87` | `2026-09-11T23:00:38Z` | `PASS` |
| `dependency-review-357ca1418eb3786e861e4436fe55005c8c6456cd` | `33927462908` | [artifact 9957331600](https://github.com/kalayciburak/stackmate/actions/runs/33927462908/artifacts/9957331600) | `4979ef8ecc939b578ef489e0cdb16ffeec4fdd97c3f99566a56aca1cfd15b4a0` | `2026-09-11T22:56:02Z` | `PASS` |
| `release-preflight-Windows-<commit>` | no run | no artifact | n/a | n/a | `NOT_RUN`; equivalent Windows PR evidence passed |

## Main completion rules

This ledger is filled only from observed runs for one exact final rc2 source
commit. Job-level conclusions, exact test counts, API/manifest hashes,
OSV/performance conclusions, artifact checksums and expiry are recorded. A
configured job was never treated as evidence of a passing run.

No entry in this file authorizes signing, Central deployment, release creation,
tag push, merge or any other publication action.
