#!/usr/bin/env python3
"""Evaluate the official base or one adapter on Phase 5.4 T1 development data."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer, BitsAndBytesConfig

from evaluate_real_world_f1 import (
    DATASET_MANIFEST_SHA256,
    REVISION,
    SOURCE_MANIFEST_SHA256,
    VALIDATION_SHA256,
    directory_sha256,
    disable_network,
    load_validation,
    metric,
    order_invariance,
    package_versions,
    root_path,
    score,
    sha256_file,
)


ROOT = Path(__file__).resolve().parents[1]


def args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--mode", choices=("base", "adapter"), required=True)
    parser.add_argument("--precision", choices=("BF16", "NF4"), required=True)
    parser.add_argument("--adapter", type=Path)
    parser.add_argument("--validation", default="datasets/real-world-v1/validation.jsonl", type=Path)
    parser.add_argument("--dataset-manifest", default="datasets/real-world-v1/manifest.json", type=Path)
    parser.add_argument("--report", required=True, type=Path)
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"expected object: {path}")
    return value


def quantization() -> BitsAndBytesConfig:
    return BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )


def load_model(base: Path, mode: str, precision: str, adapter: Path | None) -> tuple[Any, Any]:
    options: dict[str, Any] = {
        "local_files_only": True,
        "trust_remote_code": False,
        "dtype": torch.bfloat16,
        "device_map": {"": torch.cuda.current_device()},
    }
    if precision == "NF4":
        options["quantization_config"] = quantization()
    tokenizer = AutoTokenizer.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    model = AutoModelForMultimodalLM.from_pretrained(base, **options)
    if mode == "adapter":
        if adapter is None or not adapter.is_dir():
            raise RuntimeError("adapter mode requires an adapter directory")
        model = PeftModel.from_pretrained(model, adapter, local_files_only=True, is_trainable=False)
    model.eval()
    return tokenizer, model


def generate(tokenizer: Any, model: Any, row: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    encoded = tokenizer.apply_chat_template(
        row["messages"][:2], tokenize=True, add_generation_prompt=True,
        enable_thinking=False, return_dict=True, return_tensors="pt",
    )
    input_ids = encoded["input_ids"]
    attention = encoded.get("attention_mask", torch.ones_like(input_ids))
    device = next(model.parameters()).device
    input_ids, attention = input_ids.to(device), attention.to(device)
    torch.cuda.synchronize()
    torch.cuda.reset_peak_memory_stats()
    started = time.perf_counter()
    with torch.inference_mode():
        output = model.generate(
            input_ids=input_ids, attention_mask=attention, max_new_tokens=128,
            do_sample=False, num_beams=1, use_cache=True,
            pad_token_id=tokenizer.pad_token_id, eos_token_id=tokenizer.eos_token_id,
        )
    torch.cuda.synchronize()
    suffix = output[0, input_ids.shape[1]:]
    return tokenizer.decode(suffix, skip_special_tokens=True).strip(), {
        "inputTokens": int(input_ids.shape[1]),
        "outputTokens": int(suffix.shape[0]),
        "latencySeconds": round(time.perf_counter() - started, 6),
        "peakGpuVramBytes": torch.cuda.max_memory_allocated(),
        "peakGpuVramReservedBytes": torch.cuda.max_memory_reserved(),
    }


def main() -> None:
    arguments = args()
    report = root_path(arguments.report)
    if report.exists():
        raise FileExistsError(f"refusing to overwrite report: {report}")
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported():
        raise RuntimeError("CUDA with BF16 support is required")
    base = arguments.base_model.expanduser().resolve()
    validation = root_path(arguments.validation)
    manifest_path = root_path(arguments.dataset_manifest)
    if base.name != REVISION:
        raise RuntimeError("base revision drifted")
    if sha256_file(validation) != VALIDATION_SHA256 or sha256_file(manifest_path) != DATASET_MANIFEST_SHA256:
        raise RuntimeError("development data checksum drifted")
    manifest = load_json(manifest_path)
    if manifest.get("sourceManifestSha256") != SOURCE_MANIFEST_SHA256:
        raise RuntimeError("source corpus checksum drifted")
    rows = load_validation(validation)
    adapter = arguments.adapter.expanduser().resolve() if arguments.adapter else None
    if arguments.mode == "base" and adapter is not None:
        raise RuntimeError("base mode cannot receive an adapter")
    if arguments.mode == "adapter" and adapter is None:
        raise RuntimeError("adapter mode requires an adapter")
    disable_network()
    tokenizer, model = load_model(base, arguments.mode, arguments.precision, adapter)
    started = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    results: list[dict[str, Any]] = []
    for index, row in enumerate(rows, 1):
        raw, generation = generate(tokenizer, model, row)
        results.append(score(row, raw, generation))
        print(json.dumps({"completed": index, "total": len(rows), "familyId": row["familyId"]}))
    finished = datetime.now(timezone.utc).replace(microsecond=0)
    restraint = [item for item, row in zip(results, rows) if row["expectedT1"]["disposition"] == "RESTRAIN"]
    diagnose = [item for item, row in zip(results, rows) if row["expectedT1"]["disposition"] == "DIAGNOSE"]
    report_value = {
        "schemaVersion": "1", "status": "COMPLETE", "mode": arguments.mode,
        "precision": arguments.precision, "baseRevision": REVISION,
        "adapterSha256": directory_sha256(adapter) if adapter else None,
        "datasetManifestSha256": DATASET_MANIFEST_SHA256, "validationSha256": VALIDATION_SHA256,
        "sourceManifestSha256": SOURCE_MANIFEST_SHA256, "rows": len(rows), "families": 20,
        "lockedFinalLoaded": False, "trainingPerformed": False,
        "generation": {"maxNewTokens": 128, "doSample": False, "numBeams": 1, "enableThinking": False, "localFilesOnly": True, "trustRemoteCode": False},
        "startedUtc": started.isoformat(), "finishedUtc": finished.isoformat(),
        "durationSeconds": round(time.perf_counter() - wall_started, 3),
        "metrics": {
            "validJson": metric(results, "validJson"),
            "validStructured": metric(results, "validStructured"),
            "causeAccuracy": metric(results, "causeCorrect"),
            "checkAccuracy": metric(results, "checkCorrect"),
            "jointAccuracy": metric(results, "jointCorrect"),
            "diagnoseJointAccuracy": {"matched": sum(item["jointCorrect"] for item in diagnose), "total": len(diagnose)},
            "strictRestraint": {"matched": sum(item["strictRestraint"] for item in restraint), "total": len(restraint)},
            "unknownIndexes": sum(item["unknownIndex"] for item in results),
            "contractFieldMismatch": sum(item["contractFieldMismatch"] for item in results),
            "genericFallbackMisuse": sum(item["genericFallbackMisuse"] for item in results),
            "hiddenThinking": sum(item["hiddenThinking"] for item in results),
            "orderInvariance": order_invariance(results),
        },
        "performance": {
            "meanLatencySeconds": round(sum(item["latencySeconds"] for item in results) / len(results), 6),
            "peakGpuVramBytes": max(item["peakGpuVramBytes"] for item in results),
            "peakGpuVramReservedBytes": max(item["peakGpuVramReservedBytes"] for item in results),
            "maximumInputTokens": max(item["inputTokens"] for item in results),
            "maximumOutputTokens": max(item["outputTokens"] for item in results),
        },
        "network": {"access": False, "uploads": False, "telemetry": False},
        "packages": package_versions(), "results": results,
    }
    report.parent.mkdir(parents=True, exist_ok=True)
    with report.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(report_value, destination, ensure_ascii=False, indent=2)
        destination.write("\n")
    print(json.dumps({"status": "COMPLETE", "report": "<WSL_CACHE>/phase-5-4/reports/" + report.name}))


if __name__ == "__main__":
    main()
