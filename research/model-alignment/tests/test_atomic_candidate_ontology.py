from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import atomic_candidate_ontology as ontology


class AtomicCandidateOntologyTest(unittest.TestCase):
    def test_generated_ontology_is_atomic_and_linked(self) -> None:
        generated = ontology.build_ontology()
        ontology.validate_ontology(generated)
        self.assertEqual(
            {"causes": 15, "checks": 45, "total": 60}, generated["counts"]
        )
        self.assertEqual("PENDING_GATE_C", generated["datasetCoverageStatus"])

    def test_every_cause_has_three_linked_checks(self) -> None:
        entries = ontology.build_ontology()["entries"]
        causes = [item for item in entries if item["type"] == "CAUSE"]
        checks = [item for item in entries if item["type"] == "CHECK"]
        for cause in causes:
            linked = [
                item
                for item in checks
                if item["linkedCauseSemanticId"] == cause["semanticId"]
            ]
            self.assertEqual(3, len(linked), cause["semanticId"])
            self.assertTrue(
                all(
                    item["requiredEvidence"] == cause["requiredEvidence"]
                    for item in linked
                )
            )

    def test_model_text_has_no_shortcut_metadata_or_non_atomic_form(self) -> None:
        for item in ontology.build_ontology()["entries"]:
            text = item["canonicalText"]
            ontology.validate_text(text)
            self.assertNotIn(item["semanticId"], text)
            self.assertNotIn("family", text.casefold())

    def test_checked_in_artifacts_match_when_present(self) -> None:
        ontology_path = ROOT / "ontology" / "atomic-candidates-v1.json"
        report_path = ROOT / "reports" / "phase-5-5-2-ontology-review.json"
        if not ontology_path.exists() or not report_path.exists():
            self.skipTest("ontology artifacts are generated after contract review")
        checked = json.loads(ontology_path.read_text(encoding="utf-8"))
        report = json.loads(report_path.read_text(encoding="utf-8"))
        ontology.validate_ontology(checked)
        self.assertEqual(ontology.build_ontology(), checked)
        self.assertEqual(
            ontology.sha256_file(ontology_path), report["ontologySha256"]
        )
        self.assertEqual(2, len(report["reviewPasses"]))
        self.assertEqual(0, report["checks"]["unresolvedDisputes"])
        self.assertTrue(report["modelDatasetCoveragePending"])


if __name__ == "__main__":
    unittest.main()
