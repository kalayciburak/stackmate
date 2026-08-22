# Phase 5.3 leakage audit

Date: 2026-08-22

## Result

Gate C leakage status: **PASS**.

The offline auditor compared the frozen Phase 5.3 catalog and 180 generated
development rows with Phase 5.2 alignment data, all Benchmark V2 cases, Phase 5
blind cases, the Phase 5.1 locked split, the post-training challenge, the 40-case
parity corpus, and the 30 sealed new final-family descriptors.

| Prohibited condition | Count |
|---|---:|
| Manifest or row errors | 0 |
| Duplicate row IDs | 0 |
| Split/family/variant violations | 0 |
| Restricted family/recipe identifier reuse | 0 |
| Exact normalized text or semantic descriptor | 0 |
| Cross-split normalized text | 0 |
| Prohibited near duplicate | 0 |
| Compound candidate/evidence reuse | 0 |
| Private path or secret | 0 |
| Outstanding manual review | 0 |

The largest nearest-match scores were `0.541913` for character 5-grams and
`0.596899` for token 3-grams, below the fail thresholds `0.88` and `0.80`.
The semantic-review threshold was `0.60`; no final item reached it.

The report records 1,073 candidate/evidence signature reuses and 370 exception
chain overlaps for auditability. Reuse is expected because all corpora consume the
same deterministic analyzer vocabulary. It becomes prohibited only when the same
signature also shares a trigger primitive or exception-chain signature with the
same source record; that compound count is zero. Trigger-primitive overlap is zero.

## Pre-freeze failures retained

- Attempt 1 failed before comparison because CLI paths were incorrectly supplied
  relative to the alignment directory. Report SHA-256:
  `f80cf5263236900104e73a7f64cb47749bad3178c2ce577bb338de935875b746`.
- Attempt 2 correctly found four compound SocketException candidate-signature
  overlaps and two synthetic semantic near-matches. Report SHA-256:
  `89662a53ec5818698339f123a3dc631e147251e58ca62e632fd0217ca5fbb949`.
- A pre-review PASS was not accepted as final because changing all review states
  altered the source manifest. Report SHA-256:
  `b33c0b4551acdf67f34e33505e80296dc46d73d3e15b90474307647659dc930b`.

These intermediate reports and rejected generated datasets are retained under the
ignored local `<WSL_CACHE>/training/phase-5-3/` area, not Git.

## Final provenance

- Auditor SHA-256:
  `46f32fecec42d0e914e017b4d1260e357356b9fae174eacac1b49860e2ed9e50`
- Final report SHA-256:
  `63ab07efbd7b2018fab4c98f17dd4aa81654921462803540eb2ffa72fce8a5b4`
- Corpus manifest SHA-256:
  `42d2bab37173a9b8a7579203e270a02fbe9ab795b12b9a731f39fa035a8244ee`
- TRAIN SHA-256:
  `ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93`
- VALIDATION SHA-256:
  `bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789`

The audit performed no network request and wrote only its exclusive local report.
