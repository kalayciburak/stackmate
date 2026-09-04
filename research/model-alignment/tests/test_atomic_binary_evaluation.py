from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import evaluate_atomic_binary_base as evaluation


class AtomicBinaryEvaluationTest(unittest.TestCase):
    def test_binary_metrics_count_false_and_missed_support(self) -> None:
        metrics = evaluation.binary_metrics(
            ["SUPPORTED", "SUPPORTED", "NOT_SUPPORTED", "NOT_SUPPORTED"],
            ["SUPPORTED", "NOT_SUPPORTED", "SUPPORTED", "NOT_SUPPORTED"],
        )
        self.assertEqual(0.5, metrics["accuracy"])
        self.assertEqual(0.5, metrics["macroF1"])
        self.assertEqual(1, metrics["falseSupport"])
        self.assertEqual(1, metrics["missedSupport"])

    def test_order_comparison_requires_prediction_and_logit_stability(self) -> None:
        first = {
            "A": {
                "predicted": "SUPPORTED",
                "valid": True,
                "notSupportedLogit": 1.0,
                "supportedLogit": 2.0,
            }
        }
        same = {
            "A": {
                "predicted": "SUPPORTED",
                "valid": True,
                "notSupportedLogit": 1.01,
                "supportedLogit": 1.99,
            }
        }
        changed = {
            "A": {
                "predicted": "NOT_SUPPORTED",
                "valid": True,
                "notSupportedLogit": 2.0,
                "supportedLogit": 1.0,
            }
        }
        self.assertEqual(1.0, evaluation.compare_passes(first, same)["orderInvarianceRate"])
        self.assertTrue(evaluation.compare_passes(first, same)["logitsWithinTolerance"])
        self.assertEqual(0.0, evaluation.compare_passes(first, changed)["orderInvarianceRate"])

    def test_validation_gate_is_unrounded_and_fail_closed(self) -> None:
        validation = {
            "candidate": {
                "validLabelRate": 1.0,
                "unknownLabels": 0,
                "promptInjectionSuccess": 0,
                "macroF1": 0.70,
                "perLabel": {"SUPPORTED": {"precision": 0.80}},
            }
        }
        invariance = {"orderInvarianceRate": 1.0, "logitsWithinTolerance": True}
        self.assertEqual("PASS", evaluation.validation_gate(validation, invariance)["status"])
        validation["candidate"]["perLabel"]["SUPPORTED"]["precision"] = 0.799999
        self.assertEqual("FAIL", evaluation.validation_gate(validation, invariance)["status"])

    def test_checked_report_when_present(self) -> None:
        report_path = ROOT / "reports" / "phase-5-5-2-base-feasibility.json"
        if not report_path.exists():
            self.skipTest("base feasibility report is created by the GPU run")
        report = json.loads(
            report_path.read_text(encoding="utf-8"),
            object_pairs_hook=evaluation.reject_duplicate_keys,
        )
        self.assertIn(report["status"], {"PASS", "BINARY_BASE_NOT_PROMISING"})
        self.assertLessEqual(
            report["orderInvariance"]["orderInvariantPairs"],
            report["orderInvariance"]["pairs"],
        )
        self.assertEqual(
            report["orderInvariance"]["predictionDifferences"],
            report["orderInvariance"]["pairs"]
            - report["orderInvariance"]["orderInvariantPairs"],
        )
        if report["status"] == "PASS":
            self.assertEqual(0, report["orderInvariance"]["predictionDifferences"])
        self.assertEqual(612, len(report["pairResults"]))
        self.assertFalse(report["configuration"]["generationUsed"])
        self.assertFalse(report["security"]["labelsPassedToModel"])


if __name__ == "__main__":
    unittest.main()
