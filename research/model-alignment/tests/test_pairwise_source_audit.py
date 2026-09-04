from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import audit_pairwise_source as audit


class PairwiseSourceAuditTest(unittest.TestCase):
    @staticmethod
    def family(selected: set[str]) -> dict[str, object]:
        return {
            "evidenceById": {
                "E-1": {"id": "E-1", "type": "log", "value": "timeout"}
            },
            "kinds": {"cause": {"selected": frozenset(selected)}},
        }

    def test_listwise_nonselection_is_unknown_without_explicit_counterevidence(
        self,
    ) -> None:
        candidate = {
            "id": "C-OTHER",
            "text": "A plausible alternative",
            "evidenceIds": ["E-1"],
        }

        result = audit.pair_record(self.family(set()), "cause", candidate)

        self.assertEqual("UNKNOWN", result["label"])
        self.assertFalse(result["selected"])

    def test_explicit_candidate_local_negative_annotation_is_contradicted(
        self,
    ) -> None:
        candidate = {
            "id": "C-NEGATIVE",
            "text": "A candidate contradicted by the incident",
            "evidenceIds": ["E-1"],
            "contradictedEvidenceIds": ["E-1"],
        }

        result = audit.pair_record(self.family(set()), "cause", candidate)

        self.assertEqual("CONTRADICTED", result["label"])

    def test_checked_in_audit_records_the_three_class_blocker(self) -> None:
        report = json.loads(
            (ROOT / "reports" / "phase-5-5-pairwise-source-audit.json").read_text(
                encoding="utf-8"
            )
        )

        self.assertEqual("FAIL_CONTRADICTED_CLASS_ABSENT", report["status"])
        self.assertFalse(report["threeClassFeasible"])
        self.assertEqual(
            {"SUPPORTED": 102, "UNKNOWN": 104, "CONTRADICTED": 0},
            report["splits"]["TRAIN"]["labelCounts"],
        )
        self.assertEqual(
            {"SUPPORTED": 18, "UNKNOWN": 36, "CONTRADICTED": 0},
            report["splits"]["VALIDATION"]["labelCounts"],
        )
        self.assertEqual(0, report["crossSplit"]["familyOverlapCount"])
        self.assertFalse(report["privacy"]["privateAbsolutePathsIncluded"])
        self.assertFalse(report["privacy"]["individualExposedLabelsIncluded"])


if __name__ == "__main__":
    unittest.main()
