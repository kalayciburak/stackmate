#!/usr/bin/env python3
"""Audit Phase 5.5.2 dataset tokenization with the official local tokenizer."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
from typing import Any

import audit_binary_label_tokens as binary


ROOT = Path(__file__).resolve().parents[1]
REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
CHAT_TEMPLATE_SHA256 = "273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80"
EXPECTED = {
    "prompt": "fc4c3569a17ab5b902b88c6105f5b0d7587087eabd129b779395c7400ce01ddb",
    "labelAudit": "da03c29aba32416ad81fa14413861751ea58f1a5c9b7d2d9c1dab150128a962e",
    "ontology": "f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793",
    "manifest": "3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a",
}
EXPECTED_ROWS = {"TRAIN": 426, "VALIDATION": 186}
CONVENTIONAL_LIMITS = (128, 256, 512, 1024, 2048)


class TokenizationError(ValueError):
    """Raised before model loading when tokenization integrity fails."""


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise TokenizationError(f"duplicate JSON key: {key!r}")
        value[key] = item
    return value


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(
        path.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicate_keys
    )
    if not isinstance(value, dict):
        raise TokenizationError(f"JSON object required: {path.name}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line:
            continue
        value = json.loads(line, object_pairs_hook=reject_duplicate_keys)
        if not isinstance(value, dict):
            raise TokenizationError(f"JSONL object required: {path.name}:{number}")
        rows.append(value)
    return rows


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values: list[int], quantile: float) -> int:
    if not values:
        raise ValueError("percentile requires values")
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * quantile) - 1)]


def distribution(values: list[int]) -> dict[str, int | float]:
    if not values:
        raise ValueError("distribution requires values")
    return {
        "count": len(values),
        "minimum": min(values),
        "average": round(sum(values) / len(values), 3),
        "p95": percentile(values, 0.95),
        "maximum": max(values),
    }


def safe_limit(maximum: int) -> tuple[int, int]:
    required = math.ceil(maximum * 1.10)
    for limit in CONVENTIONAL_LIMITS:
        if limit >= required:
            return limit, required
    raise TokenizationError(
        f"no <=2048 conventional limit has 10 percent margin over {maximum}"
    )


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument(
        "--dataset-dir",
        type=Path,
        default=ROOT / "datasets" / "atomic-contrastive-v1",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=ROOT / "reports" / "phase-5-5-2-tokenization-audit.json",
    )
    return parser.parse_args()


def main() -> None:
    args = arguments()
    base = args.base_model.expanduser().resolve()
    dataset = args.dataset_dir.expanduser().resolve()
    report_path = args.report.expanduser().resolve()
    if report_path.exists():
        raise FileExistsError(f"CREATE_NEW refused existing report: {report_path.name}")
    if not report_path.parent.is_dir():
        raise TokenizationError("report parent does not exist")
    if not base.is_dir() or base.name != REVISION:
        raise TokenizationError("base model is not the exact official revision")
    chat_template = base / "chat_template.jinja"
    if (
        not chat_template.is_file()
        or sha256_file(chat_template) != CHAT_TEMPLATE_SHA256
    ):
        raise TokenizationError("official chat template drifted")

    paths = {
        "prompt": ROOT / "prompts" / "binary-evidence-support-v1.txt",
        "labelAudit": ROOT / "reports" / "phase-5-5-1-binary-label-token-audit.json",
        "ontology": ROOT / "ontology" / "atomic-candidates-v1.json",
        "manifest": dataset / "manifest.json",
    }
    actual_hashes = {key: sha256_file(path) for key, path in paths.items()}
    if actual_hashes != EXPECTED:
        raise TokenizationError(f"frozen input hash drift: {actual_hashes}")
    manifest = read_json(paths["manifest"])
    label_audit = read_json(paths["labelAudit"])
    label_ids = {
        label: item["tokenId"] for label, item in label_audit["labelMapping"].items()
    }
    if label_ids != {"0": 15, "1": 16}:
        raise TokenizationError("binary label token IDs drifted")

    os.environ.update(
        {
            "HF_HUB_OFFLINE": "1",
            "TRANSFORMERS_OFFLINE": "1",
            "HF_HUB_DISABLE_TELEMETRY": "1",
            "DISABLE_TELEMETRY": "1",
        }
    )
    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(
        base, local_files_only=True, trust_remote_code=False
    )
    split_rows = {
        "TRAIN": read_jsonl(dataset / "train.jsonl"),
        "VALIDATION": read_jsonl(dataset / "validation.jsonl"),
    }
    split_report: dict[str, Any] = {}
    all_full_lengths: list[int] = []
    total_unknown = 0
    duplicate_special_tokens = 0
    truncations = 0
    for split, rows in split_rows.items():
        if len(rows) != EXPECTED_ROWS[split]:
            raise TokenizationError(f"{split} row count drifted")
        prompt_lengths: list[int] = []
        full_lengths: list[int] = []
        generation_positions: list[int] = []
        unknown_count = 0
        for number, row in enumerate(rows, 1):
            messages = row.get("messages")
            if not isinstance(messages, list) or len(messages) != 3:
                raise TokenizationError(f"{split}:{number} messages drifted")
            prompt_messages = messages[:2]
            label = messages[2].get("content")
            if label not in label_ids:
                raise TokenizationError(f"{split}:{number} label drifted")
            prefix = binary.token_ids(
                tokenizer.apply_chat_template(
                    prompt_messages,
                    tokenize=True,
                    add_generation_prompt=True,
                    enable_thinking=False,
                    return_dict=True,
                )
            )
            rendered_prefix = tokenizer.apply_chat_template(
                prompt_messages,
                tokenize=False,
                add_generation_prompt=True,
                enable_thinking=False,
            )
            encoded_prefix = tokenizer.encode(
                rendered_prefix, add_special_tokens=False
            )
            if prefix != encoded_prefix:
                duplicate_special_tokens += 1
            full = binary.token_ids(
                tokenizer.apply_chat_template(
                    messages,
                    tokenize=True,
                    add_generation_prompt=False,
                    enable_thinking=False,
                    return_dict=True,
                )
            )
            if full[: len(prefix)] != prefix:
                raise TokenizationError(f"{split}:{number} assistant boundary drifted")
            expected_suffix = [label_ids[label], *binary.ASSISTANT_TERMINATION_IDS]
            if full[len(prefix) :] != expected_suffix:
                raise TokenizationError(f"{split}:{number} assistant suffix drifted")
            prompt_lengths.append(len(prefix))
            full_lengths.append(len(full))
            generation_positions.append(len(prefix) - 1)
            all_full_lengths.append(len(full))
            if len(full) > 2048:
                truncations += 1
            if tokenizer.unk_token_id is not None:
                unknown_count += sum(
                    token == tokenizer.unk_token_id for token in prefix
                )
        total_unknown += unknown_count
        split_report[split] = {
            "rows": len(rows),
            "inputPromptTokens": distribution(prompt_lengths),
            "fullTrainingTokens": distribution(full_lengths),
            "assistantGenerationPositions": distribution(generation_positions),
            "unknownTokens": unknown_count,
            "targetTokenLength": 1,
            "assistantTerminationTokensMasked": 2,
        }

    maximum = max(all_full_lengths)
    selected_limit, required_with_margin = safe_limit(maximum)
    checks = {
        "labelTokensExactlyOne": True,
        "assistantSuffixStable": True,
        "truncations": truncations,
        "duplicatedSpecialTokens": duplicate_special_tokens,
        "unknownTokens": total_unknown,
        "maximumAtOrBelow2048": maximum <= 2048,
        "tenPercentMarginAvailable": selected_limit >= required_with_margin,
    }
    if (
        truncations
        or duplicate_special_tokens
        or total_unknown
        or not checks["maximumAtOrBelow2048"]
        or not checks["tenPercentMarginAvailable"]
    ):
        raise TokenizationError(f"tokenization gate failed: {checks}")
    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "baseRevision": REVISION,
        "tokenizerClass": tokenizer.__class__.__name__,
        "chatTemplateSha256": CHAT_TEMPLATE_SHA256,
        "inputHashes": actual_hashes,
        "datasetFiles": manifest["files"],
        "labelMapping": {
            "0": {"semantic": "NOT_SUPPORTED", "tokenId": 15},
            "1": {"semantic": "SUPPORTED", "tokenId": 16},
        },
        "splits": split_report,
        "maximumFullSequenceTokens": maximum,
        "minimumWithTenPercentMargin": required_with_margin,
        "selectedMaximumSequence": selected_limit,
        "checks": checks,
        "bosEosHandling": {
            "bosToken": tokenizer.bos_token,
            "bosTokenId": tokenizer.bos_token_id,
            "eosToken": tokenizer.eos_token,
            "eosTokenId": tokenizer.eos_token_id,
            "padToken": tokenizer.pad_token,
            "padTokenId": tokenizer.pad_token_id,
            "assistantSuffixIds": [
                "LABEL_15_OR_16",
                *binary.ASSISTANT_TERMINATION_IDS,
            ],
        },
        "assistantTargetPolicy": binary.ASSISTANT_TARGET_POLICY,
        "localFilesOnly": True,
        "trustRemoteCode": False,
        "networkAccess": False,
        "modelWeightsLoaded": False,
    }
    with report_path.open("x", encoding="utf-8", newline="\n") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(
        json.dumps(
            {
                "status": "PASS",
                "maximum": maximum,
                "selectedMaximumSequence": selected_limit,
                "rows": sum(EXPECTED_ROWS.values()),
            },
            separators=(",", ":"),
        )
    )


if __name__ == "__main__":
    main()
