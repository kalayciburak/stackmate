from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import audit_pairwise_label_tokens as labels


class PairwiseContractTest(unittest.TestCase):
    def test_semantic_mapping_is_frozen(self) -> None:
        self.assertEqual(
            {"0": "CONTRADICTED", "1": "SUPPORTED", "2": "UNKNOWN"},
            labels.LABELS,
        )

    def test_prompt_requires_one_token_without_prose(self) -> None:
        prompt = (ROOT / "prompts" / "pairwise-entailment-v1.txt").read_text(
            encoding="utf-8"
        )
        self.assertIn("Return exactly one token", prompt)
        self.assertIn("Plausibility is not support", prompt)
        self.assertIn("Do not explain", prompt)
        self.assertNotIn("selectedCauseIndexes", prompt)
        self.assertNotIn("selectedCheckIndexes", prompt)

    def test_checked_in_token_report_matches_contract_when_present(self) -> None:
        report_path = ROOT / "reports" / "phase-5-5-label-token-audit.json"
        if not report_path.exists():
            self.skipTest("label-token report is generated after tokenizer audit")
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual("PASS", report["status"])
        self.assertEqual(
            {"0": 15, "1": 16, "2": 17},
            {
                label: item["tokenId"]
                for label, item in report["labelMapping"].items()
            },
        )


if __name__ == "__main__":
    unittest.main()
