from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from t1_contract import score_t1


ROOT = Path(__file__).resolve().parents[1]


class T1ContractTest(unittest.TestCase):
    def test_t1_golden_fixtures(self) -> None:
        fixture = json.loads((ROOT / "tests" / "fixtures" / "t1-golden.json").read_text(encoding="utf-8"))
        self.assertEqual("1", fixture["schemaVersion"])
        self.assertGreaterEqual(len(fixture["fixtures"]), 50)
        for item in fixture["fixtures"]:
            result = score_t1(
                item["raw"],
                cause_count=item["causeCount"],
                check_count=item["checkCount"],
            )
            self.assertEqual(result["exactT1"], item["expected"]["exactT1"], item["id"])
            self.assertEqual(result["boundsValid"], item["expected"]["boundsValid"], item["id"])
            self.assertEqual(result["failureCodes"], item["expected"]["failureCodes"], item["id"])

    def test_t1_does_not_accept_schema_v2_fields(self) -> None:
        raw = json.dumps(
            {
                "schemaVersion": "2",
                "summary": "x",
                "explanation": "x",
                "selectedCauseIds": [],
                "selectedCheckIds": [],
                "usedEvidenceIds": [],
                "limitations": ["EVIDENCE_INSUFFICIENT"],
            }
        )
        result = score_t1(raw, cause_count=1, check_count=1)
        self.assertFalse(result["exactT1"])
        self.assertIn("MISSING_FIELDS", result["failureCodes"])
        self.assertIn("EXTRA_FIELDS", result["failureCodes"])
