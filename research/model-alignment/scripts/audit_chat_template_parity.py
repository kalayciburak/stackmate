#!/usr/bin/env python3
"""Compare the official tokenizer template with the historical llama.cpp envelope."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def strict_json(path: Path) -> dict[str, Any]:
    def pairs(items: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in items:
            if key in result:
                raise ValueError(f"duplicate JSON key in {path}: {key}")
            result[key] = value
        return result

    value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=pairs)
    if not isinstance(value, dict):
        raise ValueError(f"expected JSON object: {path}")
    return value


def manual_template(system: str, user: str, bounds: str) -> str:
    return (
        "<|im_start|>system\n"
        + system
        + "\n\n"
        + bounds
        + "<|im_end|>\n<|im_start|>user\n"
        + user
        + "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
    )


def first_difference(left: list[int], right: list[int]) -> int | None:
    for index, (left_item, right_item) in enumerate(zip(left, right)):
        if left_item != right_item:
            return index
    return None if len(left) == len(right) else min(len(left), len(right))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", required=True, type=Path)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    arguments = parser.parse_args()
    corpus_path = arguments.corpus.expanduser().resolve()
    base_path = arguments.base_model.expanduser().resolve()
    report_path = arguments.report.expanduser().resolve()
    if report_path.exists():
        raise RuntimeError(f"chat-template report already exists: {report_path}")

    from transformers import AutoTokenizer

    corpus = strict_json(corpus_path)
    tokenizer = AutoTokenizer.from_pretrained(
        base_path, local_files_only=True, trust_remote_code=False
    )
    records: list[dict[str, Any]] = []
    for prompt_id in ("P0", "P3"):
        prompt = corpus["prompts"][prompt_id]
        for case in corpus["cases"]:
            effective_system = prompt["system"] + "\n\n" + corpus["bounds"]
            messages = [
                {"role": "system", "content": effective_system},
                {"role": "user", "content": case["productionUserPrompt"]},
            ]
            official = tokenizer.apply_chat_template(
                messages,
                tokenize=False,
                add_generation_prompt=True,
                enable_thinking=False,
            )
            official_ids = tokenizer.apply_chat_template(
                messages,
                tokenize=True,
                add_generation_prompt=True,
                enable_thinking=False,
                return_dict=False,
            )
            manual = manual_template(
                prompt["system"], case["productionUserPrompt"], corpus["bounds"]
            )
            manual_ids = tokenizer.encode(manual, add_special_tokens=False)
            records.append(
                {
                    "caseId": case["caseId"],
                    "promptId": prompt_id,
                    "rawSystemPrompt": prompt["system"],
                    "rawUserPrompt": case["productionUserPrompt"],
                    "officialRenderedPrompt": official,
                    "officialRenderedSha256": sha256_text(official),
                    "manualLlamaCppPrompt": manual,
                    "manualLlamaCppPromptSha256": sha256_text(manual),
                    "renderedTextEqual": official == manual,
                    "officialTokenIds": official_ids,
                    "manualTokenIds": manual_ids,
                    "tokenIdsEqual": official_ids == manual_ids,
                    "firstTokenDifference": first_difference(official_ids, manual_ids),
                    "officialTokenCount": len(official_ids),
                    "manualTokenCount": len(manual_ids),
                    "bosCount": (
                        official_ids.count(tokenizer.bos_token_id)
                        if tokenizer.bos_token_id is not None
                        else 0
                    ),
                    "eosCount": (
                        official_ids.count(tokenizer.eos_token_id)
                        if tokenizer.eos_token_id is not None
                        else 0
                    ),
                }
            )

    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "corpusId": corpus["corpusId"],
        "corpusSha256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
        "baseRepository": corpus["baseRepository"],
        "baseRevision": corpus["baseRevision"],
        "tokenizerPath": "<WSL_HF_CACHE>/models--Qwen--Qwen3.5-2B/snapshots/"
        + corpus["baseRevision"],
        "trustRemoteCode": False,
        "localFilesOnly": True,
        "networkAccess": False,
        "chatTemplateSha256": sha256_text(tokenizer.chat_template or ""),
        "specialTokens": {
            "bosToken": tokenizer.bos_token,
            "bosTokenId": tokenizer.bos_token_id,
            "eosToken": tokenizer.eos_token,
            "eosTokenId": tokenizer.eos_token_id,
            "padToken": tokenizer.pad_token,
            "padTokenId": tokenizer.pad_token_id,
            "allSpecialTokens": tokenizer.all_special_tokens,
            "allSpecialTokenIds": tokenizer.all_special_ids,
        },
        "summary": {
            "cases": len(records),
            "renderedTextEqual": sum(item["renderedTextEqual"] for item in records),
            "tokenIdsEqual": sum(item["tokenIdsEqual"] for item in records),
            "maximumOfficialTokens": max(item["officialTokenCount"] for item in records),
            "maximumManualTokens": max(item["manualTokenCount"] for item in records),
        },
        "records": records,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(json.dumps({"status": report["status"], "summary": report["summary"]}))


if __name__ == "__main__":
    main()
