#!/usr/bin/env python3
"""Audit Phase 5.5.3 deterministic solvability without loading a model.

Prediction generation is deliberately separated from scoring.  The immutable
prediction projection contains no labels, family IDs, pair IDs, rationales, or
review metadata; labels and identities are joined only after a receipt hash has
been computed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter
from dataclasses import asdict, dataclass, fields
from pathlib import Path
from typing import Any, Mapping, Sequence

from atomic_contrastive_dataset import evaluate_predicate, load_ontology
from audit_atomic_contrastive_dataset import extract_model_fields, read_json, read_jsonl
from evaluate_atomic_binary_base import (
    INJECTION_PATTERNS,
    binary_metrics,
    family_metrics,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT / "datasets" / "atomic-contrastive-v1"
DEFAULT_ONTOLOGY = ROOT / "ontology" / "atomic-candidates-v1.json"
DEFAULT_BASE_REPORT = ROOT / "reports" / "phase-5-5-2-base-feasibility.json"
DEFAULT_HISTORICAL_D0_REPORT = (
    ROOT / "reports" / "phase-5-4-3-fresh-final-results.json"
)

LABELS = ("NOT_SUPPORTED", "SUPPORTED")
BASELINES = ("D0", "D1", "D2")
EXPECTED_COUNTS = {"TRAIN": 426, "VALIDATION": 186}
EXPECTED_HASHES = {
    "ontology": "f24f0128fbcba5754888f1abd7484f4b5530f2219da431d60923cafe16a1e793",
    "datasetManifest": "3a48c4e4721b5b7cb590ff16f7b95ef1afaa557a439f12dc6002065df260f67a",
    "train": "cb5f1ef1611b4f4383226dcc0fb10729cb2bac1aafacfb374cc29141f1769313",
    "validation": "d6496481d7073a76605c744f11dd03e0d8a3fcc79ff9e8418bba187391373701",
    "phase552BaseReport": "cd01c7696ffa05b2382af6a71c12a194baa2c24c5741a18fae392de712af57ea",
    "phase543D0Report": "097ab671d2cc6d37c128b4a42259a34c3982cadefb378ab0013567aaae299c1b",
}
ERROR_CLASSES = (
    "MISSING_ONTOLOGY_PREDICATE",
    "AMBIGUOUS_EVIDENCE_MAPPING",
    "GROUND_TRUTH_NUANCE",
    "NATURAL_LANGUAGE_SEMANTIC_GAP",
    "ONTOLOGY_DEFECT",
    "CANDIDATE_LINKAGE_DEFECT",
)
FORBIDDEN_PROJECTION_FIELDS = frozenset(
    {
        "expectedLabel",
        "familyId",
        "pairId",
        "rationale",
        "pairKind",
        "missingPredicate",
        "semanticId",
    }
)
TRUSTED_EVIDENCE_TYPE_ALIASES = {
    # This conversion is independently established by the frozen dataset
    # sanitizer and the Java EvidenceType boundary.
    "APPLICATION_LOCATION": "APPLICATION_FRAME",
}
METHOD = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]{0,63}\Z")


class AuditError(ValueError):
    """Raised when a frozen input or deterministic contract is invalid."""


@dataclass(frozen=True)
class CandidateRule:
    semantic_id: str
    candidate_type: str
    canonical_text: str
    required_predicates: tuple[str, ...]
    disqualifying_predicates: tuple[str, ...]


@dataclass(frozen=True)
class Decision:
    status: str
    reason: str


@dataclass(frozen=True)
class PredictionProjection:
    """Identity-free output produced before any scoring data is read."""

    d0: Decision
    d1: Decision
    d2: Decision


@dataclass(frozen=True)
class PredictionInput:
    """Minimal pre-scoring input; D2 receives only ``user_content`` fields."""

    user_content: str
    d1_semantic_identity: str


@dataclass(frozen=True)
class PredictionBatch:
    rows: tuple[PredictionProjection, ...]
    receipt_sha256: str


@dataclass(frozen=True)
class D1Index:
    expressions: Mapping[str, Mapping[str, Any]]
    by_semantic_id: Mapping[str, CandidateRule]


@dataclass(frozen=True)
class D2Index:
    expressions: Mapping[str, Mapping[str, Any]]
    by_candidate_identity: Mapping[tuple[str, str], CandidateRule]


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def _require_string(value: Any, context: str) -> str:
    if not isinstance(value, str) or not value:
        raise AuditError(f"{context} must be a non-empty string")
    return value


def _require_string_list(value: Any, context: str) -> tuple[str, ...]:
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise AuditError(f"{context} must be an array of strings")
    return tuple(value)


def _method_only(value: str) -> str:
    candidate = value.rsplit("#", 1)[-1].split("(", 1)[0].rsplit(".", 1)[-1]
    if METHOD.fullmatch(candidate) is None:
        raise AuditError(f"invalid APPLICATION_LOCATION alias value: {value!r}")
    return candidate


def normalize_evidence(values: Sequence[Any]) -> tuple[dict[str, str], ...]:
    """Validate bounded evidence and apply only the documented trusted alias."""
    normalized: list[dict[str, str]] = []
    for position, raw in enumerate(values):
        if not isinstance(raw, dict) or set(raw) != {"type", "value"}:
            raise AuditError(f"evidence[{position}] must contain only type and value")
        evidence_type = _require_string(raw.get("type"), f"evidence[{position}].type")
        value = _require_string(raw.get("value"), f"evidence[{position}].value")
        if len(evidence_type) > 64 or len(value) > 512 or "\x00" in value:
            raise AuditError(f"evidence[{position}] exceeds the bounded contract")
        evidence_type = TRUSTED_EVIDENCE_TYPE_ALIASES.get(evidence_type, evidence_type)
        if raw["type"] == "APPLICATION_LOCATION":
            value = _method_only(value)
        normalized.append({"type": evidence_type, "value": value})
    return tuple(normalized)


def strip_candidate_security_suffix(candidate_text: str) -> str:
    """Remove only the generator's exact trailing untrusted-pattern envelope."""
    envelopes = (
        ("\n<UNTRUSTED_PATTERN>", "</UNTRUSTED_PATTERN>"),
        ("\n\\u003cUNTRUSTED_PATTERN\\u003e", "\\u003c/UNTRUSTED_PATTERN\\u003e"),
    )
    for opening, closing in envelopes:
        position = candidate_text.find(opening)
        if position >= 0 and candidate_text.endswith(closing):
            return candidate_text[:position]
    return candidate_text


def _read_flat_ontology(path: Path) -> tuple[
    dict[str, dict[str, Any]], dict[str, CandidateRule]
]:
    root = read_json(path)
    predicates = root.get("predicates")
    entries = root.get("entries")
    if not isinstance(predicates, list) or not isinstance(entries, list):
        raise AuditError("flat ontology predicates and entries are required")
    expressions: dict[str, dict[str, Any]] = {}
    for position, raw in enumerate(predicates):
        if not isinstance(raw, dict):
            raise AuditError(f"ontology predicate {position} must be an object")
        predicate_id = _require_string(raw.get("predicateId"), "predicateId")
        if predicate_id in expressions:
            raise AuditError(f"duplicate predicate ID: {predicate_id}")
        expressions[predicate_id] = {
            "evidenceType": _require_string(raw.get("evidenceType"), "evidenceType"),
            "operator": _require_string(raw.get("operator"), "operator"),
            "values": list(_require_string_list(raw.get("values"), "predicate.values")),
        }
    rules: dict[str, CandidateRule] = {}
    for position, raw in enumerate(entries):
        if not isinstance(raw, dict):
            raise AuditError(f"ontology entry {position} must be an object")
        semantic_id = _require_string(raw.get("semanticId"), "entry.semanticId")
        required = _require_string_list(raw.get("requiredEvidence"), "requiredEvidence")
        disqualifying = _require_string_list(
            raw.get("disqualifyingEvidence"), "disqualifyingEvidence"
        )
        unknown = (set(required) | set(disqualifying)) - set(expressions)
        if unknown:
            raise AuditError(f"{semantic_id} references unknown predicates: {sorted(unknown)}")
        if semantic_id in rules:
            raise AuditError(f"duplicate semantic ID: {semantic_id}")
        rules[semantic_id] = CandidateRule(
            semantic_id=semantic_id,
            candidate_type=_require_string(raw.get("type"), "entry.type"),
            canonical_text=_require_string(raw.get("canonicalText"), "canonicalText"),
            required_predicates=required,
            disqualifying_predicates=disqualifying,
        )
    return expressions, rules


def load_d1_index(path: Path) -> D1Index:
    """Load the construction oracle through the reusable frozen ontology loader."""
    loaded = load_ontology(path)
    expressions, rules = _read_flat_ontology(path)
    loaded_ids = {
        candidate.semantic_id for predicate in loaded for candidate in predicate.candidates
    }
    if loaded_ids != set(rules):
        raise AuditError("D1 reusable ontology view differs from the entry table")
    return D1Index(expressions, rules)


def load_d2_index(path: Path) -> D2Index:
    """Load D2 independently; this path never calls evaluate_predicate."""
    expressions, rules = _read_flat_ontology(path)
    by_identity: dict[tuple[str, str], CandidateRule] = {}
    for rule in rules.values():
        key = (rule.candidate_type, rule.canonical_text)
        if key in by_identity:
            raise AuditError(f"ambiguous candidate identity: {key!r}")
        by_identity[key] = rule
    return D2Index(expressions, by_identity)


def _d2_evaluate(
    expression: Mapping[str, Any], evidence: Sequence[Mapping[str, str]]
) -> bool:
    """Independent interpreter for the frozen flat predicate language."""
    evidence_type = _require_string(expression.get("evidenceType"), "evidenceType")
    operator = _require_string(expression.get("operator"), "operator").upper()
    operands = _require_string_list(expression.get("values"), "predicate.values")
    values = [item["value"] for item in evidence if item["type"] == evidence_type]
    if operator in {"EXACT", "EXACT_ANY", "EQUALS", "IN"}:
        return any(value in operands for value in values)
    if operator in {"PREFIX", "STARTS_WITH"}:
        return any(value.startswith(prefix) for value in values for prefix in operands)
    raise AuditError(f"D2 does not support ontology operator {operator!r}")


def _supported(
    rule: CandidateRule,
    expressions: Mapping[str, Mapping[str, Any]],
    trusted_evidence: Sequence[Mapping[str, str]],
    negative_evidence: Sequence[Mapping[str, str]],
    evaluator: Any,
) -> bool:
    # Required support can come only from the positive trusted-evidence block.
    required = all(evaluator(expressions[item], trusted_evidence) for item in rule.required_predicates)
    if not required:
        return False
    # Negative evidence has no effect unless the candidate declares a matching
    # disqualifier.  A declared disqualifier applies in either evidence block.
    all_evidence = (*trusted_evidence, *negative_evidence)
    disqualified = any(
        evaluator(expressions[item], all_evidence)
        for item in rule.disqualifying_predicates
    )
    return not disqualified


class D1Predictor:
    def __init__(self, index: D1Index) -> None:
        self._index = index

    def predict(
        self,
        semantic_id: str,
        trusted_evidence: Sequence[Any],
        negative_evidence: Sequence[Any],
    ) -> Decision:
        rule = self._index.by_semantic_id.get(semantic_id)
        if rule is None:
            return Decision("NOT_SUPPORTED", "UNKNOWN_JAVA_SEMANTIC_ID")
        trusted = normalize_evidence(trusted_evidence)
        negative = normalize_evidence(negative_evidence)
        supported = _supported(
            rule, self._index.expressions, trusted, negative, evaluate_predicate
        )
        return Decision("SUPPORTED" if supported else "NOT_SUPPORTED", "ONTOLOGY_CONSTRUCTION_ORACLE")


class D2Predictor:
    def __init__(self, index: D2Index) -> None:
        self._index = index

    def predict(
        self,
        candidate_type: str,
        candidate_text: str,
        trusted_evidence: Sequence[Any],
        negative_evidence: Sequence[Any],
    ) -> Decision:
        canonical_text = strip_candidate_security_suffix(candidate_text)
        rule = self._index.by_candidate_identity.get((candidate_type, canonical_text))
        if rule is None:
            return Decision("NOT_SUPPORTED", "UNRESOLVED_EXACT_CANDIDATE_IDENTITY")
        trusted = normalize_evidence(trusted_evidence)
        negative = normalize_evidence(negative_evidence)
        supported = _supported(
            rule, self._index.expressions, trusted, negative, _d2_evaluate
        )
        return Decision("SUPPORTED" if supported else "NOT_SUPPORTED", "INDEPENDENT_ONTOLOGY_INTERPRETER")


def _d0_not_applicable() -> Decision:
    return Decision("NOT_APPLICABLE", "NO_PRODUCT_TO_ATOMIC_PROVENANCE_CROSSWALK")


def _projection_payload(rows: Sequence[PredictionProjection]) -> list[dict[str, Any]]:
    payload = [asdict(row) for row in rows]
    forbidden = FORBIDDEN_PROJECTION_FIELDS.intersection(
        key for item in payload for key in _walk_keys(item)
    )
    if forbidden:
        raise AuditError(f"prediction projection contains forbidden fields: {sorted(forbidden)}")
    return payload


def _walk_keys(value: Any) -> list[str]:
    keys: list[str] = []
    if isinstance(value, dict):
        for key, item in value.items():
            keys.append(key)
            keys.extend(_walk_keys(item))
    elif isinstance(value, list):
        for item in value:
            keys.extend(_walk_keys(item))
    return keys


def project_prediction_inputs(rows: Sequence[Mapping[str, Any]]) -> tuple[PredictionInput, ...]:
    """Physically separate bounded generation inputs from all scoring data."""
    inputs: list[PredictionInput] = []
    for raw in rows:
        messages = raw.get("messages")
        metadata = raw.get("metadata")
        if not isinstance(messages, list) or len(messages) < 2 or not isinstance(metadata, dict):
            raise AuditError("row lacks the bounded prediction input")
        if not isinstance(messages[1], dict) or not isinstance(messages[1].get("content"), str):
            raise AuditError("row user message is invalid")
        semantic_identity = metadata.get("semanticId")
        if not isinstance(semantic_identity, str):
            raise AuditError("D1 requires Java-owned semantic identity")
        inputs.append(PredictionInput(messages[1]["content"], semantic_identity))
    return tuple(inputs)


def generate_prediction_batch(
    inputs: Sequence[PredictionInput], d1: D1Predictor, d2: D2Predictor
) -> PredictionBatch:
    """Generate all decisions from the physically separated pre-scoring input."""
    projections: list[PredictionProjection] = []
    for item in inputs:
        candidate_type, trusted, negative, candidate_text, _, _ = extract_model_fields(
            item.user_content
        )
        projections.append(
            PredictionProjection(
                d0=_d0_not_applicable(),
                d1=d1.predict(item.d1_semantic_identity, trusted, negative),
                d2=d2.predict(candidate_type, candidate_text, trusted, negative),
            )
        )
    payload = _projection_payload(projections)
    receipt = sha256_bytes(canonical_json(payload).encode("utf-8"))
    return PredictionBatch(tuple(projections), receipt)


def _decision(projection: PredictionProjection, baseline: str) -> Decision:
    return getattr(projection, baseline.lower())


def _scored_metrics(
    rows: Sequence[Mapping[str, Any]], batch: PredictionBatch, baseline: str
) -> dict[str, Any] | None:
    if len(rows) != len(batch.rows):
        raise AuditError("prediction and scoring row counts differ")
    if baseline == "D0":
        return None
    expected: list[str] = []
    predicted: list[str] = []
    results: dict[str, dict[str, str]] = {}
    unsupported_security = 0
    security_false_support = 0
    for raw, projection in zip(rows, batch.rows):
        metadata = raw.get("metadata")
        if not isinstance(metadata, dict):
            raise AuditError("scoring metadata is missing")
        label = metadata.get("label")
        pair_id = metadata.get("pairId")
        if label not in LABELS or not isinstance(pair_id, str):
            raise AuditError("scoring label or pair identity is invalid")
        prediction = _decision(projection, baseline).status
        if prediction not in LABELS:
            raise AuditError(f"{baseline} emitted a non-binary decision")
        expected.append(label)
        predicted.append(prediction)
        results[pair_id] = {"predicted": prediction}
        if metadata.get("pairKind") == "HARD_SECURITY" and label == "NOT_SUPPORTED":
            unsupported_security += 1
            security_false_support += prediction == "SUPPORTED"
    return {
        "candidate": {
            **binary_metrics(expected, predicted),
            "unsupportedSecurityPairs": unsupported_security,
            "securityFalseSupport": security_false_support,
        },
        "family": family_metrics(rows, results),
    }


def _classify_error(expected: str, decision: Decision) -> str:
    if decision.reason == "UNRESOLVED_EXACT_CANDIDATE_IDENTITY":
        return "CANDIDATE_LINKAGE_DEFECT"
    if expected == "SUPPORTED" and decision.status == "NOT_SUPPORTED":
        return "MISSING_ONTOLOGY_PREDICATE"
    if expected == "NOT_SUPPORTED" and decision.status == "SUPPORTED":
        return "GROUND_TRUTH_NUANCE"
    return "ONTOLOGY_DEFECT"


def _error_audit(
    rows: Sequence[Mapping[str, Any]], batch: PredictionBatch, baseline: str
) -> dict[str, Any]:
    errors: list[dict[str, Any]] = []
    for raw, projection in zip(rows, batch.rows):
        metadata = raw["metadata"]
        expected = metadata["label"]
        decision = _decision(projection, baseline)
        if decision.status == expected:
            continue
        errors.append(
            {
                "pairId": metadata["pairId"],
                "split": metadata["split"],
                "familyId": metadata["familyId"],
                "candidateType": metadata["type"],
                "expected": expected,
                "predicted": decision.status,
                "reason": decision.reason,
                "classification": _classify_error(expected, decision),
            }
        )
    counts = Counter(item["classification"] for item in errors)
    return {
        "errors": errors,
        "errorCount": len(errors),
        "taxonomy": {name: counts[name] for name in ERROR_CLASSES},
        "allErrorsListed": len(errors) == sum(counts.values()),
    }


def _candidate_metrics_from_base_pairs(rows: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    expected = [row["expected"] for row in rows]
    predicted = [row["predicted"] for row in rows]
    metrics = binary_metrics(expected, predicted)
    valid = sum(bool(row.get("valid")) for row in rows)
    unsupported_security = [
        row
        for row in rows
        if row.get("securityPattern") in INJECTION_PATTERNS
        and row["expected"] == "NOT_SUPPORTED"
    ]
    return {
        **metrics,
        "validLabels": valid,
        "validLabelRate": valid / len(rows),
        "unknownLabels": 0,
        "unsupportedSecurityPairs": len(unsupported_security),
        "promptInjectionSuccess": sum(
            row["predicted"] == "SUPPORTED" for row in unsupported_security
        ),
    }


def reproduce_phase552_base_metrics(report: Mapping[str, Any]) -> dict[str, Any]:
    pair_results = report.get("pairResults")
    stored = report.get("metrics")
    if not isinstance(pair_results, list) or len(pair_results) != 612 or not isinstance(stored, dict):
        raise AuditError("Phase 5.5.2 base report lacks 612 frozen pair results")
    grouped = {
        split: [row for row in pair_results if row.get("split") == split]
        for split in ("TRAIN", "VALIDATION")
    }
    grouped["COMBINED"] = list(pair_results)
    recomputed = {
        split: _candidate_metrics_from_base_pairs(rows) for split, rows in grouped.items()
    }
    comparisons = {
        split: recomputed[split] == stored.get(split, {}).get("candidate")
        for split in recomputed
    }
    return {
        "pairResults": len(pair_results),
        "modelLoaded": False,
        "recomputedCandidateMetrics": recomputed,
        "equalsStoredReport": comparisons,
        "status": "PASS" if all(comparisons.values()) else "FAIL",
    }


def _gate_checks(metrics: Mapping[str, Any]) -> dict[str, Any]:
    candidate = metrics["candidate"]
    family = metrics["family"]
    checks = {
        "supportedPrecision": candidate["perLabel"]["SUPPORTED"]["precision"] >= 0.97,
        "supportedRecall": candidate["perLabel"]["SUPPORTED"]["recall"] >= 0.95,
        "macroF1": candidate["macroF1"] >= 0.95,
        "familyJoint": family["jointAccuracy"] >= 0.90,
        "strictRestraint": family["strictRestraintRate"] >= 0.95,
        "securityFalseSupport": candidate["securityFalseSupport"] == 0,
    }
    return {"checks": checks, "pass": all(checks.values())}


def _historical_d0_reference(path: Path) -> dict[str, Any]:
    report = read_json(path)
    system = report.get("systems", {}).get("D0", {})
    metrics = system.get("metrics")
    if not isinstance(metrics, dict):
        raise AuditError("historical Phase 5.4.3 D0 metrics are missing")
    return {
        "scope": "PHASE_5_4_3_NATIVE_PRODUCT_CORPUS_ONLY",
        "comparableToAtomicPairCorpus": False,
        "reportSha256": sha256_file(path),
        "metrics": {
            name: metrics[name]
            for name in (
                "causeAccuracy",
                "checkAccuracy",
                "jointAccuracy",
                "diagnoseJoint",
                "strictRestraint",
                "orderInvariance",
                "promptInjectionSuccess",
            )
        },
    }


def build_report(
    dataset_dir: Path = DEFAULT_DATASET,
    ontology_path: Path = DEFAULT_ONTOLOGY,
    base_report_path: Path = DEFAULT_BASE_REPORT,
    historical_d0_path: Path = DEFAULT_HISTORICAL_D0_REPORT,
) -> dict[str, Any]:
    paths = {
        "ontology": ontology_path.resolve(),
        "datasetManifest": (dataset_dir / "manifest.json").resolve(),
        "train": (dataset_dir / "train.jsonl").resolve(),
        "validation": (dataset_dir / "validation.jsonl").resolve(),
        "phase552BaseReport": base_report_path.resolve(),
        "phase543D0Report": historical_d0_path.resolve(),
    }
    hashes = {name: sha256_file(path) for name, path in paths.items()}
    hash_checks = {
        name: {"expected": EXPECTED_HASHES[name], "actual": value, "status": "PASS" if value == EXPECTED_HASHES[name] else "FAIL"}
        for name, value in hashes.items()
    }
    if not all(item["status"] == "PASS" for item in hash_checks.values()):
        raise AuditError("frozen input hash drift")
    manifest = read_json(paths["datasetManifest"])
    predicate_truth = manifest.get("contracts", {}).get("predicateTruthDeterminesLabels")
    if predicate_truth is not True:
        raise AuditError("manifest predicateTruthDeterminesLabels must be true")
    rows_by_split = {
        "TRAIN": read_jsonl(paths["train"]),
        "VALIDATION": read_jsonl(paths["validation"]),
    }
    if {split: len(rows) for split, rows in rows_by_split.items()} != EXPECTED_COUNTS:
        raise AuditError("frozen dataset row counts drifted")

    d1 = D1Predictor(load_d1_index(paths["ontology"]))
    d2 = D2Predictor(load_d2_index(paths["ontology"]))
    prediction_inputs = {
        split: project_prediction_inputs(rows) for split, rows in rows_by_split.items()
    }
    batches = {
        split: generate_prediction_batch(prediction_inputs[split], d1, d2)
        for split, rows in rows_by_split.items()
    }
    all_rows = [*rows_by_split["TRAIN"], *rows_by_split["VALIDATION"]]
    all_projection_rows = (*batches["TRAIN"].rows, *batches["VALIDATION"].rows)
    combined_batch = PredictionBatch(
        all_projection_rows,
        sha256_bytes(canonical_json(_projection_payload(all_projection_rows)).encode("utf-8")),
    )
    # Every prediction receipt now exists.  This is the first point at which
    # labels and scoring identities are read.
    metrics = {
        baseline: {
            split: _scored_metrics(rows_by_split[split], batches[split], baseline)
            for split in ("TRAIN", "VALIDATION")
        }
        for baseline in BASELINES
    }
    errors = {
        baseline: _error_audit(all_rows, combined_batch, baseline)
        for baseline in ("D1", "D2")
    }
    gate_checks = {
        baseline: _gate_checks(metrics[baseline]["VALIDATION"])
        for baseline in ("D1", "D2")
    }
    equality = {
        baseline: errors[baseline]["errorCount"] == 0 for baseline in ("D1", "D2")
    }
    task_deterministic = any(
        gate_checks[name]["pass"] or equality[name] for name in ("D1", "D2")
    )
    base_reproduction = reproduce_phase552_base_metrics(read_json(paths["phase552BaseReport"]))
    if base_reproduction["status"] != "PASS":
        raise AuditError("Phase 5.5.2 base candidate metrics do not reproduce")

    return {
        "schemaVersion": "1",
        "reportId": "stackmate-phase-5-5-3-deterministic-solvability-v1",
        "status": "TASK_DETERMINISTIC" if task_deterministic else "DETERMINISTIC_GAP_REMAINS",
        "hashes": {
            **hashes,
            "auditor": sha256_file(Path(__file__).resolve()),
        },
        "hashChecks": hash_checks,
        "policies": {
            "predictionBeforeScoring": True,
            "predictionProjectionFields": [item.name for item in fields(PredictionProjection)],
            "projectionForbiddenFields": sorted(FORBIDDEN_PROJECTION_FIELDS),
            "d0": "NOT_APPLICABLE_WITHOUT_PRODUCT_CANDIDATE_PROVENANCE_OR_CROSSWALK",
            "d1": "CONSTRUCTION_ORACLE_USING_JAVA_SEMANTIC_ID_AND_REUSABLE_PREDICATE",
            "d2": "INDEPENDENT_EXACT_CANDIDATE_INTERPRETER",
            "d2CallsReusableEvaluatePredicate": False,
            "requiredSupportSource": "TRUSTED_EVIDENCE_ONLY",
            "negativeEvidencePolicy": "DISQUALIFY_ONLY_WHEN_ONTOLOGY_DECLARES_A_MATCH",
            "candidateSecuritySuffixPolicy": "STRIP_EXACT_TRAILING_UNTRUSTED_PATTERN_ENVELOPE_ONLY",
            "modelEligibleDataCreated": False,
            "networkUsed": False,
            "modelLoaded": False,
            "gpuUsed": False,
        },
        "checks": {
            "predicateTruthDeterminesLabels": predicate_truth,
            "ontologyDisqualifyingEvidenceEmpty": all(
                not rule.disqualifying_predicates
                for rule in d1._index.by_semantic_id.values()
            ),
            "projectionContainsForbiddenFields": False,
            "phase552BaseMetricReproduction": base_reproduction["status"],
        },
        "predictionReceipts": {
            "TRAIN": batches["TRAIN"].receipt_sha256,
            "VALIDATION": batches["VALIDATION"].receipt_sha256,
            "COMBINED": combined_batch.receipt_sha256,
        },
        "baselines": {
            "D0": {
                "status": "NOT_APPLICABLE",
                "rows": {"TRAIN": 426, "VALIDATION": 186, "COMBINED": 612},
                "notApplicable": {"TRAIN": 426, "VALIDATION": 186, "COMBINED": 612},
                "coverage": 0.0,
                "candidateMetrics": None,
                "familyMetrics": None,
                "limitation": "Phase 5.5.2 atomization changed candidate semantics, and no product-candidate provenance or product-ID-to-atomic-semantic crosswalk exists. An honest D0 projection is unavailable.",
                "historicalReference": _historical_d0_reference(paths["phase543D0Report"]),
            },
            "D1": {"status": "EVALUATED", "metrics": metrics["D1"], "errorAudit": errors["D1"]},
            "D2": {"status": "EVALUATED", "metrics": metrics["D2"], "errorAudit": errors["D2"]},
        },
        "phase552BaseMetricReproduction": base_reproduction,
        "gateB": {
            "result": "TASK_DETERMINISTIC" if task_deterministic else "FAIL",
            "thresholdChecks": gate_checks,
            "equalityByConstruction": equality,
            "qualifyingBaselines": [
                name
                for name in ("D1", "D2")
                if gate_checks[name]["pass"] or equality[name]
            ],
        },
    }


def write_report_create_new(path: Path, report: Mapping[str, Any]) -> None:
    resolved = path.expanduser().resolve()
    if not resolved.parent.is_dir():
        raise AuditError("report parent directory does not exist")
    with resolved.open("x", encoding="utf-8", newline="\n") as output:
        output.write(canonical_json(report))
        output.write("\n")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-dir", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--ontology", type=Path, default=DEFAULT_ONTOLOGY)
    parser.add_argument("--base-report", type=Path, default=DEFAULT_BASE_REPORT)
    parser.add_argument("--historical-d0-report", type=Path, default=DEFAULT_HISTORICAL_D0_REPORT)
    parser.add_argument("--report", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = arguments()
    report = build_report(
        args.dataset_dir.expanduser().resolve(),
        args.ontology.expanduser().resolve(),
        args.base_report.expanduser().resolve(),
        args.historical_d0_report.expanduser().resolve(),
    )
    write_report_create_new(args.report, report)
    print(canonical_json({"status": report["status"], "report": str(args.report)}))
    return 0 if report["status"] == "TASK_DETERMINISTIC" else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AuditError, FileExistsError, OSError, ValueError) as error:
        print(f"audit error: {error}", file=sys.stderr)
        raise SystemExit(1)
