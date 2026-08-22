"""Authoritative ordinal T1 parser and scorer.

This module deliberately does not accept the historical schema-v2 ID contract.
It is shared by validation, forensic analysis, and the fresh T1 evaluator.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any


T1_KEYS = frozenset({"selectedCauseIndexes", "selectedCheckIndexes", "limitation"})
T1_LIMITATIONS = frozenset({None, "EVIDENCE_INSUFFICIENT"})


def duplicate_rejecting_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"DUPLICATE_JSON_KEY:{key}")
        result[key] = value
    return result


@dataclass(frozen=True)
class T1Parse:
    valid: bool
    output: dict[str, Any] | None
    codes: tuple[str, ...]


def parse_t1(raw: str) -> T1Parse:
    try:
        value = json.loads(raw, object_pairs_hook=duplicate_rejecting_pairs)
    except json.JSONDecodeError:
        return T1Parse(False, None, ("INVALID_JSON",))
    except ValueError as failure:
        code = str(failure).split(":", 1)[0]
        return T1Parse(False, None, (code,))
    if not isinstance(value, dict):
        return T1Parse(False, None, ("ROOT_NOT_OBJECT",))
    codes: list[str] = []
    missing = T1_KEYS - set(value)
    extra = set(value) - T1_KEYS
    if missing:
        codes.append("MISSING_FIELDS")
    if extra:
        codes.append("EXTRA_FIELDS")
    for field in ("selectedCauseIndexes", "selectedCheckIndexes"):
        indexes = value.get(field)
        if not isinstance(indexes, list):
            codes.append(f"{field.upper()}_NOT_ARRAY")
            continue
        if any(not isinstance(item, int) or isinstance(item, bool) for item in indexes):
            codes.append(f"{field.upper()}_NOT_INTEGER_ARRAY")
        if len(indexes) != len(set(indexes)):
            codes.append(f"{field.upper()}_DUPLICATE")
    if value.get("limitation") not in T1_LIMITATIONS:
        codes.append("INVALID_LIMITATION")
    causes = value.get("selectedCauseIndexes")
    checks = value.get("selectedCheckIndexes")
    if isinstance(causes, list) and isinstance(checks, list) and bool(causes) != bool(checks):
        codes.append("CAUSE_CHECK_PAIR_MISMATCH")
    if codes:
        return T1Parse(False, value, tuple(dict.fromkeys(codes)))
    return T1Parse(True, value, ())


def score_t1(
    raw: str,
    *,
    cause_count: int,
    check_count: int,
    expected_cause_indexes: list[int] | None = None,
    expected_check_indexes: list[int] | None = None,
    restraint_required: bool | None = None,
) -> dict[str, Any]:
    parsed = parse_t1(raw)
    result: dict[str, Any] = {
        "validJson": parsed.output is not None,
        "exactT1": parsed.valid,
        "boundsValid": False,
        "limitationValid": parsed.valid,
        "causeCorrect": None,
        "checkCorrect": None,
        "jointCorrect": None,
        "strictRestraint": None,
        "failureCodes": list(parsed.codes),
    }
    if not parsed.valid or parsed.output is None:
        return result
    causes = parsed.output["selectedCauseIndexes"]
    checks = parsed.output["selectedCheckIndexes"]
    if any(item < 0 or item >= cause_count for item in causes):
        result["failureCodes"].append("CAUSE_INDEX_OUT_OF_BOUNDS")
    if any(item < 0 or item >= check_count for item in checks):
        result["failureCodes"].append("CHECK_INDEX_OUT_OF_BOUNDS")
    if result["failureCodes"]:
        result["exactT1"] = False
        return result
    result["boundsValid"] = True
    limitation = parsed.output["limitation"]
    if restraint_required is True:
        result["strictRestraint"] = (
            not causes and not checks and limitation == "EVIDENCE_INSUFFICIENT"
        )
        if not result["strictRestraint"]:
            result["limitationValid"] = False
            result["failureCodes"].append("RESTRAINT_CONTRACT_VIOLATION")
    elif restraint_required is False:
        result["strictRestraint"] = False
        if not causes and not checks:
            if limitation != "EVIDENCE_INSUFFICIENT":
                result["limitationValid"] = False
                result["failureCodes"].append("MISSING_INSUFFICIENT_LIMITATION")
        elif limitation is not None:
            result["limitationValid"] = False
            result["failureCodes"].append("CONTRADICTORY_LIMITATION")
    if expected_cause_indexes is not None:
        result["causeCorrect"] = causes == expected_cause_indexes
    if expected_check_indexes is not None:
        result["checkCorrect"] = checks == expected_check_indexes
    if result["causeCorrect"] is not None and result["checkCorrect"] is not None:
        result["jointCorrect"] = result["causeCorrect"] and result["checkCorrect"]
    result["exactT1"] = not result["failureCodes"] and result["limitationValid"]
    return result


def t1_candidate_counts(row: dict[str, Any]) -> tuple[int, int]:
    order = row.get("candidateOrder")
    if isinstance(order, dict):
        return len(order.get("causeIds", [])), len(order.get("checkIds", []))
    request = row.get("request", {})
    return len(request.get("candidateCauses", [])), len(request.get("candidateChecks", []))
