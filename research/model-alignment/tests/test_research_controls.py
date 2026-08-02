from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = ROOT.parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from evaluation_contract import (  # noqa: E402
    compare_system_metrics,
    read_adapter_lock,
    validate_evaluation_lock,
)


BASE_REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
DATASET_MANIFEST_SHA256 = "b433fd0091594570aaf8a973b7bbc0b9eb77dca5682b01d010ff8071c73ea2f2"
CHALLENGE_MANIFEST_SHA256 = "9b5477bf2e1ea1593b3559a2696a0f5e9043f8d34577fbc86be85663d3d6f5e1"
STAGE_HASHES = {
    "benchmark-v2-holdout": "292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294",
    "phase-5-blind": "eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a",
    "post-training-challenge": CHALLENGE_MANIFEST_SHA256,
}


class ResearchControlsTest(unittest.TestCase):
    def test_environment_and_run_configs_preserve_experimental_controls(self) -> None:
        environment = self._read(ROOT / "configs/environment-v1.json")
        run_r8 = self._read(ROOT / "configs/qlora-r8.json")
        run_r16 = self._read(ROOT / "configs/qlora-r16.json")

        self.assertEqual(BASE_REVISION, environment["baseModel"]["revision"])
        self.assertFalse(environment["baseModel"]["networkAccessDuringTraining"])
        self.assertTrue(environment["baseModel"]["requireHuggingFaceSnapshotRevision"])
        self.assertIn("tokenizer.json", environment["baseModel"]["requiredFiles"])
        self.assertEqual(DATASET_MANIFEST_SHA256, environment["datasetManifestSha256"])
        self.assertEqual(CHALLENGE_MANIFEST_SHA256, environment["challengeManifestSha256"])

        ignored = {"runId", "rank", "alpha", "outputDirectory"}
        self.assertEqual(
            {key: value for key, value in run_r8.items() if key not in ignored},
            {key: value for key, value in run_r16.items() if key not in ignored},
        )
        self.assertEqual((8, 16), (run_r8["rank"], run_r8["alpha"]))
        self.assertEqual((16, 32), (run_r16["rank"], run_r16["alpha"]))
        self.assertEqual("reports/model-architecture.json", run_r8["targetModulesSource"])

    def test_locked_evaluation_order_and_checksums_are_enforced(self) -> None:
        config = validate_evaluation_lock(
            ROOT / "configs/evaluation-locked.json",
            expected_stage_hashes=STAGE_HASHES,
        )
        self.assertEqual([20, 20, 30], [stage["cases"] for stage in config["lockedStages"]])

        challenge_lock = self._read(
            REPOSITORY_ROOT
            / "stackmate-benchmark/src/main/resources/corpus-v4-post-training-challenge/lock.json"
        )
        self.assertTrue(challenge_lock["lockedForTraining"])
        self.assertEqual(CHALLENGE_MANIFEST_SHA256, challenge_lock["manifestSha256"])

    def test_adapter_report_import_enforces_base_dataset_and_checksums(self) -> None:
        report = {
            "schemaVersion": "1",
            "adapterId": "F1-qlora-r8-checkpoint-100",
            "baseModelRevision": BASE_REVISION,
            "datasetManifestSha256": DATASET_MANIFEST_SHA256,
            "trainingConfigSha256": "1" * 64,
            "adapterSha256": "2" * 64,
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "selected-adapter.json"
            path.write_text(json.dumps(report), encoding="utf-8")
            imported = read_adapter_lock(
                path,
                expected_base_revision=BASE_REVISION,
                expected_dataset_manifest_sha256=DATASET_MANIFEST_SHA256,
            )
            self.assertEqual(report["adapterId"], imported.adapter_id)

            report["adapterSha256"] = "not-a-checksum"
            path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "adapterSha256"):
                read_adapter_lock(
                    path,
                    expected_base_revision=BASE_REVISION,
                    expected_dataset_manifest_sha256=DATASET_MANIFEST_SHA256,
                )

    def test_base_adapter_comparison_requires_matching_metrics(self) -> None:
        required = ("valid", "causeCorrect", "checkCorrect", "unsupportedClaims")
        delta = compare_system_metrics(
            {"valid": 20, "causeCorrect": 16, "checkCorrect": 16, "unsupportedClaims": 0},
            {"valid": 20, "causeCorrect": 18, "checkCorrect": 18, "unsupportedClaims": 0},
            required_keys=required,
        )
        self.assertEqual(
            {"valid": 0, "causeCorrect": 2, "checkCorrect": 2, "unsupportedClaims": 0},
            delta,
        )
        with self.assertRaisesRegex(ValueError, "same required metrics"):
            compare_system_metrics(
                {"valid": 20},
                {"valid": 20},
                required_keys=required,
            )

    def test_blocked_preflight_did_not_attempt_network_or_create_adapter_lock(self) -> None:
        preflight = self._read(ROOT / "reports/environment-preflight.json")
        self.assertEqual("BLOCKED", preflight["status"])
        self.assertEqual(["BASE_MODEL_PATH_NOT_SET"], preflight["failures"])
        self.assertFalse(preflight["networkAccessAttempted"])
        self.assertFalse((ROOT / "reports/selected-adapter.json").exists())

    @staticmethod
    def _read(path: Path) -> dict:
        return json.loads(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
