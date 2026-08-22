#!/usr/bin/env python3
"""Offline, fail-closed Gate C leakage audit for the Phase 5.3 corpus.

Candidate/evidence signature reuse is evidence for manual review, not automatic
leakage when both the trigger primitive and exception-chain signature differ.
The report always counts those matches and marks them as requiring explanation.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import asdict
from pathlib import Path
from typing import Any, Iterable, Sequence

from dataset_contract import FAMILIES


REPOSITORY = Path(__file__).resolve().parents[3]
ALIGNMENT_ROOT = REPOSITORY / "research/model-alignment"
OLD_TRAIN = ALIGNMENT_ROOT / "datasets/train.jsonl"
OLD_VALIDATION = ALIGNMENT_ROOT / "datasets/validation.jsonl"
OLD_MANIFEST = ALIGNMENT_ROOT / "datasets/manifest.json"
OLD_CONTRACT = ALIGNMENT_ROOT / "scripts/dataset_contract.py"
V2_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v2/manifest.json"
BLIND_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v3-qualification/manifest.json"
PHASE51_SPLIT = REPOSITORY / "stackmate-benchmark/src/main/resources/prompt-ablation/split-manifest.json"
CHALLENGE_MANIFEST = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v4-post-training-challenge/manifest.json"
CHALLENGE_LOCK = REPOSITORY / "stackmate-benchmark/src/main/resources/corpus-v4-post-training-challenge/lock.json"
PARITY_CORPUS = ALIGNMENT_ROOT / "parity/corpus.json"
PARITY_MANIFEST = ALIGNMENT_ROOT / "parity/manifest.json"

EXPECTED_FAMILY_SPLITS = {"TRAIN": 70, "VALIDATION": 20, "LOCKED_FINAL": 30}
MAX_GENERATED_VARIANTS = 2
CHAR_NGRAM_SIZE = 5
TOKEN_NGRAM_SIZE = 3
CHAR_FAIL_THRESHOLD = 0.88
TOKEN_FAIL_THRESHOLD = 0.80
SEMANTIC_REVIEW_THRESHOLD = 0.60

UUID_RE = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.I)
NUMBER_RE = re.compile(r"\b\d+\b")
TOKEN_RE = re.compile(r"[a-z][a-z0-9_$.-]*", re.I)
PRIVATE_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("windows-user-path", re.compile(r"(?i)\b[a-z]:\\users\\[^\\\s]+")),
    ("unix-home-path", re.compile(r"(?i)(?:^|[\s\"'])/(?:home|users)/[^/\s\"']+")),
    ("wsl-mounted-user-path", re.compile(r"(?i)(?:^|[\s\"'])/mnt/[a-z]/users/[^/\s\"']+")),
    ("private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", re.I)),
    ("aws-access-key", re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("github-token", re.compile(r"\bgh[oprsu]_[A-Za-z0-9]{20,}\b")),
    ("jwt", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b")),
    (
        "assigned-secret",
        re.compile(
            r"(?i)\b(?:password|passwd|secret|api[_-]?key|access[_-]?token|client[_-]?secret)\s*[:=]\s*[\"']?(?!<|redacted|null|none)[^\s,;\"']{8,}"
        ),
    ),
)


class AuditError(ValueError):
    """An input or frozen-source contract failed closed."""


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def normalized_text(value: str) -> str:
    value = UUID_RE.sub(" uuid ", value.casefold())
    value = re.sub(r"(?i)(?:[a-z]:)?[/\\][\w .\\/-]+", " path ", value)
    value = NUMBER_RE.sub(" number ", value)
    return " ".join(TOKEN_RE.findall(value))


def normalize_id(value: Any) -> str:
    return re.sub(r"[^a-z0-9]+", ".", str(value).casefold()).strip(".")


def resolve_input(raw: str) -> Path:
    path = Path(raw)
    if not path.is_absolute():
        path = REPOSITORY / path
    return path.resolve()


def display_path(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPOSITORY).as_posix()
    except ValueError:
        return "<outside-repository>"


def safe_error_message(failure: Exception) -> str:
    message = str(failure).replace(str(REPOSITORY), "<repository-root>")
    for _, pattern in PRIVATE_PATTERNS:
        message = pattern.sub("<redacted-private-material>", message)
    return message


def require_file(path: Path, label: str) -> None:
    if not path.is_file():
        raise AuditError(f"missing required {label}: {path}")


def read_json(path: Path, label: str) -> Any:
    require_file(path, label)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        raise AuditError(f"cannot read {label}: {path}: {failure}") from failure


def read_jsonl(path: Path, label: str) -> list[dict[str, Any]]:
    require_file(path, label)
    rows: list[dict[str, Any]] = []
    try:
        for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not raw.strip():
                continue
            value = json.loads(raw)
            if not isinstance(value, dict):
                raise AuditError(f"{label} line {line_number} is not a JSON object")
            rows.append(value)
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        raise AuditError(f"cannot read {label}: {path}: {failure}") from failure
    if not rows:
        raise AuditError(f"{label} contains no rows: {path}")
    return rows


def strings(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from strings(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings(item)


def values_for_keys(value: Any, wanted: set[str]) -> Iterable[Any]:
    if isinstance(value, dict):
        for key, item in value.items():
            if key.casefold() in wanted:
                if isinstance(item, list):
                    yield from item
                else:
                    yield item
            yield from values_for_keys(item, wanted)
    elif isinstance(value, list):
        for item in value:
            yield from values_for_keys(item, wanted)


def first_value(value: dict[str, Any], keys: Sequence[str], default: Any = "") -> Any:
    for key in keys:
        if key in value and value[key] is not None:
            return value[key]
    metadata = value.get("metadata")
    if isinstance(metadata, dict):
        for key in keys:
            if key in metadata and metadata[key] is not None:
                return metadata[key]
    return default


def request_payload(row: dict[str, Any]) -> dict[str, Any]:
    direct = row.get("request")
    if isinstance(direct, dict):
        return direct
    messages = row.get("messages")
    if not isinstance(messages, list):
        return {}
    for message in messages:
        if not isinstance(message, dict) or message.get("role") != "user":
            continue
        content = message.get("content")
        if not isinstance(content, str):
            continue
        start = content.find("\n")
        end = content.rfind("\n</STACKMATE_UNTRUSTED_INCIDENT>")
        if start >= 0 and end > start:
            try:
                payload = json.loads(content[start + 1 : end])
                return payload if isinstance(payload, dict) else {}
            except json.JSONDecodeError:
                return {}
    return {}


def candidate_signatures(value: dict[str, Any]) -> set[str]:
    payload = request_payload(value) or value
    result: set[str] = set()
    for key in ("candidateCauses", "candidateChecks"):
        candidates = payload.get(key, [])
        if not isinstance(candidates, list):
            continue
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            signature = {
                "kind": key,
                "id": normalize_id(candidate.get("id", "")),
                "text": normalized_text(str(candidate.get("text", ""))),
                "evidence": sorted(normalize_id(item) for item in candidate.get("evidenceIds", []) if item),
            }
            if signature["id"] or signature["text"]:
                result.add(canonical_json(signature))
    return result


def evidence_signature(value: dict[str, Any]) -> str:
    explicit = first_value(value, ("evidenceSignature", "evidence_signature", "factType", "fact_type"), "")
    if explicit:
        return normalize_id(explicit)
    payload = request_payload(value) or value
    facts = payload.get("facts", payload.get("expectedFacts", []))
    if not isinstance(facts, list):
        facts = []
    types = sorted(
        normalize_id(fact.get("type", ""))
        for fact in facts
        if isinstance(fact, dict) and fact.get("type")
    )
    return "+".join(types) or normalize_id(first_value(value, ("structuredEvidenceKind",), "manifest-only"))


def exception_chain_signature(value: dict[str, Any]) -> str:
    root = first_value(value, ("rootType", "expectedRootType"), "")
    wrappers = first_value(value, ("wrapperTopology", "wrapperTypes", "expectedWrapperTypes"), [])
    if isinstance(wrappers, str):
        wrappers = [wrappers]
    if not isinstance(wrappers, list):
        wrappers = []
    if not wrappers:
        payload = request_payload(value) or value
        facts = payload.get("facts", payload.get("expectedFacts", []))
        if isinstance(facts, list):
            wrappers = [
                fact.get("value", "")
                for fact in facts
                if isinstance(fact, dict) and str(fact.get("type", "")).upper() == "WRAPPER_TYPE"
            ]
            if not root:
                roots = [
                    fact.get("value", "")
                    for fact in facts
                    if isinstance(fact, dict)
                    and str(fact.get("type", "")).upper() in {"ROOT_TYPE", "EXCEPTION_CLASS"}
                ]
                root = roots[-1] if roots else ""
    parts = [normalize_id(item) for item in wrappers if item]
    if root:
        parts.append(normalize_id(root))
    return ">".join(parts) or "unknown-chain"


def trigger_primitive(value: dict[str, Any]) -> str:
    return normalize_id(
        first_value(
            value,
            (
                "triggerPrimitive",
                "trigger_primitive",
                "recipe",
                "semanticPatternId",
                "semantic_pattern_id",
                "templateFamily",
                "familyId",
                "family_id",
            ),
            "",
        )
    )


def failure_phase(value: dict[str, Any]) -> str:
    return normalize_id(first_value(value, ("failurePhase", "failure_phase"), "unknown-phase"))


def family_id(value: dict[str, Any]) -> str:
    return normalize_id(first_value(value, ("familyId", "family_id", "templateFamily", "template_family"), ""))


def row_id(value: dict[str, Any], fallback: str) -> str:
    raw = first_value(value, ("exampleId", "caseId", "rowId", "id"), fallback)
    return str(raw)


def split_name(value: dict[str, Any]) -> str:
    return str(first_value(value, ("split",), "")).upper()


def semantic_text(value: dict[str, Any]) -> str:
    payload = request_payload(value)
    selected: list[str] = []
    source = payload if payload else value
    facts = source.get("facts", source.get("expectedFacts", []))
    if isinstance(facts, list):
        selected.extend(str(fact.get("value", "")) for fact in facts if isinstance(fact, dict))
    for key in ("candidateCauses", "candidateChecks"):
        candidates = source.get(key, [])
        if isinstance(candidates, list):
            selected.extend(str(item.get("text", "")) for item in candidates if isinstance(item, dict))
    selected.extend(
        str(first_value(value, (key,), ""))
        for key in (
            "setup",
            "triggerCommand",
            "factPattern",
            "fact_pattern",
            "supportedCause",
            "supported_cause",
            "supportedCheck",
            "supported_check",
            "distractorCause",
            "distractor_cause",
            "distractorCheck",
            "distractor_check",
            "summary",
            "explanation",
            "manualReviewFocus",
        )
    )
    messages = value.get("messages")
    if isinstance(messages, list):
        for message in messages:
            if isinstance(message, dict) and message.get("role") == "assistant":
                selected.extend(strings(message.get("content", "")))
    text = normalized_text(" ".join(item for item in selected if item))
    if text:
        return text
    return normalized_text(" ".join(strings(value)))


def semantic_descriptor(value: dict[str, Any]) -> str:
    payload = request_payload(value) or value
    disposition = first_value(value, ("disposition", "expectedDisposition"), "")
    category = first_value(value, ("category", "expectedCategory"), "")
    if not disposition:
        disposition = "RESTRAIN" if first_value(value, ("abstentionRequired",), False) else "DIAGNOSE"
    causes = first_value(value, ("expectedCauseIds", "expectedCauseKeys"), [])
    checks = first_value(value, ("expectedCheckIds", "expectedCheckKeys"), [])
    if not isinstance(causes, list):
        causes = []
    if not isinstance(checks, list):
        checks = []
    selection = first_value(value, ("selectionCardinality",), {})
    cause_count = selection.get("causes", len(causes)) if isinstance(selection, dict) else len(causes)
    check_count = selection.get("checks", len(checks)) if isinstance(selection, dict) else len(checks)
    if not causes:
        cause_count = first_value(value, ("expectedCauseSelections",), cause_count)
    if not checks:
        check_count = first_value(value, ("expectedCheckSelections",), check_count)
    if first_value(value, ("supportedCause", "supported_cause"), None):
        cause_count = 1
    if first_value(value, ("supportedCheck", "supported_check"), None):
        check_count = 1
    incident = payload.get("incident", {})
    if not isinstance(incident, dict):
        incident = {}
    descriptor = {
        "category": normalize_id(category or incident.get("category", "unknown")),
        "trigger": trigger_primitive(value),
        "phase": failure_phase(value),
        "chain": exception_chain_signature(value),
        "evidence": evidence_signature(value),
        "disposition": normalize_id(disposition),
        "selection": [int(cause_count or 0), int(check_count or 0)],
    }
    return canonical_json(descriptor)


def protected_identifiers(value: dict[str, Any]) -> dict[str, set[str]]:
    result = {"family": set(), "recipe": set(), "trigger": set(), "all": set()}
    for key, bucket in (
        ("familyId", "family"),
        ("family_id", "family"),
        ("templateFamily", "family"),
        ("template_family", "family"),
        ("recipe", "recipe"),
        ("triggerPrimitive", "trigger"),
        ("trigger_primitive", "trigger"),
    ):
        for raw in values_for_keys(value, {key.casefold()}):
            item = normalize_id(raw)
            if item:
                result[bucket].add(item)
                result["all"].add(item)
    for raw in values_for_keys(value, {"caseid", "exampleid", "id", "expectedcauseids", "expectedcheckids", "evidenceids"}):
        item = normalize_id(raw)
        if item and len(item) >= 4:
            result["all"].add(item)
    return result


def dedupe_dicts(values: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    unique: dict[str, dict[str, Any]] = {}
    for value in values:
        unique.setdefault(canonical_json(value), value)
    return list(unique.values())


def char_ngrams(value: str) -> set[str]:
    padded = f" {value} "
    return {padded[index : index + CHAR_NGRAM_SIZE] for index in range(max(0, len(padded) - CHAR_NGRAM_SIZE + 1))}


def token_ngrams(value: str) -> set[tuple[str, ...]]:
    tokens = value.split()
    return {tuple(tokens[index : index + TOKEN_NGRAM_SIZE]) for index in range(max(0, len(tokens) - TOKEN_NGRAM_SIZE + 1))}


def jaccard(left: set[Any], right: set[Any]) -> float:
    union = left | right
    return len(left & right) / len(union) if union else 1.0


def record(source: str, source_id: str, value: dict[str, Any]) -> dict[str, Any]:
    return {
        "source": source,
        "sourceId": source_id,
        "text": semantic_text(value),
        "familyId": family_id(value),
        "trigger": trigger_primitive(value),
        "phase": failure_phase(value),
        "chain": exception_chain_signature(value),
        "evidence": evidence_signature(value),
        "candidates": candidate_signatures(value),
        "descriptor": semantic_descriptor(value),
        "identifiers": protected_identifiers(value),
    }


def verify_hash_reference(document: Any, expected: str, label: str) -> None:
    hashes = {str(item).casefold() for item in strings(document) if re.fullmatch(r"[0-9a-fA-F]{64}", str(item))}
    if expected.casefold() not in hashes:
        raise AuditError(f"generated dataset manifest does not bind {label} sha256 {expected}")


def verify_frozen_sources(source_hashes: dict[str, str]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    paths = {
        "oldAlignmentTrain": OLD_TRAIN,
        "oldAlignmentValidation": OLD_VALIDATION,
        "oldAlignmentManifest": OLD_MANIFEST,
        "oldAlignmentFamilies": OLD_CONTRACT,
        "benchmarkV2": V2_MANIFEST,
        "blind20": BLIND_MANIFEST,
        "phase51Split": PHASE51_SPLIT,
        "challenge30": CHALLENGE_MANIFEST,
        "challenge30Lock": CHALLENGE_LOCK,
        "parity40": PARITY_CORPUS,
        "parity40Manifest": PARITY_MANIFEST,
    }
    for label, path in paths.items():
        require_file(path, label)
        source_hashes[label] = sha256_file(path)

    old_manifest = read_json(OLD_MANIFEST, "old alignment manifest")
    verify_hash_reference(old_manifest, source_hashes["oldAlignmentTrain"], "old alignment train")
    verify_hash_reference(old_manifest, source_hashes["oldAlignmentValidation"], "old alignment validation")

    v2 = read_json(V2_MANIFEST, "Benchmark V2 manifest")
    blind = read_json(BLIND_MANIFEST, "blind20 manifest")
    split = read_json(PHASE51_SPLIT, "Phase 5.1 split manifest")
    challenge = read_json(CHALLENGE_MANIFEST, "challenge30 manifest")
    challenge_lock = read_json(CHALLENGE_LOCK, "challenge30 lock")
    parity = read_json(PARITY_CORPUS, "parity40 corpus")
    parity_manifest = read_json(PARITY_MANIFEST, "parity40 manifest")
    if len(v2.get("cases", [])) != 80:
        raise AuditError("Benchmark V2 cardinality drift: expected 80")
    if len(blind.get("cases", [])) != 20:
        raise AuditError("blind corpus cardinality drift: expected 20")
    if len(challenge.get("cases", [])) != 30:
        raise AuditError("challenge corpus cardinality drift: expected 30")
    if len(parity.get("cases", [])) != 40:
        raise AuditError("parity corpus cardinality drift: expected 40")
    if challenge_lock.get("manifestSha256") != source_hashes["challenge30"]:
        raise AuditError("challenge30 lock checksum drift")
    if parity_manifest.get("corpusSha256") != source_hashes["parity40"]:
        raise AuditError("parity40 manifest checksum drift")
    source_refs = split.get("sourceManifests", {})
    if source_refs.get("v2", {}).get("sha256") != source_hashes["benchmarkV2"]:
        raise AuditError("Phase 5.1 V2 checksum drift")
    if source_refs.get("blind", {}).get("sha256") != source_hashes["blind20"]:
        raise AuditError("Phase 5.1 blind checksum drift")

    old_train = read_jsonl(OLD_TRAIN, "old alignment train")
    old_validation = read_jsonl(OLD_VALIDATION, "old alignment validation")
    records = [record("oldAlignmentTrain", row_id(item, f"train-{index}"), item) for index, item in enumerate(old_train)]
    records.extend(record("oldAlignmentValidation", row_id(item, f"validation-{index}"), item) for index, item in enumerate(old_validation))
    for item in FAMILIES:
        value = asdict(item)
        records.append(record("oldAlignmentFamily", str(value["family_id"]), value))
    for source, cases in (("benchmarkV2", v2["cases"]), ("blind20", blind["cases"]), ("challenge30", challenge["cases"]), ("parity40", parity["cases"])):
        records.extend(record(source, row_id(item, f"{source}-{index}"), item) for index, item in enumerate(cases))

    by_id = {str(item.get("caseId")): item for item in v2["cases"] + blind["cases"]}
    locked_ids = split.get("splits", {}).get("lockedFinal", [])
    if len(locked_ids) != 40 or len(set(locked_ids)) != 40:
        raise AuditError("Phase 5.1 locked-final cardinality drift: expected 40 unique IDs")
    missing = sorted(set(locked_ids) - set(by_id))
    if missing:
        raise AuditError(f"Phase 5.1 locked-final IDs absent from V2/blind: {missing}")
    records.extend(record("phase51LockedFinal", case_id, by_id[case_id]) for case_id in locked_ids)
    counts = Counter(item["source"] for item in records)
    return records, {"recordCounts": dict(sorted(counts.items())), "phase51LockedFinalIds": len(locked_ids)}


def private_findings(label: str, value: Any) -> list[dict[str, str]]:
    serialized = canonical_json(value)
    findings: list[dict[str, str]] = []
    for name, pattern in PRIVATE_PATTERNS:
        for match in pattern.finditer(serialized):
            findings.append(
                {
                    "input": label,
                    "pattern": name,
                    "matchSha256": sha256_bytes(match.group(0).encode("utf-8")),
                }
            )
    return findings


def overlap_entry(generated: dict[str, Any], legacy: dict[str, Any], kind: str, value: str, prohibited: bool) -> dict[str, Any]:
    return {
        "generatedId": generated["sourceId"],
        "source": legacy["source"],
        "sourceId": legacy["sourceId"],
        "signatureKind": kind,
        "signatureSha256": sha256_bytes(value.encode("utf-8")),
        "triggerMatches": bool(generated["trigger"] and generated["trigger"] == legacy["trigger"]),
        "exceptionChainMatches": generated["chain"] != "unknown-chain" and generated["chain"] == legacy["chain"],
        "requiresExplanation": True,
        "automaticallyProhibited": prohibited,
    }


def audit(arguments: argparse.Namespace) -> dict[str, Any]:
    corpus_path = resolve_input(arguments.corpus_manifest)
    train_path = resolve_input(arguments.train)
    validation_path = resolve_input(arguments.validation)
    dataset_manifest_path = resolve_input(arguments.dataset_manifest)
    source_hashes: dict[str, str] = {}
    legacy_records, frozen_summary = verify_frozen_sources(source_hashes)
    corpus = read_json(corpus_path, "new corpus manifest")
    train = read_jsonl(train_path, "generated train")
    validation = read_jsonl(validation_path, "generated validation")
    dataset_manifest = read_json(dataset_manifest_path, "generated dataset manifest")
    for label, path in (("newCorpusManifest", corpus_path), ("generatedTrain", train_path), ("generatedValidation", validation_path), ("generatedDatasetManifest", dataset_manifest_path)):
        source_hashes[label] = sha256_file(path)
    verify_hash_reference(dataset_manifest, source_hashes["generatedTrain"], "generated train")
    verify_hash_reference(dataset_manifest, source_hashes["generatedValidation"], "generated validation")
    verify_hash_reference(dataset_manifest, source_hashes["newCorpusManifest"], "new corpus manifest")

    families = corpus.get("families")
    if not isinstance(families, list):
        raise AuditError("new corpus manifest families must be a list")
    family_counts = Counter(str(item.get("split", "")).upper() for item in families if isinstance(item, dict))
    manifest_errors: list[str] = []
    if dict(family_counts) != EXPECTED_FAMILY_SPLITS:
        manifest_errors.append(f"family split counts differ: {dict(family_counts)} != {EXPECTED_FAMILY_SPLITS}")
    ids = [str(item.get("familyId", "")) for item in families if isinstance(item, dict)]
    if len(ids) != len(set(ids)) or any(not item for item in ids):
        manifest_errors.append("new corpus family IDs are blank or duplicated")
    if int(corpus.get("plannedVariants", -1)) != MAX_GENERATED_VARIANTS:
        manifest_errors.append("new corpus plannedVariants must be 2")
    if corpus.get("maxVariants", 0) < MAX_GENERATED_VARIANTS:
        manifest_errors.append("new corpus maxVariants cannot be below plannedVariants")
    family_by_id = {str(item["familyId"]): item for item in families if isinstance(item, dict) and item.get("familyId")}
    trigger_splits: dict[str, set[str]] = defaultdict(set)
    for item in families:
        if isinstance(item, dict):
            trigger_splits[trigger_primitive(item)].add(str(item.get("split", "")).upper())
    cross_split_triggers = {
        key: sorted(value)
        for key, value in sorted(trigger_splits.items())
        if key and len(value) > 1
    }
    if cross_split_triggers:
        manifest_errors.append("trigger primitives cross new-corpus split boundaries")
    historical_records = list(legacy_records)
    sealed = [item for item in families if isinstance(item, dict) and str(item.get("split", "")).upper() == "LOCKED_FINAL"]
    legacy_records.extend(record("newSealedFinalFamily", str(item["familyId"]), item) for item in sealed)
    frozen_summary["recordCounts"] = dict(sorted(Counter(item["source"] for item in legacy_records).items()))

    generated_values: list[tuple[str, dict[str, Any]]] = [("TRAIN", item) for item in train] + [("VALIDATION", item) for item in validation]
    generated_records: list[dict[str, Any]] = []
    row_errors: list[dict[str, str]] = []
    variants: Counter[str] = Counter()
    generated_family_splits: dict[str, set[str]] = defaultdict(set)
    generated_ids: set[str] = set()
    duplicate_row_ids: list[str] = []
    for index, (file_split, row) in enumerate(generated_values):
        identifier = row_id(row, f"{file_split.lower()}-{index}")
        if identifier in generated_ids:
            duplicate_row_ids.append(identifier)
        generated_ids.add(identifier)
        row_split = split_name(row)
        if row_split != file_split:
            row_errors.append({"rowId": identifier, "error": f"row split {row_split!r} differs from file split {file_split}"})
        raw_family = str(first_value(row, ("familyId", "templateFamily"), ""))
        if raw_family not in family_by_id:
            row_errors.append({"rowId": identifier, "error": f"unknown familyId {raw_family!r}"})
            effective = row
        else:
            effective = dict(family_by_id[raw_family])
            effective.update(row)
            effective["familyId"] = raw_family
            expected_split = str(family_by_id[raw_family].get("split", "")).upper()
            if expected_split == "LOCKED_FINAL":
                row_errors.append({"rowId": identifier, "error": "generated row references a sealed LOCKED_FINAL family"})
            elif expected_split != file_split:
                row_errors.append({"rowId": identifier, "error": f"family belongs to {expected_split}, not {file_split}"})
        if raw_family:
            variants[raw_family] += 1
            generated_family_splits[raw_family].add(file_split)
        generated_records.append(record(f"generated{file_split.title()}", identifier, effective))

    over_variant_limit = {key: count for key, count in sorted(variants.items()) if count > MAX_GENERATED_VARIANTS}
    crossing_families = {key: sorted(value) for key, value in sorted(generated_family_splits.items()) if len(value) > 1}
    expected_train_families = {key for key, value in family_by_id.items() if str(value.get("split", "")).upper() == "TRAIN"}
    expected_validation_families = {key for key, value in family_by_id.items() if str(value.get("split", "")).upper() == "VALIDATION"}
    actual_train_families = {key for key, value in generated_family_splits.items() if "TRAIN" in value}
    actual_validation_families = {key for key, value in generated_family_splits.items() if "VALIDATION" in value}
    family_coverage = {
        "missingTrain": sorted(expected_train_families - actual_train_families),
        "extraTrain": sorted(actual_train_families - expected_train_families),
        "missingValidation": sorted(expected_validation_families - actual_validation_families),
        "extraValidation": sorted(actual_validation_families - expected_validation_families),
    }

    exact_text: list[dict[str, str]] = []
    exact_semantic: list[dict[str, str]] = []
    restricted_id_reuse: list[dict[str, str]] = []
    identifier_overlap: list[dict[str, str]] = []
    trigger_phase_overlap: list[dict[str, str]] = []
    chain_overlap: list[dict[str, str]] = []
    candidate_evidence_reuse: list[dict[str, Any]] = []
    nearest_char: list[dict[str, Any]] = []
    nearest_token: list[dict[str, Any]] = []
    prohibited_near: list[dict[str, Any]] = []
    manual_review: list[dict[str, Any]] = []

    for family in families:
        if not isinstance(family, dict) or not family.get("familyId"):
            continue
        current = record("newFamilyMetadata", str(family["familyId"]), family)
        targets = list(historical_records)
        if str(family.get("split", "")).upper() != "LOCKED_FINAL":
            targets.extend(item for item in legacy_records if item["source"] == "newSealedFinalFamily")
        best_semantic: tuple[float, dict[str, Any] | None] = (0.0, None)
        current_descriptor_tokens = token_ngrams(normalized_text(current["descriptor"]))
        for legacy in targets:
            common_families = current["identifiers"]["family"] & legacy["identifiers"]["family"]
            for common in sorted(common_families):
                restricted_id_reuse.append(
                    {
                        "generatedId": current["sourceId"],
                        "source": legacy["source"],
                        "sourceId": legacy["sourceId"],
                        "kind": "family",
                        "identifierSha256": sha256_bytes(common.encode()),
                    }
                )
            current_recipe_trigger = current["identifiers"]["recipe"] | current["identifiers"]["trigger"]
            legacy_recipe_trigger = legacy["identifiers"]["recipe"] | legacy["identifiers"]["trigger"]
            for common in sorted(current_recipe_trigger & legacy_recipe_trigger):
                restricted_id_reuse.append(
                    {
                        "generatedId": current["sourceId"],
                        "source": legacy["source"],
                        "sourceId": legacy["sourceId"],
                        "kind": "recipeOrTrigger",
                        "identifierSha256": sha256_bytes(common.encode()),
                    }
                )
            if current["descriptor"] == legacy["descriptor"]:
                exact_semantic.append(
                    {
                        "generatedId": current["sourceId"],
                        "source": legacy["source"],
                        "sourceId": legacy["sourceId"],
                        "descriptorSha256": sha256_bytes(current["descriptor"].encode()),
                    }
                )
            score = jaccard(
                current_descriptor_tokens,
                token_ngrams(normalized_text(legacy["descriptor"])),
            )
            if score > best_semantic[0]:
                best_semantic = (score, legacy)
        if (
            best_semantic[1] is not None
            and best_semantic[0] >= SEMANTIC_REVIEW_THRESHOLD
            and current["descriptor"] != best_semantic[1]["descriptor"]
        ):
            manual_review.append(
                {
                    "reason": "semantic-family-descriptor-near-match",
                    "generatedId": current["sourceId"],
                    "source": best_semantic[1]["source"],
                    "sourceId": best_semantic[1]["sourceId"],
                    "token3GramScore": round(best_semantic[0], 6),
                }
            )

    legacy_text_index: dict[str, list[dict[str, Any]]] = defaultdict(list)
    legacy_descriptor_index: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in legacy_records:
        if item["text"]:
            legacy_text_index[item["text"]].append(item)
        legacy_descriptor_index[item["descriptor"]].append(item)

    train_text = defaultdict(list)
    validation_text = defaultdict(list)
    for item in generated_records:
        (train_text if item["source"] == "generatedTrain" else validation_text)[item["text"]].append(item["sourceId"])
        for legacy in legacy_text_index.get(item["text"], []):
            exact_text.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "normalizedTextSha256": sha256_bytes(item["text"].encode())})
        for legacy in legacy_descriptor_index.get(item["descriptor"], []):
            exact_semantic.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "descriptorSha256": sha256_bytes(item["descriptor"].encode())})

        best_char: tuple[float, dict[str, Any] | None] = (0.0, None)
        best_token: tuple[float, dict[str, Any] | None] = (0.0, None)
        best_semantic: tuple[float, dict[str, Any] | None] = (0.0, None)
        item_char = char_ngrams(item["text"])
        item_token = token_ngrams(item["text"])
        descriptor_tokens = token_ngrams(normalized_text(item["descriptor"]))
        for legacy in legacy_records:
            char_score = jaccard(item_char, char_ngrams(legacy["text"]))
            token_score = jaccard(item_token, token_ngrams(legacy["text"]))
            semantic_score = jaccard(descriptor_tokens, token_ngrams(normalized_text(legacy["descriptor"])))
            if char_score > best_char[0]:
                best_char = (char_score, legacy)
            if token_score > best_token[0]:
                best_token = (token_score, legacy)
            if semantic_score > best_semantic[0]:
                best_semantic = (semantic_score, legacy)

            common_ids = item["identifiers"]["all"] & legacy["identifiers"]["all"]
            for common in sorted(common_ids):
                identifier_overlap.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "identifierSha256": sha256_bytes(common.encode())})
            for common in sorted(item["identifiers"]["family"] & legacy["identifiers"]["family"]):
                restricted_id_reuse.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "kind": "family", "identifierSha256": sha256_bytes(common.encode())})
            item_recipe_trigger = item["identifiers"]["recipe"] | item["identifiers"]["trigger"]
            legacy_recipe_trigger = legacy["identifiers"]["recipe"] | legacy["identifiers"]["trigger"]
            for common in sorted(item_recipe_trigger & legacy_recipe_trigger):
                restricted_id_reuse.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "kind": "recipeOrTrigger", "identifierSha256": sha256_bytes(common.encode())})
            if item["trigger"] and item["trigger"] == legacy["trigger"]:
                trigger_phase_overlap.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "trigger": item["trigger"], "failurePhaseMatches": item["phase"] == legacy["phase"]})
            if item["chain"] != "unknown-chain" and item["chain"] == legacy["chain"]:
                chain_overlap.append({"generatedId": item["sourceId"], "source": legacy["source"], "sourceId": legacy["sourceId"], "chain": item["chain"]})
            chain_or_trigger = (item["trigger"] and item["trigger"] == legacy["trigger"]) or (item["chain"] != "unknown-chain" and item["chain"] == legacy["chain"])
            if item["evidence"] and item["evidence"] == legacy["evidence"]:
                candidate_evidence_reuse.append(overlap_entry(item, legacy, "evidence", item["evidence"], bool(chain_or_trigger)))
            for common in sorted(item["candidates"] & legacy["candidates"]):
                candidate_evidence_reuse.append(overlap_entry(item, legacy, "candidate-id-text-evidence", common, bool(chain_or_trigger)))

        for metric, best, threshold, destination in (
            ("character-5-gram", best_char, CHAR_FAIL_THRESHOLD, nearest_char),
            ("token-3-gram", best_token, TOKEN_FAIL_THRESHOLD, nearest_token),
        ):
            if best[1] is not None:
                entry = {"generatedId": item["sourceId"], "source": best[1]["source"], "sourceId": best[1]["sourceId"], "score": round(best[0], 6)}
                destination.append(entry)
                if best[0] >= threshold:
                    prohibited_near.append({"metric": metric, **entry})
        if best_semantic[1] is not None and best_semantic[0] >= SEMANTIC_REVIEW_THRESHOLD and item["descriptor"] != best_semantic[1]["descriptor"]:
            manual_review.append({"reason": "semantic-descriptor-near-match", "generatedId": item["sourceId"], "source": best_semantic[1]["source"], "sourceId": best_semantic[1]["sourceId"], "token3GramScore": round(best_semantic[0], 6)})

    cross_split_text = [
        {"normalizedTextSha256": sha256_bytes(text.encode()), "trainIds": train_text[text], "validationIds": validation_text[text]}
        for text in sorted(set(train_text) & set(validation_text))
    ]
    private_material = private_findings("newCorpusManifest", corpus) + private_findings("generatedTrain", train) + private_findings("generatedValidation", validation) + private_findings("generatedDatasetManifest", dataset_manifest)
    restricted_id_reuse = dedupe_dicts(restricted_id_reuse)
    identifier_overlap = dedupe_dicts(identifier_overlap)
    exact_text = dedupe_dicts(exact_text)
    exact_semantic = dedupe_dicts(exact_semantic)
    trigger_phase_overlap = dedupe_dicts(trigger_phase_overlap)
    chain_overlap = dedupe_dicts(chain_overlap)
    candidate_evidence_reuse = dedupe_dicts(candidate_evidence_reuse)
    prohibited_near = dedupe_dicts(prohibited_near)
    manual_review = dedupe_dicts(manual_review)
    unexplained_compound_reuse = [item for item in candidate_evidence_reuse if item["automaticallyProhibited"]]

    identifier_overlap_summary = {
        "count": len(identifier_overlap),
        "bySource": dict(sorted(Counter(item["source"] for item in identifier_overlap).items())),
        "uniqueIdentifierHashes": len({item["identifierSha256"] for item in identifier_overlap}),
    }
    chain_overlap_summary = {
        "count": len(chain_overlap),
        "bySource": dict(sorted(Counter(item["source"] for item in chain_overlap).items())),
        "uniqueChains": len({item["chain"] for item in chain_overlap}),
    }
    candidate_reuse_summary = {
        "count": len(candidate_evidence_reuse),
        "bySource": dict(sorted(Counter(item["source"] for item in candidate_evidence_reuse).items())),
        "bySignatureKind": dict(
            sorted(Counter(item["signatureKind"] for item in candidate_evidence_reuse).items())
        ),
        "uniqueSignatureHashes": len(
            {item["signatureSha256"] for item in candidate_evidence_reuse}
        ),
        "prohibitedCompoundReuse": unexplained_compound_reuse,
    }

    failure_counts = {
        "manifestErrors": len(manifest_errors),
        "rowErrors": len(row_errors),
        "duplicateRowIds": len(duplicate_row_ids),
        "overVariantLimit": len(over_variant_limit),
        "crossSplitFamilies": len(crossing_families),
        "familyCoverageErrors": sum(len(value) for value in family_coverage.values()),
        "restrictedIdentifierReuse": len(restricted_id_reuse),
        "exactNormalizedText": len(exact_text),
        "crossSplitNormalizedText": len(cross_split_text),
        "exactSemanticDescriptor": len(exact_semantic),
        "nearDuplicate": len(prohibited_near),
        "compoundCandidateEvidenceReuse": len(unexplained_compound_reuse),
        "privatePathsOrSecrets": len(private_material),
        "manualReviewRequired": len(manual_review),
    }
    failure_total = sum(failure_counts.values())
    nearest_char.sort(key=lambda item: (-item["score"], item["generatedId"], item["source"], item["sourceId"]))
    nearest_token.sort(key=lambda item: (-item["score"], item["generatedId"], item["source"], item["sourceId"]))
    report = {
        "schemaVersion": "1",
        "gate": "C",
        "status": "PASS" if failure_total == 0 else "FAIL",
        "offline": True,
        "repositoryRoot": ".",
        "auditorSha256": sha256_file(Path(__file__).resolve()),
        "thresholds": {
            "characterNgram": {"size": CHAR_NGRAM_SIZE, "failAtOrAbove": CHAR_FAIL_THRESHOLD},
            "tokenNgram": {"size": TOKEN_NGRAM_SIZE, "failAtOrAbove": TOKEN_FAIL_THRESHOLD},
            "semanticDescriptorManualReviewAtOrAbove": SEMANTIC_REVIEW_THRESHOLD,
            "nearestLists": "one maximum-similarity legacy/sealed match per generated row and metric",
        },
        "sourceHashes": dict(sorted(source_hashes.items())),
        "sourceInventory": frozen_summary,
        "generatedDataset": {
            "trainRows": len(train),
            "validationRows": len(validation),
            "familySplitCounts": dict(sorted(family_counts.items())),
            "variantCounts": dict(sorted(variants.items())),
        },
        "policy": {
            "candidateEvidenceReuse": "Counted and requires explanation. Reuse alone does not fail when both trigger primitive and exception-chain signatures differ; reuse combined with either matching trigger or matching exception chain fails closed.",
            "manualReview": "Semantic descriptor near matches are not auto-cleared; every listed item requires a human decision before Gate C acceptance.",
        },
        "checks": {
            "manifest": {"errors": manifest_errors, "crossSplitTriggers": cross_split_triggers},
            "rows": {"errors": row_errors, "duplicateRowIds": sorted(duplicate_row_ids)},
            "familyIsolation": {"overVariantLimit": over_variant_limit, "crossSplitFamilies": crossing_families, "coverage": family_coverage},
            "restrictedIdentifierReuse": restricted_id_reuse,
            "allIdentifierOverlap": identifier_overlap_summary,
            "exactNormalizedRowText": {"legacyMatches": exact_text, "trainValidationMatches": cross_split_text},
            "character5Gram": {"nearestMatches": nearest_char, "prohibitedMatches": [item for item in prohibited_near if item["metric"] == "character-5-gram"]},
            "token3Gram": {"nearestMatches": nearest_token, "prohibitedMatches": [item for item in prohibited_near if item["metric"] == "token-3-gram"]},
            "exceptionChainSignature": chain_overlap_summary,
            "candidateEvidenceSignature": candidate_reuse_summary,
            "triggerPrimitiveFailurePhase": {"overlap": trigger_phase_overlap},
            "canonicalSemanticDescriptor": {"prohibitedEqual": exact_semantic},
            "privatePathsOrSecrets": {"prohibitedFindings": private_material},
        },
        "manualReviewRequired": manual_review,
        "counts": {
            "failureReasons": failure_counts,
            "failureTotal": failure_total,
            "manualReviewRequired": len(manual_review),
            "candidateEvidenceReuseRequiringExplanation": len(candidate_evidence_reuse),
            "allIdentifierOverlap": len(identifier_overlap),
            "exceptionChainOverlap": len(chain_overlap),
            "triggerPrimitiveOverlap": len(trigger_phase_overlap),
            "characterNearestMatches": len(nearest_char),
            "tokenNearestMatches": len(nearest_token),
        },
    }
    return report


def write_report_exclusive(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open("x", encoding="utf-8", newline="\n") as destination:
            destination.write(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    except FileExistsError as failure:
        raise AuditError(f"refusing to overwrite existing report: {path}") from failure


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus-manifest", required=True, help="new 120-family corpus manifest")
    parser.add_argument("--train", required=True, help="generated train.jsonl")
    parser.add_argument("--validation", required=True, help="generated validation.jsonl")
    parser.add_argument("--dataset-manifest", required=True, help="generated dataset provenance manifest")
    parser.add_argument("--report", required=True, help="new report path; an existing file is never overwritten")
    return parser.parse_args()


def main() -> None:
    arguments = parse_arguments()
    report_path = resolve_input(arguments.report)
    if report_path.exists():
        print(f"ERROR: refusing to overwrite existing report: {report_path}", file=sys.stderr)
        raise SystemExit(2)
    try:
        report = audit(arguments)
    except Exception as failure:  # Fail closed while still leaving an auditable report.
        report = {
            "schemaVersion": "1",
            "gate": "C",
            "status": "FAIL",
            "offline": True,
            "repositoryRoot": ".",
            "auditorSha256": sha256_file(Path(__file__).resolve()),
            "fatalErrors": [{"type": type(failure).__name__, "message": safe_error_message(failure)}],
        }
    try:
        write_report_exclusive(report_path, report)
    except AuditError as failure:
        print(f"ERROR: {failure}", file=sys.stderr)
        raise SystemExit(2) from failure
    print(canonical_json({"status": report["status"], "report": display_path(report_path), "failureTotal": report.get("counts", {}).get("failureTotal", 1)}))
    if report["status"] != "PASS":
        raise SystemExit(2)


if __name__ == "__main__":
    main()
