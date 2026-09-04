# Phase 6.1 artifact parity

## Public coordinate set

The local rc2 public-release and file-repository checks cover exactly:

- `stackmate-parent`;
- `stackmate-core`;
- `stackmate-spring-boot-autoconfigure`; and
- `stackmate-spring-boot-starter`.

Inference, research, sample, benchmark and test-only modules are not part of the
public coordinate set.

## Local parity evidence

| Check | Result |
|---|---|
| Public-release build/install | `PASS` |
| Core and auto-configuration main/sources/Javadoc artifacts | `PASS` |
| Dependency-only starter main JAR | `PASS` |
| Starter README-containing placeholder sources/Javadoc JARs | `PASS`, built and installed |
| Public API inventory | `PASS`: 73 types |
| Public API signature | `PASS`: `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7` |
| Local rc2 bundle manifest | `PASS`: `61a806934c50c8c89eb0d4b5f5f8eeab816ac9ce0a304c55b9d3adbb89eb3a1f` |
| Local `file://` deploy | `PASS`: parent/core/auto-configuration/starter deployed; starter sources/Javadoc present |
| Clean Maven repository consumption | `PASS`: resolved from `stackmate-file`; consumer passed 6 Surefire + 1 Failsafe |
| MD5/SHA1 validation | `PASS`: 13 required payloads and 26 checksum sidecars recalculated and matched |
| Local reproducibility | `PASS`: 23 payload files compared, zero differences |

The local hashes establish the Windows baseline. Remote parity was calculated
from downloaded artifacts bound to one exact source commit.

## Remote parity

| Platform/path | API hash | Bundle manifest | Attachment set | Consumer | Result |
|---|---|---|---|---|---|
| Linux qualification | `41f718f0...9878bea7` | `b283738f...c8c1ce53` | main/sources/Javadoc present | 6+1 `PASS` | `PASS` |
| Windows qualification | `41f718f0...9878bea7` | `52d30b06...bbba95ed` | main/sources/Javadoc present | 6+1 `PASS` | `PASS`; raw line-ending differences classified |
| macOS qualification | `41f718f0...9878bea7` | `b283738f...c8c1ce53` | main/sources/Javadoc present | 6+1 `PASS` | `PASS` |
| Windows release preflight/file repository | same public API | local file-repository report | 13 payloads, 26 checksum sidecars | 6+1 `PASS` | `PASS` in PR job; manual workflow `NOT_RUN` |

No artifact has been signed, uploaded to Central or published as a release.

## Fifth-run downloaded artifact audit

Run `33926362199` artifacts were downloaded outside Git under the user cache.
All three platforms reported 549 Surefire + 6 Failsafe tests and no
failure/error/skip. All reported 73 public API types and signature SHA-256
`41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7`.

Linux and macOS produced byte-identical core, auto-configuration and starter
main JARs. Windows had the same entry counts and zero differing `.class`
entries. Its raw archive hashes differed only in line-ending/metadata entries:
`LICENSE`, `NOTICE`, `pom.properties`, and the auto-configuration imports file.
After CRLF normalization, public API JSON and all three runtime dependency-tree
files were identical across the three OSes. Linux/macOS bundle manifest was
`b283738fd53d886c5b3677366c1d8928de3e4e2ea006aee0e3f5b0e7c8c1ce53`;
Windows was `52d30b06e2a6fefc2bd247f916c425c5acc494afbdfb3bb697f5e453bbba95ed`
because the bundle hashes those platform line endings.

This parity result is semantically acceptable but not final evidence because
the summary `commit` field recorded the PR merge SHA. The corrected collector
must reproduce the result for the final exact head.

Final run `33927462896` reproduced the same parity with every summary bound to
`357ca1418eb3786e861e4436fe55005c8c6456cd`. No research dependency or
coordinate entered the four-artifact public allowlist.
