# Phase 7 RC identity and qualification

Gate D: PASS for RC3. RC3 was required by the release-preflight fix; branch normalization alone did not require it.

| Identity | Value |
| --- | --- |
| RC2 source, preserved | `357ca1418eb3786e861e4436fe55005c8c6456cd` |
| RC2 tag object, unchanged | `1b51e78d40b5dfe1e1b22c9fdb24756eef0e57f1` |
| Active candidate | `v0.1.0-rc.3` |
| RC3 exact source | `8b40bac6dfb940324db638195766a67d5cdf7415` |
| RC3 local annotated-tag object | `7be10f599f56c59ca48ad67e048feeb030ef0e47` |
| Public version / allowlist | `0.1.0-rc.3`; parent, core, auto-configuration, starter only |
| Public API | 73 types; `41f718f0479f10daf502012a54b20e7569699d1318efe4fe0b91b95b9878bea7` |
| Local bundle manifest | `be88384fcd6847b0d9e95d8aada1e56d90d5bf47d36f343f04ac97c185f4da9a` |
| Hosted Linux / macOS manifest | `07c5db7f3fdb01cb7e168a47f450e832b62099ee3d7efbefbe3c5d83c46dd6f6` |
| Hosted Windows manifest | `efdffd12908b087c309d567838176140ded54d1821c6691cc95ca54c9d077965` |

RC1/RC2/RC3 remain local-only tags; no final `v0.1.0` tag was created. The Phase7 branch is pushed privately.
The tag was created only after green exact-source hosted and final local verification. Subsequent closing
documentation/privacy-path edits are evidence-only and are not represented as content of the source tag.

## Exact-source hosted runs

- [CI33962139971](https://github.com/kalayciburak/stackmate/actions/runs/33962139971): Linux, Windows, macOS and
  Ubuntu reproducibility PASS; each qualification has549 Surefire+6 Failsafe, zero failures/errors/skips.
- [CodeQL33962139978](https://github.com/kalayciburak/stackmate/actions/runs/33962139978): PASS. Existing historical
  test-fixture alerts are not reclassified as clean security findings or suppressed by this phase.
- [Dependency review33962139905](https://github.com/kalayciburak/stackmate/actions/runs/33962139905): PASS.
- [Manual release preflight33962139468](https://github.com/kalayciburak/stackmate/actions/runs/33962139468): PASS;
  exact-source public install, API/bundle audit and isolated file-repository consumer6+1,13 payloads,26 sidecars.

Local full verification was repeated after documentation review:543+5; standalone consumer6+1. The collector
records source8b40bac. The earlier pre-commit report is retained separately and is not cited as an exact-commit run.
Both local and hosted Ubuntu two-build comparisons cover23 payloads with zero differences. OSV public SBOM results0.

Cross-platform parity is logical, not raw bundle equality. All99 compiled class entries match. Known CRLF/LF
resource and generated-documentation/archive differences explain the raw Windows/local hashes; there are no unknown
main-JAR content differences. Linux and macOS raw manifests match. See the bounded artifact evidence, including
per-file SHA-256 inventories, for the exact comparison rather than treating all platform hashes as interchangeable.

Machine-readable records: [artifact parity](evidence/artifact-parity.json),
[local two-build comparison](evidence/local-reproducibility.json),
[local artifact manifest](evidence/local-artifact-manifest.json).
