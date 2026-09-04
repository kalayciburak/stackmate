# Phase 6.1 release-candidate identity

## Frozen rc1

| Field | Value |
|---|---|
| Tag | `v0.1.0-rc.1` |
| Commit | `248476f68ed3a3aa49e9fa1cba33d68a746a5469` |
| Remote presence | Absent |
| Disposition | Frozen local-only checkpoint; will not be moved, reused or pushed |

Workflow hardening, release metadata changes and Central-compatible starter
attachments were made after the rc1 checkpoint. Those changes require rc2 and
cannot be attached to rc1 without falsifying its immutable identity.

## Working rc2

| Field | Value |
|---|---|
| Maven version | `0.1.0-rc.2` |
| Final commit SHA | `PENDING` |
| Local tag | `PENDING` / not created |
| Remote branch/ref | `PENDING` / absent for this working state |
| Remote tag | `PENDING` / not created or pushed |
| Remote workflow identity | `PENDING` / `UNVERIFIED` |
| Pre-push local bundle manifest | `61a806934c50c8c89eb0d4b5f5f8eeab816ac9ce0a304c55b9d3adbb89eb3a1f` |

The local bundle is not yet a qualified remote identity because the final
commit and hosted run set do not exist. Its hash may change when evidence-only
documents are finalized after the successful exact-commit hosted run.

The frozen public API contains 73 types with signature SHA-256
`41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.

## Main update after authorized remote qualification

Main must bind all evidence to one exact final rc2 commit by filling the final
commit, branch/ref, workflow run IDs and URLs, artifact checksums and retention
expiry. If a tag is later authorized, record its object/target identity and
verify that it resolves to that same qualified commit before any release work.

No tag creation or push, branch push, PR, merge, signing, Central upload,
publication or GitHub Release is authorized or claimed here.
