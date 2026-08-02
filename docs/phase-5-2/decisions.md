# Phase 5.2 decisions

Date: 2026-08-02

## D1: Preserve the gated stop rules

Status: accepted.

Challenge and leakage controls are frozen before training. Adapter checkpoints are
selected only against generated family-held-out validation data. Existing holdout,
blind, Phase 5.1 locked outputs, and the new challenge set cannot participate in
training or checkpoint selection.

## D2: Isolate machine-learning dependencies

Status: accepted.

Training code lives under `research/model-alignment`. Python, PyTorch,
Transformers, PEFT, Accelerate and bitsandbytes are not Maven dependencies and are
not needed by normal Java CI.

## D3: Prefer WSL2 QLoRA

Status: provisional until smoke validation.

The host exposes an RTX 4070 Ti SUPER with 16 GB VRAM to Ubuntu WSL2. The initial
method is 4-bit NF4 QLoRA. Full-parameter tuning is forbidden. BF16/FP16 LoRA is a
fallback only if measured memory permits it and QLoRA has a documented technical
incompatibility.

## D4: Keep training bounded

Status: accepted.

The matrix is `F0` base validation plus at most `F1` rank-8 and `F2` rank-16
adapters. A third adapter run is allowed only for one isolated data-balance or
learning-rate change. Locked disappointment cannot authorize retraining.

## D5: Do not adopt a new runtime contract

Status: accepted.

Schema version 2, the Phase 5.1 validator, English-only output, and P3 remain the
primary evaluation contract. No persistent worker, production adapter, model
provisioning, or model publication is part of this phase.

## D-004: Use a compact synthetic family-split dataset for F1/F2

Status: accepted.

Dataset v1 uses 1,000 controlled synthetic examples across 40 independently
named template families. Eight families are validation-only. This produces the
requested 45/25/25/5 behavior distribution without training on locked messages,
model outputs, private logs, or opaque generated prose.

## D-005: Separate generation from leakage inspection

Status: accepted.

The generator has no locked-corpus dependency. A separate fail-closed audit may
read frozen manifests and fixture source only to compare hashes, identifiers,
normalized fields, signatures, families, and character 5-grams. It emits no
locked content into training data.
