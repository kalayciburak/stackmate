# Phase 5.3 parity corpus

Date frozen: 2026-08-22

## Scope

The parity corpus contains 40 Benchmark V2 development cases. It contains no V2
holdout, Phase 5 blind, Phase 5.1 locked-final, post-training challenge, or future
final case. It is development-only and may never be promoted to an untouched gate.

## Deterministic selection

The selection rule was fixed before runtime comparison:

1. Include every V2 development restraint and unsupported-claim trap.
2. Include at least three cases from each V2 domain.
3. Fill to 40 by one case per domain per round, preserving source-manifest order.

This produced:

| Domain | Cases |
|---|---:|
| General Java | 6 |
| Spring configuration | 6 |
| MVC | 6 |
| Database | 5 |
| Security | 5 |
| Network | 5 |
| Concurrency | 4 |
| Adversarial | 3 |

Composition:

| Property | Count |
|---|---:|
| Real runtime/integration | 31/40 |
| Synthetic wrapper/edge | 9/40 |
| Required restraint | 8/40 |
| Unsupported-claim traps | 9/40 |
| Explicit adversarial domain | 3/40 |

## Frozen artifacts

| Artifact | SHA-256 |
|---|---|
| Parity corpus | `fc07d86362b705bee5a493515180ac4daf687b3e820095142dd82bec09540bd8` |
| Benchmark V2 source manifest | `292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294` |
| Prompt split manifest | `147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07` |
| Prompt variants manifest | `1cd31f9a244f69d9e56c808a51a20ed621ed32785b00b582e4e5fe27bec0b474` |

`research/model-alignment/parity/corpus.json` stores the exact production
`InferenceRequest`, rendered production user prompt, expected cause/check IDs,
flags, forbidden claims, and P0/P3 system prompts for each case. The companion
manifest stores ordered IDs, counts, source hashes, and the corpus digest.

The Java exporter executes the existing controlled runtime/integration recipes and
passes their incidents through the production deterministic engine, sanitizer,
request factory, and prompt renderer. It refuses source-hash drift, locked IDs,
non-development cases, duplicate IDs, cardinality drift, and overwrite.
