from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import audit_binary_label_tokens as labels


class BinaryContractTest(unittest.TestCase):
    def test_semantic_mapping_is_frozen_and_binary(self) -> None:
        self.assertEqual(
            {"0": "NOT_SUPPORTED", "1": "SUPPORTED"},
            labels.LABELS,
        )

    def test_prompt_requires_one_plain_token_without_contract_drift(self) -> None:
        prompt = (ROOT / "prompts" / "binary-evidence-support-v1.txt").read_text(
            encoding="utf-8"
        )
        self.assertIn("exactly one candidate", prompt)
        self.assertIn("Return exactly one token", prompt)
        self.assertIn("0 = NOT_SUPPORTED", prompt)
        self.assertIn("1 = SUPPORTED", prompt)
        self.assertIn("does not mean that the candidate is universally false", prompt)
        self.assertIn("Plausibility is not support", prompt)
        self.assertIn("Do not explain", prompt)
        self.assertIn(
            "Do not add whitespace, punctuation, JSON, Markdown, or any other text",
            prompt,
        )
        for forbidden in (
            "2 =",
            "CONTRADICTED",
            "UNKNOWN",
            "INSUFFICIENT_EVIDENCE",
            "selectedCauseIndexes",
            "selectedCheckIndexes",
            "candidateId",
            "familyId",
            "persistent ID",
            "family ID",
            "index",
            "sibling",
        ):
            self.assertNotIn(forbidden, prompt)

    def test_invalid_or_additional_tokens_fail_safe_without_repair(self) -> None:
        token_ids = {"0": 15, "1": 16}
        self.assertEqual(
            "SUPPORTED", labels.classify_generated_tokens([16], token_ids)
        )
        self.assertEqual(
            "NOT_SUPPORTED", labels.classify_generated_tokens([15], token_ids)
        )
        for invalid in (
            None,
            "1",
            b"1",
            [],
            [True],
            [17],
            [16, 248046],
            [220, 16],
            [198, 16],
        ):
            self.assertEqual(
                "NOT_SUPPORTED",
                labels.classify_generated_tokens(invalid, token_ids),
            )
        self.assertIn("exactly one plain label token", labels.INVALID_TOKEN_POLICY)
        self.assertIn("do not repair", labels.INVALID_TOKEN_POLICY)

    def test_target_policy_is_exactly_one_label_token(self) -> None:
        self.assertEqual((248046, 198), labels.ASSISTANT_TERMINATION_IDS)
        self.assertIn("exactly one frozen label token", labels.ASSISTANT_TARGET_POLICY)
        self.assertIn("mask every prompt", labels.ASSISTANT_TARGET_POLICY)
        self.assertIn("assistant-termination token", labels.ASSISTANT_TARGET_POLICY)

    def test_checked_in_token_report_matches_contract_when_present(self) -> None:
        report_path = ROOT / "reports" / "phase-5-5-1-binary-label-token-audit.json"
        if not report_path.exists():
            self.skipTest("binary label-token report is generated after tokenizer audit")
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual("PASS", report["status"])
        self.assertEqual(
            {"0": ("NOT_SUPPORTED", 15), "1": ("SUPPORTED", 16)},
            {
                label: (item["semantic"], item["tokenId"])
                for label, item in report["labelMapping"].items()
            },
        )
        self.assertEqual([248046, 198], report["assistantGenerationSuffixIds"])
        self.assertEqual(labels.ASSISTANT_TARGET_POLICY, report["assistantTargetPolicy"])
        self.assertEqual(labels.INVALID_TOKEN_POLICY, report["invalidTokenPolicy"])
        self.assertTrue(report["localFilesOnly"])
        self.assertFalse(report["trustRemoteCode"])
        self.assertFalse(report["networkAccess"])
        for context in report["contexts"]:
            self.assertEqual(
                {
                    "0": [15, 248046, 198],
                    "1": [16, 248046, 198],
                },
                context["assistantSuffixIds"],
            )


if __name__ == "__main__":
    unittest.main()
