# Phase 5.4.3 fresh-final corpus

The final corpus contains exactly 30 families: 24 controlled real
runtime/integration families and six controlled synthetic/adversarial families.
No model output was used to design a family, candidate, or expected T1 label.

| Group | Count | Final mechanisms |
|---|---:|---|
| JDK runtime | 8 | ByteBuffer write overflow, recursive same-key ConcurrentHashMap compute, incompatible MethodHandle adaptation, object-output eligibility, encoder unmappable input, GZIP framing, StampedLock stamp capability, bounded-queue capacity |
| Spring/MVC/Jackson | 8 | SpEL grammar, bean property mutability, path grammar, required header/cookie/multipart part, empty-bean serialization, direct self-reference |
| Local network | 2 | loopback bind ownership and malformed HTTP status line |
| PostgreSQL | 6 | exclusion constraint, RLS WITH CHECK, bind-count protocol violation, wrong object type, overload ambiguity, serializable conflict |
| Synthetic/adversarial | 6 | candidate-text injection, false secret classification, JPMS opens boundary, ordinal wording trap, missing timing units, negative-evidence candidate exhaustion |

The scoring-only split contains 19 DIAGNOSE and 11 RESTRAINT families. Every
DIAGNOSE label has one directly supported cause and one tied check. RESTRAINT
families have no directly supported offered cause and require exact
`EVIDENCE_INSUFFICIENT` output.

Candidate ordering is label-table-free. ORIGINAL order sorts candidate IDs by
SHA-256 over a fixed salt, family ID, candidate kind, and candidate ID.
PERMUTED is a one-position rotation of ORIGINAL. The final ORIGINAL answer
positions are balanced:

```text
Cause positions: 0=6, 1=6, 2=7
Check positions: 0=7, 1=6, 2=6
Supported cause/check moved: 19/19
```

Final pre-freeze hashes:

```text
Source descriptors  abedc872456538290144aadc8b416f80dc431bc70cd473646431687106cf311a
Resolved descriptors d5a8c8cc106159a31014f3ecbe1288da722c30ebacfc9d0e5a185df1a296b36c
Generation inputs   8d1020d58bb31303bc6649a6a7088084091d80d5ae3287eb289220e20b02ffe8
Scoring labels      c44e481fe9ea1a019e1bfdc7bd1360428580b98808ccc605e5c82dc224e10fd3
```
