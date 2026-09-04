#!/usr/bin/env python3
"""Deterministic Gate C/D audit for the atomic contrastive dataset.

This audit is deliberately model-free and uses only the Python standard library.
It validates the published bytes and reviewed methodology before model weights may
be loaded.  Reports are immutable CREATE_NEW artifacts.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Sequence


ALIGNMENT = Path(__file__).resolve().parents[1]
REPOSITORY = ALIGNMENT.parents[1]
DEFAULT_DATASET = ALIGNMENT / "datasets" / "atomic-contrastive-v1"
DEFAULT_MANIFEST = DEFAULT_DATASET / "manifest.json"
DEFAULT_ONTOLOGY = ALIGNMENT / "ontology" / "atomic-candidates-v1.json"
DEFAULT_ONTOLOGY_REVIEW = ALIGNMENT / "reports" / "phase-5-5-2-ontology-review.json"
DEFAULT_PAIR_REVIEW_REPORT = ALIGNMENT / "reports" / "phase-5-5-2-pair-review.json"
DEFAULT_INDEPENDENT_REVIEW = (
    ALIGNMENT / "reports" / "phase-5-5-2-independent-semantic-review.json"
)
DEFAULT_DATASET_REPORT = ALIGNMENT / "reports" / "phase-5-5-2-dataset-audit.json"
DEFAULT_BASELINE_REPORT = ALIGNMENT / "reports" / "phase-5-5-2-shortcut-baselines.json"

LABELS = ("NOT_SUPPORTED", "SUPPORTED")
EXPECTED_FAMILIES = {"TRAIN": 60, "VALIDATION": 25}
EXPECTED_PAIRS = {"TRAIN": 426, "VALIDATION": 186}
EXPECTED_LABELS = {
    "TRAIN": {"SUPPORTED": 189, "NOT_SUPPORTED": 237},
    "VALIDATION": {"SUPPORTED": 93, "NOT_SUPPORTED": 93},
}
EXPECTED_TYPES = {
    "TRAIN": {"CAUSE": 110, "CHECK": 316},
    "VALIDATION": {"CAUSE": 44, "CHECK": 142},
}
EXPECTED_BASE_TYPES = {
    "TRAIN": {"CAUSE": 100, "CHECK": 300},
    "VALIDATION": {"CAUSE": 40, "CHECK": 120},
}
EXPECTED_SECURITY_PATTERNS = frozenset(
    {
        "RETURN_1",
        "RETURN_0",
        "SYSTEM_IMITATION",
        "HIDDEN_REASONING",
        "BOTH_DIGITS",
        "JSON_MARKDOWN",
        "FAKE_CLOSING_TAG_EVIDENCE",
        "ONE_BASED_EVIDENCE",
        "CONTROLLED_NON_SECRET_BAIT",
        "NEGATIVE_EVIDENCE",
        "UNSUPPORTED_FRAMEWORK_CAUSE",
        "UNICODE_HOMOGLYPH",
        "ZERO_WIDTH_BIDI",
    }
)
BASELINE_LIMITS = {"S0": 0.65, "S1": 0.65, "S2": 0.60, "S3": 0.60, "S4": 0.60}
LABEL_TOKEN = {"NOT_SUPPORTED": "0", "SUPPORTED": "1"}

PRIVATE_PATH = re.compile(
    r"(?i)(?:[a-z]:[/\\][^\s\"']+|/(?:home|mnt|tmp|users?)/[^\s\"']+|"
    r"(?:^|[/\\])src[/\\].+|[A-Za-z0-9_$.-]+\.java:\d+)"
)
SECRET = re.compile(
    r"(?i)(?:AKIA[0-9A-Z]{16}|BEGIN [A-Z ]+PRIVATE KEY|"
    r"eyJ[a-zA-Z0-9_-]{12,}\.[a-zA-Z0-9_-]{8,}\.|"
    r"(?:password|passwd|api[_-]?key|secret)\s*[:=]\s*[^\s,;]{4,}|"
    r"(?:https?|postgres(?:ql)?|mysql)://[^\s/:]+:[^\s/@]+@)"
)
TRUNCATION = re.compile(
    r"(?i)(?:\[?\.\.\.\s*(?:truncated|omitted)\s*\]?|<truncated>|"
    r"output\s+truncated|content\s+omitted|…\s*(?:truncated|omitted))"
)
TAG_PATTERN = re.compile(
    r"\A<CANDIDATE_TYPE>\n(?P<type>.*?)\n</CANDIDATE_TYPE>\n"
    r"<TRUSTED_EVIDENCE>\n(?P<evidence>.*?)\n</TRUSTED_EVIDENCE>\n"
    r"<NEGATIVE_EVIDENCE>\n(?P<negative>.*?)\n</NEGATIVE_EVIDENCE>\n"
    r"<CANDIDATE>\n(?P<candidate>.*?)\n</CANDIDATE>\Z",
    re.DOTALL,
)


class AuditError(ValueError):
    """Input or publication error that prevents a meaningful audit."""


@dataclass(frozen=True)
class AuditRow:
    split: str
    label: str
    candidate_type: str
    semantic_id: str
    family_id: str
    pair_id: str
    pair_kind: str
    difficulty: str
    canonical_text: str
    candidate_model_text: str
    model_input: str
    normalized_model_input: str
    input_length: int
    evidence_count: int
    evidence_type_signature: tuple[str, ...]
    negative_evidence_present: bool
    negative_evidence_count: int
    negative_evidence_serialized_length: int
    negative_evidence_value_length: int
    security_pattern: str | None
    input_sha256: str


class Checks:
    def __init__(self) -> None:
        self.values: list[dict[str, Any]] = []

    def add(
        self,
        check_id: str,
        passed: bool,
        *,
        observed: Any = None,
        expected: Any = None,
    ) -> None:
        item: dict[str, Any] = {"check": check_id, "status": "PASS" if passed else "FAIL"}
        if observed is not None:
            item["observed"] = observed
        if expected is not None:
            item["expected"] = expected
        self.values.append(item)

    @property
    def passed(self) -> bool:
        return all(item["status"] == "PASS" for item in self.values)


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise AuditError(f"duplicate JSON key: {key!r}")
        value[key] = item
    return value


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_keys,
        )
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise AuditError(f"cannot read JSON input {path.name}: {error}") from error
    if not isinstance(value, dict):
        raise AuditError(f"JSON object required: {path.name}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as error:
        raise AuditError(f"cannot read JSONL input {path.name}: {error}") from error
    for line_number, line in enumerate(lines, 1):
        if not line:
            continue
        try:
            value = json.loads(line, object_pairs_hook=reject_duplicate_keys)
        except (json.JSONDecodeError, AuditError) as error:
            raise AuditError(f"invalid JSONL in {path.name} line {line_number}: {error}") from error
        if not isinstance(value, dict):
            raise AuditError(f"JSONL object required in {path.name} line {line_number}")
        rows.append(value)
    return rows


def require_file(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if not resolved.is_file():
        raise AuditError(f"required input does not exist: {path.name}")
    return resolved


def safe_relative_path(root: Path, value: str, context: str) -> Path:
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise AuditError(f"{context} must be a safe relative path")
    resolved = (root / candidate).resolve()
    try:
        resolved.relative_to(root.resolve())
    except ValueError as error:
        raise AuditError(f"{context} escapes its allowed root") from error
    return require_file(resolved)


def normalize_model_input(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def extract_model_fields(user: str) -> tuple[str, list[Any], list[Any], str, str, str]:
    match = TAG_PATTERN.fullmatch(user)
    if match is None:
        raise AuditError("user model input does not match the bounded four-block contract")
    try:
        evidence = json.loads(match.group("evidence"))
        negative = json.loads(match.group("negative"))
    except json.JSONDecodeError as error:
        raise AuditError(f"model-input evidence is invalid JSON: {error}") from error
    if not isinstance(evidence, list) or not isinstance(negative, list):
        raise AuditError("model-input evidence blocks must contain arrays")
    return (
        match.group("type"),
        evidence,
        negative,
        match.group("candidate"),
        match.group("evidence"),
        match.group("negative"),
    )


def binary_metrics(expected: Sequence[str], predicted: Sequence[str]) -> dict[str, Any]:
    if len(expected) != len(predicted):
        raise ValueError("expected and predicted lengths differ")
    if not expected:
        raise ValueError("metrics require at least one example")
    if any(label not in LABELS for label in (*expected, *predicted)):
        raise ValueError("metrics received an unknown label")
    per_label: dict[str, dict[str, Any]] = {}
    for label in LABELS:
        true_positive = sum(a == label and b == label for a, b in zip(expected, predicted))
        false_positive = sum(a != label and b == label for a, b in zip(expected, predicted))
        false_negative = sum(a == label and b != label for a, b in zip(expected, predicted))
        precision_denominator = true_positive + false_positive
        recall_denominator = true_positive + false_negative
        precision = true_positive / precision_denominator if precision_denominator else 0.0
        recall = true_positive / recall_denominator if recall_denominator else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        per_label[label] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": recall_denominator,
        }
    return {
        "accuracy": sum(a == b for a, b in zip(expected, predicted)) / len(expected),
        "macroF1": sum(per_label[label]["f1"] for label in LABELS) / len(LABELS),
        "examples": len(expected),
        "perLabel": per_label,
    }


def majority_label(labels: Iterable[str]) -> str:
    counts = Counter(labels)
    return "SUPPORTED" if counts["SUPPORTED"] > counts["NOT_SUPPORTED"] else "NOT_SUPPORTED"


def fit_lookup(
    rows: Sequence[AuditRow], key: Callable[[AuditRow], Any]
) -> tuple[dict[Any, str], str]:
    grouped: dict[Any, list[str]] = defaultdict(list)
    for row in rows:
        grouped[key(row)].append(row.label)
    return ({value: majority_label(labels) for value, labels in grouped.items()}, majority_label(row.label for row in rows))


def score_lookup(
    train: Sequence[AuditRow], validation: Sequence[AuditRow], key: Callable[[AuditRow], Any]
) -> tuple[dict[str, Any], dict[str, Any]]:
    table, fallback = fit_lookup(train, key)
    expected = [row.label for row in validation]
    predictions = [table.get(key(row), fallback) for row in validation]
    details = {
        "lookupKeys": len(table),
        "fallback": fallback,
        "unseenValidationKeys": sum(key(row) not in table for row in validation),
        "tiePolicy": "NOT_SUPPORTED",
    }
    return binary_metrics(expected, predictions), details


def predict_length(rule: Mapping[str, Any], length: int) -> str:
    threshold = int(rule["threshold"])
    supported = length <= threshold if rule["direction"] == "SUPPORTED_IF_LE" else length > threshold
    return "SUPPORTED" if supported else "NOT_SUPPORTED"


def fit_length_threshold(rows: Sequence[AuditRow]) -> dict[str, Any]:
    if not rows:
        raise ValueError("length-threshold fitting requires TRAIN examples")
    lengths = sorted({row.input_length for row in rows})
    thresholds = [lengths[0] - 1, *lengths]
    expected = [row.label for row in rows]
    candidates: list[tuple[tuple[float, float, int, int], dict[str, Any]]] = []
    for direction_rank, direction in enumerate(("SUPPORTED_IF_LE", "SUPPORTED_IF_GT")):
        for threshold in thresholds:
            rule = {"threshold": threshold, "direction": direction}
            metrics = binary_metrics(expected, [predict_length(rule, row.input_length) for row in rows])
            # max() uses accuracy, then macro F1, then the smaller threshold and
            # finally SUPPORTED_IF_LE.  Validation never participates in selection.
            rank = (metrics["accuracy"], metrics["macroF1"], -threshold, -direction_rank)
            candidates.append((rank, {**rule, "trainMetrics": metrics}))
    return max(candidates, key=lambda item: item[0])[1]


def order_hash(ids: Sequence[str]) -> str:
    return sha256_bytes("".join(f"{item}\n" for item in ids).encode("utf-8"))


def audit_manifest(
    manifest: Mapping[str, Any],
    dataset_dir: Path,
    ontology_path: Path,
    pair_review_report_path: Path,
    checks: Checks,
) -> None:
    checks.add("manifest.status", manifest.get("status") == "PASS", observed=manifest.get("status"), expected="PASS")
    checks.add(
        "manifest.publication_mode",
        manifest.get("publicationMode") == "CREATE_NEW",
        observed=manifest.get("publicationMode"),
        expected="CREATE_NEW",
    )
    files = manifest.get("files")
    if not isinstance(files, dict):
        raise AuditError("manifest.files must be an object")
    file_paths = {
        "families": dataset_dir / "families.jsonl",
        "train": dataset_dir / "train.jsonl",
        "validation": dataset_dir / "validation.jsonl",
        "pairReviews": dataset_dir / "pair-reviews.jsonl",
        "reviewReport": pair_review_report_path,
    }
    for key, path in file_paths.items():
        entry = files.get(key)
        if not isinstance(entry, dict):
            checks.add(f"manifest.file_hash.{key}", False, observed="MISSING", expected="BOUND")
            continue
        expected_hash = entry.get("sha256")
        actual_hash = sha256_file(require_file(path))
        checks.add(f"manifest.file_hash.{key}", actual_hash == expected_hash, observed=actual_hash, expected=expected_hash)
        checks.add(
            f"manifest.file_name.{key}",
            entry.get("name") == path.name,
            observed=entry.get("name"),
            expected=path.name,
        )
        actual_records = (
            1
            if key == "reviewReport"
            else sum(bool(line) for line in path.read_text(encoding="utf-8").splitlines())
        )
        checks.add(
            f"manifest.file_records.{key}",
            entry.get("records") == actual_records,
            observed=actual_records,
            expected=entry.get("records"),
        )

    sources = manifest.get("sources")
    if not isinstance(sources, dict):
        raise AuditError("manifest.sources must be an object")
    source_paths: dict[str, Path] = {
        "trainSha256": ALIGNMENT / "datasets" / "real-world-v1" / "train.jsonl",
        "validationSha256": ALIGNMENT / "datasets" / "real-world-v1" / "validation.jsonl",
        "sourceManifestSha256": ALIGNMENT / "datasets" / "real-world-v1" / "manifest.json",
        "ontologySha256": ontology_path,
        "promptSha256": ALIGNMENT / "prompts" / "binary-evidence-support-v1.txt",
        "inventorySha256": ALIGNMENT / "reports" / "phase-5-4-3-existing-family-inventory.json",
    }
    generator = manifest.get("generator")
    builder = manifest.get("builder")
    if not isinstance(generator, str) or not isinstance(builder, str):
        raise AuditError("manifest generator and builder must be relative strings")
    source_paths["generatorSha256"] = safe_relative_path(ALIGNMENT, generator, "manifest.generator")
    source_paths["builderSha256"] = safe_relative_path(ALIGNMENT, builder, "manifest.builder")
    hash_keys = {key for key in sources if key.endswith("Sha256")}
    checks.add(
        "manifest.source_hash_keys",
        hash_keys == set(source_paths),
        observed=sorted(hash_keys),
        expected=sorted(source_paths),
    )
    for key, path in source_paths.items():
        actual_hash = sha256_file(require_file(path))
        checks.add(f"manifest.source_hash.{key}", actual_hash == sources.get(key), observed=actual_hash, expected=sources.get(key))


def audit_ontology(
    ontology: Mapping[str, Any], review: Mapping[str, Any], ontology_path: Path, checks: Checks
) -> dict[str, tuple[str, str]]:
    entries = ontology.get("entries")
    if not isinstance(entries, list) or not all(isinstance(item, dict) for item in entries):
        raise AuditError("ontology.entries must be an object array")
    semantic: dict[str, tuple[str, str]] = {}
    for item in entries:
        semantic_id = item.get("semanticId")
        candidate_type = item.get("type")
        text = item.get("canonicalText")
        if not all(isinstance(value, str) for value in (semantic_id, candidate_type, text)):
            raise AuditError("ontology entry identity fields must be strings")
        if semantic_id in semantic:
            raise AuditError("ontology semantic IDs must be unique")
        semantic[semantic_id] = (candidate_type, text)
    type_counts = Counter(value[0] for value in semantic.values())
    counts = {"causes": type_counts["CAUSE"], "checks": type_counts["CHECK"], "total": len(semantic)}
    checks.add("ontology.counts", counts == {"causes": 15, "checks": 45, "total": 60}, observed=counts, expected={"causes": 15, "checks": 45, "total": 60})
    checks.add("ontology.canonical_text_unique", len({value[1] for value in semantic.values()}) == 60, observed=len({value[1] for value in semantic.values()}), expected=60)
    ontology_hash = sha256_file(ontology_path)
    checks.add("ontology_review.ontology_hash", review.get("ontologySha256") == ontology_hash, observed=review.get("ontologySha256"), expected=ontology_hash)
    review_counts = review.get("counts")
    checks.add("ontology_review.counts", review_counts == counts, observed=review_counts, expected=counts)
    review_checks = review.get("checks")
    required_review_checks = {
        "atomicity": "PASS",
        "hedging": "PASS",
        "idDependence": "PASS",
        "predicateReferences": "PASS",
        "checkCauseLinks": "PASS",
        "canonicalTextUniqueness": "PASS",
        "unresolvedDisputes": 0,
    }
    checks.add("ontology_review.checks", isinstance(review_checks, dict) and all(review_checks.get(key) == value for key, value in required_review_checks.items()), observed=review_checks, expected=required_review_checks)
    lexical = sorted(semantic)
    digest_order = sorted(lexical, key=lambda item: sha256_bytes(item.encode("utf-8")))
    expected_orders = [order_hash(lexical), order_hash(digest_order)]
    passes = review.get("reviewPasses")
    passes_valid = (
        isinstance(passes, list)
        and len(passes) == 2
        and all(isinstance(item, dict) for item in passes)
        and [item.get("orderSha256") for item in passes] == expected_orders
        and all(item.get("entriesReviewed") == 60 and item.get("issues") == 0 for item in passes)
    )
    checks.add("ontology_review.two_passes", passes_valid, observed=len(passes) if isinstance(passes, list) else None, expected=2)
    bindings = review.get("sourceBindings")
    binding_results: list[bool] = []
    if isinstance(bindings, list):
        for item in bindings:
            if not isinstance(item, dict) or not isinstance(item.get("file"), str):
                binding_results.append(False)
                continue
            path = safe_relative_path(REPOSITORY, item["file"], "ontology source binding")
            binding_results.append(sha256_file(path) == item.get("sha256"))
    checks.add("ontology_review.source_hashes", len(binding_results) == 7 and all(binding_results), observed={"bound": len(binding_results), "matched": sum(binding_results)}, expected={"bound": 7, "matched": 7})
    return semantic


def audit_independent_review(
    review: Mapping[str, Any], checks: Checks
) -> dict[str, Any]:
    bindings = review.get("artifactBindings")
    if not isinstance(bindings, dict):
        raise AuditError("independent review artifactBindings must be an object")
    paths = {
        "ontologySha256": DEFAULT_ONTOLOGY,
        "ontologyReviewSha256": DEFAULT_ONTOLOGY_REVIEW,
        "datasetManifestSha256": DEFAULT_MANIFEST,
        "trainSha256": DEFAULT_DATASET / "train.jsonl",
        "validationSha256": DEFAULT_DATASET / "validation.jsonl",
        "familiesSha256": DEFAULT_DATASET / "families.jsonl",
        "pairReviewsSha256": DEFAULT_DATASET / "pair-reviews.jsonl",
        "pairReviewReportSha256": DEFAULT_PAIR_REVIEW_REPORT,
        "generatorSha256": ALIGNMENT / "scripts" / "atomic_contrastive_dataset.py",
    }
    binding_status = {
        key: bindings.get(key) == sha256_file(require_file(path))
        for key, path in paths.items()
    }
    checks.add(
        "independent_review.artifact_hashes",
        all(binding_status.values()) and set(bindings) == set(paths),
        observed=binding_status,
        expected="all current artifacts bound",
    )
    passes = review.get("reviewPasses")
    pass_valid = (
        isinstance(passes, list)
        and len(passes) == 2
        and all(isinstance(item, dict) for item in passes)
        and len({item.get("owner") for item in passes}) == 2
        and all(item.get("unresolvedIssues") == 0 for item in passes)
        and passes[0].get("issuesFound") == passes[0].get("issuesResolved") == 5
        and passes[1].get("issuesFoundAfterCorrection") == 0
    )
    checks.add(
        "independent_review.two_semantic_passes",
        pass_valid,
        observed=len(passes) if isinstance(passes, list) else None,
        expected=2,
    )
    recommendations = review.get("gateRecommendations")
    expected_recommendations = {
        "gateBAtomicOntology": "PASS",
        "gateCBinaryDataset": "PASS",
        "gateDShortcutAudit": "PASS",
    }
    checks.add(
        "independent_review.status_and_recommendations",
        review.get("status") == "PASS"
        and recommendations == expected_recommendations
        and review.get("modelWeightsLoaded") is False
        and review.get("exposedDiagnosticLabelsAccessed") is False,
        observed={"status": review.get("status"), "recommendations": recommendations},
        expected={"status": "PASS", "recommendations": expected_recommendations},
    )
    return {
        "status": review.get("status"),
        "passes": len(passes) if isinstance(passes, list) else 0,
        "resolvedFindings": len(review.get("resolvedFindings", [])),
        "limitations": review.get("limitations", []),
    }


def parse_rows(raw_rows: Mapping[str, Sequence[dict[str, Any]]], checks: Checks) -> dict[str, list[AuditRow]]:
    parsed: dict[str, list[AuditRow]] = {"TRAIN": [], "VALIDATION": []}
    shape_ok = True
    input_hash_ok = True
    metadata_free = True
    private_free = True
    truncation_free = True
    negative_flag_ok = True
    for split in ("TRAIN", "VALIDATION"):
        for raw in raw_rows[split]:
            messages = raw.get("messages")
            metadata = raw.get("metadata")
            if not isinstance(messages, list) or len(messages) != 3 or not isinstance(metadata, dict):
                raise AuditError(f"{split} row lacks messages or metadata")
            if [item.get("role") for item in messages if isinstance(item, dict)] != ["system", "user", "assistant"]:
                raise AuditError(f"{split} row has invalid message roles")
            if not all(isinstance(item.get("content"), str) for item in messages):
                raise AuditError(f"{split} row has non-string message content")
            system, user, assistant = (item["content"] for item in messages)
            label = metadata.get("label")
            if label not in LABELS:
                raise AuditError(f"{split} row has invalid label")
            candidate_type, evidence, negative, candidate_text, _, negative_text = extract_model_fields(user)
            required_metadata = (
                "semanticId", "familyId", "pairId", "pairKind", "difficulty", "text", "inputSha256"
            )
            if not all(isinstance(metadata.get(key), str) for key in required_metadata):
                raise AuditError(f"{split} row has invalid metadata identity fields")
            shape_ok &= metadata.get("split") == split
            shape_ok &= candidate_type == metadata.get("type")
            shape_ok &= assistant == LABEL_TOKEN[label]
            model_object = {"system": system, "user": user}
            input_sha = sha256_bytes(canonical_json(model_object).encode("utf-8"))
            input_hash_ok &= input_sha == metadata["inputSha256"]
            present = bool(negative)
            negative_flag_ok &= metadata.get("negativeEvidencePresent") is present
            forbidden_keys = (
                '"metadata"', '"label"', '"pairId"', '"familyId"', '"semanticId"',
                '"inputSha256"', '"category"', '"rationale"', '"reviewStatus"',
                '"missingPredicate"', '"pairKind"',
            )
            metadata_free &= not any(value in user for value in forbidden_keys)
            metadata_free &= all(
                value not in user
                for value in (
                    metadata["pairId"], metadata["familyId"], metadata["semanticId"],
                    metadata.get("rationale", ""), label,
                )
                if value
            )
            private_free &= PRIVATE_PATH.search(user) is None and SECRET.search(user) is None
            truncation_free &= TRUNCATION.search(user) is None
            shape_ok &= all(
                isinstance(item, dict)
                and set(item) == {"type", "value"}
                and all(isinstance(item.get(key), str) for key in ("type", "value"))
                for item in (*evidence, *negative)
            )
            model_input = canonical_json(model_object)
            parsed[split].append(
                AuditRow(
                    split=split,
                    label=label,
                    candidate_type=candidate_type,
                    semantic_id=metadata["semanticId"],
                    family_id=metadata["familyId"],
                    pair_id=metadata["pairId"],
                    pair_kind=metadata["pairKind"],
                    difficulty=metadata["difficulty"],
                    canonical_text=metadata["text"].split("\n<UNTRUSTED_PATTERN>", 1)[0],
                    candidate_model_text=candidate_text,
                    model_input=model_input,
                    normalized_model_input=normalize_model_input(model_input),
                    input_length=len(system) + len(user),
                    evidence_count=len(evidence),
                    evidence_type_signature=tuple(
                        sorted(str(item.get("type")) for item in evidence)
                    ),
                    negative_evidence_present=present,
                    negative_evidence_count=len(negative),
                    negative_evidence_serialized_length=len(negative_text),
                    negative_evidence_value_length=sum(len(str(item.get("value", ""))) for item in negative if isinstance(item, dict)),
                    security_pattern=metadata.get("securityPattern") if isinstance(metadata.get("securityPattern"), str) else None,
                    input_sha256=input_sha,
                )
            )
    checks.add("rows.message_and_label_contract", shape_ok)
    checks.add("rows.input_sha256", input_hash_ok)
    checks.add("rows.negative_evidence_flag", negative_flag_ok)
    checks.add("privacy.metadata_label_ids_category_rationale_absent", metadata_free)
    checks.add("privacy.private_paths_and_actual_secrets_absent", private_free)
    checks.add("privacy.truncation_markers_absent", truncation_free)
    return parsed


def negative_evidence_summary(rows: Sequence[AuditRow]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for label in LABELS:
        selected = [row for row in rows if row.label == label]
        present = [row for row in selected if row.negative_evidence_present]
        result[label] = {
            "rows": len(selected),
            "presentRows": len(present),
            "totalItems": sum(row.negative_evidence_count for row in present),
            "itemCounts": sorted(Counter(row.negative_evidence_count for row in present).items()),
            "serializedCharacterLengths": sorted(Counter(row.negative_evidence_serialized_length for row in present).items()),
            "valueCharacterLengths": sorted(Counter(row.negative_evidence_value_length for row in present).items()),
        }
    return result


def audit_dataset_content(
    parsed: Mapping[str, Sequence[AuditRow]],
    families: Sequence[dict[str, Any]],
    semantic: Mapping[str, tuple[str, str]],
    manifest: Mapping[str, Any],
    checks: Checks,
) -> dict[str, Any]:
    summaries: dict[str, Any] = {}
    family_sets: dict[str, set[str]] = {}
    family_table_ok = True
    for split in ("TRAIN", "VALIDATION"):
        family_ids = {
            item.get("familyId")
            for item in families
            if isinstance(item, dict) and item.get("split") == split and isinstance(item.get("familyId"), str)
        }
        family_sets[split] = family_ids
        represented = {row.family_id for row in parsed[split]}
        family_table_ok &= represented == family_ids
        counts = Counter(row.label for row in parsed[split])
        types = Counter(row.candidate_type for row in parsed[split])
        base_types = Counter(row.candidate_type for row in parsed[split] if row.pair_kind != "HARD_SECURITY")
        negatives = [row for row in parsed[split] if row.label == "NOT_SUPPORTED"]
        hard = sum(row.difficulty in {"HARD", "HARD_SECURITY"} for row in negatives)
        easy = sum(row.difficulty == "EASY" for row in negatives)
        hard_ratio = hard / len(negatives) if negatives else 0.0
        easy_ratio = easy / len(negatives) if negatives else 1.0
        ratios = {label: counts[label] / len(parsed[split]) for label in LABELS}
        summaries[split] = {
            "families": len(family_ids),
            "representedFamilies": len(represented),
            "pairs": len(parsed[split]),
            "labels": {label: counts[label] for label in LABELS},
            "classRatios": ratios,
            "candidateTypes": {kind: types[kind] for kind in ("CAUSE", "CHECK")},
            "nonSecurityCandidateTypes": {kind: base_types[kind] for kind in ("CAUSE", "CHECK")},
            "negativeDifficulty": {"hard": hard, "easy": easy, "hardRatio": hard_ratio, "easyRatio": easy_ratio},
            "negativeEvidence": negative_evidence_summary(parsed[split]),
        }
        checks.add(f"counts.{split.lower()}.families", len(family_ids) == EXPECTED_FAMILIES[split], observed=len(family_ids), expected=EXPECTED_FAMILIES[split])
        checks.add(f"counts.{split.lower()}.pairs", len(parsed[split]) == EXPECTED_PAIRS[split], observed=len(parsed[split]), expected=EXPECTED_PAIRS[split])
        checks.add(f"counts.{split.lower()}.labels", dict(counts) == EXPECTED_LABELS[split], observed={label: counts[label] for label in LABELS}, expected=EXPECTED_LABELS[split])
        checks.add(f"counts.{split.lower()}.candidate_types", {kind: types[kind] for kind in ("CAUSE", "CHECK")} == EXPECTED_TYPES[split], observed={kind: types[kind] for kind in ("CAUSE", "CHECK")}, expected=EXPECTED_TYPES[split])
        checks.add(f"counts.{split.lower()}.non_security_candidate_types", {kind: base_types[kind] for kind in ("CAUSE", "CHECK")} == EXPECTED_BASE_TYPES[split], observed={kind: base_types[kind] for kind in ("CAUSE", "CHECK")}, expected=EXPECTED_BASE_TYPES[split])
        checks.add(f"counts.{split.lower()}.class_ratios", all(0.40 <= value <= 0.60 for value in ratios.values()), observed=ratios, expected="each label ratio in [0.40, 0.60]")
        checks.add(f"counts.{split.lower()}.validation_label_floor", split != "VALIDATION" or all(counts[label] >= 40 for label in LABELS), observed={label: counts[label] for label in LABELS} if split == "VALIDATION" else "NOT_APPLICABLE", expected={"SUPPORTED": ">=40", "NOT_SUPPORTED": ">=40"} if split == "VALIDATION" else "NOT_APPLICABLE")
        checks.add(f"difficulty.{split.lower()}.hard_negative_ratio", hard_ratio >= 0.50, observed=hard_ratio, expected=">=0.50")
        checks.add(f"difficulty.{split.lower()}.easy_negative_ratio", easy_ratio <= 0.25, observed=easy_ratio, expected="<=0.25")
        negative_summary = summaries[split]["negativeEvidence"]
        negative_ok = (
            all(negative_summary[label]["presentRows"] >= 1 for label in LABELS)
            and len({negative_summary[label]["presentRows"] for label in LABELS}) == 1
            and all(
                row.negative_evidence_count == 1
                and 1 <= row.negative_evidence_value_length <= 128
                and row.negative_evidence_serialized_length <= 160
                for row in parsed[split]
                if row.negative_evidence_present
            )
        )
        checks.add(f"negative_evidence.{split.lower()}.presence_count_length_by_label", negative_ok, observed=negative_summary, expected="both labels, balanced presence, one bounded item per present row")
        duplicate_full = len(parsed[split]) - len({row.model_input for row in parsed[split]})
        duplicate_normalized = len(parsed[split]) - len({row.normalized_model_input for row in parsed[split]})
        checks.add(f"leakage.{split.lower()}.duplicate_model_inputs", duplicate_full == 0, observed=duplicate_full, expected=0)
        checks.add(f"leakage.{split.lower()}.duplicate_normalized_model_inputs", duplicate_normalized == 0, observed=duplicate_normalized, expected=0)

    checks.add("families.represented_exactly", family_table_ok)
    overlap = family_sets["TRAIN"] & family_sets["VALIDATION"]
    checks.add("leakage.family_overlap", not overlap, observed=len(overlap), expected=0)
    train_full = {row.model_input for row in parsed["TRAIN"]}
    validation_full = {row.model_input for row in parsed["VALIDATION"]}
    train_normalized = {row.normalized_model_input for row in parsed["TRAIN"]}
    validation_normalized = {row.normalized_model_input for row in parsed["VALIDATION"]}
    checks.add("leakage.full_model_input_overlap", not (train_full & validation_full), observed=len(train_full & validation_full), expected=0)
    checks.add("leakage.normalized_model_input_overlap", not (train_normalized & validation_normalized), observed=len(train_normalized & validation_normalized), expected=0)
    all_rows = [*parsed["TRAIN"], *parsed["VALIDATION"]]
    labels_by_input: dict[str, set[str]] = defaultdict(set)
    for row in all_rows:
        labels_by_input[row.model_input].add(row.label)
    conflicting = sum(len(labels) > 1 for labels in labels_by_input.values())
    checks.add("leakage.same_input_conflicting_labels", conflicting == 0, observed=conflicting, expected=0)
    unique_pair_ids = len({row.pair_id for row in all_rows})
    checks.add("rows.unique_pair_ids", unique_pair_ids == 612, observed=unique_pair_ids, expected=612)

    coverage: dict[tuple[str, str, str], set[str]] = defaultdict(set)
    exact_canonical_coverage: dict[tuple[str, str, str], set[str]] = defaultdict(set)
    canonical_ok = True
    for row in all_rows:
        entry = semantic.get(row.semantic_id)
        canonical_ok &= entry is not None and entry[0] == row.candidate_type and entry[1] == row.canonical_text
        coverage[(row.split, row.semantic_id, row.label)].add(row.family_id)
        if entry is not None and row.candidate_model_text == entry[1]:
            exact_canonical_coverage[(row.split, row.semantic_id, row.label)].add(row.family_id)
    checks.add("ontology.dataset_binding", canonical_ok)
    for split in ("TRAIN", "VALIDATION"):
        complete = all(exact_canonical_coverage[(split, semantic_id, label)] for semantic_id in semantic for label in LABELS)
        checks.add(f"coverage.{split.lower()}.all_60_semantics_dual_label", complete, observed=sum(all(exact_canonical_coverage[(split, semantic_id, label)] for label in LABELS) for semantic_id in semantic), expected=60)
    minimum_contexts = min(
        len(coverage[("TRAIN", semantic_id, label)] | coverage[("VALIDATION", semantic_id, label)])
        for semantic_id in semantic
        for label in LABELS
    )
    checks.add("coverage.two_family_contexts_each_label_overall", minimum_contexts >= 2, observed=minimum_contexts, expected=">=2")

    security_summary: dict[str, Any] = {}
    for split in ("TRAIN", "VALIDATION"):
        security = [row for row in parsed[split] if row.pair_kind == "HARD_SECURITY"]
        by_pattern: dict[str, list[AuditRow]] = defaultdict(list)
        for row in security:
            if row.security_pattern is not None:
                by_pattern[row.security_pattern].append(row)
        valid = set(by_pattern) == EXPECTED_SECURITY_PATTERNS and all(
            len(rows) == 2
            and {row.label for row in rows} == set(LABELS)
            and len({row.semantic_id for row in rows}) == 1
            for rows in by_pattern.values()
        )
        security_summary[split] = {
            "patterns": len(by_pattern),
            "rows": len(security),
            "labelsPerPattern": "SUPPORTED_AND_NOT_SUPPORTED" if valid else "INVALID",
        }
        checks.add(f"security.{split.lower()}.thirteen_patterns_dual_label", valid, observed=security_summary[split], expected={"patterns": 13, "rows": 26, "labelsPerPattern": "SUPPORTED_AND_NOT_SUPPORTED"})

    manifest_counts = manifest.get("counts")
    expected_manifest_counts = {
        "families": EXPECTED_FAMILIES,
        "pairs": EXPECTED_PAIRS,
        "labels": EXPECTED_LABELS,
        "securityPatternsPerSplit": 13,
        "reviewRows": 1224,
        "reviewDisputes": 0,
    }
    checks.add("manifest.aggregate_counts", isinstance(manifest_counts, dict) and all(manifest_counts.get(key) == value for key, value in expected_manifest_counts.items()), observed=manifest_counts, expected=expected_manifest_counts)
    return {
        "splits": summaries,
        "crossSplit": {
            "familyOverlap": len(overlap),
            "fullModelInputOverlap": len(train_full & validation_full),
            "normalizedModelInputOverlap": len(train_normalized & validation_normalized),
            "sameInputConflictingLabels": conflicting,
        },
        "coverage": {"semanticIds": len(semantic), "minimumFamilyContextsPerSemanticLabelOverall": minimum_contexts},
        "security": security_summary,
    }


def audit_pair_reviews(
    rows: Sequence[AuditRow], reviews: Sequence[dict[str, Any]], report: Mapping[str, Any], checks: Checks
) -> dict[str, Any]:
    row_by_pair = {row.pair_id: row for row in rows}
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    valid_rows = True
    review_ids: list[str] = []
    disputes = 0
    for review in reviews:
        pair_id = review.get("pairId")
        if not isinstance(pair_id, str):
            valid_rows = False
            continue
        grouped[pair_id].append(review)
        review_id = review.get("reviewId")
        if isinstance(review_id, str):
            review_ids.append(review_id)
        row = row_by_pair.get(pair_id)
        valid_rows &= row is not None
        if row is not None:
            valid_rows &= review.get("inputSha256") == row.input_sha256
            valid_rows &= review.get("computedLabel") == row.label
        valid_rows &= review.get("decision") == "ACCEPT"
        disputes += review.get("decision") != "ACCEPT"
    exact_two = (
        set(grouped) == set(row_by_pair)
        and all(len(values) == 2 for values in grouped.values())
        and all({item.get("reviewPass") for item in values} == {"LEXICAL", "HASH"} for values in grouped.values())
    )
    checks.add("pair_review.exactly_two_accept_rows_per_pair", exact_two and valid_rows and len(reviews) == 1224, observed={"pairs": len(grouped), "rows": len(reviews)}, expected={"pairs": 612, "rows": 1224})
    checks.add("pair_review.unique_review_ids", len(review_ids) == len(set(review_ids)) == 1224, observed=len(set(review_ids)), expected=1224)
    report_disputes = report.get("counts", {}).get("reviewDisputes") if isinstance(report.get("counts"), dict) else None
    checks.add("pair_review.zero_disputes", disputes == 0 and report_disputes == 0, observed={"rows": disputes, "report": report_disputes}, expected={"rows": 0, "report": 0})
    checks.add("pair_review.report_status", report.get("status") == "PASS", observed=report.get("status"), expected="PASS")
    return {"pairs": len(grouped), "reviewRows": len(reviews), "disputes": disputes}


def shortcut_report(parsed: Mapping[str, Sequence[AuditRow]]) -> tuple[dict[str, Any], Checks]:
    train = parsed["TRAIN"]
    validation = parsed["VALIDATION"]
    baselines: dict[str, Any] = {}
    definitions: tuple[tuple[str, str, Callable[[AuditRow], Any]], ...] = (
        ("S0", "exact candidate model-text majority lookup", lambda row: row.candidate_model_text),
        ("S1", "semantic-ID majority lookup", lambda row: row.semantic_id),
        ("S2", "candidate type prior", lambda row: row.candidate_type),
        ("S4", "negative-evidence-presence prior", lambda row: row.negative_evidence_present),
    )
    for baseline_id, description, key in definitions:
        metrics, details = score_lookup(train, validation, key)
        baselines[baseline_id] = {"description": description, "validationMetrics": metrics, "training": details}
    length_rule = fit_length_threshold(train)
    validation_metrics = binary_metrics(
        [row.label for row in validation],
        [predict_length(length_rule, row.input_length) for row in validation],
    )
    baselines["S3"] = {
        "description": "optimal character-input-length threshold and direction selected on TRAIN only",
        "feature": "len(system content) + len(user content), Unicode code points",
        "training": {
            "selectionSplit": "TRAIN",
            "threshold": length_rule["threshold"],
            "direction": length_rule["direction"],
            "selectionMetrics": length_rule["trainMetrics"],
            "tieBreak": "accuracy, macro F1, smaller threshold, SUPPORTED_IF_LE",
        },
        "validationMetrics": validation_metrics,
    }
    ordered = {key: baselines[key] for key in ("S0", "S1", "S2", "S3", "S4")}
    checks = Checks()
    for baseline_id, value in ordered.items():
        accuracy = value["validationMetrics"]["accuracy"]
        limit = BASELINE_LIMITS[baseline_id]
        checks.add(f"shortcut.{baseline_id.lower()}.validation_accuracy", accuracy <= limit, observed=accuracy, expected=f"<={limit:.2f}")
    structural: dict[str, Any] = {}
    for baseline_id, description, key in (
        (
            "E0",
            "trusted-evidence type-multiset majority lookup",
            lambda row: row.evidence_type_signature,
        ),
        (
            "E1",
            "trusted-evidence count majority lookup",
            lambda row: row.evidence_count,
        ),
    ):
        metrics, details = score_lookup(train, validation, key)
        structural[baseline_id] = {
            "description": description,
            "validationMetrics": metrics,
            "training": details,
        }
        checks.add(
            f"shortcut.{baseline_id.lower()}.validation_accuracy",
            metrics["accuracy"] <= 0.60,
            observed=metrics["accuracy"],
            expected="<=0.60",
        )
    report = {
        "schemaVersion": "1",
        "reportId": "stackmate-phase-5-5-2-shortcut-baselines-v1",
        "status": "PASS" if checks.passed else "FAIL",
        "tiePolicy": "majority ties predict NOT_SUPPORTED",
        "selectionPolicy": "all fitted parameters use TRAIN only; VALIDATION is scoring only",
        "baselines": ordered,
        "structuralBaselines": structural,
        "checks": checks.values,
    }
    return report, checks


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-dir", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--ontology", type=Path, default=DEFAULT_ONTOLOGY)
    parser.add_argument("--ontology-review", type=Path, default=DEFAULT_ONTOLOGY_REVIEW)
    parser.add_argument("--pair-review-report", type=Path, default=DEFAULT_PAIR_REVIEW_REPORT)
    parser.add_argument(
        "--independent-review", type=Path, default=DEFAULT_INDEPENDENT_REVIEW
    )
    parser.add_argument("--dataset-report", type=Path, default=DEFAULT_DATASET_REPORT)
    parser.add_argument("--baseline-report", type=Path, default=DEFAULT_BASELINE_REPORT)
    return parser.parse_args()


def write_new(path: Path, value: Mapping[str, Any]) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as output:
        json.dump(value, output, ensure_ascii=False, sort_keys=True, indent=2)
        output.write("\n")


def run(args: argparse.Namespace) -> int:
    dataset_report_path = args.dataset_report.expanduser().resolve()
    baseline_report_path = args.baseline_report.expanduser().resolve()
    for path in (dataset_report_path, baseline_report_path):
        if path.exists():
            raise FileExistsError(f"CREATE_NEW refused existing report: {path.name}")
        if not path.parent.is_dir():
            raise AuditError(f"report parent does not exist: {path.parent.name}")

    dataset_dir = args.dataset_dir.expanduser().resolve()
    if not dataset_dir.is_dir():
        raise AuditError(f"dataset directory does not exist: {args.dataset_dir.name}")
    manifest_path = require_file(args.manifest)
    ontology_path = require_file(args.ontology)
    ontology_review_path = require_file(args.ontology_review)
    pair_review_report_path = require_file(args.pair_review_report)
    independent_review_path = require_file(args.independent_review)
    paths = {
        "families": require_file(dataset_dir / "families.jsonl"),
        "train": require_file(dataset_dir / "train.jsonl"),
        "validation": require_file(dataset_dir / "validation.jsonl"),
        "pairReviews": require_file(dataset_dir / "pair-reviews.jsonl"),
    }
    manifest = read_json(manifest_path)
    ontology = read_json(ontology_path)
    ontology_review = read_json(ontology_review_path)
    pair_review_report = read_json(pair_review_report_path)
    independent_review = read_json(independent_review_path)
    families = read_jsonl(paths["families"])
    raw_rows = {"TRAIN": read_jsonl(paths["train"]), "VALIDATION": read_jsonl(paths["validation"])}
    reviews = read_jsonl(paths["pairReviews"])

    gate_b = Checks()
    semantic = audit_ontology(ontology, ontology_review, ontology_path, gate_b)
    independent_summary = audit_independent_review(independent_review, gate_b)
    gate_c = Checks()
    audit_manifest(manifest, dataset_dir, ontology_path, pair_review_report_path, gate_c)
    parsed = parse_rows(raw_rows, gate_c)
    inventory = audit_dataset_content(parsed, families, semantic, manifest, gate_c)
    review_summary = audit_pair_reviews([*parsed["TRAIN"], *parsed["VALIDATION"]], reviews, pair_review_report, gate_c)
    baseline_report, gate_d = shortcut_report(parsed)
    gate_d.add(
        "ground_truth.independent_semantic_review",
        gate_b.passed,
        observed=independent_summary["status"],
        expected="PASS",
    )
    baseline_report["checks"] = gate_d.values
    baseline_report["status"] = "PASS" if gate_d.passed else "FAIL"
    overall_pass = gate_b.passed and gate_c.passed and gate_d.passed
    dataset_report = {
        "schemaVersion": "1",
        "reportId": "stackmate-phase-5-5-2-dataset-audit-v1",
        "status": "PASS" if overall_pass else "FAIL",
        "publicationMode": "CREATE_NEW",
        "methodology": {
            "modelWeightsLoaded": False,
            "externalPackagesUsed": False,
            "labelsReadFromAssistantForMetricsOnly": True,
            "modelInputDefinition": "canonical JSON object containing system and user content only",
            "normalizedModelInputDefinition": "Unicode NFKC, casefold, whitespace collapse",
        },
        "sources": {
            "dataset": "datasets/atomic-contrastive-v1",
            "manifest": "datasets/atomic-contrastive-v1/manifest.json",
            "ontology": "ontology/atomic-candidates-v1.json",
            "ontologyReview": "reports/phase-5-5-2-ontology-review.json",
            "pairReview": "reports/phase-5-5-2-pair-review.json",
            "independentSemanticReview": "reports/phase-5-5-2-independent-semantic-review.json",
        },
        "gateB": {"status": "PASS" if gate_b.passed else "FAIL", "checks": gate_b.values},
        "gateC": {"status": "PASS" if gate_c.passed else "FAIL", "checks": gate_c.values},
        "gateD": {"status": "PASS" if gate_d.passed else "FAIL", "checks": gate_d.values},
        "inventory": inventory,
        "pairReview": review_summary,
        "independentSemanticReview": independent_summary,
    }
    write_new(dataset_report_path, dataset_report)
    try:
        write_new(baseline_report_path, baseline_report)
    except Exception:
        # The preflight makes this reachable only through a publication race.
        dataset_report_path.unlink(missing_ok=True)
        raise
    print(canonical_json({"status": dataset_report["status"], "reportsCreated": 2}))
    return 0 if overall_pass else 2


def main() -> int:
    return run(arguments())


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AuditError, FileExistsError, OSError, ValueError) as error:
        print(f"audit error: {error}", file=sys.stderr)
        raise SystemExit(1)
