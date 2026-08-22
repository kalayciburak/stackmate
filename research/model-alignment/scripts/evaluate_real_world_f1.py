#!/usr/bin/env python3
"""Run the selected historical F1 adapter once on Phase 5.3 validation/T1 data."""

from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.metadata
import json
import os
import platform
import socket
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer, BitsAndBytesConfig, set_seed


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
BASE_REPOSITORY = "Qwen/Qwen3.5-2B"
BASE_MANIFEST_SHA256 = "1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981"
SELECTED_ADAPTER_REPORT_SHA256 = "cc1ba7dae64a95407a416484d19fe84934cabb48c4f8d5066931afd2e1ba89b1"
F1_ADAPTER_SHA256 = "6642b7e3bad7eb64c6199d56db60935aabda54b5a2c26f33af4e79bc8d5ab6ca"
SOURCE_MANIFEST_SHA256 = "42d2bab37173a9b8a7579203e270a02fbe9ab795b12b9a731f39fa035a8244ee"
DATASET_MANIFEST_SHA256 = "0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2"
VALIDATION_SHA256 = "bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789"
SEED = 520021
MAX_NEW_TOKENS = 128
EXPECTED_KEYS = {"selectedCauseIndexes", "selectedCheckIndexes", "limitation"}
PACKAGE_NAMES = ("torch", "transformers", "peft", "accelerate", "bitsandbytes")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--adapter", required=True, type=Path)
    parser.add_argument(
        "--base-manifest", default="reports/base-model-manifest.json", type=Path
    )
    parser.add_argument(
        "--selected-adapter-report", default="reports/selected-adapter.json", type=Path
    )
    parser.add_argument(
        "--dataset-manifest", default="datasets/real-world-v1/manifest.json", type=Path
    )
    parser.add_argument(
        "--validation", default="datasets/real-world-v1/validation.jsonl", type=Path
    )
    parser.add_argument("--report", required=True, type=Path)
    return parser.parse_args()


def root_path(path: Path) -> Path:
    return path.expanduser().resolve() if path.is_absolute() else (ROOT / path).resolve()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


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


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON root is not an object: {path}")
    return value


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def require_exact_file(path: Path, expected: str, label: str) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"missing {label}: {path}")
    actual = sha256_file(path)
    if actual != expected:
        raise RuntimeError(f"{label} checksum drifted: expected={expected}, actual={actual}")


def validate_inputs(args: argparse.Namespace) -> tuple[Path, Path, Path, Path, Path]:
    base = args.base_model.expanduser().resolve()
    adapter = args.adapter.expanduser().resolve()
    base_manifest = root_path(args.base_manifest)
    selection_report = root_path(args.selected_adapter_report)
    dataset_manifest = root_path(args.dataset_manifest)
    validation = root_path(args.validation)
    if not base.is_dir() or base.name != REVISION:
        raise RuntimeError("base model is missing or is not the exact official revision")
    for required in ("config.json", "model.safetensors.index.json", "tokenizer.json", "tokenizer_config.json"):
        if not (base / required).is_file():
            raise RuntimeError(f"base model required file is missing: {required}")
    if not adapter.is_dir():
        raise RuntimeError("selected F1 adapter directory is missing")
    require_exact_file(base_manifest, BASE_MANIFEST_SHA256, "base-model manifest")
    require_exact_file(selection_report, SELECTED_ADAPTER_REPORT_SHA256, "selected-adapter report")
    require_exact_file(dataset_manifest, DATASET_MANIFEST_SHA256, "real-world dataset manifest")
    require_exact_file(validation, VALIDATION_SHA256, "real-world validation")
    selection = read_json(selection_report)
    if (
        selection.get("adapterId") != "F1-qlora-r8"
        or selection.get("baseModelRepository") != BASE_REPOSITORY
        or selection.get("baseModelRevision") != REVISION
        or selection.get("adapterSha256") != F1_ADAPTER_SHA256
    ):
        raise RuntimeError("selected-adapter report no longer identifies the locked F1")
    actual_adapter_hash = directory_sha256(adapter)
    if actual_adapter_hash != F1_ADAPTER_SHA256:
        raise RuntimeError("selected F1 adapter checksum drifted")
    dataset = read_json(dataset_manifest)
    if (
        dataset.get("sourceManifestSha256") != SOURCE_MANIFEST_SHA256
        or dataset.get("counts", {}).get("validationRows") != 40
        or dataset.get("counts", {}).get("sealedFinalFamilies") != 30
        or dataset.get("files", {}).get("validation", {}).get("sha256")
        != VALIDATION_SHA256
    ):
        raise RuntimeError("real-world dataset manifest contract drifted")
    return base, adapter, base_manifest, selection_report, validation


def load_validation(path: Path) -> list[dict[str, Any]]:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if len(rows) != 40:
        raise RuntimeError(f"real-world validation row count drifted: {len(rows)}")
    families = Counter(str(row.get("familyId")) for row in rows)
    if len(families) != 20 or set(families.values()) != {2}:
        raise RuntimeError("validation must contain exactly two rows for each of 20 families")
    if any(row.get("split") != "VALIDATION" for row in rows):
        raise RuntimeError("non-validation row reached F1 diagnostic")
    if any(row.get("expectedT1", {}).get("disposition") not in {"DIAGNOSE", "RESTRAIN"} for row in rows):
        raise RuntimeError("validation T1 target is invalid")
    return rows


def disable_network() -> None:
    os.environ.update(
        {
            "HF_HUB_OFFLINE": "1",
            "TRANSFORMERS_OFFLINE": "1",
            "HF_HUB_DISABLE_TELEMETRY": "1",
            "WANDB_DISABLED": "true",
        }
    )

    def blocked(*_args: Any, **_kwargs: Any) -> Any:
        raise RuntimeError("network access is disabled for the real-world F1 diagnostic")

    socket.create_connection = blocked
    socket.getaddrinfo = blocked


def load_model(base: Path, adapter: Path) -> tuple[Any, Any]:
    tokenizer = AutoTokenizer.from_pretrained(
        base, local_files_only=True, trust_remote_code=False
    )
    if tokenizer.pad_token_id is None or tokenizer.eos_token_id is None:
        raise RuntimeError("official tokenizer must define pad and EOS token IDs")
    quantization = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )
    model = AutoModelForMultimodalLM.from_pretrained(
        base,
        local_files_only=True,
        trust_remote_code=False,
        quantization_config=quantization,
        dtype=torch.bfloat16,
        device_map={"": torch.cuda.current_device()},
    )
    model = PeftModel.from_pretrained(
        model, adapter, local_files_only=True, is_trainable=False
    )
    model.eval()
    model.config.use_cache = True
    return tokenizer, model


def generate(tokenizer: Any, model: Any, row: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    messages = row["messages"][:2]
    encoded = tokenizer.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=True,
        enable_thinking=False,
        return_dict=True,
        return_tensors="pt",
    )
    input_ids = encoded["input_ids"]
    attention_mask = encoded.get("attention_mask")
    if attention_mask is None:
        attention_mask = torch.ones_like(input_ids)
    device = next(model.parameters()).device
    input_ids = input_ids.to(device)
    attention_mask = attention_mask.to(device)
    torch.cuda.synchronize()
    torch.cuda.reset_peak_memory_stats()
    started = time.perf_counter()
    with torch.inference_mode():
        generated = model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_new_tokens=MAX_NEW_TOKENS,
            do_sample=False,
            num_beams=1,
            use_cache=True,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    torch.cuda.synchronize()
    latency = time.perf_counter() - started
    suffix = generated[0, input_ids.shape[1] :]
    suffix_ids = [int(item) for item in suffix.tolist()]
    raw = tokenizer.decode(suffix, skip_special_tokens=True).strip()
    return raw, {
        "inputTokens": int(input_ids.shape[1]),
        "outputTokens": len(suffix_ids),
        "latencySeconds": round(latency, 6),
        "peakGpuVramBytes": torch.cuda.max_memory_allocated(),
        "peakGpuVramReservedBytes": torch.cuda.max_memory_reserved(),
        "stopReason": (
            "STOP_TOKEN"
            if suffix_ids and suffix_ids[-1] == tokenizer.eos_token_id
            else "MAX_NEW_TOKENS"
            if len(suffix_ids) == MAX_NEW_TOKENS
            else "MODEL_GENERATION_ENDED"
        ),
    }


def valid_index_list(value: Any) -> bool:
    return (
        isinstance(value, list)
        and all(isinstance(item, int) and not isinstance(item, bool) for item in value)
        and len(value) == len(set(value))
    )


def score(row: dict[str, Any], raw: str, generation: dict[str, Any]) -> dict[str, Any]:
    expected = row["expectedT1"]
    expected_causes = list(expected["selectedCauseIndexes"])
    expected_checks = list(expected["selectedCheckIndexes"])
    restraint = expected["disposition"] == "RESTRAIN"
    result: dict[str, Any] = {
        "familyId": row["familyId"],
        "variant": row["variant"],
        "candidateOrder": row["candidateOrder"]["mode"],
        "expected": {
            "selectedCauseIndexes": expected_causes,
            "selectedCheckIndexes": expected_checks,
            "limitation": "EVIDENCE_INSUFFICIENT" if restraint else None,
        },
        "validJson": False,
        "validStructured": False,
        "causeCorrect": False,
        "checkCorrect": False,
        "jointCorrect": False,
        "strictRestraint": False,
        "overAbstention": False,
        "unknownIndex": False,
        "contractFieldMismatch": False,
        "missingFields": [],
        "extraFields": [],
        "proseFields": False,
        "genericFallbackMisuse": False,
        "hiddenThinking": "<think" in raw.casefold(),
        "rawOutput": raw[:2048],
        "rawOutputSha256": sha256_bytes(raw.encode("utf-8")),
        **generation,
    }
    try:
        output = json.loads(raw, object_pairs_hook=reject_duplicate_keys)
    except (json.JSONDecodeError, ValueError):
        return result
    if not isinstance(output, dict):
        return result
    result["validJson"] = True
    result["missingFields"] = sorted(EXPECTED_KEYS - set(output))
    result["extraFields"] = sorted(set(output) - EXPECTED_KEYS)
    result["contractFieldMismatch"] = bool(
        result["missingFields"] or result["extraFields"]
    )
    result["proseFields"] = bool(
        set(output)
        & {"summary", "explanation", "limitations", "usedEvidenceIds", "schemaVersion"}
    )
    if result["contractFieldMismatch"]:
        return result
    causes = output.get("selectedCauseIndexes")
    checks = output.get("selectedCheckIndexes")
    limitation = output.get("limitation")
    if not valid_index_list(causes) or not valid_index_list(checks):
        return result
    if limitation not in (None, "EVIDENCE_INSUFFICIENT"):
        return result
    result["unknownIndex"] = any(
        item < 0 or item >= len(row["candidateOrder"]["causeIds"]) for item in causes
    ) or any(item < 0 or item >= len(row["candidateOrder"]["checkIds"]) for item in checks)
    if result["unknownIndex"]:
        return result
    if bool(causes) != bool(checks):
        return result
    if causes and limitation is not None or not causes and limitation != "EVIDENCE_INSUFFICIENT":
        return result
    result["validStructured"] = True
    result["causeCorrect"] = causes == expected_causes
    result["checkCorrect"] = checks == expected_checks
    result["jointCorrect"] = result["causeCorrect"] and result["checkCorrect"]
    result["strictRestraint"] = restraint and not causes and not checks and limitation == "EVIDENCE_INSUFFICIENT"
    result["genericFallbackMisuse"] = restraint and not result["strictRestraint"]
    result["overAbstention"] = not restraint and not causes and limitation == "EVIDENCE_INSUFFICIENT"
    result["predictedCauseIds"] = [row["candidateOrder"]["causeIds"][item] for item in causes]
    result["predictedCheckIds"] = [row["candidateOrder"]["checkIds"][item] for item in checks]
    return result


def metric(results: list[dict[str, Any]], field: str) -> dict[str, int]:
    return {"matched": sum(bool(item[field]) for item in results), "total": len(results)}


def order_invariance(results: list[dict[str, Any]]) -> dict[str, Any]:
    by_family: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for result in results:
        by_family[result["familyId"]].append(result)
    invariant = 0
    details: list[dict[str, Any]] = []
    for family_id, family_results in sorted(by_family.items()):
        complete = len(family_results) == 2 and all(item["validStructured"] for item in family_results)
        same = complete and all(
            family_results[0].get(field) == family_results[1].get(field)
            for field in ("predictedCauseIds", "predictedCheckIds")
        )
        invariant += int(same)
        details.append({"familyId": family_id, "invariant": bool(same)})
    return {"matched": invariant, "total": len(by_family), "families": details}


def package_versions() -> dict[str, str]:
    result: dict[str, str] = {}
    for name in PACKAGE_NAMES:
        try:
            result[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            result[name] = "NOT_INSTALLED"
    return result


def main() -> None:
    args = arguments()
    report = root_path(args.report)
    if report.exists():
        raise FileExistsError(f"refusing to overwrite report: {report}")
    if report.parent != ROOT / "reports":
        raise RuntimeError("F1 diagnostic report must remain in research/model-alignment/reports")
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported():
        raise RuntimeError("F1 diagnostic requires CUDA with BF16 support")
    base, adapter, base_manifest, selection_report, validation_path = validate_inputs(args)
    rows = load_validation(validation_path)
    disable_network()
    set_seed(SEED)
    torch.manual_seed(SEED)
    torch.cuda.manual_seed_all(SEED)
    tokenizer, model = load_model(base, adapter)
    started = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    results: list[dict[str, Any]] = []
    for index, row in enumerate(rows, 1):
        raw, generation = generate(tokenizer, model, row)
        results.append(score(row, raw, generation))
        print(json.dumps({"completed": index, "total": len(rows), "familyId": row["familyId"]}))
    finished = datetime.now(timezone.utc).replace(microsecond=0)
    restraint_results = [item for item, row in zip(results, rows) if row["expectedT1"]["disposition"] == "RESTRAIN"]
    diagnose_results = [item for item, row in zip(results, rows) if row["expectedT1"]["disposition"] == "DIAGNOSE"]
    original = [item for item in results if item["candidateOrder"] == "ORIGINAL"]
    permuted = [item for item in results if item["candidateOrder"] == "DETERMINISTIC_PERMUTATION"]
    output = {
        "schemaVersion": "1",
        "status": "COMPLETE",
        "diagnosticOnly": True,
        "trainingPerformed": False,
        "lockedFinalLoaded": False,
        "base": {
            "repository": BASE_REPOSITORY,
            "revision": REVISION,
            "path": "<WSL_CACHE>/models/models--Qwen--Qwen3.5-2B/snapshots/" + REVISION,
            "manifestSha256": sha256_file(base_manifest),
        },
        "adapter": {
            "id": "F1-qlora-r8",
            "path": "<WSL_CACHE>/adapters/phase-5-2/F1-qlora-r8",
            "sha256": directory_sha256(adapter),
            "selectionReportSha256": sha256_file(selection_report),
        },
        "dataset": {
            "split": "VALIDATION",
            "rows": len(rows),
            "families": 20,
            "validationSha256": sha256_file(validation_path),
            "datasetManifestSha256": DATASET_MANIFEST_SHA256,
            "sourceManifestSha256": SOURCE_MANIFEST_SHA256,
            "contract": "T1_SELECTION_ONLY_ORDINAL",
        },
        "generation": {
            "maxNewTokens": MAX_NEW_TOKENS,
            "doSample": False,
            "numBeams": 1,
            "seed": SEED,
            "quantization": "NF4_DOUBLE_QUANT",
            "computeDtype": "bfloat16",
            "enableThinking": False,
            "localFilesOnly": True,
            "trustRemoteCode": False,
            "outputRepair": False,
        },
        "timing": {
            "startedUtc": started.isoformat(),
            "finishedUtc": finished.isoformat(),
            "durationSeconds": round(time.perf_counter() - wall_started, 3),
        },
        "metrics": {
            "validJson": metric(results, "validJson"),
            "validStructured": metric(results, "validStructured"),
            "causeAccuracy": metric(results, "causeCorrect"),
            "checkAccuracy": metric(results, "checkCorrect"),
            "jointAccuracy": metric(results, "jointCorrect"),
            "strictRestraint": {
                "matched": sum(item["strictRestraint"] for item in restraint_results),
                "total": len(restraint_results),
            },
            "diagnoseJointAccuracy": {
                "matched": sum(item["jointCorrect"] for item in diagnose_results),
                "total": len(diagnose_results),
            },
            "overAbstention": sum(item["overAbstention"] for item in results),
            "genericFallbackMisuse": sum(item["genericFallbackMisuse"] for item in results),
            "unknownIndexes": sum(item["unknownIndex"] for item in results),
            "contractFieldMismatch": sum(item["contractFieldMismatch"] for item in results),
            "missingRequiredFields": sum(bool(item["missingFields"]) for item in results),
            "extraFields": sum(bool(item["extraFields"]) for item in results),
            "proseFields": sum(item["proseFields"] for item in results),
            "hiddenThinking": sum(item["hiddenThinking"] for item in results),
            "orderInvariance": order_invariance(results),
            "originalJointAccuracy": metric(original, "jointCorrect"),
            "permutedJointAccuracy": metric(permuted, "jointCorrect"),
        },
        "performance": {
            "meanLatencySeconds": round(sum(item["latencySeconds"] for item in results) / len(results), 6),
            "peakGpuVramBytes": max(item["peakGpuVramBytes"] for item in results),
            "peakGpuVramReservedBytes": max(item["peakGpuVramReservedBytes"] for item in results),
            "maximumInputTokens": max(item["inputTokens"] for item in results),
            "maximumOutputTokens": max(item["outputTokens"] for item in results),
        },
        "network": {"access": False, "uploads": False, "telemetry": False},
        "platform": {"value": platform.platform(), "python": sys.version.split()[0], "packages": package_versions()},
        "results": results,
    }
    report.parent.mkdir(parents=True, exist_ok=True)
    with report.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(output, destination, indent=2, ensure_ascii=False)
        destination.write("\n")
    print(json.dumps({"status": "COMPLETE", "report": report.relative_to(ROOT).as_posix()}))
    del model, tokenizer
    gc.collect()
    torch.cuda.empty_cache()


if __name__ == "__main__":
    main()
