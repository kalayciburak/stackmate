#!/usr/bin/env python3
"""Fail-closed WSL2/CUDA/base-checkpoint preflight for Phase 5.2."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import os
import platform
import sys
from pathlib import Path
from typing import Any

from dataset_contract import ROOT, canonical_json, sha256_file


EXPECTED_PACKAGES = {
    "accelerate": "1.14.0",
    "bitsandbytes": "0.50.0",
    "gguf": "0.19.0",
    "peft": "0.20.0",
    "torch": "2.13.0",
    "transformers": "5.14.1",
}


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def resolve(value: str) -> Path:
    return (ROOT / value).resolve()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/environment-v1.json")
    parser.add_argument("--report", default="reports/environment-preflight.json")
    arguments = parser.parse_args()
    config_path = resolve(arguments.config)
    config = read_json(config_path)
    failures: list[str] = []
    versions: dict[str, str] = {}
    for package, expected in EXPECTED_PACKAGES.items():
        try:
            actual = importlib.metadata.version(package)
        except importlib.metadata.PackageNotFoundError:
            actual = "NOT_INSTALLED"
        versions[package] = actual
        if actual != expected:
            failures.append(f"PACKAGE_VERSION:{package}:{actual}:{expected}")

    cuda: dict[str, Any] = {"available": False}
    try:
        import torch

        cuda = {
            "available": torch.cuda.is_available(),
            "torchCuda": torch.version.cuda,
            "deviceCount": torch.cuda.device_count(),
            "bf16Supported": torch.cuda.is_bf16_supported() if torch.cuda.is_available() else False,
        }
        if torch.cuda.is_available():
            properties = torch.cuda.get_device_properties(0)
            cuda.update(
                {
                    "name": properties.name,
                    "computeCapability": f"{properties.major}.{properties.minor}",
                    "totalMemoryBytes": properties.total_memory,
                }
            )
        else:
            failures.append("CUDA_UNAVAILABLE")
    except Exception as failure:
        failures.append(f"CUDA_PROBE:{type(failure).__name__}")

    dataset_lock = read_json(resolve(config["datasetLock"]))
    if dataset_lock["datasetManifestSha256"] != config["datasetManifestSha256"]:
        failures.append("DATASET_LOCK_MISMATCH")
    if dataset_lock["challengeManifestSha256"] != config["challengeManifestSha256"]:
        failures.append("CHALLENGE_LOCK_MISMATCH")

    model_setting = config["baseModel"]["localPathEnvironment"]
    model_value = os.environ.get(model_setting, "").strip()
    base: dict[str, Any] = {
        "repository": config["baseModel"]["repository"],
        "revision": config["baseModel"]["revision"],
        "pathEnvironment": model_setting,
        "available": False,
    }
    if not model_value:
        failures.append("BASE_MODEL_PATH_NOT_SET")
    else:
        model_path = Path(model_value).expanduser().resolve()
        base["path"] = str(model_path)
        missing = [name for name in config["baseModel"]["requiredFiles"] if not (model_path / name).is_file()]
        repository_segment = "models--" + config["baseModel"]["repository"].replace("/", "--")
        snapshot_revision_verified = (
            model_path.name == config["baseModel"]["revision"]
            and len(model_path.parents) >= 2
            and model_path.parents[1].name == repository_segment
        )
        base["snapshotRevisionVerified"] = snapshot_revision_verified
        if config["baseModel"]["requireHuggingFaceSnapshotRevision"] and not snapshot_revision_verified:
            failures.append("BASE_MODEL_REVISION_UNVERIFIED")

        referenced_shards: set[str] = set()
        index_path = model_path / "model.safetensors.index.json"
        if index_path.is_file():
            index = read_json(index_path)
            weight_map = index.get("weight_map", {})
            if isinstance(weight_map, dict):
                referenced_shards = {value for value in weight_map.values() if isinstance(value, str)}
        missing_shards = sorted(name for name in referenced_shards if not (model_path / name).is_file())
        if missing or not referenced_shards or missing_shards:
            base["missingRequiredFiles"] = missing
            base["missingSafetensorShards"] = missing_shards
            base["safetensorShardCount"] = len(referenced_shards) - len(missing_shards)
            failures.append("BASE_MODEL_CHECKPOINT_INCOMPLETE")
        else:
            base["available"] = snapshot_revision_verified
            base["safetensorShardCount"] = len(referenced_shards)

    report = {
        "schemaVersion": "1",
        "status": "PASS" if not failures else "BLOCKED",
        "configSha256": sha256_file(config_path),
        "python": sys.version.split()[0],
        "platform": platform.platform(),
        "packages": versions,
        "cuda": cuda,
        "baseModel": base,
        "datasetLockEnforced": True,
        "networkAccessAttempted": False,
        "failures": failures,
    }
    report_path = resolve(arguments.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(canonical_json({"status": report["status"], "failures": failures, "report": str(report_path)}))
    if failures:
        raise SystemExit(3)


if __name__ == "__main__":
    main()
