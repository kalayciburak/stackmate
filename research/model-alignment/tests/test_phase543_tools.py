from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import run_phase543_generation as generation
import score_phase543_rerun as scoring_rerun


class Phase543GenerationControlTest(unittest.TestCase):
    def order(
        self,
        cause_ids: list[str],
        check_ids: list[str],
        cause_origins: list[str],
        check_origins: list[str],
    ) -> dict[str, object]:
        return {
            "candidateOrder": {"causeIds": cause_ids, "checkIds": check_ids},
            "candidateProvenance": {
                "causes": [
                    {"id": identifier, "origin": origin}
                    for identifier, origin in zip(cause_ids, cause_origins, strict=True)
                ],
                "checks": [
                    {"id": identifier, "origin": origin}
                    for identifier, origin in zip(check_ids, check_origins, strict=True)
                ],
            },
        }

    def test_d0_reuses_one_specific_deterministic_candidate(self) -> None:
        order = self.order(
            ["cause.alternative", "cause.spring.property-not-writable"],
            ["check.spring.property-not-writable", "check.alternative"],
            ["BENCHMARK_CANDIDATE", "DETERMINISTIC_ANALYZER"],
            ["DETERMINISTIC_ANALYZER", "BENCHMARK_CANDIDATE"],
        )
        self.assertEqual(
            {
                "selectedCauseIndexes": [1],
                "selectedCheckIndexes": [0],
                "limitation": None,
            },
            json.loads(generation.d0_output(order)),
        )

    def test_d0_rejects_generic_and_benchmark_candidates(self) -> None:
        order = self.order(
            ["cause.unknown", "cause.benchmark.semantic"],
            ["check.inspect-root-cause", "check.benchmark.semantic"],
            ["DETERMINISTIC_ANALYZER", "BENCHMARK_CANDIDATE"],
            ["DETERMINISTIC_ANALYZER", "BENCHMARK_CANDIDATE"],
        )
        self.assertEqual(generation.ABSTENTION, json.loads(generation.d0_output(order)))

    def test_generation_input_rejects_label_fields(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "label-bearing field"):
            generation.reject_forbidden_fields({"nested": {"expectedCauseIds": []}})

    def test_execution_order_requires_complete_bound_receipts(self) -> None:
        manifest_sha = "a" * 64
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "D0 must complete"):
                generation.require_execution_order(root, "B0", manifest_sha)
            d0 = root / "D0"
            d0.mkdir()
            (d0 / "execution-receipt.json").write_text(
                json.dumps(
                    {
                        "status": "COMPLETE",
                        "system": "D0",
                        "manifestSha256": manifest_sha,
                    }
                ),
                encoding="utf-8",
            )
            generation.require_execution_order(root, "B0", manifest_sha)

    def test_formal_t1_schema_is_selection_only(self) -> None:
        schema = json.loads(
            (ROOT / "contracts" / "t1-output.schema.json").read_text(encoding="utf-8")
        )
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(
            {
                "selectedCauseIndexes",
                "selectedCheckIndexes",
                "limitation",
            },
            set(schema["required"]),
        )
        self.assertEqual(2, len(schema["oneOf"]))

    def test_scoring_rerun_counts_invalid_nullable_accuracy_as_incorrect(self) -> None:
        value = {
            "causeCorrect": None,
            "checkCorrect": None,
            "jointCorrect": None,
            "exactT1": False,
        }
        repaired = scoring_rerun.coerce_invalid_accuracy(value)
        self.assertFalse(repaired["causeCorrect"])
        self.assertFalse(repaired["checkCorrect"])
        self.assertFalse(repaired["jointCorrect"])
        self.assertFalse(repaired["exactT1"])


if __name__ == "__main__":
    unittest.main()
