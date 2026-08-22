#!/usr/bin/env python3
"""Classify frozen Phase 5.2 outputs for Phase 5.3 diagnostics.

This is an offline, deterministic report transformer. It neither loads nor runs a
model. The three input artifacts are treated as a linked evidence bundle and are
rejected before classification if their hashes or locked case identities drift.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


STAGE_ORDER = ["benchmark-v2-holdout", "phase-5-blind", "post-training-challenge"]
STAGE_CARDINALITY = {
    "benchmark-v2-holdout": 20,
    "phase-5-blind": 20,
    "post-training-challenge": 30,
}
CONDITION_ORDER = ["B0", "B1", "A1", "A2"]
EXPOSED_CONDITIONS = ["B0", "B1", "A1"]
PROMPT_MAPPING = {"B0": "P0", "B1": "P3", "A1": "P3", "A2": "P2"}
MODEL_FAMILY = {"B0": "base", "B1": "base", "A1": "adapter", "A2": "adapter"}
OUTPUT_KEYS = [
    "schemaVersion",
    "summary",
    "explanation",
    "selectedCauseIds",
    "selectedCheckIds",
    "usedEvidenceIds",
    "limitations",
]
LIST_KEYS = ["selectedCauseIds", "selectedCheckIds", "usedEvidenceIds", "limitations"]
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
EXPECTED_INPUT_SHA256 = "b036f1cc18e4ef0bd99f3de68466fb4e35cb0229855360be592afb1020af0f87"
EXPECTED_RAW_SHA256 = "a95c415d75ed5d52ff719e4007366a74c5bdb8c22df4c6238ed6bff4b73f0a90"
EXPECTED_AUTHORITATIVE_SHA256 = "e6124fea7318bf4c1bfa6071d1d7f9ccfbca041e927e6b17d0cd53737b7a1b12"
EXPECTED_MANIFEST_SHA256 = {
    "benchmarkV2": "292b395f6024837a52878b38a6c0170a62cb29fdfcc89df68b845ced7732f294",
    "phase5Blind": "eeab25e72c2a63785d45381681bdfb9f850c2e17e721c79750b4d576e6aba52a",
    "postTrainingChallenge": "9b5477bf2e1ea1593b3559a2696a0f5e9043f8d34577fbc86be85663d3d6f5e1",
    "promptSplit": "147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07",
    "promptVariants": "1cd31f9a244f69d9e56c808a51a20ed621ed32785b00b582e4e5fe27bec0b474",
}

CATEGORIES = [
    "INVALID_STRUCTURE",
    "EXTRA_PROSE",
    "TRUNCATED_OUTPUT",
    "UNKNOWN_EVIDENCE_ID",
    "UNKNOWN_CAUSE_ID",
    "UNKNOWN_CHECK_ID",
    "MISSING_CAUSE",
    "MISSING_CHECK",
    "EXTRA_CAUSE",
    "EXTRA_CHECK",
    "WRONG_CAUSE",
    "WRONG_CHECK",
    "OVER_ABSTENTION",
    "UNDER_ABSTENTION",
    "GENERIC_FALLBACK",
    "UNSUPPORTED_CLAIM",
    "CANDIDATE_ORDER_BIAS",
    "PROMPT_TEMPLATE_FAILURE",
    "EOS_OR_STOP_FAILURE",
    "VALIDATOR_FALSE_POSITIVE",
    "VALIDATOR_FALSE_NEGATIVE",
]

RULE_DESCRIPTIONS = {
    "INVALID_STRUCTURE": (
        "The raw output is not exactly one JSON object with the seven required fields, "
        "schema version 2, non-empty summary/explanation strings, and string arrays."
    ),
    "EXTRA_PROSE": (
        "A complete JSON value can be recovered but non-whitespace text remains before or after it."
    ),
    "TRUNCATED_OUTPUT": (
        "The output starts as JSON but ends with an open object, array, or string, or after a comma/colon."
    ),
    "UNKNOWN_EVIDENCE_ID": "At least one actual evidence ID is absent from the locked request facts.",
    "UNKNOWN_CAUSE_ID": "At least one actual cause ID is absent from the locked cause candidates.",
    "UNKNOWN_CHECK_ID": "At least one actual check ID is absent from the locked check candidates.",
    "MISSING_CAUSE": "The locked expected cause set contains IDs absent from the actual cause set.",
    "MISSING_CHECK": "The locked expected check set contains IDs absent from the actual check set.",
    "EXTRA_CAUSE": "The actual cause set contains IDs absent from the locked expected cause set.",
    "EXTRA_CHECK": "The actual check set contains IDs absent from the locked expected check set.",
    "WRONG_CAUSE": "Actual and expected cause IDs differ in both directions.",
    "WRONG_CHECK": "Actual and expected check IDs differ in both directions.",
    "OVER_ABSTENTION": (
        "A diagnose case has an empty cause/check selection and declares evidence insufficient."
    ),
    "UNDER_ABSTENTION": "A restraint case selects at least one cause or check.",
    "GENERIC_FALLBACK": (
        "The output selects a supplied candidate whose ID explicitly identifies it as a fallback."
    ),
    "UNSUPPORTED_CLAIM": (
        "The authoritative scorer reports an unsupported claim, or a selected known candidate has no "
        "intersection with the actual known evidence IDs."
    ),
    "CANDIDATE_ORDER_BIAS": (
        "Reserved for order-correlated errors supported by matched candidate permutations; no label is "
        "assigned without such a control."
    ),
    "PROMPT_TEMPLATE_FAILURE": (
        "For the same base-model case, one prompt condition has diagnostic failures while the other has none."
    ),
    "EOS_OR_STOP_FAILURE": (
        "The output is structurally truncated, or stops at a proper prefix of the required field sequence."
    ),
    "VALIDATOR_FALSE_POSITIVE": (
        "The authoritative validator accepts an output rejected by independent structural/reference rules."
    ),
    "VALIDATOR_FALSE_NEGATIVE": (
        "Reserved for a manually confirmed valid output rejected by the authoritative validator; no label is assigned from partial rule replay alone."
    ),
}


class ContractError(RuntimeError):
    """Raised when the locked evidence bundle is inconsistent."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", required=True, type=Path, help="locked inputs export JSON")
    parser.add_argument("--raw-report", required=True, type=Path, help="locked-evaluation.json")
    parser.add_argument(
        "--authoritative-report",
        required=True,
        type=Path,
        help="locked-evaluation-authoritative.json",
    )
    parser.add_argument("--output", required=True, type=Path, help="new Phase 5.3 diagnostic JSON")
    parser.add_argument(
        "--allow-regenerated-inputs",
        action="store_true",
        help="Allow a fresh export after verifying frozen manifests and result identities.",
    )
    return parser.parse_args()


def duplicate_rejecting_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def load_json(path: Path) -> tuple[dict[str, Any], str]:
    try:
        content = path.read_bytes()
    except OSError as failure:
        raise ContractError(f"cannot read {path}: {failure}") from failure
    try:
        parsed = json.loads(content.decode("utf-8"), object_pairs_hook=duplicate_rejecting_pairs)
    except (UnicodeDecodeError, ValueError, TypeError, json.JSONDecodeError) as failure:
        raise ContractError(f"invalid JSON artifact {path}: {failure}") from failure
    if not isinstance(parsed, dict):
        raise ContractError(f"JSON artifact must be an object: {path}")
    return parsed, sha256_bytes(content)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def require_sha256(value: Any, field: str) -> str:
    require(isinstance(value, str), f"{field} must be a SHA-256 string")
    normalized = value.lower()
    require(bool(SHA256_PATTERN.fullmatch(normalized)), f"{field} is not a SHA-256 digest")
    return normalized


def string_list(value: Any, field: str) -> list[str]:
    require(isinstance(value, list), f"{field} must be an array")
    require(all(isinstance(item, str) and item for item in value), f"{field} must contain strings")
    require(len(set(value)) == len(value), f"{field} contains duplicate IDs")
    return value


def candidate_ids(case: dict[str, Any], key: str) -> list[str]:
    request = case["request"]
    candidates = request.get(key)
    require(isinstance(candidates, list), f"{case['caseId']} request.{key} must be an array")
    ids: list[str] = []
    for index, candidate in enumerate(candidates):
        require(isinstance(candidate, dict), f"{case['caseId']} {key}[{index}] must be an object")
        candidate_id = candidate.get("id")
        require(isinstance(candidate_id, str) and candidate_id, f"{case['caseId']} has invalid candidate ID")
        evidence = string_list(
            candidate.get("evidenceIds"), f"{case['caseId']} {key}[{index}].evidenceIds"
        )
        require(bool(evidence), f"{case['caseId']} candidate {candidate_id} has no evidence IDs")
        ids.append(candidate_id)
    require(len(set(ids)) == len(ids), f"{case['caseId']} has duplicate IDs in {key}")
    return ids


def validate_inputs(
    inputs: dict[str, Any], input_hash: str, allow_regenerated: bool
) -> tuple[dict[tuple[str, str], dict[str, Any]], bool]:
    regenerated = input_hash != EXPECTED_INPUT_SHA256
    require(
        not regenerated or allow_regenerated,
        "locked input export byte hash drifted; explicit regenerated-input authorization required",
    )
    require(inputs.get("schemaVersion") == "1", "locked inputs schemaVersion drifted")
    require(inputs.get("protocol") == "stackmate-phase-5-2-locked-transformers-v1", "locked protocol drifted")
    require(inputs.get("selectionLockedBeforeGeneration") is True, "input selection is not locked")
    require(inputs.get("lockedOutputMustRemainExternal") is True, "external-output lock drifted")
    manifests = inputs.get("manifestSha256")
    require(isinstance(manifests, dict), "locked manifest hashes are missing")
    require(set(manifests) == set(EXPECTED_MANIFEST_SHA256), "locked manifest hash keys drifted")
    for name, expected_hash in EXPECTED_MANIFEST_SHA256.items():
        actual_hash = require_sha256(manifests.get(name), f"manifestSha256.{name}")
        require(actual_hash == expected_hash, f"locked manifest hash drifted: {name}")
    prompts = inputs.get("prompts")
    require(isinstance(prompts, dict) and list(prompts) == ["P0", "P2", "P3"], "locked prompts drifted")
    for prompt_id, prompt in prompts.items():
        require(isinstance(prompt, dict), f"prompt {prompt_id} must be an object")
        prompt_hash = require_sha256(prompt.get("sha256"), f"prompts.{prompt_id}.sha256")
        system = prompt.get("system")
        require(isinstance(system, str) and system, f"prompt {prompt_id} has no system text")
        # The exported hash is the authoritative resource hash. It is retained and checked for shape here;
        # artifact linkage is enforced through the byte hash recorded in both score reports.
        require(bool(prompt_hash), f"prompt {prompt_id} hash is empty")

    stages = inputs.get("stages")
    require(isinstance(stages, dict) and list(stages) == STAGE_ORDER, "locked stage order drifted")
    cases: dict[tuple[str, str], dict[str, Any]] = {}
    global_case_ids: set[str] = set()
    for stage in STAGE_ORDER:
        stage_cases = stages.get(stage)
        require(isinstance(stage_cases, list), f"stage {stage} must be an array")
        require(len(stage_cases) == STAGE_CARDINALITY[stage], f"stage {stage} cardinality drifted")
        for case in stage_cases:
            require(isinstance(case, dict), f"stage {stage} contains a non-object case")
            case_id = case.get("caseId")
            require(isinstance(case_id, str) and case_id, f"stage {stage} contains an invalid caseId")
            require(case.get("stage") == stage, f"case {case_id} stage mapping drifted")
            require(case_id not in global_case_ids, f"duplicate locked caseId: {case_id}")
            global_case_ids.add(case_id)
            request = case.get("request")
            require(isinstance(request, dict), f"case {case_id} has no request")
            require(request.get("schemaVersion") == "2", f"case {case_id} request schema drifted")
            facts = request.get("facts")
            require(isinstance(facts, list), f"case {case_id} request.facts must be an array")
            fact_ids = []
            for fact in facts:
                require(isinstance(fact, dict), f"case {case_id} has a non-object fact")
                fact_id = fact.get("id")
                require(isinstance(fact_id, str) and fact_id, f"case {case_id} has invalid fact ID")
                fact_ids.append(fact_id)
            require(len(set(fact_ids)) == len(fact_ids), f"case {case_id} has duplicate fact IDs")
            causes = candidate_ids(case, "candidateCauses")
            checks = candidate_ids(case, "candidateChecks")
            for candidate_key in ("candidateCauses", "candidateChecks"):
                for candidate in request[candidate_key]:
                    require(
                        set(candidate["evidenceIds"]) <= set(fact_ids),
                        f"case {case_id} candidate {candidate['id']} references unknown evidence",
                    )
            expected_causes = string_list(case.get("expectedCauseIds"), f"{case_id}.expectedCauseIds")
            expected_checks = string_list(case.get("expectedCheckIds"), f"{case_id}.expectedCheckIds")
            require(set(expected_causes) <= set(causes), f"case {case_id} has unknown expected cause IDs")
            require(set(expected_checks) <= set(checks), f"case {case_id} has unknown expected check IDs")
            require(isinstance(case.get("abstentionRequired"), bool), f"case {case_id} abstention flag is invalid")
            if case["abstentionRequired"]:
                require(not expected_causes and not expected_checks, f"restraint case {case_id} has expected IDs")
            cases[(stage, case_id)] = case
    require(len(cases) == 70 and len(global_case_ids) == 70, "locked case cardinality drifted")
    require(bool(input_hash), "locked input byte hash is empty")
    return cases, regenerated


def result_key(result: dict[str, Any], source: str) -> tuple[str, str, str]:
    stage = result.get("stage")
    condition = result.get("condition")
    case_id = result.get("caseId")
    require(stage in STAGE_ORDER, f"{source} has unknown stage: {stage}")
    require(condition in CONDITION_ORDER, f"{source} has unknown condition: {condition}")
    require(isinstance(case_id, str) and case_id, f"{source} has invalid caseId")
    return stage, condition, case_id


def index_results(
    report: dict[str, Any], source: str, cases: dict[tuple[str, str], dict[str, Any]]
) -> dict[tuple[str, str, str], dict[str, Any]]:
    results = report.get("results")
    require(isinstance(results, list), f"{source}.results must be an array")
    require(len(results) == 280, f"{source} result cardinality drifted")
    indexed: dict[tuple[str, str, str], dict[str, Any]] = {}
    for result in results:
        require(isinstance(result, dict), f"{source} contains a non-object result")
        key = result_key(result, source)
        require(key not in indexed, f"duplicate {source} result key: {key}")
        stage, condition, case_id = key
        require((stage, case_id) in cases, f"{source} references unknown case: {(stage, case_id)}")
        require(result.get("promptId") == PROMPT_MAPPING[condition], f"{source} prompt mapping drifted: {key}")
        require(
            result.get("abstentionRequired") == cases[(stage, case_id)]["abstentionRequired"],
            f"{source} abstention mapping drifted: {key}",
        )
        indexed[key] = result
    expected = {
        (stage, condition, case_id)
        for stage, case_id in cases
        for condition in CONDITION_ORDER
    }
    require(set(indexed) == expected, f"{source} result identities are incomplete or unexpected")
    return indexed


def validate_reports(
    raw: dict[str, Any],
    authoritative: dict[str, Any],
    diagnostic_input_hash: str,
    raw_hash: str,
    cases: dict[tuple[str, str], dict[str, Any]],
    regenerated_inputs: bool,
) -> tuple[dict[tuple[str, str, str], dict[str, Any]], dict[tuple[str, str, str], dict[str, Any]]]:
    require(raw_hash == EXPECTED_RAW_SHA256, "raw locked-evaluation byte hash drifted")
    require(raw.get("schemaVersion") == "1", "raw report schemaVersion drifted")
    require(raw.get("status") == "LOCKED_EXECUTION_COMPLETE", "raw report is not complete")
    require(raw.get("protocol") == "stackmate-phase-5-2-locked-transformers-v1", "raw report protocol drifted")
    require(raw.get("stageOrder") == STAGE_ORDER, "raw report stage order drifted")
    require(raw.get("conditionOrder") == CONDITION_ORDER, "raw report condition order drifted")
    require(raw.get("promptMapping") == PROMPT_MAPPING, "raw report prompt mapping drifted")
    require(
        require_sha256(raw.get("inputsSha256"), "raw.inputsSha256")
        == EXPECTED_INPUT_SHA256,
        "raw/original locked-input hash mismatch",
    )

    require(authoritative.get("schemaVersion") == "1", "authoritative report schemaVersion drifted")
    require(authoritative.get("status") == "AUTHORITATIVE_LOCKED_SCORE", "authoritative report status drifted")
    require(authoritative.get("cases") == 280, "authoritative cases cardinality drifted")
    require(
        require_sha256(authoritative.get("inputsSha256"), "authoritative.inputsSha256")
        == EXPECTED_INPUT_SHA256,
        "authoritative/original locked-input hash mismatch",
    )
    require(
        require_sha256(authoritative.get("rawReportSha256"), "authoritative.rawReportSha256") == raw_hash,
        "authoritative/raw report hash mismatch",
    )
    raw_results = index_results(raw, "raw", cases)
    authoritative_results = index_results(authoritative, "authoritative", cases)
    require(set(raw_results) == set(authoritative_results), "raw/authoritative result identities differ")
    for key in raw_results:
        raw_item = raw_results[key]
        auth_item = authoritative_results[key]
        for field in ("stage", "condition", "caseId", "promptId", "abstentionRequired"):
            require(raw_item.get(field) == auth_item.get(field), f"raw/authoritative {field} mismatch: {key}")
        if regenerated_inputs:
            case = cases[(key[0], key[2])]
            known_causes = set(candidate_ids(case, "candidateCauses"))
            known_checks = set(candidate_ids(case, "candidateChecks"))
            actual_causes = set(auth_item.get("selectedCauseIds") or [])
            actual_checks = set(auth_item.get("selectedCheckIds") or [])
            unknown_codes = set(auth_item.get("issueCodes") or [])
            if "UNKNOWN_CAUSE_ID" not in unknown_codes:
                require(
                    actual_causes <= known_causes,
                    f"regenerated cause candidates drifted for {key}",
                )
            if "UNKNOWN_CHECK_ID" not in unknown_codes:
                require(
                    actual_checks <= known_checks,
                    f"regenerated check candidates drifted for {key}",
                )
    require(bool(diagnostic_input_hash), "diagnostic input byte hash is empty")
    return raw_results, authoritative_results


def strict_output(raw_output: str) -> tuple[dict[str, Any] | None, list[str]]:
    issues: list[str] = []
    try:
        value = json.loads(raw_output, object_pairs_hook=duplicate_rejecting_pairs)
    except (ValueError, TypeError, json.JSONDecodeError):
        return None, ["STRUCTURE"]
    if not isinstance(value, dict):
        return None, ["STRUCTURE"]
    if set(value) != set(OUTPUT_KEYS):
        issues.append("STRUCTURE")
    if value.get("schemaVersion") != "2":
        issues.append("SCHEMA_VERSION")
    if not all(isinstance(value.get(key), str) and value[key].strip() for key in ("summary", "explanation")):
        issues.append("STRING_FIELDS")
    for key in LIST_KEYS:
        entries = value.get(key)
        if not isinstance(entries, list) or not all(isinstance(item, str) for item in entries):
            issues.append("LIST_FIELDS")
            break
    return value, list(dict.fromkeys(issues))


def recover_output(raw_output: str) -> tuple[dict[str, Any] | None, bool]:
    """Recover an object for ID comparison without claiming it is structurally valid."""
    decoder = json.JSONDecoder()
    start = raw_output.find("{")
    if start < 0:
        return None, False
    try:
        value, end = decoder.raw_decode(raw_output, start)
    except (ValueError, TypeError, json.JSONDecodeError):
        return None, False
    if not isinstance(value, dict):
        return None, False
    extra = bool(raw_output[:start].strip() or raw_output[end:].strip())
    return value, extra


def looks_truncated(raw_output: str) -> bool:
    text = raw_output.strip()
    if not text.startswith("{"):
        return False
    stack: list[str] = []
    in_string = False
    escaped = False
    pairs = {"}": "{", "]": "["}
    for char in text:
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char in "[{":
            stack.append(char)
        elif char in "]}":
            if not stack or stack[-1] != pairs[char]:
                return False
            stack.pop()
    return in_string or bool(stack) or text.endswith((",", ":"))


def proper_field_prefix(value: dict[str, Any] | None) -> bool:
    if not value:
        return False
    keys = list(value)
    return 0 < len(keys) < len(OUTPUT_KEYS) and keys == OUTPUT_KEYS[: len(keys)]


def safe_actual_list(value: dict[str, Any] | None, key: str) -> list[str]:
    if not value or not isinstance(value.get(key), list):
        return []
    return [item for item in value[key] if isinstance(item, str)]


def positions(ids: Iterable[str], ordered_candidates: list[str]) -> list[int | None]:
    lookup = {candidate_id: index for index, candidate_id in enumerate(ordered_candidates, start=1)}
    return [lookup.get(item) for item in ids]


def candidate_support_issues(case: dict[str, Any], actual: dict[str, list[str]]) -> list[str]:
    request = case["request"]
    facts = {item["id"] for item in request["facts"]}
    causes = {item["id"]: set(item["evidenceIds"]) for item in request["candidateCauses"]}
    checks = {item["id"]: set(item["evidenceIds"]) for item in request["candidateChecks"]}
    used = set(actual["evidence"]) & facts
    issues: list[str] = []
    if any(item not in facts for item in actual["evidence"]):
        issues.append("UNKNOWN_EVIDENCE")
    if any(item not in causes for item in actual["causes"]):
        issues.append("UNKNOWN_CAUSE")
    if any(item not in checks for item in actual["checks"]):
        issues.append("UNKNOWN_CHECK")
    if any(not (causes[item] & used) for item in actual["causes"] if item in causes):
        issues.append("UNSUPPORTED_REFERENCE")
    if any(not (checks[item] & used) for item in actual["checks"] if item in checks):
        issues.append("UNSUPPORTED_REFERENCE")
    if len(set(actual["causes"])) != len(actual["causes"]):
        issues.append("DUPLICATE_SELECTION")
    if len(set(actual["checks"])) != len(actual["checks"]):
        issues.append("DUPLICATE_SELECTION")
    if len(set(actual["evidence"])) != len(actual["evidence"]):
        issues.append("DUPLICATE_SELECTION")
    limitations = actual["limitations"]
    empty = not actual["causes"] and not actual["checks"]
    if empty and not limitations:
        issues.append("MISSING_LIMITATION")
    if not empty and "EVIDENCE_INSUFFICIENT" in limitations:
        issues.append("CONTRADICTORY_SELECTION")
    return list(dict.fromkeys(issues))


def classify_case(
    case: dict[str, Any], raw_item: dict[str, Any], auth_item: dict[str, Any]
) -> dict[str, Any]:
    raw_output = raw_item.get("rawOutput")
    require(isinstance(raw_output, str), f"raw output missing: {result_key(raw_item, 'raw')}")
    parsed, structural_issues = strict_output(raw_output)
    recovered, extra_prose = recover_output(raw_output)
    actual_value = parsed if parsed is not None else recovered
    actual = {
        "causes": safe_actual_list(actual_value, "selectedCauseIds"),
        "checks": safe_actual_list(actual_value, "selectedCheckIds"),
        "evidence": safe_actual_list(actual_value, "usedEvidenceIds"),
        "limitations": safe_actual_list(actual_value, "limitations"),
    }
    expected = {
        "causes": list(case["expectedCauseIds"]),
        "checks": list(case["expectedCheckIds"]),
    }
    cause_candidates = [item["id"] for item in case["request"]["candidateCauses"]]
    check_candidates = [item["id"] for item in case["request"]["candidateChecks"]]
    fact_ids = [item["id"] for item in case["request"]["facts"]]
    support_issues = candidate_support_issues(case, actual)
    independent_issues = list(structural_issues) + support_issues
    validator_accepts = not independent_issues
    authoritative_accepts = bool(auth_item.get("validatorValid"))

    expected_cause_set = set(expected["causes"])
    expected_check_set = set(expected["checks"])
    actual_cause_set = set(actual["causes"])
    actual_check_set = set(actual["checks"])
    missing_causes = sorted(expected_cause_set - actual_cause_set)
    missing_checks = sorted(expected_check_set - actual_check_set)
    extra_causes = sorted(actual_cause_set - expected_cause_set)
    extra_checks = sorted(actual_check_set - expected_check_set)
    unknown_evidence = sorted(set(actual["evidence"]) - set(fact_ids))
    unknown_causes = sorted(actual_cause_set - set(cause_candidates))
    unknown_checks = sorted(actual_check_set - set(check_candidates))

    categories: list[str] = []
    if structural_issues:
        categories.append("INVALID_STRUCTURE")
    if extra_prose:
        categories.append("EXTRA_PROSE")
    truncated = bool(structural_issues and recovered is None and looks_truncated(raw_output))
    early_stop = bool(structural_issues and proper_field_prefix(actual_value))
    if truncated:
        categories.append("TRUNCATED_OUTPUT")
    if unknown_evidence:
        categories.append("UNKNOWN_EVIDENCE_ID")
    if unknown_causes:
        categories.append("UNKNOWN_CAUSE_ID")
    if unknown_checks:
        categories.append("UNKNOWN_CHECK_ID")
    if missing_causes:
        categories.append("MISSING_CAUSE")
    if missing_checks:
        categories.append("MISSING_CHECK")
    if extra_causes:
        categories.append("EXTRA_CAUSE")
    if extra_checks:
        categories.append("EXTRA_CHECK")
    if missing_causes and extra_causes:
        categories.append("WRONG_CAUSE")
    if missing_checks and extra_checks:
        categories.append("WRONG_CHECK")
    limitation_insufficient = "EVIDENCE_INSUFFICIENT" in actual["limitations"]
    if (
        not case["abstentionRequired"]
        and bool(expected_cause_set or expected_check_set)
        and not actual_cause_set
        and not actual_check_set
        and limitation_insufficient
    ):
        categories.append("OVER_ABSTENTION")
    if case["abstentionRequired"] and bool(actual_cause_set or actual_check_set):
        categories.append("UNDER_ABSTENTION")
    known_actual_ids = (actual_cause_set & set(cause_candidates)) | (
        actual_check_set & set(check_candidates)
    )
    if (
        bool(raw_item.get("genericFallbackMisuse"))
        or bool(auth_item.get("genericFallbackMisuse"))
        or any(
            "fallback" in item.lower()
            or item in {"cause.unknown", "check.inspect-root-cause"}
            for item in known_actual_ids
        )
    ):
        categories.append("GENERIC_FALLBACK")
    authoritative_codes = set(auth_item.get("issueCodes") or [])
    if bool(auth_item.get("unsupportedClaimGenerated")) or "UNSUPPORTED_REFERENCE" in support_issues:
        categories.append("UNSUPPORTED_CLAIM")
    if truncated or early_stop:
        categories.append("EOS_OR_STOP_FAILURE")
    if authoritative_accepts and not validator_accepts:
        categories.append("VALIDATOR_FALSE_POSITIVE")
    # A partial structural/reference replay cannot prove that a production
    # semantic rejection is a validator false negative. That category requires
    # independent manual confirmation and is intentionally left unassigned here.

    ordered_categories = [category for category in CATEGORIES if category in categories]
    return {
        "stage": case["stage"],
        "condition": raw_item["condition"],
        "promptId": raw_item["promptId"],
        "modelFamily": MODEL_FAMILY[raw_item["condition"]],
        "caseId": case["caseId"],
        "class": case.get("category"),
        "expectedDisposition": case.get("expectedDisposition"),
        "abstentionRequired": case["abstentionRequired"],
        "diagnosticCategories": ordered_categories,
        "expected": {
            "causeIds": expected["causes"],
            "checkIds": expected["checks"],
        },
        "actual": {
            "causeIds": actual["causes"],
            "checkIds": actual["checks"],
            "evidenceIds": actual["evidence"],
            "limitations": actual["limitations"],
            "recoveredFromInvalidStructure": parsed is None and recovered is not None,
        },
        "differences": {
            "missingCauseIds": missing_causes,
            "extraCauseIds": extra_causes,
            "missingCheckIds": missing_checks,
            "extraCheckIds": extra_checks,
            "unknownEvidenceIds": unknown_evidence,
            "unknownCauseIds": unknown_causes,
            "unknownCheckIds": unknown_checks,
        },
        "candidates": {
            "cause": {
                "ids": cause_candidates,
                "listLength": len(cause_candidates),
                "expectedPositions": positions(expected["causes"], cause_candidates),
                "actualPositions": positions(actual["causes"], cause_candidates),
            },
            "check": {
                "ids": check_candidates,
                "listLength": len(check_candidates),
                "expectedPositions": positions(expected["checks"], check_candidates),
                "actualPositions": positions(actual["checks"], check_candidates),
            },
        },
        "structure": {
            "valid": not structural_issues,
            "extraProse": extra_prose,
            "truncated": truncated,
            "earlyFieldStop": early_stop,
        },
        "validator": {
            "authoritativeValid": authoritative_accepts,
            "authoritativeIssueCodes": sorted(authoritative_codes),
            "independentValid": validator_accepts,
            "independentIssueCodes": independent_issues,
        },
    }


def diagnostic_signature(item: dict[str, Any]) -> set[str]:
    return set(item["diagnosticCategories"]) - {"PROMPT_TEMPLATE_FAILURE"}


def add_comparative_diagnostics(results: list[dict[str, Any]]) -> None:
    """Add only diagnostics supported by a controlled comparison in this bundle."""
    by_case: dict[tuple[str, str], dict[str, dict[str, Any]]] = defaultdict(dict)
    for item in results:
        by_case[(item["stage"], item["caseId"])][item["condition"]] = item
    for conditions in by_case.values():
        b0 = conditions["B0"]
        b1 = conditions["B1"]
        b0_failures = diagnostic_signature(b0)
        b1_failures = diagnostic_signature(b1)
        if b0_failures and not b1_failures:
            b0["diagnosticCategories"].append("PROMPT_TEMPLATE_FAILURE")
        elif b1_failures and not b0_failures:
            b1["diagnosticCategories"].append("PROMPT_TEMPLATE_FAILURE")
        b0["diagnosticCategories"] = [category for category in CATEGORIES if category in b0["diagnosticCategories"]]
        b1["diagnosticCategories"] = [category for category in CATEGORIES if category in b1["diagnosticCategories"]]

    # Candidate-order bias requires matched permutations of the same candidate and expected sets.
    # The frozen export does not provide an explicit permutation-group identifier, so assigning this
    # causal label from a candidate position alone would manufacture evidence.


def dimension_counts(results: list[dict[str, Any]], field: str) -> dict[str, Any]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in results:
        grouped[str(item.get(field))].append(item)
    output: dict[str, Any] = {}
    for name in sorted(grouped):
        items = grouped[name]
        output[name] = {
            "cases": len(items),
            "casesWithDiagnostics": sum(bool(item["diagnosticCategories"]) for item in items),
            "diagnosticCategories": {
                category: sum(category in item["diagnosticCategories"] for item in items)
                for category in CATEGORIES
            },
        }
    return output


def diagnostic_counts(items: list[dict[str, Any]]) -> dict[str, int]:
    return {
        category: sum(category in item["diagnosticCategories"] for item in items)
        for category in CATEGORIES
    }


def candidate_counts(results: list[dict[str, Any]]) -> tuple[dict[str, Any], dict[str, Any]]:
    by_position: dict[str, Any] = {}
    by_length: dict[str, Any] = {}
    for kind in ("cause", "check"):
        expected_positions: Counter[str] = Counter()
        actual_position_items: dict[str, list[dict[str, Any]]] = defaultdict(list)
        length_items: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for item in results:
            candidate = item["candidates"][kind]
            length_items[str(candidate["listLength"])].append(item)
            expected_positions.update("unknown" if value is None else str(value) for value in candidate["expectedPositions"])
            for value in candidate["actualPositions"]:
                actual_position_items["unknown" if value is None else str(value)].append(item)
        by_position[kind] = {
            "expectedSelections": dict(sorted(expected_positions.items())),
            "actualSelections": {
                position: {
                    "selections": len(items),
                    "diagnosticCategories": diagnostic_counts(items),
                }
                for position, items in sorted(actual_position_items.items())
            },
        }
        by_length[kind] = {
            length: {
                "cases": len(items),
                "casesWithDiagnostics": sum(bool(item["diagnosticCategories"]) for item in items),
                "diagnosticCategories": diagnostic_counts(items),
            }
            for length, items in sorted(length_items.items(), key=lambda item: int(item[0]))
        }
    return by_position, by_length


def build_counts(results: list[dict[str, Any]]) -> dict[str, Any]:
    category_counts = {
        category: sum(category in item["diagnosticCategories"] for item in results)
        for category in CATEGORIES
    }
    by_position, by_length = candidate_counts(results)
    return {
        "cases": len(results),
        "casesWithDiagnostics": sum(bool(item["diagnosticCategories"]) for item in results),
        "byClass": dimension_counts(results, "class"),
        "byStage": dimension_counts(results, "stage"),
        "byDiagnosticCategory": category_counts,
        "byCandidatePosition": by_position,
        "byCandidateListLength": by_length,
        "byPrompt": dimension_counts(results, "promptId"),
        "byBaseVsAdapter": dimension_counts(results, "modelFamily"),
        "byCondition": dimension_counts(results, "condition"),
    }


def main() -> None:
    args = parse_args()
    output_path = args.output.expanduser().resolve()
    if output_path.exists():
        raise ContractError(f"diagnostic report already exists; overwrite forbidden: {output_path}")

    inputs_path = args.inputs.expanduser().resolve()
    raw_path = args.raw_report.expanduser().resolve()
    authoritative_path = args.authoritative_report.expanduser().resolve()
    require(len({inputs_path, raw_path, authoritative_path, output_path}) == 4, "input and output paths must be distinct")

    inputs, input_hash = load_json(inputs_path)
    raw, raw_hash = load_json(raw_path)
    authoritative, authoritative_hash = load_json(authoritative_path)
    require(
        authoritative_hash == EXPECTED_AUTHORITATIVE_SHA256,
        "authoritative locked-evaluation byte hash drifted",
    )
    cases, regenerated_inputs = validate_inputs(
        inputs, input_hash, args.allow_regenerated_inputs
    )
    raw_results, authoritative_results = validate_reports(
        raw,
        authoritative,
        input_hash,
        raw_hash,
        cases,
        regenerated_inputs,
    )

    classified: list[dict[str, Any]] = []
    for stage in STAGE_ORDER:
        stage_case_ids = [case_id for case_stage, case_id in cases if case_stage == stage]
        for condition in EXPOSED_CONDITIONS:
            for case_id in stage_case_ids:
                key = (stage, condition, case_id)
                classified.append(
                    classify_case(cases[(stage, case_id)], raw_results[key], authoritative_results[key])
                )
    require(len(classified) == 210, "exposed B0/B1/A1 result cardinality drifted")
    add_comparative_diagnostics(classified)

    report = {
        "schemaVersion": "1",
        "status": "PHASE_5_3_DIAGNOSTICS_COMPLETE",
        "protocol": "stackmate-phase-5-3-locked-failure-classification-v1",
        "scope": {
            "stages": STAGE_ORDER,
            "conditions": EXPOSED_CONDITIONS,
            "promptMapping": {key: PROMPT_MAPPING[key] for key in EXPOSED_CONDITIONS},
            "verifiedAllLockedConditions": CONDITION_ORDER,
        },
        "hashes": {
            "originalLockedInputsSha256": EXPECTED_INPUT_SHA256,
            "diagnosticInputsSha256": input_hash,
            "diagnosticInputsRegenerated": regenerated_inputs,
            "rawReportSha256": raw_hash,
            "authoritativeReportSha256": authoritative_hash,
        },
        "ruleDescriptions": {category: RULE_DESCRIPTIONS[category] for category in CATEGORIES},
        "counts": build_counts(classified),
        "results": classified,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    try:
        with output_path.open("x", encoding="utf-8", newline="\n") as destination:
            destination.write(serialized)
    except FileExistsError as failure:
        raise ContractError(f"diagnostic report already exists; overwrite forbidden: {output_path}") from failure
    print(json.dumps({"status": report["status"], "output": str(output_path), "cases": len(classified)}))


if __name__ == "__main__":
    main()
