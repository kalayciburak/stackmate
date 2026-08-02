from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


@dataclass(frozen=True)
class AdapterLock:
    adapter_id: str
    base_model_revision: str
    dataset_manifest_sha256: str
    training_config_sha256: str
    adapter_sha256: str


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object in {path}")
    return value


def require_sha256(value: object, field: str) -> str:
    if not isinstance(value, str) or SHA256_PATTERN.fullmatch(value) is None:
        raise ValueError(f"{field} must be a lowercase SHA-256 value")
    return value


def read_adapter_lock(
    path: Path,
    *,
    expected_base_revision: str,
    expected_dataset_manifest_sha256: str,
) -> AdapterLock:
    report = load_json(path)
    if report.get("schemaVersion") != "1":
        raise ValueError("Unsupported selected-adapter schema")
    if report.get("baseModelRevision") != expected_base_revision:
        raise ValueError("Selected adapter uses a different base-model revision")
    if report.get("datasetManifestSha256") != expected_dataset_manifest_sha256:
        raise ValueError("Selected adapter uses a different dataset manifest")

    adapter_id = report.get("adapterId")
    if not isinstance(adapter_id, str) or not adapter_id:
        raise ValueError("adapterId is required")

    return AdapterLock(
        adapter_id=adapter_id,
        base_model_revision=expected_base_revision,
        dataset_manifest_sha256=expected_dataset_manifest_sha256,
        training_config_sha256=require_sha256(
            report.get("trainingConfigSha256"), "trainingConfigSha256"
        ),
        adapter_sha256=require_sha256(report.get("adapterSha256"), "adapterSha256"),
    )


def validate_evaluation_lock(
    config_path: Path,
    *,
    expected_stage_hashes: dict[str, str],
) -> dict[str, Any]:
    config = load_json(config_path)
    if config.get("schemaVersion") != "1":
        raise ValueError("Unsupported evaluation-lock schema")
    if config.get("selectionMayUseLockedResults") is not False:
        raise ValueError("Locked results must not influence adapter selection")
    if config.get("maximumLockedExecutions") != 1:
        raise ValueError("Locked evaluation must allow exactly one execution")
    if config.get("developmentInputs") != ["datasets/validation.jsonl"]:
        raise ValueError("Only the training validation split may be used for selection")

    stages = config.get("lockedStages")
    if not isinstance(stages, list):
        raise ValueError("lockedStages must be an array")
    expected_names = list(expected_stage_hashes)
    if [stage.get("name") for stage in stages] != expected_names:
        raise ValueError("Locked stages or their order changed")
    if [stage.get("order") for stage in stages] != list(range(1, len(stages) + 1)):
        raise ValueError("Locked stage order must be contiguous")

    for stage in stages:
        name = stage["name"]
        actual = require_sha256(stage.get("manifestSha256"), f"{name}.manifestSha256")
        if actual != expected_stage_hashes[name]:
            raise ValueError(f"Locked manifest checksum changed for {name}")
    return config


def compare_system_metrics(
    base: dict[str, int], adapter: dict[str, int], *, required_keys: tuple[str, ...]
) -> dict[str, int]:
    if set(base) != set(required_keys) or set(adapter) != set(required_keys):
        raise ValueError("Base and adapter reports must contain the same required metrics")
    return {key: adapter[key] - base[key] for key in required_keys}
