from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import audit_atomic_contrastive_dataset as audit


def row(length: int, label: str, pair_id: str) -> audit.AuditRow:
    return audit.AuditRow(
        split="TRAIN",
        label=label,
        candidate_type="CHECK",
        semantic_id="CHECK_EXAMPLE",
        family_id=f"F-{pair_id}",
        pair_id=pair_id,
        pair_kind="HARD_NEGATIVE" if label == "NOT_SUPPORTED" else "ATOMIC_SUPPORT",
        difficulty="HARD" if label == "NOT_SUPPORTED" else "DIRECT",
        canonical_text="Inspect the bounded example.",
        candidate_model_text="Inspect the bounded example.",
        model_input=f"input-{pair_id}",
        normalized_model_input=f"input-{pair_id}",
        input_length=length,
        evidence_count=1,
        evidence_type_signature=("EXCEPTION_CLASS",),
        negative_evidence_present=False,
        negative_evidence_count=0,
        negative_evidence_serialized_length=2,
        negative_evidence_value_length=0,
        security_pattern=None,
        input_sha256=pair_id,
    )


class AtomicContrastiveAuditTest(unittest.TestCase):
    def test_binary_metrics_and_majority_tie_policy(self) -> None:
        metrics = audit.binary_metrics(
            ["NOT_SUPPORTED", "NOT_SUPPORTED", "SUPPORTED", "SUPPORTED"],
            ["NOT_SUPPORTED", "SUPPORTED", "SUPPORTED", "SUPPORTED"],
        )
        self.assertAlmostEqual(0.75, metrics["accuracy"])
        self.assertAlmostEqual(11 / 15, metrics["macroF1"])
        self.assertEqual(
            "NOT_SUPPORTED",
            audit.majority_label(["SUPPORTED", "NOT_SUPPORTED"]),
        )

    def test_s3_threshold_and_direction_are_selected_from_train_only(self) -> None:
        train = [
            row(1, "NOT_SUPPORTED", "A"),
            row(2, "NOT_SUPPORTED", "B"),
            row(8, "SUPPORTED", "C"),
            row(9, "SUPPORTED", "D"),
        ]
        rule = audit.fit_length_threshold(train)
        self.assertEqual(2, rule["threshold"])
        self.assertEqual("SUPPORTED_IF_GT", rule["direction"])
        self.assertEqual(1.0, rule["trainMetrics"]["accuracy"])

        adversarial_validation = [
            row(1, "SUPPORTED", "V1"),
            row(9, "NOT_SUPPORTED", "V2"),
        ]
        predictions = [
            audit.predict_length(rule, item.input_length)
            for item in adversarial_validation
        ]
        self.assertEqual(["NOT_SUPPORTED", "SUPPORTED"], predictions)
        self.assertEqual(
            0.0,
            audit.binary_metrics(
                [item.label for item in adversarial_validation], predictions
            )["accuracy"],
        )

    def test_checked_reports_when_present(self) -> None:
        dataset_report = ROOT / "reports" / "phase-5-5-2-dataset-audit.json"
        baseline_report = ROOT / "reports" / "phase-5-5-2-shortcut-baselines.json"
        if not dataset_report.exists() and not baseline_report.exists():
            self.skipTest("CREATE_NEW audit reports have not been published yet")
        self.assertTrue(dataset_report.is_file())
        self.assertTrue(baseline_report.is_file())
        dataset = json.loads(dataset_report.read_text(encoding="utf-8"))
        baselines = json.loads(baseline_report.read_text(encoding="utf-8"))
        self.assertEqual("PASS", dataset["status"])
        self.assertEqual("PASS", dataset["gateB"]["status"])
        self.assertEqual("PASS", dataset["gateC"]["status"])
        self.assertEqual("PASS", dataset["gateD"]["status"])
        self.assertTrue(
            all(
                check["status"] == "PASS"
            for gate in (dataset["gateB"], dataset["gateC"], dataset["gateD"])
                for check in gate["checks"]
            )
        )
        self.assertEqual("PASS", baselines["status"])
        self.assertTrue(
            all(check["status"] == "PASS" for check in baselines["checks"])
        )


if __name__ == "__main__":
    unittest.main()
