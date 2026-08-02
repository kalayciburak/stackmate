# Current Phase 5 prompt audit

Date: 2026-08-02

## Identity and shape

| Resource | Characters | Estimated tokens | SHA-256 |
|---|---:|---:|---|
| English system prompt | 1,270 | 318 | `e24474ab0ea51997d149aeb48df35ca86f06ae181636c252d93c41b654e6beb9` |
| English repair prompt | 436 | 109 | `38e696015b5b266949661b7f018737cdc2bb057e0d2f7b6f04038360244d2057` |

Token estimates use the declared reproducible approximation `ceil(characters / 4)`;
they are comparison estimates, not tokenizer-exact counts.

## System-prompt structure

The prompt has four blocks: role, untrusted-input prohibitions, deterministic
authority/ID constraints, allowed tasks, and JSON/schema requirements. Safety rules
precede the positive task list. The prompt is compact, English-only, JSON-only, and
does not contain an explicit `be helpful` instruction.

### Present controls

- Incident values are untrusted and cannot issue instructions.
- Tool, file, source, repository, network, and command claims are forbidden.
- Deterministic inputs and supplied IDs are authoritative.
- Empty selection plus a limitation is mentioned for insufficient evidence.
- Output is bounded to English JSON with no hidden reasoning or destructive command.

### Missing or weak controls

- Unsupported helpfulness is not named as an incorrect answer.
- The supplied world is not explicitly closed; outside technical knowledge is not
  operationally treated as nonexistent.
- Direct evidence support is not expressed as a step-by-step decision procedure.
- The prompt does not say that empty output selections are a successful result.
- It does not define when evidence is insufficient or require a specific limitation
  code such as `EVIDENCE_INSUFFICIENT`.
- It does not forbid selecting generic fallback IDs merely to avoid an empty result.
- It does not tie check selection to a selected, directly supported cause.
- No positive or negative few-shot examples demonstrate unsupported alternatives.
- `state the limitation` competes with the later generic schema example whose
  `limitations` value is empty; a small model can copy the final shape.
- The separate lab bounds say to use an empty limitations array unless evidence is
  insufficient, but still do not provide an operational insufficiency test.

## Instruction-order and conflict review

The core rules are not directly contradictory. The practical ambiguity comes from
priority and demonstration: the strongest concrete pattern near the end is a JSON
object with `limitations: []`, while abstention is one clause in a longer authority
paragraph. The allowed task list also says to select causes/checks without stating
that selecting nothing is equally successful. This can implicitly reward answer
production despite the earlier caution.

The term `infer` is also broad. `Never create, change, or infer one` follows a list
of fixed objects, but does not clearly prohibit adding a natural-language causal
alternative composed of ordinary technical nouns. The three Phase 5 failures use
known English words rather than unknown IDs or fabricated Java identifiers, so the
current validator and prompt both permit them.

## Few-shot, repetition, and size

There are no few-shot or negative examples. The prompt repeats some prohibitions
but repeats the abstention rule only once. Its size is not itself excessive; Phase
5's 100/100 structure result suggests that semantic priority, not basic instruction
capacity, is the leading prompt hypothesis.

## Failure-to-weakness map

| Phase 5 finding | Prompt hypothesis | Strength |
|---|---|---|
| Ten restraint cases omit `limitations` | Final schema demonstrates an empty array and no exact code/procedure is given | STRONG |
| Four restraint cases choose generic fallback IDs | Generic fallback is not explicitly forbidden | STRONG |
| Pool timeout adds driver issues | Outside technical alternatives are not framed as nonexistent/incorrect | PLAUSIBLE |
| Circular dependency adds missing bean | No negative example distinguishes supplied evidence from a plausible sibling Spring cause | PLAUSIBLE |
| Foreign-key failure adds data/type alternatives | Natural-language claims need no evidence mapping under the current contract | STRONG |
| 100/100 valid JSON | Structure is already learned and is not the Phase 5 root failure | STRONG |
| 94/100 exact selections | Prompt changes may regress prioritization while improving restraint | PLAUSIBLE |
| 91/100 useful explanations | A shorter closed-world prompt may reduce value-add; this must be measured | PLAUSIBLE |
| All three unsupported claims are prompt-caused | The validator also lacks generic semantic containment, so prompt causality alone is unproved | UNSUPPORTED |

## Repair-prompt audit

The repair prompt is structural in wording, labels the previous output untrusted,
requires all keys, limits output to supplied IDs, and forbids new claims. However,
the current Phase 5 lab attempts repair after any failed validation, including a
semantic issue. That execution policy is broader than the prompt's safe role.

Phase 5.1 policy:

- repair JSON syntax, required fields, enum/ID grammar, and length only;
- never repair a semantic-grounding rejection into acceptance;
- never add selections, facts, explanations, or technical claims;
- independently validate the repaired response and count both attempts;
- fall back immediately for semantic rejection.

## Audit conclusion

Prompt weakness is a credible cause of the restraint failures but not yet a proven
explanation for all semantic hallucinations. P1-P3 should test increasing operational
specificity while the validator and all other controls remain fixed.
