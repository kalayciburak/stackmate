# Fresh-family novelty review

Gate B selected exactly 30 proposals after comparing them against 318 exposed
records and 298 canonical family IDs.

| Group | Families | Main distinctions |
|---|---:|---|
| JDK runtime | 8 | write overflow, recursive map compute, MethodHandle adaptation, output serialization, encoder direction, compression framing, stamp capability, bounded queue |
| Spring/MVC/Jackson | 8 | expression grammar, property mutability, route grammar, header/cookie/multipart resolvers, serializer discovery, cyclic graph |
| Local network | 2 | listener bind collision and HTTP status-line parsing |
| PostgreSQL | 6 | exclusion constraints, RLS tuple policy, unbound parameter, wrong object class, overload ambiguity, serializable conflict |
| Synthetic/adversarial | 6 | candidate-text injection, false secret classification, JPMS unsupported alternative, ordinal trap, missing-unit ambiguity, negative-evidence exhaustion |

All declared closest-family IDs resolve to the existing inventory. No new ID or
recipe equals an existing ID/recipe, and no proposal crossed both automated
similarity limits.

```text
Maximum recipe character 5-gram Jaccard: 0.214286
Maximum recipe token 3-gram Jaccard: 0.030769
Maximum task character 5-gram Jaccard: 0.246011
Maximum task token 3-gram Jaccard: 0.026087
Character rejection threshold: 0.88
Token rejection threshold: 0.80
Automated rejections: 0
```

The rationale for every family is embedded in the descriptor manifest and the
machine-readable novelty report. Automated similarity supports but does not
replace the two-pass semantic/reproduction review.

Rejected ideas included PostgreSQL deadlock and lock timeout, Hikari acquisition
timeout, ordinary configuration binding, malformed URL, connection reset,
filesystem permission cases, and in-process OOM because they duplicated prior
mechanisms or were not sufficiently deterministic and safe.

Artifact hashes:

```text
Descriptor design: abedc872456538290144aadc8b416f80dc431bc70cd473646431687106cf311a
Resolved task: d5a8c8cc106159a31014f3ecbe1288da722c30ebacfc9d0e5a185df1a296b36c
Novelty report: f11c0ff42297d43920694156b55da80a15c87c843c331bae5dcc7a15de261a7c
```
