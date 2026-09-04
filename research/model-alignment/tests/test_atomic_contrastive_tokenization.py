from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import audit_atomic_contrastive_tokenization as audit


class AtomicContrastiveTokenizationTest(unittest.TestCase):
    def test_percentile_and_safe_limit_are_deterministic(self) -> None:
        self.assertEqual(4, audit.percentile([1, 2, 3, 4], 0.95))
        self.assertEqual((256, 221), audit.safe_limit(200))
        with self.assertRaises(audit.TokenizationError):
            audit.safe_limit(1900)

    def test_frozen_contract_hashes_match_checked_inputs(self) -> None:
        paths = {
            "prompt": ROOT / "prompts" / "binary-evidence-support-v1.txt",
            "labelAudit": ROOT
            / "reports"
            / "phase-5-5-1-binary-label-token-audit.json",
            "ontology": ROOT / "ontology" / "atomic-candidates-v1.json",
            "manifest": ROOT / "datasets" / "atomic-contrastive-v1" / "manifest.json",
        }
        self.assertEqual(
            audit.EXPECTED,
            {key: audit.sha256_file(path) for key, path in paths.items()},
        )

    def test_checked_report_when_present(self) -> None:
        report_path = ROOT / "reports" / "phase-5-5-2-tokenization-audit.json"
        if not report_path.exists():
            self.skipTest("tokenization report is generated after dataset freeze")
        report = json.loads(
            report_path.read_text(encoding="utf-8"),
            object_pairs_hook=audit.reject_duplicate_keys,
        )
        self.assertEqual("PASS", report["status"])
        self.assertEqual(1, report["splits"]["TRAIN"]["targetTokenLength"])
        self.assertEqual(1, report["splits"]["VALIDATION"]["targetTokenLength"])
        self.assertEqual(0, report["checks"]["truncations"])
        self.assertEqual(0, report["checks"]["duplicatedSpecialTokens"])
        self.assertEqual(0, report["checks"]["unknownTokens"])
        self.assertLessEqual(report["selectedMaximumSequence"], 2048)
        self.assertTrue(report["localFilesOnly"])
        self.assertFalse(report["trustRemoteCode"])
        self.assertFalse(report["networkAccess"])
        self.assertFalse(report["modelWeightsLoaded"])


if __name__ == "__main__":
    unittest.main()
