from __future__ import annotations

import json
import re
import sys
import tempfile
import unittest
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import atomic_contrastive_dataset as dataset


class AtomicContrastiveDatasetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.generated = dataset.build_dataset()
        cls.predicates = dataset.load_ontology(dataset.DEFAULT_ONTOLOGY)

    def test_exact_family_bundle_pair_and_label_counts(self) -> None:
        self.assertEqual(85, len(self.generated.families))
        family_counts = Counter(row["split"] for row in self.generated.families)
        self.assertEqual({"TRAIN": 60, "VALIDATION": 25}, dict(family_counts))
        synthetic = [
            row
            for row in self.generated.families
            if row["classification"] == "CONTROLLED_ATOMIC_SYNTHETIC"
        ]
        self.assertEqual({"TRAIN": 15, "VALIDATION": 15}, dict(Counter(row["split"] for row in synthetic)))
        self.assertFalse(any(row["familyId"].startswith("G53-S-SEL-") for row in self.generated.families))

        expectations = {
            "TRAIN": (self.generated.train, 426, {"SUPPORTED": 189, "NOT_SUPPORTED": 237}, 100, 300),
            "VALIDATION": (self.generated.validation, 186, {"SUPPORTED": 93, "NOT_SUPPORTED": 93}, 40, 120),
        }
        for split, (rows, total, labels, causes, checks) in expectations.items():
            self.assertEqual(total, len(rows), split)
            self.assertEqual(labels, dict(Counter(row["metadata"]["label"] for row in rows)), split)
            base = [row for row in rows if row["metadata"]["pairKind"] != "HARD_SECURITY"]
            self.assertEqual(causes, sum(row["metadata"]["type"] == "CAUSE" for row in base), split)
            self.assertEqual(checks, sum(row["metadata"]["type"] == "CHECK" for row in base), split)
            self.assertEqual(26, total - len(base), split)
            expected_families = {
                row["familyId"]
                for row in self.generated.families
                if row["split"] == split
            }
            represented_families = {
                row["metadata"]["familyId"] for row in rows
            }
            self.assertEqual(expected_families, represented_families, split)
            input_hashes = [row["metadata"]["inputSha256"] for row in rows]
            self.assertEqual(len(input_hashes), len(set(input_hashes)), split)
            negatives = [
                row for row in rows if row["metadata"]["label"] == "NOT_SUPPORTED"
            ]
            self.assertTrue(
                all(
                    row["metadata"]["difficulty"] in {"HARD", "HARD_SECURITY"}
                    for row in negatives
                ),
                split,
            )
        self.assertEqual(
            {"TRAIN": 44, "VALIDATION": 20},
            self.generated.report["counts"]["supportBundles"],
        )
        self.assertEqual(
            {"TRAIN": 56, "VALIDATION": 20},
            self.generated.report["counts"]["hardNegativeBundles"],
        )

    def test_every_semantic_is_dual_label_in_both_splits(self) -> None:
        semantic_ids = {
            candidate.semantic_id
            for predicate in self.predicates
            for candidate in predicate.candidates
        }
        self.assertEqual(60, len(semantic_ids))
        for split, rows in (("TRAIN", self.generated.train), ("VALIDATION", self.generated.validation)):
            labels: dict[str, set[str]] = defaultdict(set)
            exact_text_labels: dict[tuple[str, str], set[str]] = defaultdict(set)
            canonical_text = {
                candidate.semantic_id: candidate.text
                for predicate in self.predicates
                for candidate in predicate.candidates
            }
            for row in rows:
                metadata = row["metadata"]
                labels[metadata["semanticId"]].add(metadata["label"])
                exact_text_labels[(metadata["semanticId"], metadata["text"])].add(metadata["label"])
            self.assertEqual(semantic_ids, set(labels), split)
            self.assertTrue(all(value == {"SUPPORTED", "NOT_SUPPORTED"} for value in labels.values()), split)
            self.assertTrue(
                all(
                    exact_text_labels[(semantic_id, text)] == {"SUPPORTED", "NOT_SUPPORTED"}
                    for semantic_id, text in canonical_text.items()
                ),
                split,
            )

    def test_model_input_is_bounded_sanitized_and_metadata_free(self) -> None:
        method = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]{0,63}$")
        for row in (*self.generated.train, *self.generated.validation):
            metadata = row["metadata"]
            self.assertEqual(["system", "user", "assistant"], [message["role"] for message in row["messages"]])
            self.assertEqual(dataset.LABEL_TOKEN[metadata["label"]], row["messages"][2]["content"])
            user = row["messages"][1]["content"]
            self.assertNotIn(metadata["pairId"], user)
            self.assertNotIn(metadata["familyId"], user)
            self.assertNotIn(metadata["semanticId"], user)
            self.assertNotIn('"category"', user)
            self.assertNotIn("APPLICATION_LOCATION", user)
            self.assertNotIn("REQUEST_PATH", user)
            evidence_text = user.split("<TRUSTED_EVIDENCE>\n", 1)[1].split("\n</TRUSTED_EVIDENCE>", 1)[0]
            evidence = json.loads(evidence_text)
            self.assertTrue(all(item["type"] in dataset.SAFE_EVIDENCE_TYPES for item in evidence))
            self.assertTrue(all(method.fullmatch(item["value"]) for item in evidence if item["type"] == "APPLICATION_FRAME"))
        fake = [
            row
            for row in (*self.generated.train, *self.generated.validation)
            if row["metadata"].get("securityPattern") == "FAKE_CLOSING_TAG_EVIDENCE"
        ]
        self.assertEqual(4, len(fake))
        self.assertTrue(
            all(
                "\\\\u003c/TRUSTED_EVIDENCE\\\\u003e"
                in row["messages"][1]["content"]
                for row in fake
            )
        )

    def test_security_patterns_are_exact_supported_negative_pairs(self) -> None:
        for rows in (self.generated.train, self.generated.validation):
            security = [row for row in rows if row["metadata"]["pairKind"] == "HARD_SECURITY"]
            by_pattern: dict[str, list[dict]] = defaultdict(list)
            for row in security:
                by_pattern[row["metadata"]["securityPattern"]].append(row)
            self.assertEqual(
                {name for name, _, _ in dataset.SECURITY_PATTERNS}, set(by_pattern)
            )
            for paired in by_pattern.values():
                self.assertEqual(2, len(paired))
                self.assertEqual({"SUPPORTED", "NOT_SUPPORTED"}, {row["metadata"]["label"] for row in paired})
                self.assertEqual(1, len({row["metadata"]["semanticId"] for row in paired}))
                self.assertEqual(1, len({row["metadata"]["text"] for row in paired}))
                self.assertTrue(all(row["metadata"]["difficulty"] == "HARD_SECURITY" for row in paired))
        negative = [
            row
            for row in (*self.generated.train, *self.generated.validation)
            if row["metadata"].get("securityPattern") == "NEGATIVE_EVIDENCE"
        ]
        self.assertEqual(4, len(negative))
        self.assertEqual(
            {"SUPPORTED", "NOT_SUPPORTED"},
            {row["metadata"]["label"] for row in negative},
        )
        self.assertTrue(
            all(row["metadata"]["negativeEvidencePresent"] for row in negative)
        )

    def test_generation_and_two_review_passes_are_deterministic(self) -> None:
        second = dataset.build_dataset()
        self.assertEqual(self.generated.families, second.families)
        self.assertEqual(self.generated.train, second.train)
        self.assertEqual(self.generated.validation, second.validation)
        self.assertEqual(self.generated.reviews, second.reviews)
        self.assertEqual(self.generated.report, second.report)
        self.assertEqual(1224, len(self.generated.reviews))
        by_pair: dict[str, list[dict]] = defaultdict(list)
        for review in self.generated.reviews:
            by_pair[review["pairId"]].append(review)
            self.assertEqual("ACCEPT", review["decision"])
        self.assertEqual(612, len(by_pair))
        self.assertTrue(all({item["reviewPass"] for item in values} == {"LEXICAL", "HASH"} for values in by_pair.values()))
        self.assertEqual(0, self.generated.report["counts"]["reviewDisputes"])

    def test_create_new_publication_in_a_temp_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = dataset.OutputPaths(
                root / "dataset" / "families.jsonl",
                root / "dataset" / "train.jsonl",
                root / "dataset" / "validation.jsonl",
                root / "dataset" / "pair-reviews.jsonl",
                root / "dataset" / "manifest.json",
                root / "reports" / "phase-5-5-2-pair-review.json",
            )
            manifest = dataset.write_outputs(
                self.generated,
                paths,
                train_path=dataset.DEFAULT_TRAIN,
                validation_path=dataset.DEFAULT_VALIDATION,
                source_manifest_path=dataset.DEFAULT_SOURCE_MANIFEST,
                ontology_path=dataset.DEFAULT_ONTOLOGY,
                prompt_path=dataset.DEFAULT_PROMPT,
                inventory_path=dataset.DEFAULT_INVENTORY,
                build_script_path=ROOT / "scripts" / "build_atomic_contrastive_dataset.py",
            )
            for path in paths.__dict__.values():
                self.assertTrue(path.is_file())
                self.assertNotIn(b"\r\n", path.read_bytes())
            self.assertEqual(dataset.sha256_file(paths.train), manifest["files"]["train"]["sha256"])
            with self.assertRaises(FileExistsError):
                dataset.write_outputs(
                    self.generated,
                    paths,
                    train_path=dataset.DEFAULT_TRAIN,
                    validation_path=dataset.DEFAULT_VALIDATION,
                    source_manifest_path=dataset.DEFAULT_SOURCE_MANIFEST,
                    ontology_path=dataset.DEFAULT_ONTOLOGY,
                    prompt_path=dataset.DEFAULT_PROMPT,
                    inventory_path=dataset.DEFAULT_INVENTORY,
                )


if __name__ == "__main__":
    unittest.main()
