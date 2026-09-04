# Phase 6.1 remote-run ledger

No remote run has been executed or observed for the rc2 working tree. Every ID,
URL and result in this ledger is intentionally `PENDING` / `UNVERIFIED`.

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
