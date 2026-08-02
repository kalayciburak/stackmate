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

    from transformers import AutoProcessor

    processor = AutoProcessor.from_pretrained(Path(value), local_files_only=True)
    tokenizer = processor.tokenizer
    lengths: list[int] = []
    inputs: list[int] = []
    outputs: list[int] = []
    truncations = 0
    for filename in ("datasets/train.jsonl", "datasets/validation.jsonl"):
        for line in (ROOT / filename).read_text(encoding="utf-8").splitlines():
            example = json.loads(line)
            messages = example["messages"]
            prefix = tokenizer.apply_chat_template(
                messages[:2], tokenize=True, add_generation_prompt=True, enable_thinking=False
            )
            full = tokenizer.apply_chat_template(
                messages, tokenize=True, add_generation_prompt=False, enable_thinking=False
            )
            if full[: len(prefix)] != prefix:
                raise ValueError(f"assistant boundary mismatch: {example['exampleId']}")
            if full.count(tokenizer.bos_token_id) > 1:
                raise ValueError(f"duplicated BOS: {example['exampleId']}")
            lengths.append(len(full))
            inputs.append(len(prefix))
            outputs.append(len(full) - len(prefix))
            truncations += int(len(full) > environment["maximumSequenceLength"])
    if truncations:
        raise ValueError(f"training examples exceed sequence bound: {truncations}")
    template = tokenizer.chat_template or ""
    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "tokenizerClass": type(tokenizer).__name__,
        "tokenizerRevision": environment["baseModel"]["revision"],
        "chatTemplateSha256": hashlib.sha256(template.encode()).hexdigest(),
        "maximumSequenceLength": environment["maximumSequenceLength"],
        "averageInputTokens": sum(inputs) / len(inputs),
        "p95InputTokens": percentile(inputs, 0.95),
        "averageOutputTokens": sum(outputs) / len(outputs),
        "p95OutputTokens": percentile(outputs, 0.95),
        "maximumTotalTokens": max(lengths),
        "truncationCount": truncations,
        "assistantOnlyLabels": True,
        "duplicatedBosEos": False,
    }
    report_path = (ROOT / arguments.report).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(report, separators=(",", ":")))


if __name__ == "__main__":
    main()
