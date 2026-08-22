#!/usr/bin/env python3
"""Compare the frozen Phase 5.2 synthetic data with the Phase 5.3 corpus offline."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from collections import Counter
from pathlib import Path
from statistics import mean
from typing import Any


REPOSITORY = Path(__file__).resolve().parents[3]
UUID = re.compile(r"\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b", re.I)
NUMBER = re.compile(r"\b\d+\b")


def resolve(raw: str) -> Path:
    path = Path(raw)
    return (path if path.is_absolute() else REPOSITORY / path).resolve()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not rows or not all(isinstance(row, dict) for row in rows):
        raise ValueError(f"invalid or empty JSONL: {path}")
    return rows


def user_prompt(row: dict[str, Any]) -> str:
    messages = row.get("messages", [])
    for message in messages:
        if message.get("role") == "user":
            return str(message.get("content", ""))
    raise ValueError("row has no user message")


def normalized_prompt(value: str) -> str:
    value = UUID.sub("<uuid>", value.casefold())
    value = NUMBER.sub("<number>", value)
    return " ".join(value.split())


def percentile(values: list[int], percentile_value: float) -> int:
    ordered = sorted(values)
    index = max(0, math.ceil(percentile_value * len(ordered)) - 1)
    return ordered[index]


def expected_positions(row: dict[str, Any], version: str) -> tuple[list[int], list[int]]:
    if version == "old":
        target = json.loads(row["messages"][2]["content"])
        start = row["messages"][1]["content"].index("\n") + 1
        end = row["messages"][1]["content"].rindex("\n</STACKMATE_UNTRUSTED_INCIDENT>")
        request = json.loads(row["messages"][1]["content"][start:end])
        cause_ids = [item["id"] for item in request["candidateCauses"]]
        check_ids = [item["id"] for item in request["candidateChecks"]]
        return (
            [cause_ids.index(item) for item in target["selectedCauseIds"]],
            [check_ids.index(item) for item in target["selectedCheckIds"]],
        )
    expected = row["expectedT1"]
    return list(expected["selectedCauseIndexes"]), list(expected["selectedCheckIndexes"])


def summarize(rows: list[dict[str, Any]], version: str) -> dict[str, Any]:
    family_field = "templateFamily" if version == "old" else "familyId"
    families = Counter(str(row[family_field]) for row in rows)
    prompts = [user_prompt(row) for row in rows]
    prompt_counts = Counter(prompts)
    normalized_counts = Counter(normalized_prompt(prompt) for prompt in prompts)
    lengths = [len(prompt) for prompt in prompts]
    cause_positions: Counter[str] = Counter()
    check_positions: Counter[str] = Counter()
    for row in rows:
        causes, checks = expected_positions(row, version)
        cause_positions.update(str(index) for index in causes)
        check_positions.update(str(index) for index in checks)
    if version == "old":
        restraint = sum(bool(row["metadata"]["abstentionRequired"]) for row in rows)
        provenance = Counter(str(row.get("source", "UNKNOWN")) for row in rows)
        order_modes: Counter[str] = Counter({"OPAQUE_ID_ORDER": len(rows)})
        chain_signatures: Counter[str] = Counter()
    else:
        restraint = sum(row["expectedT1"]["disposition"] == "RESTRAIN" for row in rows)
        provenance = Counter(str(row["classification"]) for row in rows)
        order_modes = Counter(str(row["candidateOrder"]["mode"]) for row in rows)
        chain_signatures = Counter(
            ">".join(node["type"] for node in row["observedExceptionChain"])
            for row in rows
        )
    return {
        "rows": len(rows),
        "families": len(families),
        "variantsPerFamily": {
            "minimum": min(families.values()),
            "maximum": max(families.values()),
            "mean": round(mean(families.values()), 3),
            "distribution": dict(sorted(Counter(families.values()).items())),
        },
        "restraintRows": restraint,
        "provenanceRows": dict(sorted(provenance.items())),
        "candidateOrderRows": dict(sorted(order_modes.items())),
        "expectedCauseIndexCounts": dict(sorted(cause_positions.items())),
        "expectedCheckIndexCounts": dict(sorted(check_positions.items())),
        "userPromptCharacters": {
            "mean": round(mean(lengths), 3),
            "p95": percentile(lengths, 0.95),
            "maximum": max(lengths),
        },
        "uniqueUserPrompts": len(prompt_counts),
        "exactDuplicateUserPromptRows": sum(count - 1 for count in prompt_counts.values()),
        "uniqueNormalizedUserPrompts": len(normalized_counts),
        "normalizedDuplicateUserPromptRows": sum(count - 1 for count in normalized_counts.values()),
        "uniqueExceptionChainSignatures": len(chain_signatures),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--old-train", required=True)
    parser.add_argument("--old-validation", required=True)
    parser.add_argument("--new-train", required=True)
    parser.add_argument("--new-validation", required=True)
    parser.add_argument("--new-manifest", required=True)
    parser.add_argument("--report", required=True)
    arguments = parser.parse_args()
    paths = {
        "oldTrain": resolve(arguments.old_train),
        "oldValidation": resolve(arguments.old_validation),
        "newTrain": resolve(arguments.new_train),
        "newValidation": resolve(arguments.new_validation),
        "newManifest": resolve(arguments.new_manifest),
    }
    for label, path in paths.items():
        if not path.is_file():
            raise FileNotFoundError(f"missing {label}: {path}")
    report_path = resolve(arguments.report)
    if report_path.exists():
        raise FileExistsError(f"refusing to overwrite report: {report_path}")

    old_train = read_jsonl(paths["oldTrain"])
    old_validation = read_jsonl(paths["oldValidation"])
    new_train = read_jsonl(paths["newTrain"])
    new_validation = read_jsonl(paths["newValidation"])
    manifest = json.loads(paths["newManifest"].read_text(encoding="utf-8"))
    if (len(old_train), len(old_validation)) != (800, 200):
        raise ValueError("Phase 5.2 dataset row counts drifted")
    if (len(new_train), len(new_validation)) != (140, 40):
        raise ValueError("Phase 5.3 dataset row counts drifted")
    if manifest["counts"]["trainRows"] != 140 or manifest["counts"]["validationRows"] != 40:
        raise ValueError("Phase 5.3 manifest counts disagree with JSONL files")

    report = {
        "schemaVersion": "1",
        "status": "COMPLETE",
        "offline": True,
        "sources": {
            label: {"path": path.relative_to(REPOSITORY).as_posix(), "sha256": sha256(path)}
            for label, path in paths.items()
        },
        "oldSynthetic": {
            "train": summarize(old_train, "old"),
            "validation": summarize(old_validation, "old"),
        },
        "phase53RealWorld": {
            "train": summarize(new_train, "new"),
            "validation": summarize(new_validation, "new"),
        },
        "interpretation": [
            "The Phase 5.2 split repeats each family many times and is entirely deterministic synthetic data.",
            "The Phase 5.3 development split uses exactly two variants per family, includes controlled runtime and integration failures, and permutes candidate order once per family.",
            "These structural measurements do not by themselves prove downstream model generalization.",
        ],
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with report_path.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(report, destination, indent=2, ensure_ascii=False)
        destination.write("\n")
    print(json.dumps({"status": "COMPLETE", "report": report_path.relative_to(REPOSITORY).as_posix()}))


if __name__ == "__main__":
    main()
