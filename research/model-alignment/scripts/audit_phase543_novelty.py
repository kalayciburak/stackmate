#!/usr/bin/env python3
"""Audit proposed corpus-v6 families against the complete exposed inventory."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any


REPOSITORY = Path(__file__).resolve().parents[3]
ALIGNMENT = REPOSITORY / "research" / "model-alignment"
INVENTORY = ALIGNMENT / "reports" / "phase-5-4-3-existing-family-inventory.json"
DESCRIPTORS = REPOSITORY / "stackmate-benchmark" / "src" / "main" / "resources" / "corpus-v6-fresh-final" / "descriptors" / "manifest.json"
OUTPUT = ALIGNMENT / "reports" / "phase-5-4-3-family-novelty.json"
TOKEN = re.compile(r"[a-z][a-z0-9_$.-]*|<[^>]+>", re.I)


def read(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path}")
    return value


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize(value: Any) -> str:
    text = json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).casefold()
    text = re.sub(r"\b[0-9a-f]{8}-[0-9a-f-]{27,}\b", "<uuid>", text)
    text = re.sub(r"\b\d+\b", "<number>", text)
    text = re.sub(r"[a-z]:[/\\][^\"\s]+|/(?:home|mnt|tmp)/[^\"\s]+", "<path>", text)
    return " ".join(TOKEN.findall(text))


def ngrams(text: str, size: int, *, characters: bool) -> set[str]:
    values = text if characters else text.split()
    if len(values) < size:
        return {str(values)} if values else set()
    return {
        "".join(values[index:index + size]) if characters else " ".join(values[index:index + size])
        for index in range(len(values) - size + 1)
    }


def jaccard(left: set[str], right: set[str]) -> float:
    if not left and not right:
        return 1.0
    return len(left & right) / len(left | right) if left | right else 0.0


def descriptor_text(family: dict[str, Any]) -> str:
    return normalize({
        "recipe": family["recipeId"],
        "category": family["category"],
        "throwable": family["expectedThrowableChain"],
        "topology": family["dependencyTopology"],
        "setup": family["setup"],
        "trigger": family["trigger"],
        "novelty": family["noveltyRationale"],
    })


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--reproduction", type=Path)
    parser.add_argument("--resolved-descriptors", type=Path)
    parser.add_argument("--labels", type=Path)
    return parser.parse_args()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def task_semantic(
    descriptor: dict[str, Any], label: dict[str, Any]
) -> dict[str, Any]:
    observed = descriptor["observed"]
    causes = {item["id"]: item for item in observed["candidateCauses"]}
    checks = {item["id"]: item for item in observed["candidateChecks"]}
    return {
        "recipe": descriptor["recipeId"],
        "category": label["category"],
        "throwable": observed.get("throwableChain", descriptor["expectedThrowableChain"]),
        "evidence": sorted(
            ({"type": item["type"], "value": item["value"]} for item in observed["facts"]),
            key=lambda item: (item["type"], item["value"]),
        ),
        "causes": sorted(item["text"] for item in causes.values()),
        "checks": sorted(item["text"] for item in checks.values()),
        "selectedCauses": sorted(causes[item]["text"] for item in label["expectedCauseIds"]),
        "selectedChecks": sorted(checks[item]["text"] for item in label["expectedCheckIds"]),
        "disposition": label["disposition"],
        "dependencyTopology": descriptor["dependencyTopology"],
        "classification": descriptor["classification"],
    }


def main() -> None:
    args = arguments()
    output = args.output.expanduser().resolve()
    if output.exists():
        raise FileExistsError(f"novelty report already exists: {output}")
    inventory = read(INVENTORY)
    descriptors = read(DESCRIPTORS)
    reproduction = read(args.reproduction.expanduser().resolve()) if args.reproduction else None
    resolved = read(args.resolved_descriptors.expanduser().resolve()) if args.resolved_descriptors else None
    labels = read_jsonl(args.labels.expanduser().resolve()) if args.labels else None
    if (resolved is None) != (labels is None):
        raise RuntimeError("resolved descriptors and labels must be supplied together")
    families = descriptors["families"]
    existing_ids = {
        item["familyId"] for item in inventory["records"]
    } | {
        item["canonicalFamilyId"] for item in inventory["records"]
    }
    if len(families) != 30 or len({item["familyId"] for item in families}) != 30:
        raise RuntimeError("fresh design must contain exactly 30 unique families")
    real = [item for item in families if item["classification"].startswith("CONTROLLED_REAL_")]
    synthetic = [item for item in families if item["classification"] == "CONTROLLED_SYNTHETIC_ADVERSARIAL"]
    if len(real) != 24 or len(synthetic) != 6:
        raise RuntimeError("fresh design must contain 24 real and 6 synthetic families")
    if reproduction is not None:
        reproduced_ids = {item["familyId"] for item in reproduction.get("families", [])}
        expected_real_ids = {item["familyId"] for item in real}
        if (
            reproduction.get("status") != "PASS"
            or reproduction.get("unresolvedFailures") != 0
            or reproduced_ids != expected_real_ids
        ):
            raise RuntimeError("reproduction report does not cover all 24 real families")
    findings: list[dict[str, Any]] = []
    rejected: list[str] = []
    existing = [
        (
            item,
            item["normalizedSemanticText"],
            ngrams(item["normalizedSemanticText"], 5, characters=True),
            ngrams(item["normalizedSemanticText"], 3, characters=False),
        )
        for item in inventory["records"]
    ]
    resolved_by_id = (
        {item["familyId"]: item for item in resolved["families"]}
        if resolved is not None
        else {}
    )
    labels_by_id = (
        {item["familyId"]: item for item in labels}
        if labels is not None
        else {}
    )
    if resolved is not None and (
        set(resolved_by_id) != {item["familyId"] for item in families}
        or set(labels_by_id) != set(resolved_by_id)
    ):
        raise RuntimeError("resolved task artifacts do not match descriptor identities")
    for family in families:
        if family["familyId"] in existing_ids:
            rejected.append(family["familyId"] + ":ID_REUSE")
        unknown_closest = [item for item in family["closestExistingFamilies"] if item not in existing_ids]
        if unknown_closest:
            rejected.append(family["familyId"] + ":UNKNOWN_CLOSEST:" + ",".join(unknown_closest))
        fresh_text = descriptor_text(family)
        fresh_chars = ngrams(fresh_text, 5, characters=True)
        fresh_tokens = ngrams(fresh_text, 3, characters=False)
        scored = []
        for item, old_text, old_chars, old_tokens in existing:
            scored.append({
                "familyId": item["familyId"],
                "catalog": item["catalog"],
                "characterFiveGramJaccard": round(jaccard(fresh_chars, old_chars), 6),
                "tokenThreeGramJaccard": round(jaccard(fresh_tokens, old_tokens), 6),
                "normalizedTextEqual": fresh_text == old_text,
            })
        scored.sort(key=lambda item: (item["characterFiveGramJaccard"] + item["tokenThreeGramJaccard"], item["characterFiveGramJaccard"], item["tokenThreeGramJaccard"]), reverse=True)
        nearest = scored[:3]
        threshold_failure = any(
            item["normalizedTextEqual"]
            or (
                item["characterFiveGramJaccard"] >= 0.88
                and item["tokenThreeGramJaccard"] >= 0.80
            )
            for item in nearest
        )
        if threshold_failure:
            rejected.append(family["familyId"] + ":AUTOMATED_DUPLICATE")
        task_nearest: list[dict[str, Any]] = []
        task_threshold_failure = False
        task_signature = None
        if resolved is not None:
            task_value = task_semantic(
                resolved_by_id[family["familyId"]], labels_by_id[family["familyId"]]
            )
            task_text = normalize(task_value)
            comparable = {
                key: task_value[key]
                for key in (
                    "recipe",
                    "category",
                    "throwable",
                    "evidence",
                    "causes",
                    "checks",
                    "dependencyTopology",
                    "classification",
                )
            }
            task_signature = hashlib.sha256(
                normalize(comparable).encode("utf-8")
            ).hexdigest()
            task_chars = ngrams(task_text, 5, characters=True)
            task_tokens = ngrams(task_text, 3, characters=False)
            task_scored = [
                {
                    "familyId": item["familyId"],
                    "catalog": item["catalog"],
                    "characterFiveGramJaccard": round(
                        jaccard(task_chars, old_chars), 6
                    ),
                    "tokenThreeGramJaccard": round(
                        jaccard(task_tokens, old_tokens), 6
                    ),
                    "normalizedTextEqual": task_text == old_text,
                    "semanticSignatureEqual": task_signature
                    == item["semanticSignature"],
                }
                for item, old_text, old_chars, old_tokens in existing
            ]
            task_scored.sort(
                key=lambda item: (
                    item["characterFiveGramJaccard"]
                    + item["tokenThreeGramJaccard"],
                    item["characterFiveGramJaccard"],
                    item["tokenThreeGramJaccard"],
                ),
                reverse=True,
            )
            task_nearest = task_scored[:3]
            task_threshold_failure = any(
                item["normalizedTextEqual"]
                or item["semanticSignatureEqual"]
                or (
                    item["characterFiveGramJaccard"] >= 0.88
                    and item["tokenThreeGramJaccard"] >= 0.80
                )
                for item in task_nearest
            )
            if task_threshold_failure:
                rejected.append(family["familyId"] + ":TASK_LEVEL_DUPLICATE")
        findings.append({
            "familyId": family["familyId"],
            "recipeId": family["recipeId"],
            "classification": family["classification"],
            "declaredClosestExistingFamilies": family["closestExistingFamilies"],
            "noveltyRationale": family["noveltyRationale"],
            "nearestAutomatedMatches": nearest,
            "automatedThresholdPass": not threshold_failure,
            "taskSemanticSignature": task_signature,
            "taskNearestAutomatedMatches": task_nearest,
            "taskAutomatedThresholdPass": not task_threshold_failure,
            "manualSemanticReview": "RECORDED_SEPARATELY",
        })
    report = {
        "schemaVersion": "1",
        "status": "PASS" if not rejected else "FAIL",
        "inventorySha256": sha(INVENTORY),
        "descriptorSha256": sha(DESCRIPTORS),
        "reproductionSha256": sha(args.reproduction.expanduser().resolve()) if args.reproduction else None,
        "resolvedDescriptorSha256": sha(args.resolved_descriptors.expanduser().resolve()) if args.resolved_descriptors else None,
        "scoringLabelsSha256": sha(args.labels.expanduser().resolve()) if args.labels else None,
        "familyCount": len(families),
        "realFamilies": len(real),
        "syntheticFamilies": len(synthetic),
        "rejected": rejected,
        "thresholds": {"characterFiveGram": 0.88, "tokenThreeGram": 0.80},
        "findings": findings,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({key: report[key] for key in ("status", "familyCount", "realFamilies", "syntheticFamilies", "rejected")}, separators=(",", ":")))
    if rejected:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
