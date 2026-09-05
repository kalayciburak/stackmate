# Phase 7 history security audit

## Decision

The repository remains private. The current public-facing documentation is
sanitized, while personal paths and public author identity metadata remain
recoverable from preserved Git history. No history rewrite was performed.

## Provenance

- Full-history anchor: `56dc1f8452da90f558807790c20d7cf1eac0fc00`
- RC3 source delta: `56dc1f8452da90f558807790c20d7cf1eac0fc00..8b40bac6dfb940324db638195766a67d5cdf7415`
- Scanner: official Gitleaks `8.30.1`
- Upstream: [official Gitleaks release](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1)
- Checksum manifest SHA-256: `061476c21adaf5441516f96f185c1a4706a83cd6329b9b38762271b3d4a52fae`
- Windows x64 archive SHA-256: `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e`
- Verified executable SHA-256: `17157e2ee8b76fc8b1d8bee607a250e34b8a8023c8bc81822d4b5ee4d78fcb7c`
- Reports were generated with full redaction in an external audit directory outside Git.

The checksum manifest matched the release asset digest. The downloaded archive
matched both the manifest entry and release asset digest. No repository content
was uploaded for scanning.

## Full reachable-history scope

The immutable local snapshot contained 17 local branches, 3 remote-tracking
references, and 18 annotated tags. These resolved to 35 unique root objects and
20 unique commit roots.

| Item | Count |
| --- | ---: |
| Reachable commits | 122 |
| Reachable objects | 3,699 |
| Blobs | 1,592 |
| Trees | 1,967 |
| Annotated tag objects | 18 |
| Blob bytes | 69,357,250 |

Gitleaks scanned 122 commits and approximately 63.89 MB of patch content. Tag
messages and the three reachable binary blobs were scanned separately.

## Triage

| Class | Result |
| --- | --- |
| `SECRET` | No confirmed secret. Sixteen detector matches were fully redacted and triaged. |
| `CONTROLLED_TEST_FIXTURE` | Five Gitleaks matches were sanitizer or qualification fixtures. Additional path, email, identifier, private-IP, and UNC shapes were controlled fixtures. |
| `RESEARCH_METADATA` | Eleven Gitleaks matches were tokenizer revisions, hashes, manifests, or evaluation metadata. Seventeen organization-name matches were negative scope statements, not operational data. |
| `FALSE_POSITIVE` | Numeric/hash substrings and dependency-version URL fragments matched auxiliary patterns without representing identifiers or private hosts. |
| `PERSONAL_PATH` | Seven distinct machine-local values appeared at 30 historical blob locations. Values are intentionally omitted. |
| `PUBLIC_AUTHOR_IDENTITY` | One distinct Git identity remained across 262 author, committer, and tagger metadata occurrences. Project contact metadata also remains in history. |

No model weights, adapters, GGUF files, safetensors files, Git LFS pointers,
sensitive filenames, or large binary blobs were reachable. The 24 blobs at
least 1 MiB were text research datasets or reports. Three small Python bytecode
blobs totaled 55,482 bytes and produced no Gitleaks finding.

## RC3 source delta

The exact RC3 source delta contains one commit, 26 changed paths, and 90 added
lines. Gitleaks produced zero findings. Auxiliary checks found no newly added
machine-local path, email, private IP, employer/private-system marker,
government domain, internal-service marker, binary, large blob, model artifact,
adapter, or Git LFS pointer. Two 11-digit numeric shapes failed the Turkish
identifier checksum and were classified as `FALSE_POSITIVE`.

The closing Phase7 documentation/evidence directory was separately scanned with the same pinned Gitleaks binary:
exit0, zero findings. JSON evidence was parsed and inspected for machine-local paths before staging.

## Residual history

Path-only sanitization changes the current documentation but intentionally does
not remove prior blob versions. Historical machine-local paths remain
recoverable in Git history, including old documentation snapshots and one
research utility script that is frozen and was not edited. Author, committer,
tagger, and project-contact metadata also remain intact.

Detector absence is not proof that an unknown, proprietary, encrypted, or
obfuscated secret cannot exist. The audit covered local reachable refs and tags;
it did not cover reflogs, unreachable objects, or refs that were never fetched.
