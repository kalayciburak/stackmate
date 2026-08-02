#!/usr/bin/env python3
"""Audit generated data against every locked evaluation source."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Iterable

from dataset_contract import ROOT, load_config, normalized_text, semantic_document, sha256_file


REPOSITORY = ROOT.parents[1]
V2_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v2/manifest.json"
BLIND_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v3-qualification/manifest.json"
SPLIT_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/prompt-ablation/split-manifest.json"
CHALLENGE_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v4-post-training-challenge/manifest.json"
CHALLENGE_LOCK = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v4-post-training-challenge/lock.json"
LOCKED_FACTORIES = (
    REPOSITORY / "stackmate-benchmark/src/test/java/io/github/kalayciburak/stackmate/benchmark/corpus/v2/CorpusV2IncidentFactory.java",
    REPOSITORY / "stackmate-benchmark/src/test/java/io/github/kalayciburak/stackmate/benchmark/corpus/v2/CorpusV2RuntimeRecipeTest.java",
    REPOSITORY / "stackmate-benchmark/src/test/java/io/github/kalayciburak/stackmate/benchmark/corpus/qualification/QualificationBlindIncidentFactory.java",
    REPOSITORY / "stackmate-benchmark/src/test/java/io/github/kalayciburak/stackmate/benchmark/corpus/qualification/QualificationBlindRuntimeRecipeTest.java",
    REPOSITORY / "stackmate-benchmark/src/test/java/io/github/kalayciburak/stackmate/benchmark/corpus/alignment/PostTrainingChallengeIncidentFactory.java",
    REPOSITORY / "stackmate-benchmark/src/test/java/io/github/kalayciburak/stackmate/benchmark/corpus/alignment/PostTrainingChallengeRuntimeRecipeTest.java",
)
JAVA_STRING = re.compile(r'"((?:[^"\\]|\\.){8,})"')


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def json_scalars(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from json_scalars(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from json_scalars(item)


def locked_values() -> tuple[set[str], dict[str, set[str]], list[dict[str, str]]]:
    split = read_json(SPLIT_MANIFEST)
    locked_ids = set(split["splits"]["lockedFinal"])
    v2 = read_json(V2_MANIFEST)
    blind = read_json(BLIND_MANIFEST)
    challenge = read_json(CHALLENGE_MANIFEST)
    v2_locked = [case for case in v2["cases"] if case["caseId"] in locked_ids]
    if len(v2_locked) != 20 or len(blind["cases"]) != 20 or len(challenge["cases"]) != 30:
        raise ValueError("locked corpus cardinality drift")
    groups = {"v2Holdout": v2_locked, "blind": blind["cases"], "challenge": challenge["cases"]}
    normalized: set[str] = set()
    records: list[dict[str, str]] = []
    signatures: dict[str, set[str]] = {"category": set(), "evidence": set(), "candidate": set(), "template": set()}
    for group, cases in groups.items():
        for case in cases:
            signatures["category"].add(case.get("category", "UNKNOWN"))
            evidence = "+".join(sorted(fact.get("type", "") for fact in case.get("expectedFacts", [])))
            signatures["evidence"].add(evidence or "MANIFEST_ONLY")
            candidate = (
                f"selectedCause={len(case.get('expectedCauseKeys', []))};"
                f"selectedCheck={len(case.get('expectedCheckKeys', []))}"
            )
            signatures["candidate"].add(candidate)
            signatures["template"].add(case.get("recipe", ""))
            for raw in json_scalars(case):
                text = normalized_text(raw)
                if len(text) >= 12:
                    normalized.add(text)
                    records.append({"group": group, "caseId": case["caseId"], "text": text})
    for source in LOCKED_FACTORIES:
        for raw in JAVA_STRING.findall(source.read_text(encoding="utf-8")):
            text = normalized_text(bytes(raw, "utf-8").decode("unicode_escape"))
            if len(text) >= 20:
                normalized.add(text)
                records.append({"group": "runtimeSource", "caseId": source.name, "text": text})
    return normalized, signatures, records


def ngrams(value: str, size: int) -> set[str]:
    padded = f" {value} "
    return {padded[index : index + size] for index in range(max(0, len(padded) - size + 1))}


def similarity(left: set[str], right: set[str]) -> float:
    union = left | right
    return len(left & right) / len(union) if union else 1.0


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/dataset-v1.json")
    arguments = parser.parse_args()
    config_path = (ROOT / arguments.config).resolve()
    config = load_config(config_path)
    train_path = (ROOT / config["trainOutput"]).resolve()
    validation_path = (ROOT / config["validationOutput"]).resolve()
    examples = load_jsonl(train_path) + load_jsonl(validation_path)
    locked_text, locked_signatures, locked_records = locked_values()
    challenge_lock = read_json(CHALLENGE_LOCK)
    if sha256_file(CHALLENGE_MANIFEST) != challenge_lock["manifestSha256"]:
        raise ValueError("post-training challenge checksum drift")

    exact: list[dict[str, str]] = []
    family_overlap: list[str] = []
    forbidden_identifiers: list[dict[str, str]] = []
    locked_ids = {
        record["caseId"] for record in locked_records if record["group"] != "runtimeSource"
    }
    near: list[dict[str, Any]] = []
    size = int(config["characterNgramSize"])
    threshold = float(config["nearDuplicateThreshold"])
    locked_ngrams = [(record, ngrams(record["text"], size)) for record in locked_records if len(record["text"]) >= 32]
    categories: set[str] = set()
    evidence_signatures: set[str] = set()
    candidate_signatures: set[str] = set()
    top_observed: dict[str, Any] = {"score": 0.0}
    for example in examples:
        document = semantic_document(example)
        fields = [document]
        user = example["messages"][1]["content"]
        payload = json.loads(user[user.index("\n") + 1 : user.rindex("\n</STACKMATE_UNTRUSTED_INCIDENT>")])
        fields.extend(normalized_text(fact["value"]) for fact in payload["facts"])
        fields.extend(normalized_text(item["text"]) for item in payload["candidateCauses"])
        fields.extend(normalized_text(item["text"]) for item in payload["candidateChecks"])
        for value in fields:
            if len(value) >= 12 and value in locked_text:
                exact.append({"exampleId": example["exampleId"], "normalizedSha256": __import__("hashlib").sha256(value.encode()).hexdigest()})
        serialized = json.dumps(example, ensure_ascii=False)
        for identifier in sorted(locked_ids | locked_signatures["template"]):
            if identifier and identifier in serialized:
                forbidden_identifiers.append(
                    {"exampleId": example["exampleId"], "identifierSha256": __import__("hashlib").sha256(identifier.encode()).hexdigest()}
                )
        if example["exampleId"] in locked_ids or example["templateFamily"] in locked_signatures["template"]:
            family_overlap.append(example["exampleId"])
        categories.add(example["metadata"]["category"])
        evidence_signatures.add(example["metadata"]["evidenceSignature"])
        candidate_signatures.add(example["metadata"]["candidateSignature"])
        best = (0.0, None)
        for source_value in fields:
            if len(source_value) < 32:
                continue
            source_grams = ngrams(source_value, size)
            for record, target_grams in locked_ngrams:
                score = similarity(source_grams, target_grams)
                if score > best[0]:
                    best = (score, record)
        if best[1] is not None and best[0] > top_observed["score"]:
            top_observed = {
                "score": round(best[0], 6),
                "exampleId": example["exampleId"],
                "lockedGroup": best[1]["group"],
                "lockedCaseId": best[1]["caseId"],
            }
        if best[0] >= threshold and best[1] is not None:
            near.append({"exampleId": example["exampleId"], "score": round(best[0], 6), "lockedGroup": best[1]["group"], "lockedCaseId": best[1]["caseId"]})

    train_families = {item["templateFamily"] for item in examples if item["split"] == "TRAIN"}
    validation_families = {item["templateFamily"] for item in examples if item["split"] == "VALIDATION"}
    cross_family = sorted(train_families & validation_families)
    prohibited = (
        len(exact)
        + len(family_overlap)
        + len(forbidden_identifiers)
        + len(near)
        + len(cross_family)
    )
    report = {
        "schemaVersion": "1",
        "status": "PASS" if prohibited == 0 else "FAIL",
        "auditorSha256": sha256_file(Path(__file__).resolve()),
        "configSha256": sha256_file(config_path),
        "dataset": {
            "manifestSha256": sha256_file((ROOT / config["manifestOutput"]).resolve()),
            "trainSha256": sha256_file(train_path),
            "validationSha256": sha256_file(validation_path),
            "examples": len(examples),
        },
        "lockedSources": {
            "v2ManifestSha256": sha256_file(V2_MANIFEST),
            "blindManifestSha256": sha256_file(BLIND_MANIFEST),
            "phase51SplitSha256": sha256_file(SPLIT_MANIFEST),
            "challengeManifestSha256": sha256_file(CHALLENGE_MANIFEST),
            "challengeLockEnforced": True,
            "runtimeSourceFiles": len(LOCKED_FACTORIES),
        },
        "checks": {
            "exactNormalizedText": {"prohibitedMatches": exact},
            "exceptionCategory": {"overlap": sorted(categories & locked_signatures["category"]), "policy": "reported; category alone is not leakage"},
            "evidenceSignature": {"overlap": sorted(evidence_signatures & locked_signatures["evidence"]), "policy": "reported; signature alone is not leakage"},
            "candidateSignature": {"overlap": sorted(candidate_signatures & locked_signatures["candidate"]), "policy": "reported; signature alone is not leakage"},
            "templateFamily": {"prohibitedMatches": family_overlap, "crossSplitFamilies": cross_family},
            "lockedIdentifiers": {"prohibitedMatches": forbidden_identifiers},
            "characterNgram": {
                "size": size,
                "threshold": threshold,
                "topObserved": top_observed,
                "prohibitedMatches": near,
            },
        },
        "prohibitedOverlapCount": prohibited,
    }
    report_path = (ROOT / config["leakageReport"]).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"status": report["status"], "report": str(report_path), "prohibitedOverlapCount": prohibited}, separators=(",", ":")))
    if prohibited:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
