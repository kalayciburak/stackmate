#!/usr/bin/env python3
"""Run one label-blind Phase 5.4.3 D0, B0, or A1 generation attempt."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import math
import os
import platform
import socket
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable


REPOSITORY = Path(__file__).resolve().parents[3]
BASE_REPOSITORY = "Qwen/Qwen3.5-2B"
BASE_REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
BASE_MANIFEST_SHA256 = "1dc49db3fd0a49ee7bf33190b04c1bac5d1ed489e87855a81c64ac7344571981"
ADAPTER_SHA256 = "7848c9a140d2616e009b5179aa4a02867590f95dd261154c0b983ee45c187b49"
SELECTION_COMMIT = "08b5b55adee82a83c3d81e599deca8ca6b3458ca"
SYSTEM_PROMPT_SHA256 = "9daee6dc707ff1c347346f5f0c667cba2b03035ab3966ddaf362744e0c528897"
ORDER = ("ORIGINAL", "PERMUTED")
SYSTEMS = ("D0", "B0", "A1")
FREEZE_TAG = "phase-5-4-3-fresh-final-frozen"
FAMILY_COUNT = 30
PREDICTION_COUNT = FAMILY_COUNT * len(ORDER)
GENERATION = {
    "maxNewTokens": 128,
    "doSample": False,
    "numBeams": 1,
    "enableThinking": False,
    "useCache": True,
}
ABSTENTION = {
    "selectedCauseIndexes": [],
    "selectedCheckIndexes": [],
    "limitation": "EVIDENCE_INSUFFICIENT",
}
FORBIDDEN_FIELD_STEMS = ("label", "expected", "disposition", "purpose")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--system", required=True, choices=SYSTEMS)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--base-model", type=Path)
    parser.add_argument("--adapter", type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    return parser.parse_args()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def directory_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    for item in sorted(candidate for candidate in path.rglob("*") if candidate.is_file()):
        digest.update(item.relative_to(path).as_posix().encode("utf-8"))
        digest.update(b"\0")
        with item.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def compact_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def duplicate_rejecting_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(
        path.read_text(encoding="utf-8"), object_pairs_hook=duplicate_rejecting_pairs
    )
    require(isinstance(value, dict), f"JSON object required: {path.name}")
    return value


def require_sha256(value: Any, name: str) -> str:
    require(
        isinstance(value, str)
        and len(value) == 64
        and all(character in "0123456789abcdefABCDEF" for character in value),
        f"{name} must be a SHA-256 checksum",
    )
    return value.lower()


def nested_dicts(value: Any) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from nested_dicts(child)
    elif isinstance(value, list):
        for child in value:
            yield from nested_dicts(child)


def manifest_scalar(manifest: dict[str, Any], names: tuple[str, ...], label: str) -> Any:
    found: list[Any] = []
    for item in nested_dicts(manifest):
        for name in names:
            if name in item and not isinstance(item[name], (dict, list)):
                found.append(item[name])
    unique = []
    for value in found:
        if value not in unique:
            unique.append(value)
    require(len(unique) == 1, f"manifest must bind exactly one {label}")
    return unique[0]


def resolve_manifest_path(raw: str, manifest_path: Path) -> Path:
    candidate = Path(raw).expanduser()
    if candidate.is_absolute():
        return candidate.resolve()
    choices = ((manifest_path.parent / candidate).resolve(), (REPOSITORY / candidate).resolve())
    existing = [path for path in choices if path.exists()]
    require(len(existing) == 1 or (len(existing) == 2 and existing[0] == existing[1]),
            f"manifest path is missing or ambiguous: {candidate.as_posix()}")
    return existing[0]


def artifact_spec(
    manifest: dict[str, Any],
    manifest_path: Path,
    *,
    logical_names: tuple[str, ...],
    path_fields: tuple[str, ...],
    sha_fields: tuple[str, ...],
    filename_test: Callable[[str], bool],
    label: str,
) -> tuple[Path, str]:
    candidates: list[tuple[str, Any]] = []
    for item in nested_dicts(manifest):
        for logical_name in logical_names:
            nested = item.get(logical_name)
            if isinstance(nested, dict):
                raw_path = nested.get(
                    "path",
                    nested.get("file", nested.get("fileName", nested.get("relativePath"))),
                )
                raw_sha = nested.get(
                    "sha256", nested.get("fileSha256", nested.get("expectedSha256"))
                )
                if isinstance(raw_path, str) and raw_sha is not None:
                    candidates.append((raw_path, raw_sha))
        raw_paths = [item[name] for name in path_fields if isinstance(item.get(name), str)]
        raw_hashes = [item[name] for name in sha_fields if item.get(name) is not None]
        if len(raw_paths) == 1 and len(raw_hashes) == 1:
            candidates.append((raw_paths[0], raw_hashes[0]))
        for raw_path, raw_sha in item.items():
            if isinstance(raw_path, str) and filename_test(raw_path) and isinstance(raw_sha, str):
                candidates.append((raw_path, raw_sha))
            elif isinstance(raw_path, str) and filename_test(raw_path) and isinstance(raw_sha, dict):
                nested_sha = raw_sha.get(
                    "sha256", raw_sha.get("fileSha256", raw_sha.get("expectedSha256"))
                )
                if nested_sha is not None:
                    candidates.append((raw_path, nested_sha))
        named_path = item.get(
            "path", item.get("file", item.get("fileName", item.get("relativePath")))
        )
        named_sha = item.get("sha256", item.get("fileSha256", item.get("expectedSha256")))
        if isinstance(named_path, str) and filename_test(named_path) and named_sha is not None:
            candidates.append((named_path, named_sha))
    normalized: list[tuple[Path, str]] = []
    for raw_path, raw_sha in candidates:
        pair = (resolve_manifest_path(raw_path, manifest_path), require_sha256(raw_sha, label))
        if pair not in normalized:
            normalized.append(pair)
    require(len(normalized) == 1, f"manifest must bind exactly one {label} file and checksum")
    path, expected_sha = normalized[0]
    require(path.is_file(), f"bound {label} file is missing")
    require(sha256_file(path) == expected_sha, f"bound {label} checksum drifted")
    return path, expected_sha


def reject_forbidden_fields(value: Any, location: str = "$") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            folded = key.casefold()
            require(
                not any(stem in folded for stem in FORBIDDEN_FIELD_STEMS),
                f"label-bearing field is forbidden at {location}.{key}",
            )
            reject_forbidden_fields(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_forbidden_fields(child, f"{location}[{index}]")


def read_generation_inputs(path: Path, prompt_sha: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        require(line.strip() != "", f"blank generation input line: {line_number}")
        row = json.loads(line, object_pairs_hook=duplicate_rejecting_pairs)
        require(isinstance(row, dict), f"generation input line {line_number} is not an object")
        reject_forbidden_fields(row)
        require(
            set(row) == {"schemaVersion", "familyId", "classification", "orders"},
            f"generation input root shape drifted at line {line_number}",
        )
        require(row["schemaVersion"] == "1", "generation input schema version drifted")
        require(isinstance(row["familyId"], str) and row["familyId"], "familyId is missing")
        require(isinstance(row["classification"], str) and row["classification"], "classification is missing")
        orders = row["orders"]
        require(isinstance(orders, list) and len(orders) == 2, "each family must contain two orders")
        require([item.get("order") for item in orders if isinstance(item, dict)] == list(ORDER),
                "candidate order must be ORIGINAL then PERMUTED")
        for order in orders:
            verify_generation_order(order, prompt_sha)
        rows.append(row)
    require(len(rows) == FAMILY_COUNT, "generation-inputs.jsonl must contain exactly 30 rows")
    family_ids = [row["familyId"] for row in rows]
    require(len(set(family_ids)) == FAMILY_COUNT, "generation family IDs must be unique")
    return rows


def candidate_ids(request: dict[str, Any], field: str) -> list[str]:
    values = request.get(field)
    require(isinstance(values, list) and values, f"request.{field} must be a non-empty array")
    ids = [item.get("id") for item in values if isinstance(item, dict)]
    require(len(ids) == len(values) and all(isinstance(value, str) and value for value in ids),
            f"request.{field} IDs are invalid")
    require(len(set(ids)) == len(ids), f"request.{field} IDs are not unique")
    return ids


def verify_generation_order(order: Any, prompt_sha: str) -> None:
    require(isinstance(order, dict), "generation order must be an object")
    required = {
        "order", "request", "candidateOrder", "candidateProvenance", "messages",
        "requestSha256", "messagesSha256", "systemPromptSha256",
    }
    require(set(order) == required, "generation order shape drifted")
    request = order["request"]
    require(isinstance(request, dict), "generation request must be an object")
    cause_ids = candidate_ids(request, "candidateCauses")
    check_ids = candidate_ids(request, "candidateChecks")
    candidate_order = order["candidateOrder"]
    require(isinstance(candidate_order, dict) and set(candidate_order) == {"causeIds", "checkIds"},
            "candidateOrder shape drifted")
    require(candidate_order["causeIds"] == cause_ids and candidate_order["checkIds"] == check_ids,
            "candidateOrder does not match request candidates")
    provenance = order["candidateProvenance"]
    require(isinstance(provenance, dict) and set(provenance) == {"causes", "checks"},
            "candidateProvenance shape drifted")
    for field, expected_ids in (("causes", cause_ids), ("checks", check_ids)):
        values = provenance[field]
        require(isinstance(values, list) and len(values) == len(expected_ids),
                f"candidateProvenance.{field} cardinality drifted")
        require([item.get("id") for item in values if isinstance(item, dict)] == expected_ids,
                f"candidateProvenance.{field} order drifted")
        require(all(set(item) == {"id", "origin"} and isinstance(item["origin"], str)
                    for item in values if isinstance(item, dict)),
                f"candidateProvenance.{field} shape drifted")
    messages = order["messages"]
    require(
        isinstance(messages, list)
        and len(messages) == 2
        and [message.get("role") for message in messages if isinstance(message, dict)] == ["system", "user"]
        and all(set(message) == {"role", "content"} and isinstance(message["content"], str) for message in messages),
        "messages must contain exactly system and user",
    )
    require(sha256_bytes(canonical_bytes(request)) == require_sha256(order["requestSha256"], "requestSha256"),
            "requestSha256 drifted")
    require(sha256_bytes(canonical_bytes(messages)) == require_sha256(order["messagesSha256"], "messagesSha256"),
            "messagesSha256 drifted")
    require(require_sha256(order["systemPromptSha256"], "systemPromptSha256") == prompt_sha,
            "system prompt binding drifted")
    require(sha256_bytes(messages[0]["content"].encode("utf-8")) == prompt_sha,
            "system prompt content checksum drifted")


def validate_base_manifest(path: Path, base_path: Path) -> dict[str, Any]:
    manifest = read_json(path)
    require(manifest.get("repositoryId") == BASE_REPOSITORY, "base manifest repository drifted")
    require(manifest.get("officialRevision") == BASE_REVISION, "base manifest revision drifted")
    require(manifest.get("resolvedRevision") == BASE_REVISION, "base resolved revision drifted")
    files = manifest.get("files")
    require(isinstance(files, list) and files, "base manifest expected files are missing")
    seen: set[str] = set()
    for item in files:
        require(isinstance(item, dict), "base manifest file entry is invalid")
        name = item.get("name")
        require(isinstance(name, str) and name and name not in seen, "base manifest file name is invalid")
        require(Path(name).name == name, "base manifest file paths must be flat")
        seen.add(name)
        expected = require_sha256(item.get("sha256"), f"base file {name}")
        candidate = base_path / name
        require(candidate.is_file(), f"base model is missing {name}")
        require(sha256_file(candidate) == expected, f"base model file checksum drifted: {name}")
    return manifest


def verify_selection_commit(commit: str) -> None:
    completed = subprocess.run(
        ["git", "cat-file", "-e", f"{commit}^{{commit}}"],
        cwd=REPOSITORY,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    require(completed.returncode == 0, "adapter selection commit is unavailable")


def git_output(*arguments: str, text: bool = True) -> str | bytes:
    completed = subprocess.run(
        ["git", *arguments],
        cwd=REPOSITORY,
        stdin=subprocess.DEVNULL,
        capture_output=True,
        text=text,
        check=False,
    )
    require(completed.returncode == 0, f"git verification failed: {' '.join(arguments)}")
    return completed.stdout.strip() if text else completed.stdout


def verify_frozen_git_state(manifest_path: Path, manifest_sha: str) -> None:
    require(REPOSITORY in manifest_path.parents, "frozen manifest must be inside the repository")
    require(git_output("cat-file", "-t", FREEZE_TAG) == "tag", "freeze tag must be annotated")
    tag_commit = git_output("rev-parse", f"{FREEZE_TAG}^{{commit}}")
    head = git_output("rev-parse", "HEAD")
    require(head == tag_commit, "HEAD must equal the frozen-corpus tag commit")
    require(git_output("status", "--porcelain") == "", "worktree must be clean before generation")
    relative = manifest_path.relative_to(REPOSITORY).as_posix()
    tracked = git_output("ls-files", "--error-unmatch", relative)
    require(tracked == relative, "frozen manifest is not tracked")
    blob = git_output("show", f"{tag_commit}:{relative}", text=False)
    require(isinstance(blob, bytes) and sha256_bytes(blob) == manifest_sha,
            "tagged manifest blob checksum drifted")


def disable_network() -> None:
    os.environ.update({
        "HF_HUB_OFFLINE": "1",
        "TRANSFORMERS_OFFLINE": "1",
        "HF_HUB_DISABLE_TELEMETRY": "1",
        "DISABLE_TELEMETRY": "1",
        "WANDB_DISABLED": "true",
        "TOKENIZERS_PARALLELISM": "false",
    })

    def blocked(*_args: Any, **_kwargs: Any) -> Any:
        raise RuntimeError("network access is disabled during Phase 5.4.3 generation")

    socket.create_connection = blocked
    socket.getaddrinfo = blocked


def require_execution_order(output_root: Path, system: str, manifest_sha: str) -> None:
    positions = {name: index for index, name in enumerate(SYSTEMS)}
    current = positions[system]
    for name in SYSTEMS:
        directory = output_root / name
        if positions[name] < current:
            receipt = directory / "execution-receipt.json"
            require(receipt.is_file(), f"{name} must complete before {system}")
            value = read_json(receipt)
            require(
                value.get("status") == "COMPLETE" and value.get("system") == name,
                f"{name} receipt is not complete",
            )
            require(
                value.get("manifestSha256") == manifest_sha,
                f"{name} receipt uses a different frozen manifest",
            )
        else:
            require(not directory.exists(), f"{name} generation attempt already exists")


def d0_output(order: dict[str, Any]) -> str:
    causes = order["candidateProvenance"]["causes"]
    eligible = [
        (index, item["id"])
        for index, item in enumerate(causes)
        if item["origin"] == "DETERMINISTIC_ANALYZER"
        and item["id"].startswith("cause.")
        and item["id"] != "cause.unknown"
        and not item["id"].endswith(".driver-error")
        and len(item["id"]) > len("cause.")
    ]
    if len(eligible) != 1:
        return compact_json(ABSTENTION)
    cause_index, cause_id = eligible[0]
    matching_check = "check." + cause_id[len("cause."):]
    matches = [
        index
        for index, item in enumerate(order["candidateProvenance"]["checks"])
        if item["id"] == matching_check and item["origin"] == "DETERMINISTIC_ANALYZER"
    ]
    if len(matches) != 1:
        return compact_json(ABSTENTION)
    return compact_json({
        "selectedCauseIndexes": [cause_index],
        "selectedCheckIndexes": [matches[0]],
        "limitation": None,
    })


def load_model(system: str, base_path: Path, adapter_path: Path | None) -> tuple[Any, Any, Any]:
    import torch
    from transformers import AutoModelForMultimodalLM, AutoTokenizer

    require(torch.cuda.is_available(), "CUDA is unavailable")
    require(torch.cuda.is_bf16_supported(), "BF16 CUDA is unavailable")
    device_index = torch.cuda.current_device()
    tokenizer = AutoTokenizer.from_pretrained(
        base_path, local_files_only=True, trust_remote_code=False
    )
    require(tokenizer.pad_token_id is not None and tokenizer.eos_token_id is not None,
            "tokenizer pad/eos tokens are missing")
    model = AutoModelForMultimodalLM.from_pretrained(
        base_path,
        local_files_only=True,
        trust_remote_code=False,
        dtype=torch.bfloat16,
        device_map={"": device_index},
    )
    if system == "A1":
        from peft import PeftModel

        require(adapter_path is not None, "A1 adapter is missing")
        model = PeftModel.from_pretrained(
            model, adapter_path, local_files_only=True, is_trainable=False
        )
    model.eval()
    return torch, tokenizer, model


def model_output(torch: Any, tokenizer: Any, model: Any, order: dict[str, Any]) -> tuple[str, int, int, float, int, int]:
    encoded = tokenizer.apply_chat_template(
        order["messages"],
        tokenize=True,
        add_generation_prompt=True,
        enable_thinking=False,
        return_dict=True,
        return_tensors="pt",
    )
    input_ids = encoded["input_ids"]
    attention_mask = encoded.get("attention_mask", torch.ones_like(input_ids))
    require(
        input_ids.shape[1] + GENERATION["maxNewTokens"] <= 2048,
        "tokenized input plus output budget exceeds maximum sequence length",
    )
    device = next(model.parameters()).device
    input_ids = input_ids.to(device)
    attention_mask = attention_mask.to(device)
    torch.cuda.synchronize(device)
    torch.cuda.reset_peak_memory_stats(device)
    started = time.perf_counter()
    with torch.inference_mode():
        generated = model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_new_tokens=GENERATION["maxNewTokens"],
            do_sample=GENERATION["doSample"],
            num_beams=GENERATION["numBeams"],
            use_cache=GENERATION["useCache"],
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    torch.cuda.synchronize(device)
    latency = time.perf_counter() - started
    output_ids = generated[0, input_ids.shape[1]:]
    raw = tokenizer.decode(output_ids, skip_special_tokens=True).strip()
    input_tokens = int(attention_mask.sum().item())
    output_tokens = int(output_ids.shape[0])
    return (
        raw,
        input_tokens,
        output_tokens,
        latency,
        int(torch.cuda.max_memory_allocated(device)),
        int(torch.cuda.max_memory_reserved(device)),
    )


def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def percentile95(values: list[float]) -> float:
    require(bool(values), "performance sample is empty")
    ordered = sorted(values)
    return ordered[max(0, math.ceil(0.95 * len(ordered)) - 1)]


def package_versions(system: str) -> dict[str, str]:
    del system
    result: dict[str, str] = {}
    for name in ("torch", "transformers", "peft"):
        try:
            result[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            result[name] = "NOT_INSTALLED"
    return result


def write_json_new(path: Path, value: dict[str, Any]) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(value, destination, ensure_ascii=False, sort_keys=True, indent=2)
        destination.write("\n")
        destination.flush()
        os.fsync(destination.fileno())


def main() -> None:
    arguments = parse_args()
    manifest_path = arguments.manifest.expanduser().resolve()
    output_root = arguments.output_root.expanduser()
    require(output_root.is_absolute(), "--output-root must be absolute")
    output_root = output_root.resolve()
    require(output_root != REPOSITORY and REPOSITORY not in output_root.parents,
            "--output-root must be outside the repository")
    require(manifest_path.is_file(), "manifest is missing")
    manifest_sha = require_sha256(arguments.manifest_sha256, "manifestSha256")
    require(sha256_file(manifest_path) == manifest_sha, "manifest checksum drifted")
    manifest = read_json(manifest_path)
    require(manifest.get("status") == "FROZEN", "manifest status is not FROZEN")
    verify_frozen_git_state(manifest_path, manifest_sha)
    require_execution_order(output_root, arguments.system, manifest_sha)

    base_revision = manifest_scalar(manifest, ("baseRevision", "baseModelRevision"), "base revision")
    adapter_sha = require_sha256(
        manifest_scalar(manifest, ("adapterSha256",), "adapter checksum"), "adapterSha256"
    )
    selection_commit = manifest_scalar(
        manifest, ("selectionCommit", "adapterSelectionCommit"), "selection commit"
    )
    prompt_sha = require_sha256(
        manifest_scalar(
            manifest,
            ("systemPromptSha256", "promptSha256", "t1PromptSha256"),
            "system prompt checksum",
        ),
        "systemPromptSha256",
    )
    require(base_revision == BASE_REVISION, "manifest base revision drifted")
    require(adapter_sha == ADAPTER_SHA256, "manifest adapter checksum drifted")
    require(selection_commit == SELECTION_COMMIT, "manifest selection commit drifted")
    require(prompt_sha == SYSTEM_PROMPT_SHA256, "manifest system prompt checksum drifted")

    inputs_path, inputs_sha = artifact_spec(
        manifest, manifest_path,
        logical_names=("generationInputs", "generationInput"),
        path_fields=("generationInputsPath", "generationInputPath"),
        sha_fields=("generationInputsSha256", "generationInputSha256"),
        filename_test=lambda value: Path(value).name == "generation-inputs.jsonl",
        label="generation inputs",
    )
    base_manifest_path, base_manifest_sha = artifact_spec(
        manifest, manifest_path,
        logical_names=("baseModelManifest", "baseManifest"),
        path_fields=("baseModelManifestPath", "baseManifestPath"),
        sha_fields=("baseModelManifestSha256", "baseManifestSha256"),
        filename_test=lambda value: Path(value).name == "base-model-manifest.json",
        label="base model manifest",
    )
    schema_path, schema_sha = artifact_spec(
        manifest, manifest_path,
        logical_names=("t1Schema", "outputSchema", "schema"),
        path_fields=("t1SchemaPath", "outputSchemaPath", "schemaPath"),
        sha_fields=("t1SchemaSha256", "outputSchemaSha256", "schemaSha256"),
        filename_test=lambda value: Path(value).name == "t1-output.schema.json",
        label="T1 schema",
    )
    scorer_path, scorer_sha = artifact_spec(
        manifest, manifest_path,
        logical_names=("scorer", "scoringProgram"),
        path_fields=("scorerPath", "scoringProgramPath"),
        sha_fields=("scorerSha256", "scoringProgramSha256"),
        filename_test=lambda value: Path(value).suffix == ".py"
        and "scor" in Path(value).stem.casefold(),
        label="scorer",
    )
    generation_config_path, generation_config_sha = artifact_spec(
        manifest, manifest_path,
        logical_names=("generationConfig",),
        path_fields=("generationConfigPath",),
        sha_fields=("generationConfigSha256",),
        filename_test=lambda value: Path(value).name == "phase-5-4-3-generation.json",
        label="generation config",
    )
    selection_path, selection_sha = artifact_spec(
        manifest, manifest_path,
        logical_names=("selectionReport", "adapterSelection"),
        path_fields=("selectionReportPath", "adapterSelectionPath"),
        sha_fields=("selectionReportSha256", "adapterSelectionSha256"),
        filename_test=lambda value: Path(value).name == "phase-5-4-1-selected-adapter.json",
        label="selection report",
    )
    del schema_path, scorer_path

    bindings = manifest.get("bindings")
    require(isinstance(bindings, dict), "manifest bindings are missing")
    require(bindings.get("baseModelManifestSha256") == base_manifest_sha == BASE_MANIFEST_SHA256,
            "base model manifest binding drifted")
    require(bindings.get("generationInputsSha256") == inputs_sha,
            "generation input artifact binding drifted")
    require(bindings.get("t1SchemaSha256") == schema_sha, "T1 schema artifact binding drifted")
    require(bindings.get("scorerSha256") == scorer_sha, "scorer artifact binding drifted")
    require(bindings.get("generationConfigSha256") == generation_config_sha,
            "generation config artifact binding drifted")
    require(bindings.get("generationRunnerSha256") == sha256_file(Path(__file__).resolve()),
            "generation runner binding drifted")
    require(bindings.get("baseModelRepository") == BASE_REPOSITORY,
            "base repository binding drifted")

    generation_config = read_json(generation_config_path)
    require(generation_config.get("profileId") == "P5.4.3-T1-EXACTLY-ONCE",
            "generation profile drifted")
    require(generation_config.get("contract") == "T1_SELECTION_ONLY_ORDINAL",
            "generation contract drifted")
    require(generation_config.get("baseRepository") == BASE_REPOSITORY,
            "generation base repository drifted")
    require(generation_config.get("baseRevision") == BASE_REVISION, "generation config revision drifted")
    require(generation_config.get("precision") == "BF16", "generation precision drifted")
    require(generation_config.get("maximumSequenceLength") == 2048,
            "maximum sequence length drifted")
    require(generation_config.get("maxNewTokens") == GENERATION["maxNewTokens"], "maxNewTokens drifted")
    require(generation_config.get("doSample") is GENERATION["doSample"], "doSample drifted")
    require(generation_config.get("numBeams") == GENERATION["numBeams"], "numBeams drifted")
    require(generation_config.get("enableThinking") is GENERATION["enableThinking"], "enableThinking drifted")
    require(generation_config.get("useCache") is GENERATION["useCache"], "useCache drifted")
    require(generation_config.get("batchSize") == 1, "generation batch size drifted")
    require(generation_config.get("candidateOrders") == list(ORDER), "generation order config drifted")
    require(generation_config.get("systems") == list(SYSTEMS), "generation systems config drifted")
    require(generation_config.get("executionAttemptsPerSystem") == 1, "exactly-once config drifted")
    require(generation_config.get("localFilesOnly") is True, "local-only config drifted")
    require(generation_config.get("trustRemoteCode") is False, "remote-code config drifted")
    require(generation_config.get("telemetry") is False, "telemetry config drifted")
    require(generation_config.get("remoteReporting") is False, "remote reporting config drifted")

    selection = read_json(selection_path)
    require(selection.get("adapterSha256") == ADAPTER_SHA256, "selection adapter binding drifted")
    require(selection.get("baseModelRevision") == BASE_REVISION, "selection base revision drifted")
    require(selection.get("selectionCommit") == SELECTION_COMMIT, "selection commit binding drifted")
    require(selection.get("lockedFinalOpened") is False, "selection was made after locked access")
    require(selection.get("selectionUsesLockedData") is False, "selection used locked data")
    require(selection.get("status") == "SELECTED_VALIDATION_ONLY", "selection status drifted")
    require(selection.get("baseModelRepository") == BASE_REPOSITORY, "selection repository drifted")
    verify_selection_commit(selection_commit)
    rows = read_generation_inputs(inputs_path, prompt_sha)

    base_path: Path | None = None
    adapter_path: Path | None = None
    if arguments.system == "D0":
        require(arguments.base_model is None, "D0 does not accept --base-model")
        require(arguments.adapter is None, "--adapter is accepted only for A1")
    else:
        require(arguments.base_model is not None, f"{arguments.system} requires --base-model")
        base_path = arguments.base_model.expanduser().resolve()
        require(base_path.is_dir() and base_path.name == BASE_REVISION,
                "base model path must be the exact revision directory")
        validate_base_manifest(base_manifest_path, base_path)
        if arguments.system == "A1":
            require(arguments.adapter is not None, "A1 requires --adapter")
            adapter_path = arguments.adapter.expanduser().resolve()
            require(adapter_path.is_dir(), "adapter directory is missing")
            require(REPOSITORY not in adapter_path.parents, "adapter must remain outside the repository")
            require(directory_sha256(adapter_path) == ADAPTER_SHA256, "adapter directory checksum drifted")
        else:
            require(arguments.adapter is None, "--adapter is accepted only for A1")

    disable_network()
    torch = tokenizer = model = None
    if arguments.system != "D0":
        require(base_path is not None, "base model path is missing")
        torch, tokenizer, model = load_model(arguments.system, base_path, adapter_path)

    system_directory = output_root / arguments.system
    system_directory.mkdir(parents=True, exist_ok=False)
    predictions_path = system_directory / "predictions.jsonl"
    receipt_path = system_directory / "execution-receipt.json"
    started_at = utc_now()
    wall_started = time.perf_counter()
    latencies: list[float] = []
    peak_allocated = 0
    peak_reserved = 0
    prediction_rows = 0
    with predictions_path.open("x", encoding="utf-8", newline="\n") as predictions:
        for family in rows:
            for order in family["orders"]:
                if arguments.system == "D0":
                    generated = (d0_output(order), 0, 0, 0.0, 0, 0)
                else:
                    generated = model_output(torch, tokenizer, model, order)
                raw, input_tokens, output_tokens, latency, allocated, reserved = generated
                prediction = {
                    "familyId": family["familyId"],
                    "order": order["order"],
                    "system": arguments.system,
                    "rawOutput": raw,
                    "rawOutputSha256": sha256_bytes(raw.encode("utf-8")),
                    "requestSha256": order["requestSha256"],
                    "messagesSha256": order["messagesSha256"],
                    "promptSha": prompt_sha,
                    "systemPromptSha256": prompt_sha,
                    "inputTokens": input_tokens,
                    "outputTokens": output_tokens,
                    "latencySeconds": round(latency, 6),
                    "peakAllocatedBytes": allocated,
                    "peakReservedBytes": reserved,
                }
                predictions.write(canonical_bytes(prediction).decode("utf-8") + "\n")
                predictions.flush()
                os.fsync(predictions.fileno())
                prediction_rows += 1
                latencies.append(latency)
                peak_allocated = max(peak_allocated, allocated)
                peak_reserved = max(peak_reserved, reserved)
    require(prediction_rows == PREDICTION_COUNT, "prediction cardinality drifted")

    finished_at = utc_now()
    duration = time.perf_counter() - wall_started
    receipt = {
        "schemaVersion": "1",
        "status": "COMPLETE",
        "system": arguments.system,
        "manifestSha256": manifest_sha,
        "bindings": bindings,
        "startedAtUtc": started_at.isoformat(),
        "finishedAtUtc": finished_at.isoformat(),
        "durationSeconds": round(duration, 6),
        "packages": package_versions(arguments.system),
        "device": {
            "type": "CPU_DETERMINISTIC" if arguments.system == "D0" else "CUDA",
            "index": None if arguments.system == "D0" else int(torch.cuda.current_device()),
            "name": None if arguments.system == "D0" else torch.cuda.get_device_name(torch.cuda.current_device()),
            "bf16": False if arguments.system == "D0" else bool(torch.cuda.is_bf16_supported()),
        },
        "performance": {
            "meanLatencySeconds": round(sum(latencies) / len(latencies), 6),
            "p95LatencySeconds": round(percentile95(latencies), 6),
            "peakAllocatedBytes": peak_allocated,
            "peakReservedBytes": peak_reserved,
        },
        "generation": GENERATION,
        "networkAccess": False,
        "rows": prediction_rows,
        "predictionRows": prediction_rows,
        "predictionsSha256": sha256_file(predictions_path),
        "process": {
            "pid": os.getpid(),
            "parentPid": os.getppid(),
            "pythonExecutable": Path(sys.executable).name,
            "pythonVersion": platform.python_version(),
            "platform": platform.platform(),
        },
    }
    write_json_new(receipt_path, receipt)
    print(json.dumps({
        "status": "COMPLETE",
        "system": arguments.system,
        "predictionRows": prediction_rows,
        "predictionsSha256": receipt["predictionsSha256"],
    }, separators=(",", ":")))


if __name__ == "__main__":
    main()
