#!/usr/bin/env python3
"""Run the frozen Phase 5.3 parity corpus through pinned llama.cpp."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


EXECUTABLE_SHA256 = "4208fb923f6109143d47035aee315295c1c1ce87a062a8cfbbcb02a4d8a3fee2"
CORPUS_ID = "stackmate-phase-5-3-parity-v1"
MODEL_ARTIFACTS = {
    "r1-third-party-q4": {
        "runtimePrefix": "R1_GGUF_LLAMA_CPP",
        "bytes": 1_396_198_496,
        "sha256": "57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8",
        "identifier": "<IGNORED_STACKMATE>/phase3/models/qwen3.5-2b/Qwen_Qwen3.5-2B-Q4_K_M.gguf",
    },
    "r5-official-bf16": {
        "runtimePrefix": "R5_OFFICIAL_BF16_GGUF_LLAMA_CPP",
        "bytes": 3_897_387_456,
        "sha256": "577da3084b5b231ce973299394676ebc30104d3743200c13d5ccdb8f8bbed3c6",
        "identifier": "<WSL_CACHE>/gguf/phase-5-3/qwen3.5-2b-official-15852e8-bf16.gguf",
    },
    "r5-official-q4-k-m": {
        "runtimePrefix": "R5_OFFICIAL_Q4_K_M_GGUF_LLAMA_CPP",
        "bytes": 1_312_164_288,
        "sha256": "935bdb7b044e16348543d587dcc741b3bf3a0b33ab1d392502d4865a32d751b7",
        "identifier": "<WSL_CACHE>/gguf/phase-5-3/qwen3.5-2b-official-15852e8-q4_k_m.gguf",
    },
}
TOKEN_LINE = re.compile(r"(?m)^\S+\s+I\s+(\d+)\s+->")
TOKEN_COUNT = re.compile(r"number of tokens in prompt\s*=\s*(\d+)")
TIMINGS = {
    "loadMillis": re.compile(r"(?m)load time\s*=\s*([0-9.]+)\s*ms"),
    "promptEvalMillis": re.compile(r"(?m)prompt eval time\s*=\s*([0-9.]+)\s*ms"),
    "generationMillis": re.compile(r"(?m)(?<!prompt )eval time\s*=\s*([0-9.]+)\s*ms"),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument(
        "--artifact",
        choices=tuple(MODEL_ARTIFACTS),
        default="r1-third-party-q4",
    )
    parser.add_argument("--executable", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--prompts", nargs="+", default=["P0", "P3"])
    parser.add_argument("--decoding", choices=("schema", "unconstrained"), default="schema")
    parser.add_argument("--threads", type=int, default=16)
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--maximum-output-tokens", type=int, default=192)
    parser.add_argument(
        "--transport-newline-compensation",
        action="store_true",
        help="Append one file-transport newline so llama.cpp receives the official final token.",
    )
    return parser.parse_args()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def strict_json(path: Path) -> dict[str, Any]:
    def pairs(items: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in items:
            if key in result:
                raise ValueError(f"duplicate JSON key in {path}: {key}")
            result[key] = value
        return result

    value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=pairs)
    if not isinstance(value, dict):
        raise ValueError(f"expected JSON object: {path}")
    return value


def bounded_string(minimum: int, maximum: int) -> dict[str, Any]:
    return {"type": "string", "minLength": minimum, "maxLength": maximum}


def enum_string(values: list[str]) -> dict[str, Any]:
    return {"type": "string", "enum": values}


def id_array(values: list[str]) -> dict[str, Any]:
    return {
        "type": "array",
        "maxItems": len(values),
        "items": enum_string(values) if values else bounded_string(1, 256),
    }


def output_schema(request: dict[str, Any]) -> dict[str, Any]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "schemaVersion": enum_string([request["schemaVersion"]]),
            "summary": bounded_string(1, 120),
            "explanation": bounded_string(1, 180),
            "selectedCauseIds": id_array(
                [item["id"] for item in request["candidateCauses"]]
            ),
            "selectedCheckIds": id_array(
                [item["id"] for item in request["candidateChecks"]]
            ),
            "usedEvidenceIds": id_array([item["id"] for item in request["facts"]]),
            "limitations": {
                "type": "array",
                "maxItems": 1,
                "items": bounded_string(1, 96),
            },
        },
        "required": [
            "schemaVersion",
            "summary",
            "explanation",
            "selectedCauseIds",
            "selectedCheckIds",
            "usedEvidenceIds",
            "limitations",
        ],
    }


def render_prompt(system: str, user: str, bounds: str) -> str:
    return (
        "<|im_start|>system\n"
        + system
        + "\n\n"
        + bounds
        + "<|im_end|>\n<|im_start|>user\n"
        + user
        + "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
    )


def model_output(runtime_output: str) -> str:
    value = runtime_output.strip()
    marker = "[end of text]"
    if value.endswith(marker):
        value = value[: -len(marker)].strip()
    return value


def runtime_path(args: argparse.Namespace) -> str:
    return (
        MODEL_ARTIFACTS[args.artifact]["runtimePrefix"]
        + "_"
        + args.decoding.upper()
        + ("_TOKEN_PARITY" if args.transport_newline_compensation else "")
    )


def last_timing(pattern: re.Pattern[str], log: str) -> float | None:
    values = pattern.findall(log)
    return float(values[-1]) if values else None


def run_case(
    args: argparse.Namespace,
    case: dict[str, Any],
    prompt_id: str,
    prompt_definition: dict[str, Any],
    bounds: str,
) -> dict[str, Any]:
    system = prompt_definition["system"]
    effective_system = system + "\n\n" + bounds
    user = case["productionUserPrompt"]
    rendered = render_prompt(system, user, bounds)
    transport_prompt = rendered + ("\n" if args.transport_newline_compensation else "")
    schema = output_schema(case["request"])
    started = time.perf_counter()
    timed_out = False
    exit_code = -1
    stdout = b""
    stderr = b""
    with tempfile.TemporaryDirectory(prefix="stackmate-parity-llama-") as temporary:
        temporary_path = Path(temporary)
        prompt_path = temporary_path / "prompt.txt"
        schema_path = temporary_path / "schema.json"
        prompt_path.write_text(transport_prompt, encoding="utf-8", newline="\n")
        schema_path.write_text(
            json.dumps(schema, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
            newline="\n",
        )
        command = [
            str(args.executable),
            "--model",
            str(args.model),
            "--ctx-size",
            "2048",
            "--predict",
            str(args.maximum_output_tokens),
            "--threads",
            str(args.threads),
            "--threads-batch",
            str(args.threads),
            "--seed",
            "42",
            "--reasoning",
            "off",
            "--no-conversation",
            "--no-display-prompt",
            "--file",
            str(prompt_path),
            "--temperature",
            "0",
            "--top-k",
            "1",
            "--top-p",
            "1",
            "--verbose-prompt",
        ]
        if args.decoding == "schema":
            command.extend(["--json-schema-file", str(schema_path)])
        environment = {
            key: value
            for key, value in os.environ.items()
            if key.casefold() in {"systemroot", "path"}
        }
        try:
            completed = subprocess.run(
                command,
                cwd=temporary_path,
                env=environment,
                capture_output=True,
                timeout=args.timeout_seconds,
                check=False,
            )
            stdout = completed.stdout
            stderr = completed.stderr
            exit_code = completed.returncode
        except subprocess.TimeoutExpired as failure:
            timed_out = True
            stdout = failure.stdout or b""
            stderr = failure.stderr or b""

    elapsed = time.perf_counter() - started
    runtime_output = stdout.decode("utf-8", errors="replace")
    decoded = model_output(runtime_output)
    log = stderr.decode("utf-8", errors="replace")
    token_ids = [int(value) for value in TOKEN_LINE.findall(log)]
    count_match = TOKEN_COUNT.search(log)
    declared_count = int(count_match.group(1)) if count_match else None
    parsed_json: Any = None
    parse_error: str | None = None
    try:
        parsed_json = json.loads(decoded)
    except Exception as failure:
        parse_error = type(failure).__name__

    return {
        "runtimePath": runtime_path(args),
        "promptId": prompt_id,
        "promptVersion": prompt_definition["version"],
        "caseId": case["caseId"],
        "rawSystemPrompt": system,
        "rawSystemPromptSha256": sha256_bytes(system.encode("utf-8")),
        "rawSystemPromptCharacters": len(system),
        "effectiveSystemPrompt": effective_system,
        "effectiveSystemPromptSha256": sha256_bytes(
            effective_system.encode("utf-8")
        ),
        "rawUserPrompt": user,
        "rawUserPromptSha256": sha256_bytes(user.encode("utf-8")),
        "rawUserPromptCharacters": len(user),
        "appliedChatTemplate": rendered,
        "appliedChatTemplateSha256": sha256_bytes(rendered.encode("utf-8")),
        "appliedChatTemplateCharacters": len(rendered),
        "transportPromptSha256": sha256_bytes(transport_prompt.encode("utf-8")),
        "transportNewlineCompensation": args.transport_newline_compensation,
        "promptTokenIds": token_ids,
        "promptTokenCount": declared_count,
        "promptTokenIdsComplete": declared_count == len(token_ids),
        "specialTokens": {
            "bosAdded": False,
            "eosAddedToPrompt": False,
            "assistantPrefix": "<think>\n\n</think>\n\n",
            "stopSequences": [],
            "eosHandling": "llama.cpp runtime default; --ignore-eos absent",
        },
        "thinking": {"reasoningFlag": "off", "thinkingPrefixInPrompt": True},
        "generationParameters": {
            "contextSize": 2048,
            "maximumOutputTokens": args.maximum_output_tokens,
            "temperature": 0.0,
            "topK": 1,
            "topP": 1.0,
            "minP": 0.05,
            "typicalP": 1.0,
            "repeatLastN": 64,
            "repetitionPenalty": 1.0,
            "presencePenalty": 0.0,
            "frequencyPenalty": 0.0,
            "seed": 42,
            "schemaConstrained": args.decoding == "schema",
            "conversation": False,
            "processLifecycle": "process-per-case",
            "transportNewlineCompensation": args.transport_newline_compensation,
        },
        "timedOut": timed_out,
        "exitCode": exit_code,
        "latencySeconds": round(elapsed, 3),
        "timings": {
            name: last_timing(pattern, log) for name, pattern in TIMINGS.items()
        },
        "rawGeneratedBase64": base64.b64encode(stdout).decode("ascii"),
        "rawGeneratedBytes": len(stdout),
        "rawGeneratedSha256": sha256_bytes(stdout),
        "decodedText": decoded,
        "decodedTextSha256": sha256_bytes(decoded.encode("utf-8")),
        "wholeOutputJsonParsed": parsed_json is not None,
        "parsedJson": parsed_json,
        "parseError": parse_error,
        "outputExtraction": "strip-whitespace-and-terminal-[end of text]-marker",
        "repairAttempted": False,
        "hiddenThinking": any(
            marker in decoded.casefold()
            for marker in ("<think>", "</think>", "reasoning_content")
        ),
        "runtimeLogSha256": sha256_bytes(stderr),
        "runtimeWarnings": sorted(
            {
                line.strip()
                for line in log.splitlines()
                if "unused tensor" in line or "warning" in line.casefold()
            }
        ),
    }


def main() -> None:
    args = parse_args()
    args.corpus = args.corpus.expanduser().resolve()
    args.manifest = args.manifest.expanduser().resolve()
    args.model = args.model.expanduser().resolve()
    args.executable = args.executable.expanduser().resolve()
    args.report = args.report.expanduser().resolve()
    if args.report.exists():
        raise RuntimeError(f"parity report already exists; overwrite forbidden: {args.report}")
    if set(args.prompts) - {"P0", "P3"}:
        raise RuntimeError("parity permits only P0 and P3")
    if args.threads < 1 or args.timeout_seconds < 1:
        raise RuntimeError("threads and timeout must be positive")

    corpus = strict_json(args.corpus)
    manifest = strict_json(args.manifest)
    corpus_sha256 = sha256_file(args.corpus)
    if corpus.get("corpusId") != CORPUS_ID or manifest.get("corpusId") != CORPUS_ID:
        raise RuntimeError("unexpected parity corpus identity")
    if manifest.get("corpusSha256") != corpus_sha256:
        raise RuntimeError("parity corpus checksum mismatch")
    if len(corpus.get("cases", [])) != 40:
        raise RuntimeError("parity corpus cardinality drift")
    artifact = MODEL_ARTIFACTS[args.artifact]
    if args.model.stat().st_size != artifact["bytes"]:
        raise RuntimeError("pinned GGUF artifact size mismatch")
    if sha256_file(args.model) != artifact["sha256"]:
        raise RuntimeError("pinned GGUF artifact checksum mismatch")
    if sha256_file(args.executable) != EXECUTABLE_SHA256:
        raise RuntimeError("pinned llama.cpp executable mismatch")

    started = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    results: list[dict[str, Any]] = []
    for prompt_id in args.prompts:
        prompt_definition = corpus["prompts"][prompt_id]
        for index, case in enumerate(corpus["cases"], start=1):
            result = run_case(args, case, prompt_id, prompt_definition, corpus["bounds"])
            results.append(result)
            print(
                json.dumps(
                    {
                        "runtimePath": result["runtimePath"],
                        "prompt": prompt_id,
                        "completed": index,
                        "total": len(corpus["cases"]),
                        "caseId": case["caseId"],
                        "parsed": result["wholeOutputJsonParsed"],
                    }
                ),
                flush=True,
            )

    report = {
        "schemaVersion": "1",
        "status": "PARITY_GENERATION_COMPLETE",
        "runtimePath": runtime_path(args),
        "corpusId": CORPUS_ID,
        "corpusSha256": corpus_sha256,
        "manifestSha256": sha256_file(args.manifest),
        "modelArtifact": artifact["identifier"],
        "modelBytes": artifact["bytes"],
        "modelSha256": artifact["sha256"],
        "runtimeArtifact": "<IGNORED_STACKMATE>/phase5/runtime/b10223/bin/llama-completion.exe",
        "runtimeRelease": "b10223",
        "runtimeCommit": "11924d4c17abc27383376a1ac6a24fa3e36c1c0c",
        "runtimeSha256": EXECUTABLE_SHA256,
        "prompts": args.prompts,
        "decoding": args.decoding,
        "repairPolicy": "not-run-in-primary-parity",
        "startTimeUtc": started.isoformat(),
        "finishTimeUtc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "durationSeconds": round(time.perf_counter() - wall_started, 3),
        "networkAccess": False,
        "telemetry": False,
        "results": results,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(json.dumps({"status": report["status"], "report": str(args.report)}))


if __name__ == "__main__":
    main()
