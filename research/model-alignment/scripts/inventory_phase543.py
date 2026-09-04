#!/usr/bin/env python3
"""Build a normalized inventory of every family exposed before Phase 5.4.3."""

from __future__ import annotations

import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from dataset_contract import FAMILIES


REPOSITORY = Path(__file__).resolve().parents[3]
ALIGNMENT = REPOSITORY / "research" / "model-alignment"
RESOURCES = REPOSITORY / "stackmate-benchmark" / "src" / "main" / "resources"
OUTPUT = ALIGNMENT / "reports" / "phase-5-4-3-existing-family-inventory.json"


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path}")
    return value


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value: Any) -> str:
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def file_digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalized(value: Any) -> str:
    text = canonical(value).casefold()
    text = re.sub(r"\b[0-9a-f]{8}-[0-9a-f-]{27,}\b", "<uuid>", text)
    text = re.sub(r"\b\d+\b", "<number>", text)
    text = re.sub(r"[a-z]:[/\\][^\"\s]+|/(?:home|mnt|tmp)/[^\"\s]+", "<path>", text)
    return " ".join(re.findall(r"[a-z][a-z0-9_$.-]*|<[^>]+>", text))


def signature(value: Any) -> str:
    return hashlib.sha256(normalized(value).encode("utf-8")).hexdigest()


def make_record(
    *,
    catalog: str,
    family_id: str,
    canonical_family_id: str | None,
    recipe: Any,
    category: Any,
    throwable: Any,
    evidence: Any,
    causes: Any,
    checks: Any,
    topology: Any,
    classification: str,
    exposures: list[str],
    source_sha256: str,
) -> dict[str, Any]:
    semantic = {
        "recipe": recipe,
        "category": category,
        "throwable": throwable,
        "evidence": evidence,
        "causes": causes,
        "checks": checks,
        "dependencyTopology": topology,
        "classification": classification,
    }
    return {
        "familyId": family_id,
        "canonicalFamilyId": canonical_family_id or family_id,
        "catalog": catalog,
        "recipeId": recipe,
        "category": category,
        "throwableChainSignature": signature(throwable),
        "rootCauseSignature": signature({"recipe": recipe, "category": category, "throwable": throwable}),
        "evidenceSignature": signature(evidence),
        "candidateCauseSignature": signature(causes),
        "candidateCheckSignature": signature(checks),
        "runtimeDependencyTopology": topology,
        "classification": classification,
        "exposures": sorted(set(exposures)),
        "sourceManifestSha256": source_sha256,
        "semanticSignature": signature(semantic),
        "normalizedSemanticText": normalized(semantic),
    }


def main() -> None:
    if OUTPUT.exists():
        raise FileExistsError(f"inventory already exists: {OUTPUT}")
    records: list[dict[str, Any]] = []

    v2_path = RESOURCES / "corpus-v2" / "manifest.json"
    v2 = read_json(v2_path)
    v2_sha = file_digest(v2_path)
    for case in v2["cases"]:
        records.append(make_record(
            catalog="benchmark-v2",
            family_id=case["caseId"],
            canonical_family_id=None,
            recipe=case["recipe"],
            category=case["category"],
            throwable=[case["expectedRootType"]],
            evidence=case["expectedFacts"],
            causes=case["expectedCauseKeys"],
            checks=case["expectedCheckKeys"],
            topology={"domain": case["domain"], "frameworks": v2.get("frameworks", [])},
            classification=case["provenance"],
            exposures=["MODEL_EVALUATION", f"V2_{case['split']}"],
            source_sha256=v2_sha,
        ))

    screening_path = RESOURCES / "corpus-v3-screening" / "manifest.json"
    screening = read_json(screening_path)
    screening_sha = file_digest(screening_path)
    for case in screening["cases"]:
        records.append(make_record(
            catalog="phase4-screening-alias",
            family_id=case["caseId"],
            canonical_family_id=case["sourceCaseId"],
            recipe=case["purpose"],
            category="SCREENING_ALIAS",
            throwable=[],
            evidence=case["expectedFacts"],
            causes=case["expectedCauseSelections"],
            checks=case["expectedCheckSelections"],
            topology={"sourceCorpus": screening["sourceCorpusId"]},
            classification=case["provenance"],
            exposures=["MODEL_EVALUATION", "SCREENING"],
            source_sha256=screening_sha,
        ))

    blind_path = RESOURCES / "corpus-v3-qualification" / "manifest.json"
    blind = read_json(blind_path)
    blind_sha = file_digest(blind_path)
    for case in blind["cases"]:
        records.append(make_record(
            catalog="phase5-blind",
            family_id=case["caseId"],
            canonical_family_id=None,
            recipe=case["recipe"],
            category=case["expectedCategory"],
            throwable=[case["expectedRootType"]],
            evidence=case["expectedFacts"],
            causes=case["expectedCauseSelections"],
            checks=case["expectedCheckSelections"],
            topology={"domain": case["domain"]},
            classification=case["provenance"],
            exposures=["MODEL_EVALUATION", "BLIND_QUALIFICATION"],
            source_sha256=blind_sha,
        ))

    challenge_path = RESOURCES / "corpus-v4-post-training-challenge" / "manifest.json"
    challenge = read_json(challenge_path)
    challenge_sha = file_digest(challenge_path)
    for case in challenge["cases"]:
        records.append(make_record(
            catalog="post-training-challenge",
            family_id=case["caseId"],
            canonical_family_id=None,
            recipe=case["recipe"],
            category=case["category"],
            throwable=[case["expectedRootType"]],
            evidence=[],
            causes=case["expectedCauseSelections"],
            checks=case["expectedCheckSelections"],
            topology={"frameworks": challenge.get("frameworks", [])},
            classification=case["provenance"],
            exposures=["MODEL_EVALUATION", "POST_TRAINING_CHALLENGE"],
            source_sha256=challenge_sha,
        ))

    v5_path = RESOURCES / "corpus-v5-real-world" / "manifest.json"
    v5 = read_json(v5_path)
    v5_sha = file_digest(v5_path)
    for family in v5["families"]:
        exposure = {
            "TRAIN": ["TRAINING", "PHASE54_SMOKE_ELIGIBLE"],
            "VALIDATION": ["VALIDATION", "MODEL_EVALUATION"],
            "LOCKED_FINAL": ["HISTORICAL_LOCKED_FINAL_DESCRIPTOR"],
        }[family["split"]]
        records.append(make_record(
            catalog="real-world-v1",
            family_id=family["familyId"],
            canonical_family_id=None,
            recipe=family["triggerPrimitive"],
            category=family["category"],
            throwable=[*family["wrapperTopology"], family["rootType"]],
            evidence=family["structuredEvidenceKind"],
            causes=family["expectedCauseIds"],
            checks=family["expectedCheckIds"],
            topology={"fixtureCode": family["fixtureCode"], "failurePhase": family["failurePhase"]},
            classification=family["classification"],
            exposures=exposure,
            source_sha256=v5_sha,
        ))

    dataset_contract_path = ALIGNMENT / "scripts" / "dataset_contract.py"
    dataset_contract_sha = file_digest(dataset_contract_path)
    for family in FAMILIES:
        exposures = ["SFT_TRAINING"] if family.split == "train" else ["SFT_VALIDATION"]
        records.append(make_record(
            catalog="phase52-synthetic-sft",
            family_id=family.family_id,
            canonical_family_id=None,
            recipe=family.fact_pattern,
            category=family.category,
            throwable=[family.fact_type],
            evidence={"factType": family.fact_type, "pattern": family.fact_pattern},
            causes=[family.supported_cause, family.distractor_cause],
            checks=[family.supported_check, family.distractor_check],
            topology={"kind": family.kind},
            classification="CONTROLLED_SYNTHETIC",
            exposures=exposures,
            source_sha256=dataset_contract_sha,
        ))

    attack_path = RESOURCES / "prompt-ablation" / "adversarial-prompt-suite.json"
    attack = read_json(attack_path)
    attack_sha = file_digest(attack_path)
    for item in attack["attacks"]:
        records.append(make_record(
            catalog="phase51-adversarial-prompt",
            family_id=item["id"],
            canonical_family_id=None,
            recipe=item["kind"],
            category="PROMPT_SECURITY",
            throwable=[],
            evidence={"payloadKind": item["kind"]},
            causes=[],
            checks=[],
            topology={"surface": "untrusted-incident-value"},
            classification="SYNTHETIC_ADVERSARIAL",
            exposures=["MODEL_EVALUATION", "PROMPT_SECURITY"],
            source_sha256=attack_sha,
        ))

    split_path = RESOURCES / "prompt-ablation" / "split-manifest.json"
    split = read_json(split_path)
    split_exposure = {
        family_id: f"PHASE51_{name.upper()}"
        for name, values in split["splits"].items()
        for family_id in values
    }
    for record in records:
        if record["familyId"] in split_exposure:
            record["exposures"] = sorted(set([*record["exposures"], split_exposure[record["familyId"]]]))

    records.sort(key=lambda item: (item["catalog"], item["familyId"]))
    by_catalog = Counter(item["catalog"] for item in records)
    by_category = Counter(str(item["category"]) for item in records)
    by_classification = Counter(item["classification"] for item in records)
    by_exposure: Counter[str] = Counter()
    for item in records:
        by_exposure.update(item["exposures"])
    canonical_ids = {item["canonicalFamilyId"] for item in records}
    signature_groups: dict[str, list[str]] = defaultdict(list)
    for item in records:
        signature_groups[item["semanticSignature"]].append(f"{item['catalog']}:{item['familyId']}")
    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "inventoryId": "stackmate-phase-5-4-3-existing-family-inventory-v1",
        "summary": {
            "records": len(records),
            "uniqueCanonicalFamilyIds": len(canonical_ids),
            "catalogs": dict(sorted(by_catalog.items())),
            "categories": dict(sorted(by_category.items())),
            "classifications": dict(sorted(by_classification.items())),
            "exposures": dict(sorted(by_exposure.items())),
            "semanticSignatureCollisions": sum(len(values) > 1 for values in signature_groups.values()),
        },
        "sourceHashes": {
            "benchmarkV2": v2_sha,
            "screening": screening_sha,
            "blind": blind_sha,
            "challenge": challenge_sha,
            "realWorldV1": v5_sha,
            "syntheticSftContract": dataset_contract_sha,
            "adversarialPromptSuite": attack_sha,
            "promptSplit": file_digest(split_path),
        },
        "signatureCollisions": {
            key: values for key, values in sorted(signature_groups.items()) if len(values) > 1
        },
        "records": records,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"status": report["status"], **report["summary"]}, separators=(",", ":")))


if __name__ == "__main__":
    main()
