#!/usr/bin/env python3
"""Classify original locked outputs without invoking a model."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from t1_contract import score_t1


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_INPUT_SHA256 = "e7323f558fbe968711daa1075ed106a64f9e8d66e12d2292153bb6410d792777"
EXPECTED_RAW_SHA256 = "6a222a12dfea9d612510ded7861893f7f3f8dc1b12af47cef927da14033a1b60"
EXPECTED_AUTHORITATIVE_SHA256 = "83c9218f7733f91d4911254aa872217fbccc24ec7b029c74337594b56e782ea3"
EXPECTED_RECEIPT_SHA256 = "502bfbfcae48474c752b3ac8b142ecd19d76f4d67cc9944f4d52952dc6e6f81f"
STAGES = ("benchmark-v2-holdout", "phase-5-blind", "post-training-challenge")
CONDITIONS = ("B0", "B1", "A1", "A2")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", required=True, type=Path)
    parser.add_argument("--raw-report", required=True, type=Path)
    parser.add_argument("--authoritative-report", required=True, type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=duplicate_pairs)
    if not isinstance(value, dict):
        raise ValueError(f"JSON object expected: {path}")
    return value


def duplicate_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def schema_v2_shape(raw: str) -> tuple[bool, tuple[str, ...]]:
    try:
        value = json.loads(raw, object_pairs_hook=duplicate_pairs)
    except (ValueError, TypeError, json.JSONDecodeError):
        return False, ("INVALID_JSON",)
    if not isinstance(value, dict):
        return False, ("ROOT_NOT_OBJECT",)
    expected = {
        "schemaVersion", "summary", "explanation", "selectedCauseIds",
        "selectedCheckIds", "usedEvidenceIds", "limitations",
    }
    codes: list[str] = []
    if set(value) != expected:
        codes.append("SCHEMA_V2_FIELDS")
    if value.get("schemaVersion") != "2":
        codes.append("SCHEMA_V2_VERSION")
    for field in ("summary", "explanation"):
        if not isinstance(value.get(field), str) or not value[field].strip():
            codes.append(f"SCHEMA_V2_{field.upper()}")
    for field in ("selectedCauseIds", "selectedCheckIds", "usedEvidenceIds", "limitations"):
        values = value.get(field)
        if not isinstance(values, list) or any(not isinstance(item, str) for item in values):
            codes.append(f"SCHEMA_V2_{field.upper()}")
    return not codes, tuple(dict.fromkeys(codes))


def classify(raw: str, case: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    v2_valid, v2_codes = schema_v2_shape(raw)
    request = case["request"]
    cause_ids = [item["id"] for item in request["candidateCauses"]]
    check_ids = [item["id"] for item in request["candidateChecks"]]
    expected_causes = list(case.get("expectedCauseIds", []))
    expected_checks = list(case.get("expectedCheckIds", []))
    expected_cause_indexes = [cause_ids.index(item) for item in expected_causes]
    expected_check_indexes = [check_ids.index(item) for item in expected_checks]
    t1 = score_t1(
        raw,
        cause_count=len(cause_ids),
        check_count=len(check_ids),
        expected_cause_indexes=expected_cause_indexes,
        expected_check_indexes=expected_check_indexes,
        restraint_required=bool(case.get("abstentionRequired")),
    )
    try:
        json.loads(raw, object_pairs_hook=duplicate_pairs)
        parsed_json = True
    except (ValueError, TypeError, json.JSONDecodeError):
        parsed_json = False
    stripped = raw.strip()
    if not parsed_json and stripped.startswith("{") and not stripped.endswith("}"):
        classification = "TRUNCATED"
    elif t1["exactT1"] and v2_valid:
        classification = "VALID_BOTH"
    elif t1["exactT1"]:
        classification = "VALID_T1"
    elif v2_valid:
        classification = "VALID_SCHEMA_V2"
    elif parsed_json:
        classification = "INVALID_BOTH"
    else:
        classification = "UNKNOWN_FORMAT"
    return classification, {
        "classification": classification,
        "t1": t1,
        "schemaV2": {"validShape": v2_valid, "failureCodes": list(v2_codes)},
        "rawOutputSha256": hashlib.sha256(raw.encode("utf-8")).hexdigest(),
        "rawKeys": sorted(json.loads(raw).keys()) if parsed_json and isinstance(json.loads(raw), dict) else [],
    }


def main() -> None:
    arguments = parse_args()
    inputs_path = arguments.inputs.expanduser().resolve()
    raw_path = arguments.raw_report.expanduser().resolve()
    authoritative_path = arguments.authoritative_report.expanduser().resolve()
    receipt_path = arguments.receipt.expanduser().resolve()
    output_path = arguments.output.expanduser().resolve()
    if output_path.exists():
        raise FileExistsError(f"forensic report already exists: {output_path}")
    hashes = {
        "inputs": sha256_file(inputs_path),
        "rawReport": sha256_file(raw_path),
        "authoritativeReport": sha256_file(authoritative_path),
        "receipt": sha256_file(receipt_path),
    }
    expected = {
        "inputs": EXPECTED_INPUT_SHA256,
        "rawReport": EXPECTED_RAW_SHA256,
        "authoritativeReport": EXPECTED_AUTHORITATIVE_SHA256,
        "receipt": EXPECTED_RECEIPT_SHA256,
    }
    if hashes != expected:
        raise RuntimeError(f"historical evidence hash drift: {hashes}")
    inputs = read_json(inputs_path)
    raw = read_json(raw_path)
    cases = {
        (stage, case["caseId"]): case
        for stage in STAGES
        for case in inputs["stages"][stage]
    }
    by_condition: dict[str, list[dict[str, Any]]] = defaultdict(list)
    classifications: Counter[str] = Counter()
    t1_metrics: dict[str, Counter[str]] = defaultdict(Counter)
    v2_metrics: dict[str, Counter[str]] = defaultdict(Counter)
    field_signatures: dict[str, Counter[str]] = defaultdict(Counter)
    details: list[dict[str, Any]] = []
    for item in raw["results"]:
        key = (item["stage"], item["caseId"])
        case = cases[key]
        classification, detail = classify(item.get("rawOutput", ""), case)
        condition = item["condition"]
        classifications[classification] += 1
        by_condition[condition].append(detail)
        t1_metrics[condition]["validT1"] += int(detail["t1"]["exactT1"])
        t1_metrics[condition]["causeCorrect"] += int(bool(detail["t1"].get("causeCorrect")))
        t1_metrics[condition]["checkCorrect"] += int(bool(detail["t1"].get("checkCorrect")))
        t1_metrics[condition]["jointCorrect"] += int(bool(detail["t1"].get("jointCorrect")))
        t1_metrics[condition]["strictRestraint"] += int(bool(detail["t1"].get("strictRestraint")))
        v2_metrics[condition]["validSchemaV2Shape"] += int(detail["schemaV2"]["validShape"])
        field_signatures[condition]["|".join(detail["rawKeys"])] += 1
        details.append({
            "stage": item["stage"],
            "condition": condition,
            "caseId": item["caseId"],
            "promptId": item["promptId"],
            **detail,
        })
    report = {
        "schemaVersion": "1",
        "status": "FORENSIC_DIAGNOSTIC_ONLY",
        "originalFinalStatus": "CONTRACT_CONTAMINATED_FINAL",
        "evidenceHashes": hashes,
        "results": len(details),
        "classifications": dict(classifications),
        "byCondition": {
            condition: {
                "results": len(by_condition[condition]),
                "classifications": dict(Counter(item["classification"] for item in by_condition[condition])),
                "t1Metrics": dict(t1_metrics[condition]),
                "schemaV2Metrics": dict(v2_metrics[condition]),
                "rawFieldSignatures": dict(field_signatures[condition]),
            }
            for condition in CONDITIONS
        },
        "t1SalvageBoundary": "No original A1/A2 result is a valid T1 output when the locked P3/P2 path is classified.",
        "details": details,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(report, destination, ensure_ascii=False, indent=2)
        destination.write("\n")
    print(json.dumps({"status": report["status"], "output": str(output_path), "classifications": report["classifications"]}))


if __name__ == "__main__":
    main()
