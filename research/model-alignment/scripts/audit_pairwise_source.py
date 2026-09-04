#!/usr/bin/env python3
"""Audit the native real-world corpus as a conservative pairwise source."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


REPOSITORY = Path(__file__).resolve().parents[3]
ALIGNMENT = REPOSITORY / "research" / "model-alignment"
DATASET = ALIGNMENT / "datasets" / "real-world-v1"
DEFAULT_TRAIN = DATASET / "train.jsonl"
DEFAULT_VALIDATION = DATASET / "validation.jsonl"
DEFAULT_DATASET_MANIFEST = DATASET / "manifest.json"
DEFAULT_INVENTORY = (
    ALIGNMENT / "reports" / "phase-5-4-3-existing-family-inventory.json"
)
DEFAULT_REPORT = ALIGNMENT / "reports" / "pairwise-source-audit.json"

EXPECTED_SPLITS = {
    "TRAIN": {"families": 70, "rows": 140},
    "VALIDATION": {"families": 20, "rows": 40},
}
LABELS = ("SUPPORTED", "UNKNOWN", "CONTRADICTED")
TOKEN = re.compile(r"[a-z][a-z0-9_$.-]*|<[^>]+>", re.IGNORECASE)
PRIVATE_PATH = re.compile(r"[a-z]:[/\\][^\"\s]+|/(?:home|mnt|tmp)/[^\"\s]+", re.I)
UUID = re.compile(r"\b[0-9a-f]{8}-[0-9a-f-]{27,}\b", re.I)
NUMBER = re.compile(r"\b\d+\b")


class ContractError(ValueError):
    """Raised when an input does not satisfy the source contract."""


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise ContractError(f"duplicate JSON key: {key!r}")
        value[key] = item
    return value


def parse_json(text: str, source: str) -> Any:
    try:
        return json.loads(text, object_pairs_hook=reject_duplicate_keys)
    except (json.JSONDecodeError, ContractError) as error:
        raise ContractError(f"invalid JSON in {source}: {error}") from error


def read_json(path: Path) -> dict[str, Any]:
    value = parse_json(path.read_text(encoding="utf-8"), path.name)
    if not isinstance(value, dict):
        raise ContractError(f"JSON object required: {path.name}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        if not line.strip():
            continue
        value = parse_json(line, f"{path.name}:{line_number}")
        if not isinstance(value, dict):
            raise ContractError(
                f"JSONL object required: {path.name}:{line_number}"
            )
        rows.append(value)
    return rows


def canonical(value: Any) -> str:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    )


def normalized(value: Any) -> str:
    text = canonical(value).casefold()
    text = UUID.sub("<uuid>", text)
    text = NUMBER.sub("<number>", text)
    text = PRIVATE_PATH.sub("<path>", text)
    return " ".join(TOKEN.findall(text))


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def digest(value: Any) -> str:
    return digest_bytes(canonical(value).encode("utf-8"))


def normalized_digest(value: Any) -> str:
    return digest_bytes(normalized(value).encode("utf-8"))


def file_digest(path: Path) -> str:
    return digest_bytes(path.read_bytes())


def require_dict(value: Any, context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ContractError(f"{context} must be an object")
    return value


def require_list(value: Any, context: str) -> list[Any]:
    if not isinstance(value, list):
        raise ContractError(f"{context} must be an array")
    return value


def require_string(value: Any, context: str) -> str:
    if not isinstance(value, str) or not value:
        raise ContractError(f"{context} must be a non-empty string")
    return value


def require_string_list(value: Any, context: str) -> list[str]:
    values = require_list(value, context)
    if any(not isinstance(item, str) or not item for item in values):
        raise ContractError(f"{context} must contain non-empty strings")
    if len(values) != len(set(values)):
        raise ContractError(f"{context} contains duplicate values")
    return values


def require_indexes(value: Any, context: str, size: int) -> list[int]:
    values = require_list(value, context)
    if any(type(item) is not int for item in values):
        raise ContractError(f"{context} must contain integer indexes")
    if len(values) != len(set(values)):
        raise ContractError(f"{context} contains duplicate indexes")
    if any(item < 0 or item >= size for item in values):
        raise ContractError(f"{context} contains an out-of-range index")
    return values


def family_set_sha(family_ids: Iterable[str]) -> str:
    payload = "".join(f"{family_id}\n" for family_id in sorted(family_ids))
    return digest_bytes(payload.encode("utf-8"))


def row_candidates(
    row: dict[str, Any], kind: str, context: str
) -> tuple[list[dict[str, Any]], list[str]]:
    plural = "causes" if kind == "cause" else "checks"
    order_key = "causeIds" if kind == "cause" else "checkIds"
    candidates = require_list(
        require_dict(row.get("candidates"), f"{context}.candidates").get(plural),
        f"{context}.candidates.{plural}",
    )
    typed: list[dict[str, Any]] = []
    candidate_ids: list[str] = []
    for index, value in enumerate(candidates):
        candidate = require_dict(value, f"{context}.{plural}[{index}]")
        candidate_id = require_string(
            candidate.get("id"), f"{context}.{plural}[{index}].id"
        )
        require_string(candidate.get("text"), f"{context}.{plural}[{index}].text")
        require_string_list(
            candidate.get("evidenceIds"),
            f"{context}.{plural}[{index}].evidenceIds",
        )
        typed.append(candidate)
        candidate_ids.append(candidate_id)
    if len(candidate_ids) != len(set(candidate_ids)):
        raise ContractError(f"{context}.{plural} contains duplicate candidate IDs")
    order = require_string_list(
        require_dict(row.get("candidateOrder"), f"{context}.candidateOrder").get(
            order_key
        ),
        f"{context}.candidateOrder.{order_key}",
    )
    if order != candidate_ids:
        raise ContractError(
            f"{context}.candidateOrder.{order_key} must match candidate order"
        )
    return typed, order


def expected_ids(
    row: dict[str, Any], kind: str, order: list[str], context: str
) -> frozenset[str]:
    key = "selectedCauseIndexes" if kind == "cause" else "selectedCheckIndexes"
    indexes = require_indexes(
        require_dict(row.get("expectedT1"), f"{context}.expectedT1").get(key),
        f"{context}.expectedT1.{key}",
        len(order),
    )
    return frozenset(order[index] for index in indexes)


def explicit_negative(candidate: dict[str, Any], context: str) -> bool:
    """Recognize only explicit, candidate-local negative annotations."""
    negative_ids = candidate.get("negativeEvidenceIds")
    contradicted_ids = candidate.get("contradictedEvidenceIds")
    for field, value in (
        ("negativeEvidenceIds", negative_ids),
        ("contradictedEvidenceIds", contradicted_ids),
    ):
        if value is not None:
            ids = require_string_list(value, f"{context}.{field}")
            if ids:
                return True
    polarity = candidate.get("polarity")
    if polarity is not None:
        polarity = require_string(polarity, f"{context}.polarity").upper()
        if polarity in {"NEGATIVE", "CONTRADICTED"}:
            return True
    return False


def validate_row(
    row: dict[str, Any], split: str, row_number: int
) -> dict[str, Any]:
    context = f"{split} row {row_number}"
    family_id = require_string(row.get("familyId"), f"{context}.familyId")
    if row.get("split") != split:
        raise ContractError(f"{context}.split must be {split!r}")
    variant = row.get("variant")
    if type(variant) is not int or variant not in {0, 1}:
        raise ContractError(f"{context}.variant must be 0 or 1")
    disposition = require_string(
        require_dict(row.get("expectedT1"), f"{context}.expectedT1").get(
            "disposition"
        ),
        f"{context}.expectedT1.disposition",
    )
    if disposition not in {"DIAGNOSE", "RESTRAIN"}:
        raise ContractError(f"{context}.disposition must be DIAGNOSE or RESTRAIN")

    evidence_values = require_list(row.get("evidence"), f"{context}.evidence")
    evidence_by_id: dict[str, dict[str, Any]] = {}
    for index, value in enumerate(evidence_values):
        evidence = require_dict(value, f"{context}.evidence[{index}]")
        evidence_id = require_string(
            evidence.get("id"), f"{context}.evidence[{index}].id"
        )
        if evidence_id in evidence_by_id:
            raise ContractError(f"{context}.evidence contains duplicate IDs")
        evidence_by_id[evidence_id] = evidence

    kinds: dict[str, dict[str, Any]] = {}
    for kind in ("cause", "check"):
        candidates, order = row_candidates(row, kind, context)
        selected = expected_ids(row, kind, order, context)
        for index, candidate in enumerate(candidates):
            refs = candidate["evidenceIds"]
            missing = sorted(set(refs) - evidence_by_id.keys())
            if missing:
                raise ContractError(
                    f"{context}.{kind}[{index}] has unresolved evidence references"
                )
            for field in ("negativeEvidenceIds", "contradictedEvidenceIds"):
                if field in candidate:
                    negative_refs = require_string_list(
                        candidate[field], f"{context}.{kind}[{index}].{field}"
                    )
                    if set(negative_refs) - evidence_by_id.keys():
                        raise ContractError(
                            f"{context}.{kind}[{index}].{field} has unresolved references"
                        )
        kinds[kind] = {
            "candidates": candidates,
            "order": order,
            "selected": selected,
        }
    return {
        "row": row,
        "familyId": family_id,
        "variant": variant,
        "disposition": disposition,
        "evidenceById": evidence_by_id,
        "kinds": kinds,
    }


def validate_split(
    rows: list[dict[str, Any]], split: str
) -> dict[str, dict[int, dict[str, Any]]]:
    expected = EXPECTED_SPLITS[split]
    if len(rows) != expected["rows"]:
        raise ContractError(
            f"{split} must contain {expected['rows']} rows, found {len(rows)}"
        )
    families: dict[str, dict[int, dict[str, Any]]] = defaultdict(dict)
    for row_number, row in enumerate(rows, start=1):
        validated = validate_row(row, split, row_number)
        family_id = validated["familyId"]
        variant = validated["variant"]
        if variant in families[family_id]:
            raise ContractError(f"{split} family {family_id!r} repeats variant {variant}")
        families[family_id][variant] = validated
    if len(families) != expected["families"]:
        raise ContractError(
            f"{split} must contain {expected['families']} families, found {len(families)}"
        )
    for family_id, variants in families.items():
        if set(variants) != {0, 1}:
            raise ContractError(f"{split} family {family_id!r} must have variants 0 and 1")
        left, right = variants[0], variants[1]
        if left["disposition"] != right["disposition"]:
            raise ContractError(
                f"{split} family {family_id!r} changes disposition across variants"
            )
        for kind in ("cause", "check"):
            if set(left["kinds"][kind]["order"]) != set(
                right["kinds"][kind]["order"]
            ):
                raise ContractError(
                    f"{split} family {family_id!r} changes {kind} candidate IDs"
                )
            if left["kinds"][kind]["selected"] != right["kinds"][kind]["selected"]:
                raise ContractError(
                    f"{split} family {family_id!r} changes expected {kind} IDs"
                )
    return dict(families)


def validate_manifest(
    manifest: dict[str, Any], train: Path, validation: Path
) -> None:
    counts = require_dict(manifest.get("counts"), "manifest.counts")
    expected_counts = {
        "trainFamilies": 70,
        "trainRows": 140,
        "validationFamilies": 20,
        "validationRows": 40,
    }
    for key, expected in expected_counts.items():
        if counts.get(key) != expected:
            raise ContractError(f"manifest.counts.{key} must be {expected}")
    if manifest.get("variantsPerFamily") != 2:
        raise ContractError("manifest.variantsPerFamily must be 2")
    files = require_dict(manifest.get("files"), "manifest.files")
    for key, path, rows in (
        ("train", train, 140),
        ("validation", validation, 40),
    ):
        entry = require_dict(files.get(key), f"manifest.files.{key}")
        if entry.get("records") != rows:
            raise ContractError(f"manifest.files.{key}.records must be {rows}")
        expected_sha = require_string(
            entry.get("sha256"), f"manifest.files.{key}.sha256"
        )
        if file_digest(path) != expected_sha:
            raise ContractError(f"{key} SHA-256 does not match dataset manifest")


def pair_record(
    family: dict[str, Any], kind: str, candidate: dict[str, Any]
) -> dict[str, Any]:
    evidence_by_id = family["evidenceById"]
    evidence = [evidence_by_id[item] for item in candidate["evidenceIds"]]
    raw_pair = {"kind": kind, "candidate": candidate, "evidence": evidence}
    evidence_semantic = [
        {key: item.get(key) for key in ("type", "value")} for item in evidence
    ]
    candidate_semantic = {
        "kind": kind,
        "text": candidate["text"],
        "evidence": evidence_semantic,
    }
    candidate_id = candidate["id"]
    selected = candidate_id in family["kinds"][kind]["selected"]
    contradicted = explicit_negative(candidate, f"candidate {candidate_id}")
    label = "SUPPORTED" if selected else "CONTRADICTED" if contradicted else "UNKNOWN"
    return {
        "label": label,
        "selected": selected,
        "allEvidenceRefsResolve": True,
        "rawPairSignature": digest(raw_pair),
        "normalizedPairSignature": normalized_digest(raw_pair),
        "candidateTextSignature": digest(candidate["text"]),
        "candidateSemanticSignature": normalized_digest(candidate_semantic),
        "evidenceSignature": normalized_digest(evidence_semantic),
    }


def split_inventory(
    families: dict[str, dict[int, dict[str, Any]]]
) -> tuple[dict[str, Any], dict[str, set[str]], list[dict[str, Any]]]:
    labels = Counter({label: 0 for label in LABELS})
    kind_labels = {
        kind: Counter({label: 0 for label in LABELS})
        for kind in ("cause", "check")
    }
    dispositions: Counter[str] = Counter()
    ambiguity_candidates = Counter({"cause": 0, "check": 0})
    ambiguity_families: dict[str, set[str]] = {
        "cause": set(),
        "check": set(),
    }
    signatures: dict[str, set[str]] = {
        "rawPairSignatures": set(),
        "normalizedPairSignatures": set(),
        "candidateTextSignatures": set(),
        "candidateSemanticSignatures": set(),
        "evidenceSignatures": set(),
    }
    family_semantics: list[dict[str, Any]] = []
    for family_id in sorted(families):
        family = families[family_id][0]
        dispositions[family["disposition"]] += 1
        family_expected: dict[str, list[str]] = {}
        family_evidence: set[str] = set()
        for kind in ("cause", "check"):
            family_expected[kind] = sorted(family["kinds"][kind]["selected"])
            for candidate in family["kinds"][kind]["candidates"]:
                pair = pair_record(family, kind, candidate)
                labels[pair["label"]] += 1
                kind_labels[kind][pair["label"]] += 1
                for key in signatures:
                    signatures[key].add(pair[key[:-1]])
                if not pair["selected"] and pair["allEvidenceRefsResolve"]:
                    ambiguity_candidates[kind] += 1
                    ambiguity_families[kind].add(family_id)
                family_evidence.update(candidate["evidenceIds"])
        family_semantics.append(
            {
                "familyId": family_id,
                "expectedCauseSignature": normalized_digest(family_expected["cause"]),
                "expectedCheckSignature": normalized_digest(family_expected["check"]),
                "evidenceReferenceSignature": normalized_digest(sorted(family_evidence)),
            }
        )
    summary = {
        "families": len(families),
        "rows": len(families) * 2,
        "canonicalVariant": 0,
        "pairCounts": {
            "cause": sum(kind_labels["cause"].values()),
            "check": sum(kind_labels["check"].values()),
            "total": sum(labels.values()),
        },
        "labelCounts": {label: labels[label] for label in LABELS},
        "labelCountsByKind": {
            kind: {label: kind_labels[kind][label] for label in LABELS}
            for kind in ("cause", "check")
        },
        "dispositionFamilyCounts": {
            "DIAGNOSE": dispositions["DIAGNOSE"],
            "RESTRAIN": dispositions["RESTRAIN"],
        },
        "familySetSha256": family_set_sha(families),
        "listwiseSelectionAmbiguity": {
            "unselectedCandidatesWithResolvedEvidence": {
                "cause": ambiguity_candidates["cause"],
                "check": ambiguity_candidates["check"],
                "total": sum(ambiguity_candidates.values()),
            },
            "familiesWithSuchCandidates": {
                "cause": len(ambiguity_families["cause"]),
                "check": len(ambiguity_families["check"]),
                "either": len(ambiguity_families["cause"] | ambiguity_families["check"]),
            },
            "interpretation": "AMBIGUOUS_NOT_CONTRADICTED",
        },
    }
    return summary, signatures, family_semantics


def overlap_summary(
    left: dict[str, set[str]], right: dict[str, set[str]]
) -> dict[str, dict[str, int]]:
    return {
        key: {
            "trainUnique": len(left[key]),
            "validationUnique": len(right[key]),
            "overlap": len(left[key] & right[key]),
        }
        for key in left
    }


def inventory_overlap(
    inventory: dict[str, Any], family_semantics: list[dict[str, Any]]
) -> dict[str, Any]:
    records = require_list(inventory.get("records"), "inventory.records")
    eligible: list[dict[str, Any]] = []
    catalogs: Counter[str] = Counter()
    exposures: Counter[str] = Counter()
    for index, value in enumerate(records):
        record = require_dict(value, f"inventory.records[{index}]")
        catalog = require_string(record.get("catalog"), f"inventory.records[{index}].catalog")
        record_exposures = require_string_list(
            record.get("exposures"), f"inventory.records[{index}].exposures"
        )
        same_development_source = catalog == "real-world-v1" and bool(
            {"TRAINING", "VALIDATION"} & set(record_exposures)
        )
        if same_development_source:
            continue
        eligible.append(record)
        catalogs[catalog] += 1
        exposures.update(record_exposures)

    family_ids = {item["familyId"] for item in family_semantics}
    exposed_ids = {
        require_string(item.get("familyId"), "inventory record familyId")
        for item in eligible
    } | {
        require_string(item.get("canonicalFamilyId"), "inventory record canonicalFamilyId")
        for item in eligible
    }
    cause_signatures = {item["expectedCauseSignature"] for item in family_semantics}
    check_signatures = {item["expectedCheckSignature"] for item in family_semantics}
    exposed_cause = {
        item.get("candidateCauseSignature") for item in eligible
        if isinstance(item.get("candidateCauseSignature"), str)
    }
    exposed_check = {
        item.get("candidateCheckSignature") for item in eligible
        if isinstance(item.get("candidateCheckSignature"), str)
    }
    return {
        "policy": {
            "excluded": "same real-world-v1 TRAINING and VALIDATION records",
            "included": "real-world-v1 LOCKED_FINAL and all other catalogs/exposures",
        },
        "eligibleRecords": len(eligible),
        "eligibleCatalogCounts": dict(sorted(catalogs.items())),
        "eligibleExposureCounts": dict(sorted(exposures.items())),
        "overlapCounts": {
            "familyIds": len(family_ids & exposed_ids),
            "expectedCauseSignatures": len(cause_signatures & exposed_cause),
            "expectedCheckSignatures": len(check_signatures & exposed_check),
        },
        "individualExposedLabelsIncluded": False,
    }


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train", type=Path, default=DEFAULT_TRAIN)
    parser.add_argument("--validation", type=Path, default=DEFAULT_VALIDATION)
    parser.add_argument("--dataset-manifest", type=Path, default=DEFAULT_DATASET_MANIFEST)
    parser.add_argument("--inventory", type=Path, default=DEFAULT_INVENTORY)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    return parser.parse_args()


def resolve_input(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if not resolved.is_file():
        raise ContractError(f"input file does not exist: {path.name}")
    return resolved


def main() -> int:
    args = arguments()
    train_path = resolve_input(args.train)
    validation_path = resolve_input(args.validation)
    manifest_path = resolve_input(args.dataset_manifest)
    inventory_path = resolve_input(args.inventory)
    report_path = args.report.expanduser().resolve()
    if report_path.exists():
        raise FileExistsError(f"report already exists (CREATE_NEW): {report_path.name}")
    if not report_path.parent.is_dir():
        raise ContractError(f"report parent directory does not exist: {report_path.parent.name}")

    train_rows = read_jsonl(train_path)
    validation_rows = read_jsonl(validation_path)
    manifest = read_json(manifest_path)
    inventory = read_json(inventory_path)
    validate_manifest(manifest, train_path, validation_path)
    train_families = validate_split(train_rows, "TRAIN")
    validation_families = validate_split(validation_rows, "VALIDATION")
    family_overlap = set(train_families) & set(validation_families)

    train_summary, train_signatures, train_semantics = split_inventory(train_families)
    validation_summary, validation_signatures, validation_semantics = split_inventory(
        validation_families
    )
    contradicted_absent = (
        train_summary["labelCounts"]["CONTRADICTED"] == 0
        or validation_summary["labelCounts"]["CONTRADICTED"] == 0
    )
    status = "FAIL_CONTRADICTED_CLASS_ABSENT" if contradicted_absent else "PASS"
    report = {
        "schemaVersion": "1",
        "auditId": "stackmate-pairwise-source-audit-v1",
        "status": status,
        "threeClassFeasible": not contradicted_absent,
        "methodology": {
            "canonicalVariant": 0,
            "nativeLabelPolicy": {
                "expectedStableId": "SUPPORTED",
                "otherNativeCandidate": "UNKNOWN",
                "contradictedRequirement": "explicit candidate-targeted negative evidence or polarity",
            },
            "contradictedClassInferredFromListwiseNonSelection": False,
            "pairUnit": "one canonical pair per native cause candidate and check candidate",
        },
        "sources": {
            "trainSha256": file_digest(train_path),
            "validationSha256": file_digest(validation_path),
            "datasetManifestSha256": file_digest(manifest_path),
            "inventorySha256": file_digest(inventory_path),
        },
        "splits": {
            "TRAIN": train_summary,
            "VALIDATION": validation_summary,
        },
        "crossSplit": {
            "familyOverlapCount": len(family_overlap),
            "signatureOverlaps": overlap_summary(
                train_signatures, validation_signatures
            ),
        },
        "exposedDiagnosticOverlap": inventory_overlap(
            inventory, [*train_semantics, *validation_semantics]
        ),
        "privacy": {
            "privateAbsolutePathsIncluded": False,
            "individualExposedLabelsIncluded": False,
        },
    }
    with report_path.open("x", encoding="utf-8", newline="\n") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(
        json.dumps(
            {
                "status": status,
                "threeClassFeasible": report["threeClassFeasible"],
                "reportCreated": True,
            },
            separators=(",", ":"),
        )
    )
    return 2 if contradicted_absent else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ContractError, FileExistsError, OSError) as error:
        print(f"audit error: {error}", file=sys.stderr)
        raise SystemExit(1)
