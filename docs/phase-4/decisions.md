# Phase 4 decision log

## D1 - Gate order is binding

English-only product simplification completes before candidate downloads or new
model evaluation. Only screening finalists may enter the full benchmark and
persistent-worker lab. Only a `READY_CANDIDATE` may authorize a production adapter.
Provisioning remains out of scope for the entire phase.

## D2 - Historical evidence is immutable

Phase 1, Phase 2, and Phase 3 reports retain their original English/Turkish findings.
Current runtime localization may be removed without rewriting dated evidence.

## D3 - Continue ADR numbering

The requested Phase 4 ADR numbers 0015 and 0016 already belong to Phase 3. Phase 4
will use the next available identifiers beginning at 0017 rather than overwrite or
rename historical decisions.

## D4 - English-only is a pre-release breaking simplification

Runtime diagnostics, prompts, model validation, setup messages, and exports become
English-only. `README.tr.md` remains a complete Turkish user/developer guide. The
public language property and language-specific public types are removed instead of
retaining one-value abstractions.

## D5 - Threat-model assumptions

StackMate is a local developer library for servlet applications. Remote callers may
influence exception text through the host application, while model/native paths are
operator-controlled. No OS sandbox, authentication boundary, cloud inference, model
download, or production native worker is assumed in Gate A. These assumptions are
explicit because they materially lower remote code-execution likelihood while
leaving local supply-chain and denial-of-service risks relevant.

## D6 - Resolve the mandatory-reference parameter conflict transparently

Official metadata reports 2,274,069,824 total parameters for the multimodal
`Qwen/Qwen3.5-2B` Phase 3 reference. It therefore exceeds Phase 4's explicit
2,000,000,000 total-parameter cap. The exact artifact remains in the screening table
as an ineligible historical control, but it cannot become a finalist. Eligible
candidates are SmolLM2-360M-Instruct, Qwen3-0.6B, and Qwen2.5-1.5B-Instruct.
