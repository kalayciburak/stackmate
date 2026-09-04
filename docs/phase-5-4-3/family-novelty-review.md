# Fresh-family novelty review

Gate B selected exactly 30 proposals after comparing them against 318 exposed
records and 298 canonical family IDs.

| Group | Families | Main distinctions |
|---|---:|---|
| JDK runtime | 8 | write overflow, service/selector lifecycle, output serialization, encoder direction, compression framing, stamp capability, bounded queue |
| Spring/MVC/Jackson | 8 | expression grammar, property mutability, route grammar, header/cookie/multipart resolvers, serializer discovery, cyclic graph |
| Local network | 2 | listener bind collision and HTTP status-line parsing |
| PostgreSQL | 6 | exclusion constraints, RLS tuple policy, unbound parameter, wrong object class, overload ambiguity, serializable conflict |
| Synthetic/adversarial | 6 | candidate-text injection, secret bait, unsupported alternative, ordinal trap, ambiguous restraint, simultaneous candidate bounds |

All declared closest-family IDs resolve to the existing inventory. No new ID or
recipe equals an existing ID/recipe, and no proposal crossed both automated
similarity limits.

```text
Maximum character 5-gram Jaccard: 0.214141
Maximum token 3-gram Jaccard: 0.011111
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
Descriptor design: 6d5ca00867954028561e8777acc2d592f4d0373dc764f44654f96f51e278e228
Novelty report: 39bdfeb257232c1ba6b8bdb194f732a9f19eb77cdfa923849d5c98b621e88149
```
