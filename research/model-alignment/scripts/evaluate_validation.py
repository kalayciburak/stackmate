#!/usr/bin/env python3
"""Evaluate one adapter only on the frozen Phase 5.2 validation split."""

from __future__ import annotations

import argparse
import gc
import importlib.metadata
import json
import math
import os
import platform
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer

from dataset_contract import ROOT, sha256_file
from train_qlora import directory_sha256, read_json


EXPECTED_KEYS = {
    "schemaVersion",
    "summary",
    "explanation",
    "selectedCauseIds",
    "selectedCheckIds",
    "usedEvidenceIds",
    "limitations",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter", required=True, type=Path)
    parser.add_argument("--environment", default="configs/environment-v1.json")
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--max-new-tokens", type=int, default=192)
    return parser.parse_args()


def load_rows() -> list[dict[str, Any]]:
    path = ROOT / "datasets/validation.jsonl"
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    if len(rows) != 200:
        raise RuntimeError(f"Validation split changed: {len(rows)}")
    return rows


def model_path(environment: dict[str, Any]) -> Path:
    setting = environment["baseModel"]["localPathEnvironment"]
    value = os.environ.get(setting, "").strip() or os.environ.get("BASE_MODEL_PATH", "").strip()
    if not value:
        raise RuntimeError(f"{setting} or BASE_MODEL_PATH is not set")
    path = Path(value).expanduser().resolve()
    if path.name != environment["baseModel"]["revision"]:
        raise RuntimeError(f"Unexpected base revision path: {path}")
    return path


def load_model(base_path: Path, adapter_path: Path) -> Any:
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported():
        raise RuntimeError("Validation requires CUDA with BF16")
    from transformers import BitsAndBytesConfig

    quantization = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )
    base = AutoModelForMultimodalLM.from_pretrained(
        base_path,
        local_files_only=True,
        trust_remote_code=False,
        quantization_config=quantization,
        dtype=torch.bfloat16,
        device_map={"": torch.cuda.current_device()},
    )
    model = PeftModel.from_pretrained(
        base,
        adapter_path,
        local_files_only=True,
        is_trainable=False,
    )
    model.eval()
    return model


def generate(model: Any, tokenizer: Any, row: dict[str, Any], maximum_new_tokens: int) -> str:
    prompt = tokenizer.apply_chat_template(
        row["messages"][:2],
        tokenize=True,
        add_generation_prompt=True,
        enable_thinking=False,
        return_tensors="pt",
    )
    if isinstance(prompt, torch.Tensor):
        input_ids = prompt
        attention_mask = torch.ones_like(input_ids)
    else:
        input_ids = prompt["input_ids"]
        attention_mask = prompt["attention_mask"]
    device = next(model.parameters()).device
    input_ids = input_ids.to(device)
    attention_mask = attention_mask.to(device)
    with torch.inference_mode():
        output = model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_new_tokens=maximum_new_tokens,
            do_sample=False,
            use_cache=True,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    return tokenizer.decode(output[0, input_ids.shape[1] :], skip_special_tokens=True).strip()


def score(row: dict[str, Any], raw: str) -> dict[str, Any]:
    target = json.loads(row["messages"][2]["content"])
    user_payload_start = row["messages"][1]["content"].index("\n") + 1
    user_payload_end = row["messages"][1]["content"].rindex("\n</STACKMATE_UNTRUSTED_INCIDENT>")
    request = json.loads(row["messages"][1]["content"][user_payload_start:user_payload_end])
    expected_causes = target["selectedCauseIds"]
    expected_checks = target["selectedCheckIds"]
    abstention_required = bool(row["metadata"]["abstentionRequired"])
    result: dict[str, Any] = {
        "exampleId": row["exampleId"],
        "kind": row["kind"],
        "validJson": False,
        "validStructured": False,
        "causeCorrect": False,
        "checkCorrect": False,
        "abstentionCorrect": False,
        "unsupportedClaim": False,
        "unknownId": False,
        "genericFallbackMisuse": False,
        "usefulExplanation": False,
        "confusion": False,
        "output": raw[:4096],
    }
    try:
        output = json.loads(raw)
    except json.JSONDecodeError:
        return result
    if not isinstance(output, dict):
        return result
    result["validJson"] = True
    if set(output) != EXPECTED_KEYS:
        return result
    if output.get("schemaVersion") != "2":
        return result
    if not all(isinstance(output.get(key), list) for key in ("selectedCauseIds", "selectedCheckIds", "usedEvidenceIds", "limitations")):
        return result
    if not all(isinstance(output.get(key), str) and output[key].strip() for key in ("summary", "explanation")):
        return result
    result["validStructured"] = True
    result["causeCorrect"] = output["selectedCauseIds"] == expected_causes
    result["checkCorrect"] = output["selectedCheckIds"] == expected_checks
    result["abstentionCorrect"] = (
        not abstention_required
        or output["selectedCauseIds"] == []
        and output["selectedCheckIds"] == []
        and "EVIDENCE_INSUFFICIENT" in output["limitations"]
    )
    result["usefulExplanation"] = 1 <= len(output["explanation"]) <= 180
    cause_map = {item["id"]: item for item in request["candidateCauses"]}
    check_map = {item["id"]: item for item in request["candidateChecks"]}
    fact_ids = {item["id"] for item in request["facts"]}
    selected_cause_ids = set(output["selectedCauseIds"])
    selected_check_ids = set(output["selectedCheckIds"])
    result["unknownId"] = bool(
        selected_cause_ids - set(cause_map)
        or selected_check_ids - set(check_map)
        or set(output["usedEvidenceIds"]) - fact_ids
    )
    result["unsupportedClaim"] = any(
        not cause_map.get(item, {}).get("evidenceIds") for item in selected_cause_ids if item in cause_map
    ) or any(
        not check_map.get(item, {}).get("evidenceIds") for item in selected_check_ids if item in check_map
    )
    result["genericFallbackMisuse"] = abstention_required and bool(
        selected_cause_ids or selected_check_ids or "EVIDENCE_INSUFFICIENT" not in output["limitations"]
    )
    result["confusion"] = result["unsupportedClaim"] or result["genericFallbackMisuse"]
    return result


def main() -> None:
    arguments = parse_args()
    environment_path = (ROOT / arguments.environment).resolve()
    environment = read_json(environment_path)
    adapter_path = arguments.adapter.expanduser().resolve()
    if not adapter_path.is_dir():
        raise RuntimeError(f"Adapter directory missing: {adapter_path}")
    rows = load_rows()
    base_path = model_path(environment)
    tokenizer = AutoTokenizer.from_pretrained(
        base_path, local_files_only=True, trust_remote_code=False
    )
    started = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    if torch.cuda.is_available():
        torch.cuda.reset_peak_memory_stats()
    model = load_model(base_path, adapter_path)
    results: list[dict[str, Any]] = []
    for index, row in enumerate(rows, start=1):
        raw = generate(model, tokenizer, row, arguments.max_new_tokens)
        results.append(score(row, raw))
        if index % 25 == 0:
            print(json.dumps({"completed": index, "total": len(rows)}))
    finished = datetime.now(timezone.utc).replace(microsecond=0)
    duration = time.perf_counter() - wall_started
    def count(field: str) -> int:
        return sum(bool(item[field]) for item in results)
    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "selectionDataOnly": True,
        "split": "validation",
        "examples": len(results),
        "baseRepository": environment["baseModel"]["repository"],
        "baseRevision": environment["baseModel"]["revision"],
        "adapterPath": "<WSL_CACHE>/adapters/phase-5-2/" + adapter_path.name,
        "adapterSha256": directory_sha256(adapter_path),
        "datasetLockSha256": sha256_file(ROOT / "datasets/lock.json"),
        "validationSha256": sha256_file(ROOT / "datasets/validation.jsonl"),
        "startTimeUtc": started.isoformat(),
        "finishTimeUtc": finished.isoformat(),
        "durationSeconds": round(duration, 3),
        "metrics": {
            "validJson": {"matched": count("validJson"), "total": len(results)},
            "validStructured": {"matched": count("validStructured"), "total": len(results)},
            "causeAccuracy": {"matched": count("causeCorrect"), "total": len(results)},
            "checkAccuracy": {"matched": count("checkCorrect"), "total": len(results)},
            "abstention": {
                "matched": count("abstentionCorrect"),
                "total": sum(
                    bool(item["metadata"]["abstentionRequired"])
                    for item in rows
                ),
            },
            "unsupportedClaims": count("unsupportedClaim"),
            "unknownIds": count("unknownId"),
            "genericFallbackMisuse": count("genericFallbackMisuse"),
            "usefulExplanation": {"matched": count("usefulExplanation"), "total": len(results)},
            "confusion": count("confusion"),
        },
        "peakGpuVramBytes": torch.cuda.max_memory_allocated() if torch.cuda.is_available() else 0,
        "networkAccessDuringEvaluation": False,
        "platform": platform.platform(),
        "python": sys.version.split()[0],
        "packages": {
            name: importlib.metadata.version(name)
            for name in ("peft", "torch", "transformers", "bitsandbytes")
        },
        "caseResults": results,
    }
    report_path = arguments.report if arguments.report.is_absolute() else ROOT / arguments.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    del model
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    print(json.dumps({"status": report["status"], "report": str(report_path.resolve())}))


if __name__ == "__main__":
    main()
