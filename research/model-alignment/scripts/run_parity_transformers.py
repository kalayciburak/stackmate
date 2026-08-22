#!/usr/bin/env python3
"""Run development-only Phase 5.3 parity inference with Transformers."""

from __future__ import annotations

import os

# These must be set before importing Hugging Face packages.
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
os.environ["HF_DATASETS_OFFLINE"] = "1"
os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
os.environ["DO_NOT_TRACK"] = "1"
os.environ["WANDB_DISABLED"] = "true"

import argparse
import hashlib
import importlib.metadata
import json
import platform
import socket
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch
from peft import PeftModel
from transformers import (
    AutoModelForMultimodalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    set_seed,
)


ROOT = Path(__file__).resolve().parent.parent
CORPUS_PATH = ROOT / "parity/corpus.json"
CORPUS_MANIFEST_PATH = ROOT / "parity/manifest.json"
EXPECTED_REPOSITORY = "Qwen/Qwen3.5-2B"
EXPECTED_REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
EXPECTED_SPLIT = "BENCHMARK_V2_DEVELOPMENT"
MAX_NEW_TOKENS = 192
SEED = 42
PACKAGE_NAMES = (
    "accelerate",
    "bitsandbytes",
    "huggingface-hub",
    "peft",
    "safetensors",
    "torch",
    "transformers",
)
RUNTIME_PATHS = {
    "bf16": "R2_TRANSFORMERS_BF16",
    "nf4": "R3_TRANSFORMERS_NF4",
    "f1": "R4_TRANSFORMERS_NF4_F1",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run one offline development-only Transformers parity condition."
    )
    parser.add_argument("--mode", required=True, choices=("bf16", "nf4", "f1"))
    parser.add_argument("--prompt", choices=("P0", "P3"), default="P3")
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--adapter", type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument(
        "--base-model-manifest",
        type=Path,
        default=Path("reports/base-model-manifest.json"),
    )
    parser.add_argument(
        "--selected-adapter-report",
        type=Path,
        default=Path("reports/selected-adapter.json"),
    )
    parser.add_argument(
        "--allow-f1-p0-lab",
        action="store_true",
        help="Explicitly permit the non-standard F1/P0 lab condition.",
    )
    return parser.parse_args()


def resolve_root_path(path: Path) -> Path:
    candidate = path if path.is_absolute() else ROOT / path
    return candidate.expanduser().resolve()


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(
        path.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicate_keys
    )
    if not isinstance(value, dict):
        raise RuntimeError(f"expected a JSON object: {path.name}")
    return value


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def directory_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    for item in sorted(item for item in path.rglob("*") if item.is_file()):
        digest.update(item.relative_to(path).as_posix().encode("utf-8"))
        digest.update(b"\0")
        with item.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def require_development_corpus() -> tuple[dict[str, Any], dict[str, Any], str]:
    corpus = read_json(CORPUS_PATH)
    manifest = read_json(CORPUS_MANIFEST_PATH)
    corpus_hash = sha256_file(CORPUS_PATH)
    if manifest.get("corpusSha256") != corpus_hash:
        raise RuntimeError("parity corpus checksum differs from its frozen manifest")
    if corpus.get("corpusId") != manifest.get("corpusId"):
        raise RuntimeError("parity corpus ID differs from its frozen manifest")
    if corpus.get("sourceCommit") != manifest.get("sourceCommit"):
        raise RuntimeError("parity corpus source commit differs from its frozen manifest")
    if corpus.get("split") != EXPECTED_SPLIT:
        raise RuntimeError("only the Phase 5.3 development parity split is permitted")
    if corpus.get("baseRepository") != EXPECTED_REPOSITORY:
        raise RuntimeError("parity corpus base repository drifted")
    if corpus.get("baseRevision") != EXPECTED_REVISION:
        raise RuntimeError("parity corpus base revision drifted")
    if corpus.get("maximumOutputTokens") != MAX_NEW_TOKENS:
        raise RuntimeError("parity corpus generation limit drifted")

    cases = corpus.get("cases")
    manifest_ids = manifest.get("caseIds")
    if not isinstance(cases, list) or not isinstance(manifest_ids, list):
        raise RuntimeError("parity corpus cases or manifest case IDs are invalid")
    case_ids = [item.get("caseId") for item in cases if isinstance(item, dict)]
    if len(case_ids) != len(cases) or case_ids != manifest_ids:
        raise RuntimeError("parity case order differs from its frozen manifest")
    if len(case_ids) != manifest.get("caseCount") or len(set(case_ids)) != len(case_ids):
        raise RuntimeError("parity case cardinality or uniqueness check failed")
    return corpus, manifest, corpus_hash


def require_official_base(
    base_path: Path, manifest_path: Path, corpus: dict[str, Any]
) -> tuple[dict[str, Any], str]:
    if not base_path.is_dir():
        raise RuntimeError("local official base model directory is missing")
    manifest = read_json(manifest_path)
    if manifest.get("repositoryId") != corpus["baseRepository"]:
        raise RuntimeError("base manifest repository differs from the parity corpus")
    if manifest.get("officialRevision") != corpus["baseRevision"]:
        raise RuntimeError("base manifest revision differs from the parity corpus")
    if manifest.get("resolvedRevision") != corpus["baseRevision"]:
        raise RuntimeError("base manifest resolved revision is not the official revision")

    expected_parent = "models--" + corpus["baseRepository"].replace("/", "--")
    if (
        base_path.name != corpus["baseRevision"]
        or len(base_path.parents) < 2
        or base_path.parents[1].name != expected_parent
    ):
        raise RuntimeError("base model path is not the exact official snapshot revision")

    files = manifest.get("files")
    if not isinstance(files, list) or not files:
        raise RuntimeError("base manifest contains no files")
    for record in files:
        if not isinstance(record, dict):
            raise RuntimeError("base manifest file record is invalid")
        name = record.get("name")
        if not isinstance(name, str):
            raise RuntimeError("base manifest file name is invalid")
        relative = Path(name)
        if relative.is_absolute() or ".." in relative.parts or relative.as_posix() != name:
            raise RuntimeError("base manifest contains an unsafe file name")
        local_file = base_path / relative
        if not local_file.is_file():
            raise RuntimeError(f"base manifest file is missing: {name}")
        if local_file.stat().st_size != record.get("size"):
            raise RuntimeError(f"base manifest file size differs: {name}")
        if sha256_file(local_file) != record.get("sha256"):
            raise RuntimeError(f"base manifest file checksum differs: {name}")
    return manifest, sha256_file(manifest_path)


def require_selected_f1(
    adapter_path: Path | None,
    selection_path: Path,
    corpus: dict[str, Any],
) -> tuple[Path, dict[str, Any], str, str]:
    if adapter_path is None:
        raise RuntimeError("--adapter is required in f1 mode")
    resolved_adapter = adapter_path.expanduser().resolve()
    if not resolved_adapter.is_dir():
        raise RuntimeError("selected F1 adapter directory is missing")
    selection = read_json(selection_path)
    if selection.get("adapterId") != "F1-qlora-r8":
        raise RuntimeError("selected adapter report does not identify F1")
    if selection.get("baseModelRepository") != corpus["baseRepository"]:
        raise RuntimeError("selected adapter base repository differs from the parity corpus")
    if selection.get("baseModelRevision") != corpus["baseRevision"]:
        raise RuntimeError("selected adapter base revision differs from the parity corpus")
    adapter_hash = directory_sha256(resolved_adapter)
    if selection.get("adapterSha256") != adapter_hash:
        raise RuntimeError("selected F1 adapter checksum differs from its selection report")
    return resolved_adapter, selection, adapter_hash, sha256_file(selection_path)


def quantization_config() -> BitsAndBytesConfig:
    return BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )


def load_model(mode: str, base_path: Path, adapter_path: Path | None) -> Any:
    options: dict[str, Any] = {
        "local_files_only": True,
        "trust_remote_code": False,
        "dtype": torch.bfloat16,
        "device_map": {"": torch.cuda.current_device()},
    }
    if mode in ("nf4", "f1"):
        options["quantization_config"] = quantization_config()
    model = AutoModelForMultimodalLM.from_pretrained(base_path, **options)
    if mode == "f1":
        if adapter_path is None:
            raise RuntimeError("internal error: F1 adapter was not resolved")
        model = PeftModel.from_pretrained(
            model,
            adapter_path,
            local_files_only=True,
            is_trainable=False,
        )
    model.eval()
    model.config.use_cache = True
    return model


def normalized_token_ids(value: int | list[int] | tuple[int, ...] | None) -> list[int]:
    if value is None:
        return []
    if isinstance(value, int):
        return [value]
    return [int(item) for item in value]


def token_metadata(tokenizer: Any) -> dict[str, Any]:
    stop_ids = normalized_token_ids(tokenizer.eos_token_id)
    return {
        "bosToken": tokenizer.bos_token,
        "bosTokenId": tokenizer.bos_token_id,
        "eosToken": tokenizer.eos_token,
        "eosTokenId": tokenizer.eos_token_id,
        "padToken": tokenizer.pad_token,
        "padTokenId": tokenizer.pad_token_id,
        "stopTokenIds": stop_ids,
        "stopTokens": [tokenizer.convert_ids_to_tokens(item) for item in stop_ids],
    }


def parse_whole_output(raw: str) -> dict[str, Any]:
    try:
        parsed = json.loads(raw, object_pairs_hook=reject_duplicate_keys)
    except (json.JSONDecodeError, ValueError) as failure:
        return {
            "status": "INVALID_JSON",
            "success": False,
            "isObject": False,
            "errorType": type(failure).__name__,
            "error": str(failure),
            "repairAttempted": False,
        }
    return {
        "status": "VALID_OBJECT" if isinstance(parsed, dict) else "VALID_NON_OBJECT",
        "success": True,
        "isObject": isinstance(parsed, dict),
        "errorType": None,
        "error": None,
        "repairAttempted": False,
    }


def render_prompt(
    tokenizer: Any, raw_system: str, raw_user: str, bounds: str
) -> tuple[dict[str, Any], torch.Tensor, torch.Tensor]:
    effective_system = raw_system + "\n\n" + bounds
    messages = [
        {"role": "system", "content": effective_system},
        {"role": "user", "content": raw_user},
    ]
    rendered = tokenizer.apply_chat_template(
        messages,
        tokenize=False,
        add_generation_prompt=True,
        enable_thinking=False,
    )
    encoded = tokenizer.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=True,
        enable_thinking=False,
        return_dict=True,
        return_tensors="pt",
    )
    input_ids = encoded["input_ids"]
    attention_mask = encoded.get("attention_mask")
    if attention_mask is None:
        attention_mask = torch.ones_like(input_ids)
    effective_json = canonical_json(messages)
    prompt_record = {
        "rawSystem": raw_system,
        "rawSystemSha256": sha256_text(raw_system),
        "rawUser": raw_user,
        "rawUserSha256": sha256_text(raw_user),
        "effectiveSystem": effective_system,
        "effectiveSystemSha256": sha256_text(effective_system),
        "effectivePrompt": messages,
        "effectivePromptSha256": sha256_text(effective_json),
        "effectivePromptHashEncoding": "canonical-json-utf8",
        "renderedTemplate": rendered,
        "renderedTemplateSha256": sha256_text(rendered),
        "renderedTemplateCharacters": len(rendered),
        "inputTokenIds": [int(item) for item in input_ids[0].tolist()],
        "inputTokenCount": int(input_ids.shape[1]),
    }
    return prompt_record, input_ids, attention_mask


def stopping_reason(suffix_ids: list[int], stop_ids: list[int]) -> str:
    if suffix_ids and suffix_ids[-1] in stop_ids:
        return "STOP_TOKEN"
    if len(suffix_ids) == MAX_NEW_TOKENS:
        return "MAX_NEW_TOKENS"
    return "MODEL_GENERATION_ENDED"


def run_case(
    model: Any,
    tokenizer: Any,
    case: dict[str, Any],
    mode: str,
    prompt_id: str,
    prompt_definition: dict[str, Any],
    bounds: str,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    raw_user = case.get("productionUserPrompt")
    raw_system = prompt_definition.get("system")
    if not isinstance(raw_user, str) or not isinstance(raw_system, str):
        raise RuntimeError(f"invalid prompt material for case {case.get('caseId')}")
    prompt, input_ids, attention_mask = render_prompt(
        tokenizer, raw_system, raw_user, bounds
    )
    device = next(model.parameters()).device
    input_ids = input_ids.to(device)
    attention_mask = attention_mask.to(device)
    torch.cuda.synchronize()
    torch.cuda.reset_peak_memory_stats()
    started = time.perf_counter()
    with torch.inference_mode():
        generated = model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_new_tokens=MAX_NEW_TOKENS,
            do_sample=False,
            num_beams=1,
            use_cache=True,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    torch.cuda.synchronize()
    latency = time.perf_counter() - started
    suffix = generated[0, input_ids.shape[1] :]
    suffix_ids = [int(item) for item in suffix.tolist()]
    raw = tokenizer.decode(suffix, skip_special_tokens=True)
    raw_bytes = raw.encode("utf-8")
    stop_ids = metadata["stopTokenIds"]
    return {
        "runtimePath": RUNTIME_PATHS[mode],
        "caseId": case["caseId"],
        "promptId": prompt_id,
        "promptVersion": prompt_definition.get("version"),
        "prompt": prompt,
        "decodedSuffixTokenIds": suffix_ids,
        "decodedSuffixTokenCount": len(suffix_ids),
        "rawGeneratedText": raw,
        "rawGeneratedTextUtf8Bytes": len(raw_bytes),
        "rawGeneratedTextSha256": sha256_bytes(raw_bytes),
        "outputExtractionCategory": "DECODED_SUFFIX_NO_REPAIR",
        "wholeOutputJson": parse_whole_output(raw),
        "latencySeconds": round(latency, 6),
        "peakGpuVramBytes": torch.cuda.max_memory_allocated(),
        "peakGpuVramReservedBytes": torch.cuda.max_memory_reserved(),
        "stop": {
            "reason": stopping_reason(suffix_ids, stop_ids),
            "matchedTokenId": suffix_ids[-1]
            if suffix_ids and suffix_ids[-1] in stop_ids
            else None,
        },
    }


def package_versions() -> dict[str, str]:
    versions: dict[str, str] = {}
    for name in PACKAGE_NAMES:
        try:
            versions[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            versions[name] = "NOT_INSTALLED"
    return versions


def disable_network() -> None:
    def blocked(*_args: Any, **_kwargs: Any) -> Any:
        raise RuntimeError("network access is disabled for the parity runner")

    socket.create_connection = blocked


def main() -> None:
    args = parse_args()
    report_path = args.report.expanduser().resolve()
    if report_path.exists():
        raise RuntimeError(f"parity report already exists; overwrite forbidden: {report_path}")
    if args.mode == "f1" and args.prompt != "P3" and not args.allow_f1_p0_lab:
        raise RuntimeError("f1 mode is restricted to P3; use --allow-f1-p0-lab explicitly")
    if args.mode != "f1" and args.adapter is not None:
        raise RuntimeError("--adapter is permitted only in f1 mode")
    if args.allow_f1_p0_lab and not (args.mode == "f1" and args.prompt == "P0"):
        raise RuntimeError("--allow-f1-p0-lab applies only to the f1/P0 condition")
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported():
        raise RuntimeError("parity inference requires CUDA with BF16 support")

    corpus, corpus_manifest, corpus_hash = require_development_corpus()
    base_path = args.base_model.expanduser().resolve()
    base_manifest_path = resolve_root_path(args.base_model_manifest)
    _, base_manifest_hash = require_official_base(base_path, base_manifest_path, corpus)

    adapter_path: Path | None = None
    adapter_record: dict[str, Any] | None = None
    if args.mode == "f1":
        selection_path = resolve_root_path(args.selected_adapter_report)
        adapter_path, selection, adapter_hash, selection_hash = require_selected_f1(
            args.adapter, selection_path, corpus
        )
        adapter_record = {
            "adapterId": selection["adapterId"],
            "adapterSha256": adapter_hash,
            "selectionReportSha256": selection_hash,
        }

    prompt_definition = corpus.get("prompts", {}).get(args.prompt)
    bounds = corpus.get("bounds")
    if not isinstance(prompt_definition, dict) or not isinstance(bounds, str):
        raise RuntimeError("selected prompt or corpus bounds are missing")
    maximum_input_characters = corpus.get("requestMaximumInputCharacters")
    if not isinstance(maximum_input_characters, int):
        raise RuntimeError("corpus input character bound is invalid")
    for case in corpus["cases"]:
        user = case.get("productionUserPrompt")
        if not isinstance(user, str) or len(user) > maximum_input_characters:
            raise RuntimeError(f"case input exceeds corpus bounds: {case.get('caseId')}")

    set_seed(SEED)
    disable_network()
    tokenizer = AutoTokenizer.from_pretrained(
        base_path,
        local_files_only=True,
        trust_remote_code=False,
    )
    if tokenizer.pad_token_id is None or tokenizer.eos_token_id is None:
        raise RuntimeError("official tokenizer must define pad and EOS token IDs")
    metadata = token_metadata(tokenizer)
    model = load_model(args.mode, base_path, adapter_path)

    started_utc = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    results: list[dict[str, Any]] = []
    peak_allocated = 0
    peak_reserved = 0
    for index, case in enumerate(corpus["cases"], start=1):
        result = run_case(
            model,
            tokenizer,
            case,
            args.mode,
            args.prompt,
            prompt_definition,
            bounds,
            metadata,
        )
        results.append(result)
        peak_allocated = max(peak_allocated, result["peakGpuVramBytes"])
        peak_reserved = max(peak_reserved, result["peakGpuVramReservedBytes"])
        print(
            json.dumps(
                {
                    "caseId": case["caseId"],
                    "completed": index,
                    "total": len(corpus["cases"]),
                }
            )
        )

    finished_utc = datetime.now(timezone.utc).replace(microsecond=0)
    generation = {
        "maxNewTokens": MAX_NEW_TOKENS,
        "doSample": False,
        "numBeams": 1,
        "useCache": True,
        "seed": SEED,
        "computeDtype": "bfloat16",
        "quantization": None
        if args.mode == "bf16"
        else {
            "loadIn4Bit": True,
            "type": "nf4",
            "doubleQuantization": True,
            "computeDtype": "bfloat16",
        },
        "enableThinking": False,
        "addGenerationPrompt": True,
        "localFilesOnly": True,
        "trustRemoteCode": False,
        "networkAccess": False,
        "networkGuard": "Hugging Face offline variables plus socket.create_connection block",
        "telemetry": False,
        "outputRepair": False,
        "tokenMetadata": metadata,
    }
    report = {
        "schemaVersion": "1",
        "status": "DEVELOPMENT_PARITY_COMPLETE",
        "phase": "5.3",
        "dataScope": "DEVELOPMENT_ONLY",
        "lockedOrFinalCasesAccessed": False,
        "mode": args.mode,
        "runtimePath": RUNTIME_PATHS[args.mode],
        "promptId": args.prompt,
        "promptIds": [args.prompt],
        "labOverride": args.allow_f1_p0_lab,
        "corpusSha256": corpus_hash,
        "artifacts": {
            "corpus": {
                "corpusId": corpus["corpusId"],
                "sha256": corpus_hash,
                "manifestSha256": sha256_file(CORPUS_MANIFEST_PATH),
                "sourceCommit": corpus_manifest["sourceCommit"],
            },
            "baseModel": {
                "repository": corpus["baseRepository"],
                "revision": corpus["baseRevision"],
                "manifestSha256": base_manifest_hash,
            },
            "adapter": adapter_record,
        },
        "generation": generation,
        "startTimeUtc": started_utc.isoformat(),
        "finishTimeUtc": finished_utc.isoformat(),
        "durationSeconds": round(time.perf_counter() - wall_started, 6),
        "peakGpuVramBytes": peak_allocated,
        "peakGpuVramReservedBytes": peak_reserved,
        "caseCount": len(results),
        "javaScorerRawOutputField": "rawGeneratedText",
        "python": sys.version.split()[0],
        "platform": platform.platform(),
        "packages": package_versions(),
        "results": results,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with report_path.open("x", encoding="utf-8", newline="\n") as target:
        json.dump(report, target, ensure_ascii=False, indent=2)
        target.write("\n")
    print(
        json.dumps(
            {
                "status": report["status"],
                "mode": args.mode,
                "promptId": args.prompt,
                "cases": len(results),
            }
        )
    )


if __name__ == "__main__":
    main()
