#!/usr/bin/env python3
"""Audit proposed corpus-v6 families against the complete exposed inventory."""

from __future__ import annotations

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
TOKEN = re.compile(r"[a-z][a-z0-9_$.-]*", re.I)


def read(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path}")
    return value


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize(value: Any) -> str:
    text = json.dumps(value, ensure_ascii=False, sort_keys=True).casefold()
    text = re.sub(r"\b\d+\b", " number ", text)
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


def main() -> None:
    if OUTPUT.exists():
        raise FileExistsError(f"novelty report already exists: {OUTPUT}")
    inventory = read(INVENTORY)
    descriptors = read(DESCRIPTORS)
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
        findings.append({
            "familyId": family["familyId"],
            "recipeId": family["recipeId"],
            "classification": family["classification"],
            "declaredClosestExistingFamilies": family["closestExistingFamilies"],
            "noveltyRationale": family["noveltyRationale"],
            "nearestAutomatedMatches": nearest,
            "automatedThresholdPass": not threshold_failure,
            "manualSemanticReview": "PASS_PENDING_REPRODUCTION",
        })
    report = {
        "schemaVersion": "1",
        "status": "PASS" if not rejected else "FAIL",
        "inventorySha256": sha(INVENTORY),
        "descriptorSha256": sha(DESCRIPTORS),
        "familyCount": len(families),
        "realFamilies": len(real),
        "syntheticFamilies": len(synthetic),
        "rejected": rejected,
        "thresholds": {"characterFiveGram": 0.88, "tokenThreeGram": 0.80},
        "findings": findings,
    }
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({key: report[key] for key in ("status", "familyCount", "realFamilies", "syntheticFamilies", "rejected")}, separators=(",", ":")))
    if rejected:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
