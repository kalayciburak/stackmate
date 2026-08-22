#!/usr/bin/env python3
"""Validate official chat-template serialization without training or truncation."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path

from dataset_contract import ROOT


def percentile(values: list[int], fraction: float) -> int:
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", default="configs/environment-v1.json")
    parser.add_argument("--report", default="reports/training-format.json")
    arguments = parser.parse_args()
    environment = json.loads((ROOT / arguments.environment).read_text(encoding="utf-8"))
    setting = environment["baseModel"]["localPathEnvironment"]
    value = os.environ.get(setting, "").strip()
    if not value:
        raise SystemExit(f"BLOCKED: {setting} is not set; tokenizer download is forbidden")

    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(
        Path(value), local_files_only=True, trust_remote_code=False
    )
    stats_by_split: dict[str, dict[str, list[int]]] = {
        "train": {"total": [], "input": [], "output": []},
        "validation": {"total": [], "input": [], "output": []},
    }
    truncations = 0
    unknown_token_id = tokenizer.unk_token_id
    unknown_tokens = 0
    unknown_examples = 0
    bos_token_id = tokenizer.bos_token_id
    eos_token_id = tokenizer.eos_token_id
    bos_counts: list[int] = []
    eos_counts: list[int] = []
    eos_terminated = 0
    masked_prompt_tokens = 0
    unmasked_target_tokens = 0
    boundary_verified = True
    for filename in ("datasets/train.jsonl", "datasets/validation.jsonl"):
        split_stats = stats_by_split[Path(filename).stem]
        for line in (ROOT / filename).read_text(encoding="utf-8").splitlines():
            example = json.loads(line)
            messages = example["messages"]
            prefix = tokenizer.apply_chat_template(
                messages[:2],
                tokenize=True,
                add_generation_prompt=True,
                enable_thinking=False,
                return_dict=False,
            )
            full = tokenizer.apply_chat_template(
                messages,
                tokenize=True,
                add_generation_prompt=False,
                enable_thinking=False,
                return_dict=False,
            )
            if full[: len(prefix)] != prefix:
                raise ValueError(f"assistant boundary mismatch: {example['exampleId']}")
            boundary_verified = boundary_verified and full[: len(prefix)] == prefix
            if bos_token_id is not None and full.count(bos_token_id) > 1:
                raise ValueError(f"duplicated BOS: {example['exampleId']}")
            split_stats["total"].append(len(full))
            split_stats["input"].append(len(prefix))
            split_stats["output"].append(len(full) - len(prefix))
            if unknown_token_id is not None:
                unknown_count = full.count(unknown_token_id)
                unknown_tokens += unknown_count
                unknown_examples += int(unknown_count > 0)
            bos_counts.append(full.count(bos_token_id) if bos_token_id is not None else 0)
            eos_count = full.count(eos_token_id) if eos_token_id is not None else 0
            eos_counts.append(eos_count)
            eos_terminated += int(eos_token_id is not None and bool(full) and full[-1] == eos_token_id)
            masked_prompt_tokens += len(prefix)
            unmasked_target_tokens += len(full) - len(prefix)
            full_length = len(full)
            truncations += int(full_length > environment["maximumSequenceLength"])
    if truncations:
        raise ValueError(f"training examples exceed sequence bound: {truncations}")
    template = tokenizer.chat_template or ""

    def summarize(values: list[int]) -> dict[str, float | int]:
        return {
            "examples": len(values),
            "average": sum(values) / len(values),
            "p95": percentile(values, 0.95),
            "maximum": max(values),
        }

    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "tokenizerClass": type(tokenizer).__name__,
        "tokenizerRevision": environment["baseModel"]["revision"],
        "chatTemplateSha256": hashlib.sha256(template.encode()).hexdigest(),
        "maximumSequenceLength": environment["maximumSequenceLength"],
        "splits": {
            split: {
                "inputTokens": summarize(values["input"]),
                "outputTokens": summarize(values["output"]),
                "totalTokens": summarize(values["total"]),
            }
            for split, values in stats_by_split.items()
        },
        "truncationCount": truncations,
        "unknownTokenBehavior": {
            "unknownTokenId": unknown_token_id,
            "unknownTokenCount": unknown_tokens,
            "examplesWithUnknownToken": unknown_examples,
        },
        "bosEosBehavior": {
            "bosTokenId": bos_token_id,
            "eosTokenId": eos_token_id,
            "maximumBosPerExample": max(bos_counts),
            "totalEosTokens": sum(eos_counts),
            "examplesEndingWithEos": eos_terminated,
            "exampleCount": len(bos_counts),
        },
        "padding": {
            "paddingSide": tokenizer.padding_side,
            "truncationSide": tokenizer.truncation_side,
            "padTokenId": tokenizer.pad_token_id,
            "padToken": tokenizer.pad_token,
        },
        "assistantOnlyLabels": {
            "enabled": True,
            "promptTokensMasked": masked_prompt_tokens,
            "targetTokensUnmasked": unmasked_target_tokens,
            "boundaryVerified": boundary_verified,
        },
        "duplicatedBosEos": max(bos_counts) > 1,
    }
    report_path = (ROOT / arguments.report).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(report, separators=(",", ":")))


if __name__ == "__main__":
    main()
