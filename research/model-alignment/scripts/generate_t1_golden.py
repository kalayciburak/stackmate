#!/usr/bin/env python3
"""Create deterministic T1 parser golden fixtures without model execution."""

from __future__ import annotations

import json
from pathlib import Path

from t1_contract import score_t1


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "tests" / "fixtures" / "t1-golden.json"


def raw(causes: object, checks: object, limitation: object, extra: str = "") -> str:
    value = {
        "selectedCauseIndexes": causes,
        "selectedCheckIndexes": checks,
        "limitation": limitation,
    }
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")) + extra


def main() -> None:
    if OUTPUT.exists():
        raise FileExistsError(f"golden fixture already exists: {OUTPUT}")
    definitions: list[tuple[str, str, int, int]] = []
    for index in range(10):
        definitions.append((f"valid-single-{index:02d}", raw([index % 3], [index % 3], None), 3, 3))
    for index in range(10, 20):
        definitions.append((f"valid-multi-{index:02d}", raw([0, 1], [1, 2], None), 3, 3))
    for index in range(20, 25):
        definitions.append((f"valid-restraint-{index:02d}", raw([], [], "EVIDENCE_INSUFFICIENT"), 3, 3))
    definitions.extend(
        [
            ("duplicate-cause", raw([0, 0], [0], None), 3, 3),
            ("duplicate-check", raw([0], [1, 1], None), 3, 3),
            ("negative-cause", raw([-1], [0], None), 3, 3),
            ("out-of-bounds-check", raw([0], [3], None), 3, 3),
            ("missing-check", '{"selectedCauseIndexes":[0],"limitation":null}', 3, 3),
            ("extra-field", raw([0], [0], None).replace("}", ',"extra":true}'), 3, 3),
            ("invalid-limitation", raw([0], [0], "NOT_ALLOWED"), 3, 3),
            ("pair-mismatch", raw([0], [], None), 3, 3),
            ("cause-not-array", raw("0", [0], None), 3, 3),
            ("cause-bool", raw([True], [0], None), 3, 3),
            ("root-array", "[]", 3, 3),
            ("invalid-json", "{", 3, 3),
            ("trailing-prose", raw([0], [0], None, " trailing"), 3, 3),
            ("empty-diagnose-null", raw([], [], None), 3, 3),
            ("selected-insufficient", raw([0], [0], "EVIDENCE_INSUFFICIENT"), 3, 3),
            ("negative-check", raw([0], [-1], None), 3, 3),
            ("duplicate-both", raw([0, 0], [1, 1], None), 3, 3),
            ("check-not-array", raw([0], "0", None), 3, 3),
            ("limitation-number", raw([0], [0], 0), 3, 3),
            ("empty-object", "{}", 3, 3),
            ("valid-whitespace", "  " + raw([1], [2], None) + "  ", 3, 3),
            ("valid-unicode-irrelevant", raw([2], [1], None), 3, 3),
            ("out-of-bounds-both", raw([3], [3], None), 3, 3),
            ("mixed-index-types", raw([0, "1"], [0], None), 3, 3),
            ("null-root", "null", 3, 3),
        ]
    )
    if len(definitions) != 50:
        raise AssertionError(len(definitions))
    fixtures = []
    for identifier, value, cause_count, check_count in definitions:
        score = score_t1(value, cause_count=cause_count, check_count=check_count)
        fixtures.append(
            {
                "id": identifier,
                "raw": value,
                "causeCount": cause_count,
                "checkCount": check_count,
                "expected": {
                    "exactT1": score["exactT1"],
                    "boundsValid": score["boundsValid"],
                    "failureCodes": score["failureCodes"],
                },
            }
        )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps({"schemaVersion": "1", "fixtures": fixtures}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"status": "PASS", "fixtures": len(fixtures), "output": str(OUTPUT)}))


if __name__ == "__main__":
    main()
