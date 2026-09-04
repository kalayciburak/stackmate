#!/usr/bin/env python3
"""Audit the native corpus for conservative binary evidence-support pairs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

import audit_pairwise_source as source


ALIGNMENT = Path(__file__).resolve().parents[1]
DEFAULT_TRAIN = source.DEFAULT_TRAIN
DEFAULT_VALIDATION = source.DEFAULT_VALIDATION
DEFAULT_DATASET_MANIFEST = source.DEFAULT_DATASET_MANIFEST
DEFAULT_TRI_CLASS_AUDIT = (
    ALIGNMENT / "reports" / "phase-5-5-pairwise-source-audit.json"
)
DEFAULT_REPORT = ALIGNMENT / "reports" / "phase-5-5-1-binary-source-audit.json"

LABELS = ("SUPPORTED", "NOT_SUPPORTED")
REASONS = (
    "MULTI_TARGET_CHECK",
    "PROBE_PLACEHOLDER",
    "POSITIVE_EVIDENCE_UNSELECTED",
    "HEDGED_CAUSE",
    "CONFLICTING_MODEL_INPUT_LABEL",
)
POSITIVE_EVIDENCE_UNSELECTED_IDS = frozenset(
    {
        "cause.database.driver-error",
        "cause.database.data-exception",
        "cause.java.invalid-state",
        "cause.infrastructure.socket-timeout",
        "cause.web.json-stream-read",
        "cause.web.request-body-unreadable",
    }
)
EXPECTED = {
    "TRAIN": {
        "rawFamilies": 70,
        "rawPairs": 206,
        "rawLabels": {"SUPPORTED": 102, "NOT_SUPPORTED": 104},
        "exclusions": {
            "MULTI_TARGET_CHECK": 58,
            "PROBE_PLACEHOLDER": 12,
            "POSITIVE_EVIDENCE_UNSELECTED": 25,
            "HEDGED_CAUSE": 7,
            "CONFLICTING_MODEL_INPUT_LABEL": 2,
        },
        "representedFamilies": 64,
        "preConflictFamilies": 65,
        "preConflictPairs": 104,
        "preConflictLabels": {"SUPPORTED": 58, "NOT_SUPPORTED": 46},
        "retainedPairs": 102,
        "retainedLabels": {"SUPPORTED": 57, "NOT_SUPPORTED": 45},
        "excludedPairs": 104,
    },
    "VALIDATION": {
        "rawFamilies": 20,
        "rawPairs": 54,
        "rawLabels": {"SUPPORTED": 18, "NOT_SUPPORTED": 36},
        "exclusions": {
            "MULTI_TARGET_CHECK": 10,
            "PROBE_PLACEHOLDER": 4,
            "POSITIVE_EVIDENCE_UNSELECTED": 5,
            "HEDGED_CAUSE": 1,
            "CONFLICTING_MODEL_INPUT_LABEL": 0,
        },
        "representedFamilies": 19,
        "preConflictFamilies": 19,
        "preConflictPairs": 34,
        "preConflictLabels": {"SUPPORTED": 12, "NOT_SUPPORTED": 22},
        "retainedPairs": 34,
        "retainedLabels": {"SUPPORTED": 12, "NOT_SUPPORTED": 22},
        "excludedPairs": 20,
    },
}
WORD_OR = re.compile(r"\bor\b", re.IGNORECASE)
WORD_MAY = re.compile(r"\bmay\b", re.IGNORECASE)
WORD = re.compile(r"\S+")

SECURITY_PATTERNS = {
    "RETURN_LABEL": re.compile(
        r"\b(?:return|respond|output)\b.{0,32}\b(?:0|1|supported|not_supported|label)\b",
        re.IGNORECASE,
    ),
    "SYSTEM_IMITATION": re.compile(
        r"\b(?:system prompt|system message|you are (?:chatgpt|stackmate|an? assistant))\b",
        re.IGNORECASE,
    ),
    "HIDDEN_REASONING": re.compile(
        r"\b(?:hidden reasoning|chain[- ]of[- ]thought|private reasoning)\b",
        re.IGNORECASE,
    ),
    "LABEL_BAIT": re.compile(
        r"\b(?:label (?:is|as)|classify as|answer (?:is|with))\b",
        re.IGNORECASE,
    ),
    "BOTH_LABEL_DIGITS": re.compile(
        r"(?s)(?=.*\b0\b)(?=.*\b1\b)", re.IGNORECASE
    ),
    "JSON_MARKDOWN_DELIMITER": re.compile(r"```|~~~|\{\s*\"|\[\s*\{"),
    "FAKE_CLOSING_TAG": re.compile(r"</[a-z][^>]*>", re.IGNORECASE),
    "EXPECTED_ANSWER": re.compile(
        r"\b(?:expected|correct) (?:answer|label)\b", re.IGNORECASE
    ),
    "ONE_BASED": re.compile(r"\b(?:one[- ]based|1[- ]based|index (?:one|1))\b", re.IGNORECASE),
    "SECRET_MARKER": re.compile(
        r"benchmark-secret-do-not-leak|phase5-ultra-secret|abc\.def\.ghi|"
        r"eyJhbGciOiJIUzI1NiJ9",
        re.IGNORECASE,
    ),
    "PLAUSIBLE_UNSUPPORTED_FRAMEWORK_CAUSE": re.compile(
        r"\b(?:unsupported|plausible)\b.{0,80}\b"
        r"(?:spring|hibernate|jdbc|jackson|reactor|webflux)\b",
        re.IGNORECASE,
    ),
    "UNICODE_HOMOGLYPH": re.compile("[\u0370-\u03ff\u0400-\u052f]"),
    "ZERO_WIDTH_OR_BIDI": re.compile(
        "[\u200b\u200c\u200d\u2060\ufeff\u202a-\u202e\u2066-\u2069]"
    ),
}


def exclusion_reasons(
    kind: str, candidate: dict[str, Any], selected: bool
) -> tuple[str, ...]:
    """Return every deterministic exclusion reason that applies to a pair."""
    candidate_id = source.require_string(candidate.get("id"), "candidate.id")
    text = source.require_string(candidate.get("text"), "candidate.text")
    reasons: list[str] = []
    if kind == "check" and ("," in text or WORD_OR.search(text)):
        reasons.append("MULTI_TARGET_CHECK")
    if "probe" in candidate_id.casefold() or "controlled probe branch" in text.casefold():
        reasons.append("PROBE_PLACEHOLDER")
    if (
        kind == "cause"
        and not selected
        and candidate_id in POSITIVE_EVIDENCE_UNSELECTED_IDS
    ):
        reasons.append("POSITIVE_EVIDENCE_UNSELECTED")
    if kind == "cause" and WORD_MAY.search(text):
        reasons.append("HEDGED_CAUSE")
    return tuple(reasons)


def percentile(values: list[int], percent: int) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, (len(ordered) * percent + 99) // 100 - 1)
    return ordered[index]


def distribution(values: Iterable[int]) -> dict[str, int | float]:
    ordered = sorted(values)
    if not ordered:
        return {"count": 0, "minimum": 0, "median": 0, "p90": 0, "maximum": 0, "mean": 0.0}
    middle = len(ordered) // 2
    median = (
        ordered[middle]
        if len(ordered) % 2
        else (ordered[middle - 1] + ordered[middle]) / 2
    )
    return {
        "count": len(ordered),
        "minimum": ordered[0],
        "median": median,
        "p90": percentile(ordered, 90),
        "maximum": ordered[-1],
        "mean": round(sum(ordered) / len(ordered), 3),
    }


def evidence_semantic(
    family: dict[str, Any], candidate: dict[str, Any]
) -> list[dict[str, Any]]:
    return [
        {"type": family["evidenceById"][item].get("type"),
         "value": family["evidenceById"][item].get("value")}
        for item in candidate["evidenceIds"]
    ]


def pair_entry(
    family: dict[str, Any], kind: str, candidate: dict[str, Any]
) -> dict[str, Any]:
    candidate_id = candidate["id"]
    selected = candidate_id in family["kinds"][kind]["selected"]
    evidence = evidence_semantic(family, candidate)
    throwable_topology = [
        {
            "depth": item.get("depth"),
            "type": item.get("type"),
            "wrapper": item.get("wrapper"),
        }
        for item in source.require_list(
            family["row"].get("observedExceptionChain"),
            "row.observedExceptionChain",
        )
    ]
    pair = source.pair_record(family, kind, candidate)
    semantic = {"kind": kind, "candidate": candidate["text"], "evidence": evidence}
    evidence_characters = sum(
        len(str(item.get("type", ""))) + len(str(item.get("value", "")))
        for item in evidence
    )
    return {
        "familyId": family["familyId"],
        "category": source.require_string(family["row"].get("category"), "row.category"),
        "kind": kind,
        "label": "SUPPORTED" if selected else "NOT_SUPPORTED",
        "reasons": exclusion_reasons(kind, candidate, selected),
        "rawPairSignature": pair["rawPairSignature"],
        "normalizedPairSignature": pair["normalizedPairSignature"],
        "semanticPairSignature": source.normalized_digest(semantic),
        "modelInputSignature": source.digest(semantic),
        "candidateSignature": source.normalized_digest(candidate["text"]),
        "evidenceSignature": source.normalized_digest(evidence),
        "throwableTopologySignature": source.normalized_digest(throwable_topology),
        "candidateCharacters": len(candidate["text"]),
        "candidateWords": len(WORD.findall(candidate["text"])),
        "evidenceItems": len(evidence),
        "evidenceCharacters": evidence_characters,
        "securityText": "\n".join(
            [
                candidate["text"],
                *(
                    f"{item.get('type', '')}\n{item.get('value', '')}"
                    for item in evidence
                ),
            ]
        ),
        "candidate": candidate,
    }


def collect_pairs(
    families: dict[str, dict[int, dict[str, Any]]]
) -> list[dict[str, Any]]:
    pairs: list[dict[str, Any]] = []
    for family_id in sorted(families):
        family = families[family_id][0]
        for kind in ("cause", "check"):
            for candidate in family["kinds"][kind]["candidates"]:
                pairs.append(pair_entry(family, kind, candidate))
    apply_conflicting_model_input_exclusion(pairs)
    return pairs


def apply_conflicting_model_input_exclusion(
    pairs: list[dict[str, Any]],
) -> None:
    """Exclude every pre-screened row of an exact input with both labels."""
    pre_conflict = [pair for pair in pairs if not pair["reasons"]]
    labels: dict[str, set[str]] = defaultdict(set)
    for pair in pre_conflict:
        labels[pair["modelInputSignature"]].add(pair["label"])
    conflicting = {
        signature for signature, values in labels.items() if len(values) > 1
    }
    for pair in pre_conflict:
        if pair["modelInputSignature"] in conflicting:
            pair["reasons"] = (*pair["reasons"], "CONFLICTING_MODEL_INPUT_LABEL")


def signature_sets(pairs: list[dict[str, Any]]) -> dict[str, set[str]]:
    keys = (
        "rawPairSignature",
        "normalizedPairSignature",
        "semanticPairSignature",
        "modelInputSignature",
        "candidateSignature",
        "evidenceSignature",
        "throwableTopologySignature",
    )
    return {key: {pair[key] for pair in pairs} for key in keys}


def overlap(
    train: list[dict[str, Any]], validation: list[dict[str, Any]]
) -> dict[str, dict[str, int]]:
    left = signature_sets(train)
    right = signature_sets(validation)
    return {
        key: {
            "trainUnique": len(left[key]),
            "validationUnique": len(right[key]),
            "overlap": len(left[key] & right[key]),
        }
        for key in left
    }


def conflict_counts(pairs: list[dict[str, Any]]) -> dict[str, Any]:
    checks: dict[str, Any] = {}
    for key in (
        "rawPairSignature",
        "normalizedPairSignature",
        "semanticPairSignature",
        "modelInputSignature",
    ):
        labels: dict[str, set[str]] = defaultdict(set)
        for pair in pairs:
            labels[pair[key]].add(pair["label"])
        count = sum(len(values) > 1 for values in labels.values())
        checks[key] = {"conflictingSignatures": count, "pass": count == 0}
    return checks


def lengths(pairs: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "candidateCharacters": distribution(pair["candidateCharacters"] for pair in pairs),
        "candidateWords": distribution(pair["candidateWords"] for pair in pairs),
        "evidenceItemsPerPair": distribution(pair["evidenceItems"] for pair in pairs),
        "evidenceCharactersPerPair": distribution(
            pair["evidenceCharacters"] for pair in pairs
        ),
    }


def category_counts(pairs: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
    family_ids: dict[str, set[str]] = defaultdict(set)
    pair_counts: Counter[str] = Counter()
    for pair in pairs:
        family_ids[pair["category"]].add(pair["familyId"])
        pair_counts[pair["category"]] += 1
    return {
        category: {
            "families": len(family_ids[category]),
            "pairs": pair_counts[category],
        }
        for category in sorted(pair_counts)
    }


def reason_summary(pairs: list[dict[str, Any]]) -> dict[str, Any]:
    counts = Counter(reason for pair in pairs for reason in pair["reasons"])
    combinations = Counter(
        "+".join(pair["reasons"]) for pair in pairs if pair["reasons"]
    )
    overlapping = sum(len(pair["reasons"]) > 1 for pair in pairs)
    return {
        "reasonCounts": {reason: counts[reason] for reason in REASONS},
        "reasonCombinationCounts": dict(sorted(combinations.items())),
        "pairsWithMultipleReasons": overlapping,
        "overlapAware": True,
    }


def family_reviews(
    split: str, pairs: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for pair in pairs:
        grouped[pair["familyId"]].append(pair)
    reviews: list[dict[str, Any]] = []
    for family_id in sorted(grouped):
        values = grouped[family_id]
        reason_counts = Counter(reason for pair in values for reason in pair["reasons"])
        reviews.append(
            {
                "split": split,
                "familySignature": source.digest(family_id),
                "category": values[0]["category"],
                "rawPairs": len(values),
                "retainedPairs": sum(not pair["reasons"] for pair in values),
                "excludedPairs": sum(bool(pair["reasons"]) for pair in values),
                "exclusionReasonCounts": {
                    reason: reason_counts[reason]
                    for reason in REASONS
                    if reason_counts[reason]
                },
                "individualLabelsIncluded": False,
            }
        )
    return reviews


def security_coverage(pairs: list[dict[str, Any]]) -> dict[str, Any]:
    attack_counts = {
        name: sum(bool(pattern.search(pair["securityText"])) for pair in pairs)
        for name, pattern in SECURITY_PATTERNS.items()
    }
    negative_fields = {
        "negativeEvidenceIds": 0,
        "contradictedEvidenceIds": 0,
        "negativeOrContradictedPolarity": 0,
    }
    for pair in pairs:
        candidate = pair["candidate"]
        for field in ("negativeEvidenceIds", "contradictedEvidenceIds"):
            if candidate.get(field):
                negative_fields[field] += 1
        if str(candidate.get("polarity", "")).upper() in {"NEGATIVE", "CONTRADICTED"}:
            negative_fields["negativeOrContradictedPolarity"] += 1
    all_zero = not any(attack_counts.values()) and not any(negative_fields.values())
    return {
        "scope": "natural canonical candidate text and candidate-linked evidence only",
        "requiredAttackCategoryPairCounts": attack_counts,
        "candidateLocalNegativeFieldPairCounts": negative_fields,
        "allRequiredCountsZero": all_zero,
        "dynamicCoverageAvailable": False,
    }


def split_summary(split: str, pairs: list[dict[str, Any]]) -> dict[str, Any]:
    pre_conflict = [
        pair
        for pair in pairs
        if set(pair["reasons"]) <= {"CONFLICTING_MODEL_INPUT_LABEL"}
    ]
    retained = [pair for pair in pairs if not pair["reasons"]]
    raw_labels = Counter(pair["label"] for pair in pairs)
    retained_labels = Counter(pair["label"] for pair in retained)
    pre_conflict_labels = Counter(pair["label"] for pair in pre_conflict)
    represented = {pair["familyId"] for pair in retained}
    return {
        "raw": {
            "families": len({pair["familyId"] for pair in pairs}),
            "pairs": len(pairs),
            "proposedLabelCounts": {label: raw_labels[label] for label in LABELS},
            "categoryCounts": category_counts(pairs),
            "lengthDistributions": lengths(pairs),
        },
        "exclusions": {
            **reason_summary(pairs),
            "excludedPairs": len(pairs) - len(retained),
            "excludedPairsAssignedLabels": 0,
        },
        "preConflict": {
            "representedFamilies": len(
                {pair["familyId"] for pair in pre_conflict}
            ),
            "pairs": len(pre_conflict),
            "labelCounts": {
                label: pre_conflict_labels[label] for label in LABELS
            },
            "conflictingModelInputSignatures": conflict_counts(pre_conflict)[
                "modelInputSignature"
            ]["conflictingSignatures"],
        },
        "retained": {
            "representedFamilies": len(represented),
            "pairs": len(retained),
            "labelCounts": {label: retained_labels[label] for label in LABELS},
            "labelRatiosPercent": {
                label: round(100 * retained_labels[label] / len(retained), 3)
                for label in LABELS
            },
            "kindCounts": dict(
                sorted(Counter(pair["kind"] for pair in retained).items())
            ),
            "categoryCounts": category_counts(retained),
            "lengthDistributions": lengths(retained),
        },
    }


def assert_expected(split: str, summary: dict[str, Any]) -> None:
    expected = EXPECTED[split]
    actual = {
        "rawFamilies": summary["raw"]["families"],
        "rawPairs": summary["raw"]["pairs"],
        "rawLabels": summary["raw"]["proposedLabelCounts"],
        "exclusions": summary["exclusions"]["reasonCounts"],
        "representedFamilies": summary["retained"]["representedFamilies"],
        "preConflictFamilies": summary["preConflict"]["representedFamilies"],
        "preConflictPairs": summary["preConflict"]["pairs"],
        "preConflictLabels": summary["preConflict"]["labelCounts"],
        "retainedPairs": summary["retained"]["pairs"],
        "retainedLabels": summary["retained"]["labelCounts"],
        "excludedPairs": summary["exclusions"]["excludedPairs"],
    }
    if actual != expected:
        raise source.ContractError(f"{split} binary audit counts drifted")


def class_range_checks(summaries: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    requirements = (
        ("TRAIN_SUPPORTED_PRESENT", "TRAIN", "SUPPORTED", 1),
        ("TRAIN_NOT_SUPPORTED_PRESENT", "TRAIN", "NOT_SUPPORTED", 1),
        ("VALIDATION_SUPPORTED_MINIMUM", "VALIDATION", "SUPPORTED", 15),
        ("VALIDATION_NOT_SUPPORTED_MINIMUM", "VALIDATION", "NOT_SUPPORTED", 15),
    )
    checks = [
        {
            "check": name,
            "minimum": minimum,
            "actual": summaries[split]["retained"]["labelCounts"][label],
            "pass": summaries[split]["retained"]["labelCounts"][label] >= minimum,
        }
        for name, split, label, minimum in requirements
    ]
    for split in ("TRAIN", "VALIDATION"):
        ratios = summaries[split]["retained"]["labelRatiosPercent"]
        for label, minimum, maximum in (
            ("SUPPORTED", 30.0, 60.0),
            ("NOT_SUPPORTED", 40.0, 70.0),
        ):
            actual = ratios[label]
            checks.append(
                {
                    "check": f"{split}_{label}_RATIO",
                    "minimumPercent": minimum,
                    "maximumPercent": maximum,
                    "actualPercent": actual,
                    "pass": minimum <= actual <= maximum,
                }
            )
    return checks


def shortcut_leakage(pairs: list[dict[str, Any]]) -> dict[str, Any]:
    text_labels: dict[str, set[str]] = defaultdict(set)
    not_supported: Counter[str] = Counter()
    for pair in pairs:
        text_labels[pair["candidateSignature"]].add(pair["label"])
        if pair["label"] == "NOT_SUPPORTED":
            not_supported[pair["candidate"]["id"]] += 1
    concentrated = {
        "cause.unknown": ("UNKNOWN_CAUSE_TEXT", 30),
        "check.inspect-root-cause": ("INSPECT_ROOT_CAUSE_CHECK_TEXT", 30),
        "check.web.json-parse-message": ("JSON_PARSE_CHECK_TEXT", 7),
    }
    actual = {candidate_id: not_supported[candidate_id] for candidate_id in concentrated}
    if len(text_labels) != 29 or sum(len(labels) > 1 for labels in text_labels.values()) != 0:
        raise source.ContractError("retained candidate-text leakage counts drifted")
    if actual != {candidate_id: value[1] for candidate_id, value in concentrated.items()}:
        raise source.ContractError("NOT_SUPPORTED candidate-text concentration drifted")
    if sum(not_supported.values()) != 67 or len(not_supported) != 3:
        raise source.ContractError("NOT_SUPPORTED text concentration is no longer complete")
    return {
        "retainedPairs": len(pairs),
        "uniqueCandidateTexts": len(text_labels),
        "textsWithBothLabels": 0,
        "candidateTextPerfectlyDeterminesLabel": True,
        "notSupportedTextCount": 3,
        "notSupportedPairs": 67,
        "notSupportedConcentration": [
            {"textRole": role, "pairs": count}
            for role, count in (value for value in concentrated.values())
        ],
        "candidateIdsIncluded": False,
        "finding": "CANDIDATE_TEXT_SHORTCUT_LEAKAGE",
        "compatibleWithLearningEvidenceDependence": False,
    }


def build_report(
    train_path: Path = DEFAULT_TRAIN,
    validation_path: Path = DEFAULT_VALIDATION,
    manifest_path: Path = DEFAULT_DATASET_MANIFEST,
    tri_class_audit_path: Path = DEFAULT_TRI_CLASS_AUDIT,
) -> dict[str, Any]:
    train_rows = source.read_jsonl(train_path)
    validation_rows = source.read_jsonl(validation_path)
    manifest = source.read_json(manifest_path)
    tri_class_audit = source.read_json(tri_class_audit_path)
    if tri_class_audit.get("status") != "FAIL_CONTRADICTED_CLASS_ABSENT":
        raise source.ContractError("Phase 5.5 tri-class audit status drifted")
    source.validate_manifest(manifest, train_path, validation_path)
    train_families = source.validate_split(train_rows, "TRAIN")
    validation_families = source.validate_split(validation_rows, "VALIDATION")
    train_pairs = collect_pairs(train_families)
    validation_pairs = collect_pairs(validation_families)
    retained_train = [pair for pair in train_pairs if not pair["reasons"]]
    retained_validation = [pair for pair in validation_pairs if not pair["reasons"]]
    summaries = {
        "TRAIN": split_summary("TRAIN", train_pairs),
        "VALIDATION": split_summary("VALIDATION", validation_pairs),
    }
    for split in summaries:
        assert_expected(split, summaries[split])
    reviews = [
        *family_reviews("TRAIN", train_pairs),
        *family_reviews("VALIDATION", validation_pairs),
    ]
    if len(reviews) != 90:
        raise source.ContractError("binary audit must include 90 family reviews")
    security = security_coverage([*train_pairs, *validation_pairs])
    if not security["allRequiredCountsZero"]:
        raise source.ContractError("natural source security coverage unexpectedly changed")
    checks = class_range_checks(summaries)
    validation_supported = summaries["VALIDATION"]["retained"]["labelCounts"]["SUPPORTED"]
    status = (
        "BINARY_VALIDATION_CLASS_INSUFFICIENT"
        if validation_supported < 15
        else "PASS"
    )
    leakage = shortcut_leakage([*retained_train, *retained_validation])
    retained_overlaps = overlap(retained_train, retained_validation)
    category_overlap = len(
        {pair["category"] for pair in retained_train}
        & {pair["category"] for pair in retained_validation}
    )
    return {
        "schemaVersion": "1",
        "auditId": "stackmate-binary-source-audit-v1",
        "status": status,
        "gateBlockers": [
            "BINARY_VALIDATION_CLASS_INSUFFICIENT",
            "CANDIDATE_TEXT_SHORTCUT_LEAKAGE",
        ],
        "methodology": {
            "canonicalVariant": 0,
            "rawProposal": {
                "expectedSelected": "SUPPORTED",
                "unselected": "NOT_SUPPORTED",
            },
            "proposalAcceptedWithoutReview": False,
            "excludedPairsReceiveNoLabel": True,
            "ambiguousLabelsForced": False,
            "readOnly": True,
            "datasetWritten": False,
        },
        "sources": {
            "train": {"file": train_path.name, "sha256": source.file_digest(train_path)},
            "validation": {
                "file": validation_path.name,
                "sha256": source.file_digest(validation_path),
            },
            "datasetManifest": {
                "file": manifest_path.name,
                "sha256": source.file_digest(manifest_path),
            },
            "triClassAudit": {
                "file": tri_class_audit_path.name,
                "sha256": source.file_digest(tri_class_audit_path),
            },
            "validationHelper": {
                "file": Path(source.__file__).name,
                "sha256": source.file_digest(Path(source.__file__)),
            },
        },
        "splits": summaries,
        "crossSplit": {
            "familyOverlapCount": len(set(train_families) & set(validation_families)),
            "rawSignatureOverlaps": overlap(train_pairs, validation_pairs),
            "retainedSignatureOverlaps": retained_overlaps,
            "retainedLeakageOverlaps": {
                "candidateText": retained_overlaps["candidateSignature"]["overlap"],
                "exactModelInputPair": retained_overlaps["modelInputSignature"]["overlap"],
                "evidence": retained_overlaps["evidenceSignature"]["overlap"],
                "throwableTopology": retained_overlaps["throwableTopologySignature"]["overlap"],
                "category": category_overlap,
                "exactPairLabelConflicts": conflict_counts(
                    [*retained_train, *retained_validation]
                )["modelInputSignature"]["conflictingSignatures"],
            },
            "conflictingLabelSignatureChecks": {
                "raw": conflict_counts([*train_pairs, *validation_pairs]),
                "retained": conflict_counts([*retained_train, *retained_validation]),
            },
        },
        "classRangeChecks": checks,
        "allClassRangeChecksPass": all(check["pass"] for check in checks),
        "shortcutLeakage": leakage,
        "familyAggregateReviews": reviews,
        "familyAggregateReviewCount": len(reviews),
        "securityCoverage": security,
        "privacy": {
            "timestampsIncluded": False,
            "absolutePathsIncluded": False,
            "candidateIdsIncluded": False,
            "familyIdsIncluded": False,
            "individualLabelsIncluded": False,
            "exposedDiagnosticLabelsAccessed": False,
        },
    }


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train", type=Path, default=DEFAULT_TRAIN)
    parser.add_argument("--validation", type=Path, default=DEFAULT_VALIDATION)
    parser.add_argument("--dataset-manifest", type=Path, default=DEFAULT_DATASET_MANIFEST)
    parser.add_argument(
        "--tri-class-audit", type=Path, default=DEFAULT_TRI_CLASS_AUDIT
    )
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    return parser.parse_args()


def resolve_input(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if not resolved.is_file():
        raise source.ContractError(f"input file does not exist: {path.name}")
    return resolved


def main() -> int:
    args = arguments()
    train_path = resolve_input(args.train)
    validation_path = resolve_input(args.validation)
    manifest_path = resolve_input(args.dataset_manifest)
    tri_class_audit_path = resolve_input(args.tri_class_audit)
    report_path = args.report.expanduser().resolve()
    if report_path.exists():
        raise FileExistsError(f"report already exists (CREATE_NEW): {report_path.name}")
    if not report_path.parent.is_dir():
        raise source.ContractError(
            f"report parent directory does not exist: {report_path.parent.name}"
        )
    report = build_report(
        train_path, validation_path, manifest_path, tri_class_audit_path
    )
    with report_path.open("x", encoding="utf-8", newline="\n") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(
        json.dumps(
            {
                "status": report["status"],
                "reportCreated": True,
                "datasetWritten": False,
            },
            separators=(",", ":"),
        )
    )
    return 2 if report["status"] == "BINARY_VALIDATION_CLASS_INSUFFICIENT" else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (source.ContractError, FileExistsError, OSError) as error:
        print(f"audit error: {error}", file=sys.stderr)
        raise SystemExit(1)
