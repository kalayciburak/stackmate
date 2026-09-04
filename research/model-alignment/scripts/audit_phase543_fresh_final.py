#!/usr/bin/env python3
"""Audit Phase 5.4.3 fresh-final exporter artifacts before freeze."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import sys
from collections.abc import Mapping
from collections import Counter
from pathlib import Path
from statistics import mean
from typing import Any, Callable


SYSTEM_PROMPT_SHA256 = "9daee6dc707ff1c347346f5f0c667cba2b03035ab3966ddaf362744e0c528897"
BASE_REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
CHAT_TEMPLATE_SHA256 = "273d8e0e683b885071fb17e08d71e5f2a5ddfb5309756181681de4f5a1822d80"
FORBIDDEN_GENERATION_FIELDS = frozenset({"disposition", "purposeLabels", "label"})
EXPECTED_FAMILIES = 30
EXPECTED_REAL_FAMILIES = 24
EXPECTED_SYNTHETIC_FAMILIES = 6
EXPECTED_ORDERS = ("ORIGINAL", "PERMUTED")
CAUSE_ORDER_SALT = "phase543-order-v4"
CHECK_ORDER_SALT = "phase543-order-v6"


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generation-inputs", required=True, type=Path)
    parser.add_argument("--scoring-labels", required=True, type=Path)
    parser.add_argument("--descriptors", required=True, type=Path)
    parser.add_argument("--reproduction", required=True, type=Path)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    return parser.parse_args()


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicate_keys)
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path.name}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            raise ValueError(f"blank JSONL line: {path.name}:{line_number}")
        value = json.loads(line, object_pairs_hook=reject_duplicate_keys)
        if not isinstance(value, dict):
            raise ValueError(f"JSONL object required: {path.name}:{line_number}")
        rows.append(value)
    return rows


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def value_sha256(value: Any) -> str:
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ordering_key(salt: str, family_id: str, kind: str, candidate_id: str) -> str:
    value = f"{salt}|{family_id}|{kind}|{candidate_id}".encode("utf-8")
    return hashlib.sha256(value).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def require_string(value: Any, scope: str) -> str:
    require(isinstance(value, str) and bool(value), f"non-empty string required: {scope}")
    return value


def require_string_list(value: Any, scope: str) -> list[str]:
    require(isinstance(value, list), f"array required: {scope}")
    require(all(isinstance(item, str) and item for item in value), f"string array required: {scope}")
    require(len(value) == len(set(value)), f"duplicate IDs: {scope}")
    return value


def require_indexes(value: Any, bound: int, scope: str) -> list[int]:
    require(isinstance(value, list), f"index array required: {scope}")
    require(
        all(isinstance(item, int) and not isinstance(item, bool) for item in value),
        f"integer index array required: {scope}",
    )
    require(len(value) == len(set(value)), f"duplicate indexes: {scope}")
    require(all(0 <= item < bound for item in value), f"index out of bounds: {scope}")
    return value


def rows_by_family(rows: list[dict[str, Any]], scope: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        family_id = require_string(row.get("familyId"), f"{scope}.familyId")
        require(family_id not in result, f"duplicate familyId in {scope}: {family_id}")
        result[family_id] = row
    return result


def orders_by_name(row: dict[str, Any], scope: str) -> dict[str, dict[str, Any]]:
    orders = row.get("orders")
    require(isinstance(orders, list) and len(orders) == 2, f"exactly two orders required: {scope}")
    result: dict[str, dict[str, Any]] = {}
    for item in orders:
        require(isinstance(item, dict), f"order object required: {scope}")
        name = require_string(item.get("order"), f"{scope}.order")
        require(name not in result, f"duplicate order {name}: {scope}")
        result[name] = item
    require(set(result) == set(EXPECTED_ORDERS), f"ORIGINAL/PERMUTED required: {scope}")
    return result


def candidate_ids(request: dict[str, Any], field: str, scope: str) -> list[str]:
    candidates = request.get(field)
    require(isinstance(candidates, list), f"candidate array required: {scope}.{field}")
    ids = []
    for candidate in candidates:
        require(isinstance(candidate, dict), f"candidate object required: {scope}.{field}")
        ids.append(require_string(candidate.get("id"), f"{scope}.{field}.id"))
    require(len(ids) == len(set(ids)), f"duplicate candidate IDs: {scope}.{field}")
    return ids


def forbidden_fields(value: Any, path: str = "$") -> list[str]:
    found: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key.startswith("expected") or key in FORBIDDEN_GENERATION_FIELDS:
                found.append(child_path)
            found.extend(forbidden_fields(child, child_path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            found.extend(forbidden_fields(child, f"{path}[{index}]"))
    return found


class Audit:
    """Collect independent checks without preventing the final report."""

    def __init__(self, redactions: list[Path]) -> None:
        self.checks: list[dict[str, Any]] = []
        self.redactions = sorted(
            ((str(path), path.name) for path in redactions),
            key=lambda item: len(item[0]),
            reverse=True,
        )

    def safe_error(self, failure: Exception) -> str:
        message = str(failure)
        for absolute, name in self.redactions:
            message = message.replace(absolute, name)
            message = message.replace(absolute.replace("\\", "/"), name)
        return message

    def run(self, name: str, operation: Callable[[], Any]) -> Any | None:
        try:
            result = operation()
            self.checks.append({"name": name, "status": "PASS"})
            return result
        except Exception as failure:  # Report every contract or environment failure.
            self.checks.append(
                {"name": name, "status": "FAIL", "error": self.safe_error(failure)}
            )
            return None

    @property
    def passed(self) -> bool:
        return all(item["status"] == "PASS" for item in self.checks)


def validate_generation(rows: list[dict[str, Any]]) -> dict[str, Any]:
    families = rows_by_family(rows, "generationInputs")
    require(len(families) == EXPECTED_FAMILIES, "generation inputs must contain 30 families")
    classifications = Counter()
    for family_id, row in families.items():
        classification = require_string(row.get("classification"), f"{family_id}.classification")
        if classification.startswith("CONTROLLED_REAL_"):
            classifications["real"] += 1
        else:
            require(
                classification == "CONTROLLED_SYNTHETIC_ADVERSARIAL",
                f"invalid classification: {family_id}",
            )
            classifications["synthetic"] += 1
        leaked = forbidden_fields(row)
        require(not leaked, f"label-bearing fields in generation input {family_id}: {leaked}")
        orders = orders_by_name(row, f"generationInputs.{family_id}")
        order_ids: dict[str, tuple[list[str], list[str]]] = {}
        for order_name, order in orders.items():
            scope = f"{family_id}.{order_name}"
            request = order.get("request")
            require(isinstance(request, dict), f"request object required: {scope}")
            causes = candidate_ids(request, "candidateCauses", scope)
            checks = candidate_ids(request, "candidateChecks", scope)
            declared = order.get("candidateOrder")
            require(isinstance(declared, dict), f"candidateOrder object required: {scope}")
            require(require_string_list(declared.get("causeIds"), f"{scope}.causeIds") == causes,
                    f"cause candidateOrder mismatch: {scope}")
            require(require_string_list(declared.get("checkIds"), f"{scope}.checkIds") == checks,
                    f"check candidateOrder mismatch: {scope}")
            provenance = order.get("candidateProvenance")
            require(isinstance(provenance, dict), f"candidateProvenance object required: {scope}")
            for field, ids in (("causes", causes), ("checks", checks)):
                items = provenance.get(field)
                require(isinstance(items, list), f"provenance array required: {scope}.{field}")
                provenance_ids = [
                    require_string(item.get("id") if isinstance(item, dict) else None,
                                   f"{scope}.candidateProvenance.{field}.id")
                    for item in items
                ]
                require(provenance_ids == ids, f"candidate provenance order mismatch: {scope}.{field}")
            messages = order.get("messages")
            require(isinstance(messages, list) and len(messages) == 2,
                    f"exactly two messages required: {scope}")
            require([item.get("role") if isinstance(item, dict) else None for item in messages]
                    == ["system", "user"], f"system/user messages required: {scope}")
            require(all(set(item) == {"role", "content"} and isinstance(item["content"], str)
                        for item in messages if isinstance(item, dict)), f"invalid message shape: {scope}")
            model_payload = "\n".join(item["content"] for item in messages)
            require(
                "freshfinal" not in model_payload.casefold()
                and "FreshFinalV2RuntimeFixtures" not in model_payload,
                f"fixture identity leaked into model messages: {scope}",
            )
            system_hash = hashlib.sha256(messages[0]["content"].encode("utf-8")).hexdigest()
            require(system_hash == SYSTEM_PROMPT_SHA256, f"system content hash mismatch: {scope}")
            require(order.get("systemPromptSha256") == SYSTEM_PROMPT_SHA256,
                    f"declared system hash mismatch: {scope}")
            require(order.get("requestSha256") == value_sha256(request), f"request hash mismatch: {scope}")
            require(order.get("messagesSha256") == value_sha256(messages), f"messages hash mismatch: {scope}")
            order_ids[order_name] = (causes, checks)
        original, permuted = order_ids["ORIGINAL"], order_ids["PERMUTED"]
        require(set(original[0]) == set(permuted[0]) and set(original[1]) == set(permuted[1]),
                f"permutation changed candidate IDs: {family_id}")
        require(original[0] != permuted[0] and original[1] != permuted[1],
                f"permutation did not change both candidate orders: {family_id}")
        require(
            original[0]
            == sorted(
                original[0],
                key=lambda candidate_id: ordering_key(
                    CAUSE_ORDER_SALT, family_id, "cause", candidate_id
                ),
            ),
            f"original cause ordering policy drifted: {family_id}",
        )
        require(
            original[1]
            == sorted(
                original[1],
                key=lambda candidate_id: ordering_key(
                    CHECK_ORDER_SALT, family_id, "check", candidate_id
                ),
            ),
            f"original check ordering policy drifted: {family_id}",
        )
        require(
            permuted[0] == original[0][1:] + original[0][:1]
            and permuted[1] == original[1][1:] + original[1][:1],
            f"permuted rotation policy drifted: {family_id}",
        )
    require(classifications == {
        "real": EXPECTED_REAL_FAMILIES,
        "synthetic": EXPECTED_SYNTHETIC_FAMILIES,
    },
            f"generation composition must be 24/6, got {dict(classifications)}")
    return {"families": len(families), "classification": dict(sorted(classifications.items()))}


def validate_labels(
    rows: list[dict[str, Any]], generation: dict[str, dict[str, Any]]
) -> dict[str, Any]:
    labels = rows_by_family(rows, "scoringLabels")
    require(set(labels) == set(generation), "generation/label family ID sets differ")
    dispositions = Counter()
    diagnose_cause_positions = Counter()
    diagnose_check_positions = Counter()
    for family_id, label in labels.items():
        disposition = require_string(label.get("disposition"), f"{family_id}.disposition")
        require(disposition in {"DIAGNOSE", "RESTRAINT"}, f"invalid disposition: {family_id}")
        dispositions[disposition] += 1
        purpose_labels = require_string_list(
            label.get("purposeLabels"), f"{family_id}.purposeLabels"
        )
        declared_purposes = set(purpose_labels) & {"DIAGNOSE", "RESTRAINT"}
        require(
            declared_purposes == {disposition},
            f"purposeLabels/disposition mismatch: {family_id}",
        )
        expected_causes = require_string_list(label.get("expectedCauseIds"), f"{family_id}.expectedCauseIds")
        expected_checks = require_string_list(label.get("expectedCheckIds"), f"{family_id}.expectedCheckIds")
        require(bool(expected_causes) == bool(expected_checks), f"cause/check expectation mismatch: {family_id}")
        generation_orders = orders_by_name(generation[family_id], f"generationInputs.{family_id}")
        label_orders = orders_by_name(label, f"scoringLabels.{family_id}")
        for order_name in EXPECTED_ORDERS:
            request = generation_orders[order_name]["request"]
            cause_ids = candidate_ids(request, "candidateCauses", f"{family_id}.{order_name}")
            check_ids = candidate_ids(request, "candidateChecks", f"{family_id}.{order_name}")
            expected = label_orders[order_name]
            cause_indexes = require_indexes(
                expected.get("selectedCauseIndexes"), len(cause_ids),
                f"{family_id}.{order_name}.selectedCauseIndexes",
            )
            check_indexes = require_indexes(
                expected.get("selectedCheckIndexes"), len(check_ids),
                f"{family_id}.{order_name}.selectedCheckIndexes",
            )
            require([cause_ids[index] for index in cause_indexes] == expected_causes,
                    f"expected cause ID/index remap mismatch: {family_id}.{order_name}")
            require([check_ids[index] for index in check_indexes] == expected_checks,
                    f"expected check ID/index remap mismatch: {family_id}.{order_name}")
            limitation = expected.get("limitation")
            if disposition == "RESTRAINT":
                require(not cause_indexes and not check_indexes and limitation == "EVIDENCE_INSUFFICIENT",
                        f"restraint limitation contract violation: {family_id}.{order_name}")
            else:
                require(bool(cause_indexes) and bool(check_indexes) and limitation is None,
                        f"diagnose limitation contract violation: {family_id}.{order_name}")
        if disposition == "DIAGNOSE":
            original_label = label_orders["ORIGINAL"]
            permuted_label = label_orders["PERMUTED"]
            require(
                original_label["selectedCauseIndexes"]
                != permuted_label["selectedCauseIndexes"]
                and original_label["selectedCheckIndexes"]
                != permuted_label["selectedCheckIndexes"],
                f"supported candidate did not move: {family_id}",
            )
            diagnose_cause_positions[original_label["selectedCauseIndexes"][0]] += 1
            diagnose_check_positions[original_label["selectedCheckIndexes"][0]] += 1
    require(dispositions == {"DIAGNOSE": 19, "RESTRAINT": 11},
            f"disposition distribution must be 19/11, got {dict(dispositions)}")
    require(
        max(diagnose_cause_positions.values()) - min(diagnose_cause_positions.values()) <= 1,
        f"diagnose cause positions are imbalanced: {dict(diagnose_cause_positions)}",
    )
    require(
        max(diagnose_check_positions.values()) - min(diagnose_check_positions.values()) <= 1,
        f"diagnose check positions are imbalanced: {dict(diagnose_check_positions)}",
    )
    return {
        "families": len(labels),
        "disposition": dict(sorted(dispositions.items())),
        "originalCausePositions": dict(sorted(diagnose_cause_positions.items())),
        "originalCheckPositions": dict(sorted(diagnose_check_positions.items())),
    }


def validate_descriptors(
    root: dict[str, Any], generation: dict[str, dict[str, Any]]
) -> dict[str, Any]:
    rows = root.get("families")
    require(isinstance(rows, list), "descriptors.families array required")
    descriptors = rows_by_family(rows, "descriptors")
    require(root.get("familyCount") == EXPECTED_FAMILIES, "descriptor familyCount must be 30")
    require(set(descriptors) == set(generation), "generation/descriptor family ID sets differ")
    for family_id, descriptor in descriptors.items():
        require(descriptor.get("classification") == generation[family_id].get("classification"),
                f"descriptor classification mismatch: {family_id}")
        require(descriptor.get("frozenT1SystemPromptSha256") == SYSTEM_PROMPT_SHA256,
                f"descriptor system prompt hash mismatch: {family_id}")
    return {"families": len(descriptors)}


def validate_reproduction(
    root: dict[str, Any], generation: dict[str, dict[str, Any]]
) -> dict[str, Any]:
    rows = root.get("families")
    require(isinstance(rows, list), "reproduction.families array required")
    reproductions = rows_by_family(rows, "reproduction")
    real_ids = {
        family_id for family_id, row in generation.items()
        if str(row.get("classification", "")).startswith("CONTROLLED_REAL_")
    }
    require(len(real_ids) == EXPECTED_REAL_FAMILIES, "generation must expose 24 real family IDs")
    require(set(reproductions) == real_ids, "reproduction IDs must equal the 24 real family IDs")
    require(root.get("realFamilies") == 24, "reproduction.realFamilies must be 24")
    require(root.get("variantsPerFamily") == 2, "reproduction.variantsPerFamily must be 2")
    require(root.get("runsPerVariant") == 2, "reproduction.runsPerVariant must be 2")
    require(root.get("unresolvedFailures") == 0, "reproduction unresolvedFailures must be 0")
    require(root.get("status") == "PASS", "reproduction root status must be PASS")
    run_count = 0
    for family_id, family in reproductions.items():
        require(family.get("status") == "PASS", f"reproduction status must be PASS: {family_id}")
        require(family.get("runsPerVariant") == 2, f"runsPerVariant must be 2: {family_id}")
        variants = family.get("variants")
        require(isinstance(variants, list) and len(variants) == 2, f"two variants required: {family_id}")
        require({item.get("variant") for item in variants if isinstance(item, dict)} == {0, 1},
                f"variant IDs must be 0/1: {family_id}")
        for variant in variants:
            runs = variant.get("runs") if isinstance(variant, dict) else None
            require(isinstance(runs, list) and len(runs) == 2, f"two runs required: {family_id}")
            require({item.get("run") for item in runs if isinstance(item, dict)} == {1, 2},
                    f"run IDs must be 1/2: {family_id}")
            require(all(isinstance(item, dict) and item.get("status") == "PASS" for item in runs),
                    f"all reproduction runs must PASS: {family_id}")
            run_count += len(runs)
    return {"families": len(reproductions), "variants": 48, "runs": run_count, "unresolved": 0}


def percentile95(values: list[int]) -> int:
    return sorted(values)[math.ceil(0.95 * len(values)) - 1]


def token_stats(values: list[int]) -> dict[str, Any]:
    require(bool(values), "token count collection is empty")
    return {
        "count": len(values),
        "mean": round(mean(values), 3),
        "p95": percentile95(values),
        "min": min(values),
        "max": max(values),
    }


def tokenization_audit(
    base_model: Path,
    generation: dict[str, dict[str, Any]],
    labels: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    require(base_model.is_dir(), f"local base model directory not found: {base_model.name}")
    require(base_model.name == BASE_REVISION, "official base revision directory mismatch")
    chat_template = base_model / "chat_template.jinja"
    require(
        chat_template.is_file() and file_sha256(chat_template) == CHAT_TEMPLATE_SHA256,
        "official chat template hash mismatch",
    )
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    os.environ["DISABLE_TELEMETRY"] = "1"
    os.environ["DO_NOT_TRACK"] = "1"
    from transformers import AutoTokenizer  # Imported only after offline controls are set.

    tokenizer = AutoTokenizer.from_pretrained(
        base_model, local_files_only=True, trust_remote_code=False
    )
    prompt_counts: list[int] = []
    target_counts: list[int] = []
    sequence_counts: list[int] = []
    unknown_tokens = 0
    for family_id in sorted(generation):
        generation_orders = orders_by_name(generation[family_id], f"generationInputs.{family_id}")
        label_orders = orders_by_name(labels[family_id], f"scoringLabels.{family_id}")
        for order_name in EXPECTED_ORDERS:
            messages = generation_orders[order_name]["messages"]
            encoded = tokenizer.apply_chat_template(
                messages,
                tokenize=True,
                enable_thinking=False,
                add_generation_prompt=True,
                truncation=False,
                return_dict=True,
            )
            if isinstance(encoded, Mapping):
                encoded = encoded.get("input_ids")
            elif hasattr(encoded, "input_ids"):
                encoded = encoded.input_ids
            if hasattr(encoded, "tolist"):
                encoded = encoded.tolist()
            if isinstance(encoded, list) and encoded and isinstance(encoded[0], list):
                require(len(encoded) == 1, f"unexpected tokenizer batch: {family_id}.{order_name}")
                encoded = encoded[0]
            require(isinstance(encoded, list) and all(isinstance(item, int) for item in encoded),
                    f"tokenizer did not return token IDs: {family_id}.{order_name}")
            prompt_counts.append(len(encoded))
            if tokenizer.unk_token_id is not None:
                unknown_tokens += encoded.count(tokenizer.unk_token_id)
            target = {
                "selectedCauseIndexes": label_orders[order_name]["selectedCauseIndexes"],
                "selectedCheckIndexes": label_orders[order_name]["selectedCheckIndexes"],
                "limitation": label_orders[order_name]["limitation"],
            }
            target_ids = tokenizer(
                canonical_bytes(target).decode("utf-8"), add_special_tokens=False
            )["input_ids"]
            target_counts.append(len(target_ids))
            sequence_counts.append(len(encoded) + len(target_ids))
            if tokenizer.unk_token_id is not None:
                unknown_tokens += target_ids.count(tokenizer.unk_token_id)
    require(len(prompt_counts) == 60, "exactly 60 chat-template tokenizations required")
    require(len(target_counts) == 60, "exactly 60 expected T1 target token counts required")
    over_limit = sum(count > 2048 for count in sequence_counts)
    require(over_limit == 0, f"full T1 sequence token limit exceeded in {over_limit} inputs")
    generation_budget_counts = [count + 128 for count in prompt_counts]
    require(
        all(count <= 2048 for count in generation_budget_counts),
        "prompt plus frozen generation budget exceeds 2048",
    )
    require(unknown_tokens == 0, f"unknown tokens detected: {unknown_tokens}")
    return {
        "baseModel": base_model.name,
        "localFilesOnly": True,
        "trustRemoteCode": False,
        "enableThinking": False,
        "addGenerationPrompt": True,
        "truncation": False,
        "maximumInputTokens": 2048,
        "truncationCount": over_limit,
        "unknownTokenCount": unknown_tokens,
        "prompts": token_stats(prompt_counts),
        "expectedT1Targets": token_stats(target_counts),
        "fullSequences": token_stats(sequence_counts),
        "promptPlusGenerationBudget": token_stats(generation_budget_counts),
        "tokenizerClass": tokenizer.__class__.__name__,
        "paddingSide": tokenizer.padding_side,
        "padTokenId": tokenizer.pad_token_id,
        "eosTokenId": tokenizer.eos_token_id,
        "bosTokenId": tokenizer.bos_token_id,
        "chatTemplateSha256": CHAT_TEMPLATE_SHA256,
    }


def main() -> int:
    args = arguments()
    report_path = args.report.expanduser().resolve()
    if report_path.exists():
        print(f"refusing to overwrite report: {report_path.name}", file=sys.stderr)
        return 2

    paths = {
        "generationInputs": args.generation_inputs.expanduser().resolve(),
        "scoringLabels": args.scoring_labels.expanduser().resolve(),
        "descriptors": args.descriptors.expanduser().resolve(),
        "reproduction": args.reproduction.expanduser().resolve(),
    }
    audit = Audit(
        [*paths.values(), args.base_model.expanduser().resolve(), report_path]
    )
    sources: dict[str, dict[str, Any]] = {}
    for name, path in paths.items():
        digest = audit.run(f"read-and-hash-{name}", lambda path=path: file_sha256(path))
        sources[name] = {"file": path.name, "sha256": digest}

    generation_rows = audit.run("parse-generation-inputs", lambda: read_jsonl(paths["generationInputs"]))
    label_rows = audit.run("parse-scoring-labels", lambda: read_jsonl(paths["scoringLabels"]))
    descriptors = audit.run("parse-descriptors", lambda: read_json(paths["descriptors"]))
    reproduction = audit.run("parse-reproduction", lambda: read_json(paths["reproduction"]))

    generation: dict[str, dict[str, Any]] | None = None
    labels: dict[str, dict[str, Any]] | None = None
    generation_validation: dict[str, Any] | None = None
    label_validation: dict[str, Any] | None = None
    descriptor_validation: dict[str, Any] | None = None
    reproduction_validation: dict[str, Any] | None = None
    if generation_rows is not None:
        generation_validation = audit.run(
            "generation-contract", lambda: validate_generation(generation_rows)
        )
        generation = audit.run("index-generation-families", lambda: rows_by_family(generation_rows, "generationInputs"))
    if label_rows is not None and generation is not None:
        label_validation = audit.run(
            "label-contract", lambda: validate_labels(label_rows, generation)
        )
        labels = audit.run("index-label-families", lambda: rows_by_family(label_rows, "scoringLabels"))
    if descriptors is not None and generation is not None:
        descriptor_validation = audit.run(
            "descriptor-id-contract", lambda: validate_descriptors(descriptors, generation)
        )
    if reproduction is not None and generation is not None:
        reproduction_validation = audit.run(
            "reproduction-contract", lambda: validate_reproduction(reproduction, generation)
        )

    tokenization: dict[str, Any] | None = None
    if generation is not None and labels is not None:
        tokenization = audit.run(
            "offline-tokenization",
            lambda: tokenization_audit(args.base_model.expanduser().resolve(), generation, labels),
        )

    report = {
        "schemaVersion": "1",
        "status": "PASS" if audit.passed else "FAIL",
        "sources": sources,
        "baseModel": {"directoryName": args.base_model.expanduser().resolve().name},
        "counts": {
            "generationRows": len(generation_rows) if generation_rows is not None else None,
            "labelRows": len(label_rows) if label_rows is not None else None,
            "descriptorFamilies": len(descriptors.get("families", [])) if descriptors is not None else None,
            "reproductionFamilies": len(reproduction.get("families", [])) if reproduction is not None else None,
        },
        "distributions": {
            "classification": dict(sorted(Counter(
                "real" if str(row.get("classification", "")).startswith("CONTROLLED_REAL_") else "synthetic"
                for row in (generation_rows or [])
            ).items())),
            "disposition": dict(sorted(Counter(
                str(row.get("disposition")) for row in (label_rows or [])
            ).items())),
        },
        "tokenization": tokenization,
        "validated": {
            "generation": generation_validation,
            "labels": label_validation,
            "descriptors": descriptor_validation,
            "reproduction": reproduction_validation,
        },
        "checks": audit.checks,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with report_path.open("x", encoding="utf-8", newline="\n") as destination:
            json.dump(report, destination, ensure_ascii=False, indent=2)
            destination.write("\n")
    except FileExistsError:
        print(f"refusing to overwrite report: {report_path.name}", file=sys.stderr)
        return 2
    print(json.dumps({"status": report["status"], "report": report_path.name}, separators=(",", ":")))
    return 0 if audit.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
