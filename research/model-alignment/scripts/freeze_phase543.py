#!/usr/bin/env python3
"""Freeze separated Phase 5.4.3 corpus artifacts and their execution bindings."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from typing import Any


ALIGNMENT = Path(__file__).resolve().parents[1]
REPOSITORY = ALIGNMENT.parents[1]
BENCHMARK_ROOT = (
    REPOSITORY
    / "stackmate-benchmark"
    / "src"
    / "main"
    / "resources"
    / "corpus-v6-fresh-final"
)
REPORTS = ALIGNMENT / "reports"
SEALED = ALIGNMENT / "sealed" / "phase-5-4-3-labels"
GENERATION_DESTINATION = BENCHMARK_ROOT / "generation-inputs" / "fresh-final.jsonl"
DESCRIPTOR_DESTINATION = BENCHMARK_ROOT / "descriptors" / "resolved.json"
RESOURCE_MANIFEST = BENCHMARK_ROOT / "manifests" / "fresh-final-manifest.json"
LABEL_DESTINATION = SEALED / "scoring-labels.jsonl"
REPRODUCTION_DESTINATION = REPORTS / "phase-5-4-3-reproduction.json"
REPORT_MANIFEST = REPORTS / "phase-5-4-3-fresh-final-manifest.json"

BASE_REPOSITORY = "Qwen/Qwen3.5-2B"
BASE_REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
BASE_MANIFEST_SHA256 = "1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981"
ADAPTER_ID = "P5.4.1-T1-checkpoint-18"
ADAPTER_SHA256 = "7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49"
ADAPTER_SELECTION_COMMIT = "08b5b55adee82a83c3d81e599deca8ca6b3458ca"
T1_PROMPT_SHA256 = "9daee6dc707ff1c347346f5f0c667cba2b03035ab3966ddaf362744e0c528897"
T1_SCHEMA_EXAMPLE_SHA256 = "c04d55dc313934f78b78900cf3ebd3413ae9a523d17195a1f014a1f7a40ccd67"
CHAT_TEMPLATE_SHA256 = "273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80"
SCORER_VERSION = "phase-5-4-3-t1-scorer-v1"


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--export-directory", required=True, type=Path)
    parser.add_argument("--corpus-audit", required=True, type=Path)
    parser.add_argument(
        "--novelty-report",
        default="reports/phase-5-4-3-family-novelty.json",
        type=Path,
    )
    parser.add_argument(
        "--ground-truth-review",
        default="reports/phase-5-4-3-ground-truth-review.json",
        type=Path,
    )
    return parser.parse_args()


def alignment_path(path: Path) -> Path:
    return path.expanduser().resolve() if path.is_absolute() else (ALIGNMENT / path).resolve()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path.name}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    values: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        value = json.loads(line)
        if not isinstance(value, dict):
            raise ValueError(f"JSON object required in {path.name}")
        values.append(value)
    return values


def repository_relative(path: Path) -> str:
    return path.resolve().relative_to(REPOSITORY).as_posix()


def artifact(path: Path, *, records: int | None = None) -> dict[str, Any]:
    value: dict[str, Any] = {
        "path": repository_relative(path),
        "sizeBytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }
    if records is not None:
        value["records"] = records
    return value


def write_exclusive(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as destination:
        destination.write(content)


def verify_commit(commit: str) -> None:
    completed = subprocess.run(
        ["git", "cat-file", "-e", f"{commit}^{{commit}}"],
        cwd=REPOSITORY,
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        raise ValueError("adapter selection commit is unavailable")


def expected_sources(export_directory: Path) -> dict[str, Path]:
    result = {
        "resolvedDescriptors": export_directory / "descriptors.json",
        "generationInputs": export_directory / "generation-inputs.jsonl",
        "scoringLabels": export_directory / "scoring-labels.jsonl",
        "reproduction": export_directory / "reproduction.json",
    }
    missing = [key for key, path in result.items() if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"export artifacts missing: {missing}")
    return result


def verify_export(sources: dict[str, Path]) -> None:
    descriptors = read_object(sources["resolvedDescriptors"])
    generation = read_jsonl(sources["generationInputs"])
    labels = read_jsonl(sources["scoringLabels"])
    reproduction = read_object(sources["reproduction"])
    identities = {
        "descriptors": {item["familyId"] for item in descriptors.get("families", [])},
        "generation": {item["familyId"] for item in generation},
        "labels": {item["familyId"] for item in labels},
    }
    if any(len(values) != 30 for values in identities.values()):
        raise ValueError("fresh-final export must contain 30 unique family identities")
    if len({frozenset(values) for values in identities.values()}) != 1:
        raise ValueError("fresh-final artifact identities differ")
    if reproduction.get("status") != "PASS" or reproduction.get("realFamilies") != 24:
        raise ValueError("reproduction report is not a 24-family PASS")
    if reproduction.get("runsPerVariant") != 2 or reproduction.get("variantsPerFamily") != 2:
        raise ValueError("reproduction run cardinality drifted")


def verify_prerequisites(
    corpus_audit: Path,
    novelty: Path,
    ground_truth_review: Path,
    sources: dict[str, Path],
) -> None:
    audit = read_object(corpus_audit)
    if audit.get("status") != "PASS":
        raise ValueError("fresh-final corpus audit did not pass")
    novelty_value = read_object(novelty)
    if novelty_value.get("status") != "PASS" or novelty_value.get("rejected"):
        raise ValueError("fresh-family novelty audit did not pass")
    review = read_object(ground_truth_review)
    expected_review_hashes = {
        "resolvedDescriptorsSha256": sha256_file(sources["resolvedDescriptors"]),
        "generationInputsSha256": sha256_file(sources["generationInputs"]),
        "scoringLabelsSha256": sha256_file(sources["scoringLabels"]),
        "reproductionSha256": sha256_file(sources["reproduction"]),
    }
    if (
        review.get("status") != "PASS"
        or review.get("reviewedFamilies") != 30
        or review.get("unresolvedLabelDisputes") != 0
        or review.get("unresolvedNoveltyDisputes") != 0
        or review.get("unresolvedReproductionFailures") != 0
        or any(review.get(key) != value for key, value in expected_review_hashes.items())
    ):
        raise ValueError("ground-truth review is incomplete or bound to different artifacts")
    base_manifest = REPORTS / "base-model-manifest.json"
    if sha256_file(base_manifest) != BASE_MANIFEST_SHA256:
        raise ValueError("base-model manifest drifted")
    base_value = read_object(base_manifest)
    if (
        base_value.get("repositoryId") != BASE_REPOSITORY
        or base_value.get("officialRevision") != BASE_REVISION
    ):
        raise ValueError("base-model identity drifted")
    selection = read_object(REPORTS / "phase-5-4-1-selected-adapter.json")
    if (
        selection.get("adapterId") != ADAPTER_ID
        or selection.get("adapterSha256") != ADAPTER_SHA256
        or selection.get("selectionCommit") != ADAPTER_SELECTION_COMMIT
        or selection.get("sourceCheckpoint") != "checkpoint-18"
        or selection.get("baseModelRevision") != BASE_REVISION
    ):
        raise ValueError("selected adapter binding drifted")
    verify_commit(ADAPTER_SELECTION_COMMIT)


def main() -> None:
    args = arguments()
    export_directory = args.export_directory.expanduser().resolve()
    corpus_audit = alignment_path(args.corpus_audit)
    novelty = alignment_path(args.novelty_report)
    ground_truth_review = alignment_path(args.ground_truth_review)
    sources = expected_sources(export_directory)
    verify_export(sources)
    verify_prerequisites(corpus_audit, novelty, ground_truth_review, sources)

    destinations = {
        "resolvedDescriptors": DESCRIPTOR_DESTINATION,
        "generationInputs": GENERATION_DESTINATION,
        "scoringLabels": LABEL_DESTINATION,
        "reproduction": REPRODUCTION_DESTINATION,
    }
    all_targets = [*destinations.values(), RESOURCE_MANIFEST, REPORT_MANIFEST]
    existing = [repository_relative(path) for path in all_targets if path.exists()]
    if existing:
        raise FileExistsError(f"freeze refuses overwrite: {existing}")

    prewrite_dependencies = [
        ALIGNMENT / "contracts" / "t1-output.schema.json",
        ALIGNMENT / "configs" / "phase-5-4-3-generation.json",
        ALIGNMENT / "scripts" / "t1_contract.py",
        ALIGNMENT / "scripts" / "T1Contract.java",
        ALIGNMENT / "scripts" / "T1ContractCli.java",
        REPORTS / "phase-5-4-2-t1-cross-language-parity.json",
        ALIGNMENT / "scripts" / "run_phase543_generation.py",
        ALIGNMENT / "scripts" / "score_phase543.py",
        corpus_audit,
        novelty,
        ground_truth_review,
    ]
    missing_dependencies = [path.name for path in prewrite_dependencies if not path.is_file()]
    if missing_dependencies:
        raise FileNotFoundError(f"freeze dependency missing: {missing_dependencies}")

    for key, destination in destinations.items():
        write_exclusive(destination, sources[key].read_bytes())

    schema = ALIGNMENT / "contracts" / "t1-output.schema.json"
    generation_config = ALIGNMENT / "configs" / "phase-5-4-3-generation.json"
    python_parser = ALIGNMENT / "scripts" / "t1_contract.py"
    java_parser = ALIGNMENT / "scripts" / "T1Contract.java"
    java_cli = ALIGNMENT / "scripts" / "T1ContractCli.java"
    parity = REPORTS / "phase-5-4-2-t1-cross-language-parity.json"
    generation_runner = ALIGNMENT / "scripts" / "run_phase543_generation.py"
    scorer = ALIGNMENT / "scripts" / "score_phase543.py"
    exporter = (
        REPOSITORY
        / "stackmate-benchmark"
        / "src"
        / "test"
        / "java"
        / "io"
        / "github"
        / "kalayciburak"
        / "stackmate"
        / "benchmark"
        / "corpus"
        / "freshfinal"
        / "FreshFinalV2CorpusExportLab.java"
    )
    required_files = [
        schema,
        generation_config,
        python_parser,
        java_parser,
        java_cli,
        parity,
        generation_runner,
        scorer,
        exporter,
        corpus_audit,
        novelty,
        ground_truth_review,
    ]
    if any(not path.is_file() for path in required_files):
        missing = [path.name for path in required_files if not path.is_file()]
        raise FileNotFoundError(f"freeze dependency missing: {missing}")

    bindings = {
        "baseModelRepository": BASE_REPOSITORY,
        "baseRevision": BASE_REVISION,
        "baseModelManifestSha256": BASE_MANIFEST_SHA256,
        "adapterId": ADAPTER_ID,
        "adapterSha256": ADAPTER_SHA256,
        "adapterSelectionCommit": ADAPTER_SELECTION_COMMIT,
        "t1PromptSha256": T1_PROMPT_SHA256,
        "t1SchemaSha256": sha256_file(schema),
        "t1SchemaExampleSha256": T1_SCHEMA_EXAMPLE_SHA256,
        "pythonParserSha256": sha256_file(python_parser),
        "javaParserSha256": sha256_file(java_parser),
        "javaParserCliSha256": sha256_file(java_cli),
        "crossLanguageParitySha256": sha256_file(parity),
        "scorerVersion": SCORER_VERSION,
        "scorerSha256": sha256_file(scorer),
        "generationRunnerSha256": sha256_file(generation_runner),
        "generationConfigSha256": sha256_file(generation_config),
        "generationInputsSha256": sha256_file(GENERATION_DESTINATION),
        "tokenizerRevision": BASE_REVISION,
        "tokenizerClass": "Qwen2Tokenizer",
        "chatTemplateSha256": CHAT_TEMPLATE_SHA256,
    }
    manifest = {
        "schemaVersion": "1",
        "status": "FROZEN",
        "corpusId": "phase-5-4-3-fresh-final-t1",
        "frozenDate": "2026-09-04",
        "counts": {
            "families": 30,
            "realFamilies": 24,
            "syntheticFamilies": 6,
            "diagnoseFamilies": 19,
            "restraintFamilies": 11,
            "candidateOrdersPerFamily": 2,
            "generationRequestsPerModelSystem": 60,
        },
        "artifacts": {
            "sourceDescriptors": artifact(
                BENCHMARK_ROOT / "descriptors" / "manifest.json", records=30
            ),
            "resolvedDescriptors": artifact(DESCRIPTOR_DESTINATION, records=30),
            "generationInputs": artifact(GENERATION_DESTINATION, records=30),
            "scoringLabels": artifact(LABEL_DESTINATION, records=30),
            "reproduction": artifact(REPRODUCTION_DESTINATION, records=24),
            "corpusAudit": artifact(corpus_audit),
            "existingFamilyInventory": artifact(
                REPORTS / "phase-5-4-3-existing-family-inventory.json", records=318
            ),
            "familyNovelty": artifact(novelty, records=30),
            "groundTruthReview": artifact(ground_truth_review, records=30),
            "baseModelManifest": artifact(REPORTS / "base-model-manifest.json"),
            "adapterSelection": artifact(
                REPORTS / "phase-5-4-1-selected-adapter.json"
            ),
            "t1Schema": artifact(schema),
            "pythonParser": artifact(python_parser),
            "javaParser": artifact(java_parser),
            "javaParserCli": artifact(java_cli),
            "crossLanguageParity": artifact(parity),
            "generationConfig": artifact(generation_config),
            "generationRunner": artifact(generation_runner),
            "scorer": artifact(scorer),
            "exporter": artifact(exporter),
        },
        "bindings": bindings,
        "orderingPolicy": {
            "original": "SHA-256 sort by fixed aggregate-balancing salt, family ID and candidate ID",
            "permuted": "one-position rotation of the frozen original order",
            "causeOrderSalt": "phase543-order-v4",
            "checkOrderSalt": "phase543-order-v6",
            "familyMetric": "both ORIGINAL and PERMUTED must be correct",
        },
        "accessControl": {
            "generationLoadsLabels": False,
            "generationLabelsOnClasspath": False,
            "generationWritesScores": False,
            "scoringMayBeRepeatedWithoutGeneration": True,
            "attemptsPerSystem": 1,
        },
        "network": {
            "modelLoad": "OFFLINE_LOCAL_ONLY",
            "telemetry": False,
            "uploads": False,
        },
    }
    manifest_bytes = (
        json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    ).encode("utf-8")
    write_exclusive(RESOURCE_MANIFEST, manifest_bytes)
    write_exclusive(REPORT_MANIFEST, manifest_bytes)
    print(
        json.dumps(
            {
                "status": "FROZEN",
                "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
                "generationInputsSha256": bindings["generationInputsSha256"],
                "scoringLabelsSha256": sha256_file(LABEL_DESTINATION),
            },
            separators=(",", ":"),
        )
    )


if __name__ == "__main__":
    main()
