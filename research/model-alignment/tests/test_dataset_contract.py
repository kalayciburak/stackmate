from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from dataset_contract import build_examples, canonical_json, load_config, sha256_file  # noqa: E402


class DatasetContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.config_path = ROOT / "configs/dataset-v1.json"
        cls.config = load_config(cls.config_path)
        cls.prompt = (ROOT / cls.config["prompt"]).read_text(encoding="utf-8").strip()
        cls.train = cls._read_jsonl(ROOT / cls.config["trainOutput"])
        cls.validation = cls._read_jsonl(ROOT / cls.config["validationOutput"])
        cls.manifest = json.loads((ROOT / cls.config["manifestOutput"]).read_text(encoding="utf-8"))

    @staticmethod
    def _read_jsonl(path: Path) -> list[dict]:
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]

    def test_generation_is_deterministic(self) -> None:
        first = build_examples(self.config, self.prompt)
        second = build_examples(self.config, self.prompt)
        self.assertEqual(canonical_json(first), canonical_json(second))
        self.assertEqual(first[0], self.train)
        self.assertEqual(first[1], self.validation)

    def test_distribution_and_family_split_are_frozen(self) -> None:
        examples = self.train + self.validation
        self.assertEqual(1000, len(examples))
        self.assertEqual(800, len(self.train))
        self.assertEqual(200, len(self.validation))
        counts = {kind: sum(item["kind"] == kind for item in examples) for kind in {item["kind"] for item in examples}}
        self.assertEqual({"SUPPORTED": 450, "HARD_NEGATIVE": 250, "ABSTENTION": 250, "SECURITY": 50}, counts)
        train_families = {item["templateFamily"] for item in self.train}
        validation_families = {item["templateFamily"] for item in self.validation}
        self.assertEqual(set(), train_families & validation_families)
        self.assertEqual(32, len(train_families))
        self.assertEqual(8, len(validation_families))

    def test_manifest_checksums_match_generated_files(self) -> None:
        self.assertEqual(520021, self.manifest["seed"])
        self.assertFalse(self.manifest["lockedCorpusReadByGenerator"])
        for name in ("train", "validation"):
            entry = self.manifest["files"][name]
            self.assertEqual(entry["sha256"], sha256_file(ROOT / entry["path"]))
        self.assertEqual(self.manifest["configSha256"], sha256_file(self.config_path))
        self.assertEqual(self.manifest["promptSha256"], sha256_file(ROOT / self.manifest["prompt"]))

    def test_chat_rows_and_targets_obey_schema_two(self) -> None:
        for example in self.train + self.validation:
            self.assertRegex(example["exampleId"], r"^SFT-(TRN|VAL)-[A-Z0-9-]+-\d{3}$")
            self.assertEqual(["system", "user", "assistant"], [message["role"] for message in example["messages"]])
            user = example["messages"][1]["content"]
            self.assertTrue(user.startswith('<STACKMATE_UNTRUSTED_INCIDENT schemaVersion="2">\n'))
            self.assertTrue(user.endswith("\n</STACKMATE_UNTRUSTED_INCIDENT>"))
            payload = json.loads(user[user.index("\n") + 1 : user.rindex("\n</")])
            target = json.loads(example["messages"][2]["content"])
            self.assertEqual("2", payload["schemaVersion"])
            self.assertEqual("2", payload["promptVersion"])
            self.assertEqual("2", target["schemaVersion"])
            self.assertEqual(
                {"schemaVersion", "summary", "explanation", "selectedCauseIds", "selectedCheckIds", "usedEvidenceIds", "limitations"},
                set(target),
            )
            causes = {item["id"] for item in payload["candidateCauses"]}
            checks = {item["id"] for item in payload["candidateChecks"]}
            facts = {item["id"] for item in payload["facts"]}
            self.assertLessEqual(set(target["selectedCauseIds"]), causes)
            self.assertLessEqual(set(target["selectedCheckIds"]), checks)
            self.assertLessEqual(set(target["usedEvidenceIds"]), facts)
            if example["metadata"]["abstentionRequired"]:
                self.assertEqual([], target["selectedCauseIds"])
                self.assertEqual([], target["selectedCheckIds"])
                self.assertEqual([], target["usedEvidenceIds"])
                self.assertEqual(["EVIDENCE_INSUFFICIENT"], target["limitations"])
            else:
                self.assertEqual([], target["limitations"])

    def test_targets_have_no_hidden_reasoning_secret_or_private_path(self) -> None:
        forbidden = re.compile(r"(?i)(chain.of.thought|<analysis>|c:\\users\\|/home/|AKIA[0-9A-Z]{16}|BEGIN [A-Z ]+PRIVATE KEY)")
        for example in self.train + self.validation:
            target = example["messages"][2]["content"]
            self.assertIsNone(forbidden.search(target), example["exampleId"])
            serialized = canonical_json(example)
            self.assertIsNone(
                re.search(r"(?i)(c:\\users\\|/home/|AKIA[0-9A-Z]{16}|BEGIN [A-Z ]+PRIVATE KEY)", serialized),
                example["exampleId"],
            )
            self.assertLess(len(target), 1200)


if __name__ == "__main__":
    unittest.main()
