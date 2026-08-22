#!/usr/bin/env python3
"""Run the Phase 5.4.1 locked evaluation once with fail-closed access control."""

from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.metadata
import json
import os
import platform
import socket
import subprocess
import sys
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer

import run_locked_evaluation as legacy


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
SELECTION_COMMIT = "08b5b55adee82a83c3d81e599deca8ca6b3458ca"
SELECTION_REPORT_SHA256 = "410a53337658b715e0446548bfb7b23102d58469d74df16d445dba8637b10284"
BASE_MANIFEST_SHA256 = "1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981"
INPUT_SHA256 = "e7323f558fbe968711daa1075ed106a64f9e8d66e12d2292153bb6410d792777"
SEALED_DESCRIPTOR_SHA256 = "53f3ae08ff53bf192da6cd54f3a05de62cc9c3d0ec7fbadedf3b1c2c6caed61e"
ADAPTER_SHA256 = "7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49"
STAGE_ORDER = ["benchmark-v2-holdout", "phase-5-blind", "post-training-challenge"]
CONDITION_ORDER = [("B0", False, "P0"), ("B1", False, "P3"), ("A1", True, "P3"), ("A2", True, "P2")]
STAGE_CARDINALITY = {"benchmark-v2-holdout": 20, "phase-5-blind": 20, "post-training-challenge": 30}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", required=True, type=Path)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--adapter", required=True, type=Path)
    parser.add_argument("--selection", default="reports/phase-5-4-1-selected-adapter.json", type=Path)
    parser.add_argument("--base-manifest", default="reports/base-model-manifest.json", type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    parser.add_argument("--max-new-tokens", type=int, default=192)
    return parser.parse_args()


def repository_path(path: Path) -> Path:
    return path.expanduser().resolve() if path.is_absolute() else (ROOT / path).resolve()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def directory_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    for item in sorted(item for item in path.rglob("*") if item.is_file()):
        digest.update(item.relative_to(path).as_posix().encode("utf-8"))
        digest.update(b"\0")
        with item.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


def duplicate_rejecting_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=duplicate_rejecting_pairs)
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path}")
    return value


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def disable_network() -> None:
    def blocked(*_args: Any, **_kwargs: Any) -> Any:
        raise RuntimeError("network access is disabled during locked evaluation")

    socket.create_connection = blocked
    socket.getaddrinfo = blocked


def verify_selection(selection_path: Path, adapter_path: Path, manifest_path: Path) -> dict[str, Any]:
    require(selection_path.is_file(), "selection report is missing")
    require(sha256_file(selection_path) == SELECTION_REPORT_SHA256, "selection report checksum drifted")
    selection = read_json(selection_path)
    require(selection.get("status") == "SELECTED_VALIDATION_ONLY", "selection is not validation-only")
    require(selection.get("baseModelRepository") == "Qwen/Qwen3.5-2B", "selection base repository drifted")
    require(selection.get("baseModelRevision") == REVISION, "selection base revision drifted")
    require(selection.get("adapterSha256") == ADAPTER_SHA256, "selection adapter hash drifted")
    require(selection.get("sealedFinalDescriptorSha256") == SEALED_DESCRIPTOR_SHA256, "sealed descriptor binding drifted")
    require(selection.get("selectionCommit") == SELECTION_COMMIT, "selection commit binding drifted")
    require(selection.get("lockedFinalOpened") is False, "selection was produced after locked access")
    require(selection.get("selectionUsesLockedData") is False, "selection used locked data")
    commit_check = subprocess.run(
        ["git", "cat-file", "-e", f"{SELECTION_COMMIT}^{{commit}}"],
        cwd=REPOSITORY,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    require(commit_check.returncode == 0, "selection commit is not present in the repository")
    require(adapter_path.is_dir(), "selected adapter directory is missing")
    require(adapter_path.is_absolute() and REPOSITORY not in adapter_path.parents, "adapter must remain outside the repository")
    require(directory_sha256(adapter_path) == ADAPTER_SHA256, "selected adapter directory checksum drifted")
    require(manifest_path.is_file() and sha256_file(manifest_path) == BASE_MANIFEST_SHA256, "base manifest checksum drifted")
    return selection


def verify_nonfinal_paths(inputs_path: Path, report_path: Path, receipt_path: Path) -> None:
    require(inputs_path.is_file(), "locked inputs export is missing")
    require(inputs_path.is_absolute() and REPOSITORY not in inputs_path.parents, "locked input must remain external")
    require(report_path.is_absolute() and REPOSITORY not in report_path.parents, "locked raw report must remain external")
    require(receipt_path.is_absolute() and REPOSITORY not in receipt_path.parents, "access receipt must remain external")
    require(not receipt_path.exists(), "locked access receipt already exists; exact-once guard is closed")
    require(not report_path.exists(), "locked raw report already exists; overwrite is forbidden")


def verify_base(base_path: Path) -> None:
    require(base_path.is_dir() and base_path.name == REVISION, "base model is not the pinned official revision")
    for required in ("config.json", "model.safetensors.index.json", "tokenizer.json", "tokenizer_config.json"):
        require((base_path / required).is_file(), f"base model is missing {required}")


def verify_input_structure(data: dict[str, Any], actual_hash: str, max_new_tokens: int) -> None:
    require(actual_hash == INPUT_SHA256, "locked input byte hash drifted")
    require(data.get("schemaVersion") == "1", "locked input schema drifted")
    require(data.get("protocol") == "stackmate-phase-5-2-locked-transformers-v1", "locked protocol drifted")
    require(data.get("baseModel") == "Qwen/Qwen3.5-2B", "locked input base repository drifted")
    require(data.get("baseRevision") == REVISION, "locked input base revision drifted")
    require(data.get("selectionLockedBeforeGeneration") is True, "locked input is not selection-locked")
    require(data.get("lockedOutputMustRemainExternal") is True, "locked output boundary drifted")
    require(data.get("maxNewTokens") == max_new_tokens == 192, "locked generation limit drifted")
    require(data.get("generation") == "greedy" and data.get("reasoning") == "off", "locked generation mode drifted")
    stages = data.get("stages")
    require(isinstance(stages, dict) and list(stages) == STAGE_ORDER, "locked stage order drifted")
    seen: set[str] = set()
    for stage in STAGE_ORDER:
        cases = stages.get(stage)
        require(isinstance(cases, list) and len(cases) == STAGE_CARDINALITY[stage], f"locked {stage} cardinality drifted")
        for case in cases:
            require(isinstance(case, dict), "locked case is not an object")
            case_id = case.get("caseId")
            require(isinstance(case_id, str) and case_id and case_id not in seen, "locked case IDs are not unique")
            require(case.get("stage") == stage, "locked case stage mapping drifted")
            seen.add(case_id)
    require(len(seen) == 70, "locked case cardinality drifted")


def load_base(path: Path) -> Any:
    return AutoModelForMultimodalLM.from_pretrained(
        path,
        local_files_only=True,
        trust_remote_code=False,
        dtype=torch.bfloat16,
        device_map={"": torch.cuda.current_device()},
    )


def load_adapter(path: Path, adapter_path: Path) -> Any:
    return PeftModel.from_pretrained(load_base(path), adapter_path, local_files_only=True, is_trainable=False)


def generate(model: Any, tokenizer: Any, case: dict[str, Any], prompt_definition: dict[str, Any], max_new_tokens: int) -> str:
    messages = [
        {"role": "system", "content": prompt_definition["system"] + "\n\n" + legacy.BOUNDS},
        {"role": "user", "content": case["userPrompt"]["productionUser"]},
    ]
    encoded = tokenizer.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=True,
        enable_thinking=False,
        return_dict=True,
        return_tensors="pt",
    )
    input_ids = encoded["input_ids"]
    attention_mask = encoded.get("attention_mask", torch.ones_like(input_ids))
    device = next(model.parameters()).device
    input_ids = input_ids.to(device)
    attention_mask = attention_mask.to(device)
    with torch.inference_mode():
        generated = model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            num_beams=1,
            use_cache=True,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    return tokenizer.decode(generated[0, input_ids.shape[1]:], skip_special_tokens=True).strip()


def write_exclusive(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(value, destination, ensure_ascii=False, indent=2)
        destination.write("\n")


def main() -> None:
    arguments = parse_args()
    inputs_path = arguments.inputs.expanduser().resolve()
    base_path = arguments.base_model.expanduser().resolve()
    adapter_path = arguments.adapter.expanduser().resolve()
    selection_path = repository_path(arguments.selection)
    manifest_path = repository_path(arguments.base_manifest)
    report_path = arguments.report.expanduser().resolve()
    receipt_path = arguments.receipt.expanduser().resolve()
    verify_nonfinal_paths(inputs_path, report_path, receipt_path)
    verify_base(base_path)
    selection = verify_selection(selection_path, adapter_path, manifest_path)
    require(torch.cuda.is_available() and torch.cuda.is_bf16_supported(), "BF16 CUDA is unavailable")
    os.environ.update({"HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1", "HF_HUB_DISABLE_TELEMETRY": "1", "WANDB_DISABLED": "true"})
    disable_network()
    tokenizer = AutoTokenizer.from_pretrained(base_path, local_files_only=True, trust_remote_code=False)
    require(tokenizer.pad_token_id is not None and tokenizer.eos_token_id is not None, "official tokenizer special tokens are missing")
    preflight_model = load_base(base_path)
    del preflight_model
    gc.collect()
    torch.cuda.empty_cache()

    receipt_started = datetime.now(timezone.utc).replace(microsecond=0)
    write_exclusive(receipt_path, {
        "schemaVersion": "1",
        "status": "LOCKED_FINAL_OPENED_ONCE",
        "selectionCommit": SELECTION_COMMIT,
        "selectionReportSha256": SELECTION_REPORT_SHA256,
        "adapterSha256": ADAPTER_SHA256,
        "baseRevision": REVISION,
        "baseManifestSha256": BASE_MANIFEST_SHA256,
        "sealedFinalDescriptorSha256": SEALED_DESCRIPTOR_SHA256,
        "expectedInputsSha256": INPUT_SHA256,
        "stageOrder": STAGE_ORDER,
        "conditionOrder": [item[0] for item in CONDITION_ORDER],
        "openedAtUtc": receipt_started.isoformat(),
        "networkAccess": False,
    })

    input_hash = sha256_file(inputs_path)
    data = json.loads(inputs_path.read_text(encoding="utf-8"), object_pairs_hook=duplicate_rejecting_pairs)
    verify_input_structure(data, input_hash, arguments.max_new_tokens)
    started = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    results: list[dict[str, Any]] = []
    by_condition: dict[str, list[dict[str, Any]]] = defaultdict(list)
    seen_results: set[tuple[str, str, str]] = set()
    for stage in STAGE_ORDER:
        cases = data["stages"][stage]
        print(json.dumps({"stage": stage, "cases": len(cases), "status": "START"}))
        base_model = load_base(base_path)
        base_model.eval()
        for condition, _, prompt_id in CONDITION_ORDER[:2]:
            for index, case in enumerate(cases, 1):
                started_case = time.perf_counter()
                raw = generate(base_model, tokenizer, case, data["prompts"][prompt_id], arguments.max_new_tokens)
                item = legacy.score(case, condition, prompt_id, raw)
                item["generationSeconds"] = round(time.perf_counter() - started_case, 3)
                key = (stage, condition, case["caseId"])
                require(key not in seen_results, f"duplicate locked result: {key}")
                seen_results.add(key)
                results.append(item)
                by_condition[condition].append(item)
                print(json.dumps({"stage": stage, "condition": condition, "completed": index, "total": len(cases), "caseId": case["caseId"], "valid": item.get("valid", False)}))
        del base_model
        gc.collect()
        torch.cuda.empty_cache()
        adapter_model = load_adapter(base_path, adapter_path)
        adapter_model.eval()
        for condition, _, prompt_id in CONDITION_ORDER[2:]:
            for index, case in enumerate(cases, 1):
                started_case = time.perf_counter()
                raw = generate(adapter_model, tokenizer, case, data["prompts"][prompt_id], arguments.max_new_tokens)
                item = legacy.score(case, condition, prompt_id, raw)
                item["generationSeconds"] = round(time.perf_counter() - started_case, 3)
                key = (stage, condition, case["caseId"])
                require(key not in seen_results, f"duplicate locked result: {key}")
                seen_results.add(key)
                results.append(item)
                by_condition[condition].append(item)
                print(json.dumps({"stage": stage, "condition": condition, "completed": index, "total": len(cases), "caseId": case["caseId"], "valid": item.get("valid", False)}))
        del adapter_model
        gc.collect()
        torch.cuda.empty_cache()
        print(json.dumps({"stage": stage, "status": "COMPLETE"}))

    require(len(results) == 280 and len(seen_results) == 280, "locked result cardinality drifted")
    finished = datetime.now(timezone.utc).replace(microsecond=0)
    summaries = {condition: legacy.condition_summary(items) for condition, items in by_condition.items()}
    stage_summaries = {
        stage: {
            condition: legacy.condition_summary([item for item in by_condition[condition] if item["stage"] == stage])
            for condition, _, _ in CONDITION_ORDER
        }
        for stage in STAGE_ORDER
    }
    report = {
        "schemaVersion": "1",
        "status": "LOCKED_EXECUTION_COMPLETE",
        "protocol": data["protocol"],
        "baseRepository": data["baseModel"],
        "baseRevision": REVISION,
        "precision": "BF16",
        "allocator": os.environ.get("PYTORCH_CUDA_ALLOC_CONF", "default"),
        "baseModelManifestSha256": sha256_file(manifest_path),
        "selectionReportSha256": SELECTION_REPORT_SHA256,
        "selectionCommit": selection["selectionCommit"],
        "adapterPath": "<WSL_CACHE>/adapters/phase-5-4-1/selected-P5.4-T1-real-world-r8",
        "adapterSha256": ADAPTER_SHA256,
        "inputsPath": "<WINDOWS_TEMP>/stackmate-phase-5-3-locked-inputs.json",
        "inputsSha256": input_hash,
        "accessReceiptSha256": sha256_file(receipt_path),
        "sealedFinalDescriptorSha256": SEALED_DESCRIPTOR_SHA256,
        "stageOrder": STAGE_ORDER,
        "conditionOrder": [item[0] for item in CONDITION_ORDER],
        "promptMapping": {item[0]: item[2] for item in CONDITION_ORDER},
        "generation": {"maxNewTokens": arguments.max_new_tokens, "doSample": False, "reasoning": "off", "trustRemoteCode": False, "localFilesOnly": True, "networkAccess": False},
        "startTimeUtc": started.isoformat(),
        "finishTimeUtc": finished.isoformat(),
        "durationSeconds": round(time.perf_counter() - wall_started, 3),
        "peakGpuVramBytes": torch.cuda.max_memory_allocated(),
        "peakGpuVramReservedBytes": torch.cuda.max_memory_reserved(),
        "platform": platform.platform(),
        "python": sys.version.split()[0],
        "packages": {name: importlib.metadata.version(name) for name in ("peft", "torch", "transformers", "bitsandbytes")},
        "summaries": summaries,
        "stageSummaries": stage_summaries,
        "qualityReviewStatus": "PENDING_MANUAL_REVIEW",
        "results": results,
    }
    write_exclusive(report_path, report)
    print(json.dumps({"status": report["status"], "report": str(report_path), "durationSeconds": report["durationSeconds"], "accessReceipt": str(receipt_path)}))


if __name__ == "__main__":
    main()
