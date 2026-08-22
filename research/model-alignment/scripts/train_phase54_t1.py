#!/usr/bin/env python3
"""Run the single Phase 5.4 real-world T1 LoRA/QLoRA experiment."""

from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.metadata
import json
import math
import os
import platform
import socket
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import psutil
import torch
from peft import LoraConfig, PeftModel, TaskType, get_peft_model, prepare_model_for_kbit_training
from torch.utils.data import Dataset
from transformers import AutoModelForMultimodalLM, AutoTokenizer, BitsAndBytesConfig, Trainer, TrainingArguments, set_seed


ROOT = Path(__file__).resolve().parents[1]
REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
RUN_ID = "P5.4-T1-real-world-r8"
TARGETS = [
    "down_proj", "gate_proj", "in_proj_a", "in_proj_b", "in_proj_qkv", "in_proj_z",
    "k_proj", "o_proj", "out_proj", "q_proj", "up_proj", "v_proj",
]
EXPECTED = {
    "rank": 8,
    "alpha": 16,
    "dropout": 0.05,
    "learningRate": 0.0001,
    "epochs": 6,
    "perDeviceTrainBatchSize": 1,
    "perDeviceValidationBatchSize": 1,
    "gradientAccumulationSteps": 16,
    "gradientCheckpointing": True,
    "maximumSequenceLength": 2048,
    "seed": 520021,
}
EXPECTED_HASHES = {
    "sourceManifestSha256": "42d2bab37173a9b8a7579203e270a02fbe9ab795b12b9a731f39fa035a8244ee",
    "trainSha256": "ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93",
    "validationSha256": "bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789",
    "manifestSha256": "0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--environment", default="configs/environment-v1.json", type=Path)
    parser.add_argument("--architecture", default="reports/model-architecture.json", type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--training-dir", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--smoke-train-examples", type=int, default=16)
    parser.add_argument("--smoke-max-steps", type=int, default=10)
    return parser.parse_args()


def resolve_repo(path: Path) -> Path:
    return path.expanduser().resolve() if path.is_absolute() else (ROOT / path).resolve()


def outside_repo(path: Path, label: str) -> Path:
    value = path.expanduser().resolve()
    if not value.is_absolute() or value == ROOT or ROOT in value.parents:
        raise ValueError(f"{label} must be an absolute path outside the repository")
    return value


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
        digest.update(item.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def directory_size(path: Path) -> int:
    return sum(item.stat().st_size for item in path.rglob("*") if item.is_file())


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return value


def write_exclusive(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(value, destination, ensure_ascii=False, indent=2)
        destination.write("\n")


def require_config(config: dict[str, Any], environment: dict[str, Any], architecture: dict[str, Any]) -> str:
    if config.get("schemaVersion") != "1" or config.get("runId") != RUN_ID:
        raise ValueError("Phase 5.4 run profile is not the frozen one-shot profile")
    for key, expected in EXPECTED.items():
        if config.get(key) != expected:
            raise ValueError(f"Locked Phase 5.4 setting drifted: {key}")
    if config.get("environment") != "configs/environment-v1.json":
        raise ValueError("Phase 5.4 must use the frozen environment profile")
    if config.get("targetModules") != TARGETS:
        raise ValueError("Phase 5.4 target module list drifted")
    if config.get("contract") != "T1_SELECTION_ONLY_ORDINAL":
        raise ValueError("Phase 5.4 must use T1")
    precision = config.get("precision")
    if precision not in {"BF16_LORA", "NF4_QLORA"}:
        raise ValueError("precision must be BF16_LORA or NF4_QLORA")
    if environment.get("baseModel", {}).get("revision") != REVISION:
        raise ValueError("environment base revision drifted")
    if environment.get("maximumSequenceLength") != 2048:
        raise ValueError("environment sequence limit drifted")
    if architecture.get("selectedTargetModules") != TARGETS:
        raise ValueError("architecture selected targets drifted")
    return precision


def model_path(environment: dict[str, Any]) -> Path:
    key = environment["baseModel"]["localPathEnvironment"]
    value = os.environ.get(key, "").strip() or os.environ.get("BASE_MODEL_PATH", "").strip()
    if not value:
        raise RuntimeError(f"{key} or BASE_MODEL_PATH is not set")
    path = Path(value).expanduser().resolve()
    if path.name != REVISION:
        raise RuntimeError("base model path is not pinned to the official revision")
    for required in environment["baseModel"]["requiredFiles"]:
        if not (path / required).is_file():
            raise RuntimeError(f"base model is missing {required}")
    return path


def configure_offline() -> None:
    os.environ.update({
        "HF_HUB_OFFLINE": "1",
        "TRANSFORMERS_OFFLINE": "1",
        "HF_HUB_DISABLE_TELEMETRY": "1",
        "WANDB_DISABLED": "true",
    })

    def blocked(*_args: Any, **_kwargs: Any) -> Any:
        raise RuntimeError("network access is disabled during Phase 5.4 training")

    socket.create_connection = blocked
    socket.getaddrinfo = blocked


def load_rows(path: Path, limit: int | None = None) -> list[dict[str, Any]]:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if limit is not None:
        rows = rows[:limit]
    if not rows:
        raise ValueError(f"empty dataset: {path}")
    for row in rows:
        if not isinstance(row.get("messages"), list) or len(row["messages"]) != 3:
            raise ValueError("T1 row must contain system/user/assistant messages")
        if row.get("split") not in {"TRAIN", "VALIDATION"}:
            raise ValueError("training path loaded a non-development split")
        if "LOCKED_FINAL" in json.dumps(row, ensure_ascii=False):
            raise ValueError("locked-final marker reached training data")
    return rows


def row_id(row: dict[str, Any]) -> str:
    return f"{row.get('familyId', 'unknown')}/v{row.get('variant', 'unknown')}"


def tokenize_rows(rows: list[dict[str, Any]], tokenizer: Any, maximum: int) -> tuple[list[dict[str, Any]], int]:
    records: list[dict[str, Any]] = []
    tokens = 0
    for row in rows:
        messages = row["messages"]
        prefix = tokenizer.apply_chat_template(
            messages[:2], tokenize=True, add_generation_prompt=True,
            enable_thinking=False, return_dict=False,
        )
        full = tokenizer.apply_chat_template(
            messages, tokenize=True, add_generation_prompt=False,
            enable_thinking=False, return_dict=False,
        )
        identifier = row_id(row)
        if full[:len(prefix)] != prefix:
            raise ValueError(f"assistant boundary mismatch: {identifier}")
        if len(full) > maximum:
            raise ValueError(f"truncation would be required: {identifier}")
        labels = [-100] * len(prefix) + full[len(prefix):]
        if all(value == -100 for value in labels):
            raise ValueError(f"empty assistant target: {identifier}")
        records.append({
            "input_ids": full,
            "attention_mask": [1] * len(full),
            "labels": labels,
            "exampleId": identifier,
        })
        tokens += len(full)
    return records, tokens


class TokenizedRows(Dataset):
    def __init__(self, rows: list[dict[str, Any]]) -> None:
        self.rows = rows

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int) -> dict[str, Any]:
        return self.rows[index]


class CausalCollator:
    def __init__(self, tokenizer: Any) -> None:
        self.pad_token_id = tokenizer.pad_token_id

    def __call__(self, features: list[dict[str, Any]]) -> dict[str, torch.Tensor]:
        maximum = max(len(item["input_ids"]) for item in features)
        return {
            "input_ids": torch.tensor([item["input_ids"] + [self.pad_token_id] * (maximum - len(item["input_ids"])) for item in features], dtype=torch.long),
            "attention_mask": torch.tensor([item["attention_mask"] + [0] * (maximum - len(item["attention_mask"])) for item in features], dtype=torch.long),
            "labels": torch.tensor([item["labels"] + [-100] * (maximum - len(item["labels"])) for item in features], dtype=torch.long),
        }


def quant_config(config: dict[str, Any]) -> BitsAndBytesConfig:
    fallback = config["fallbackQuantization"]
    return BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type=fallback["type"],
        bnb_4bit_use_double_quant=fallback["doubleQuantization"],
        bnb_4bit_compute_dtype=torch.bfloat16,
    )


def load_train_model(base: Path, targets: list[str], config: dict[str, Any], precision: str) -> Any:
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported():
        raise RuntimeError("CUDA with BF16 support is required")
    options: dict[str, Any] = {
        "local_files_only": True,
        "trust_remote_code": False,
        "dtype": torch.bfloat16,
        "device_map": {"": torch.cuda.current_device()},
    }
    if precision == "NF4_QLORA":
        options["quantization_config"] = quant_config(config)
    model = AutoModelForMultimodalLM.from_pretrained(base, **options)
    model.config.use_cache = False
    if precision == "NF4_QLORA":
        model = prepare_model_for_kbit_training(
            model, use_gradient_checkpointing=True,
            gradient_checkpointing_kwargs={"use_reentrant": False},
        )
    model = get_peft_model(model, LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=config["rank"], lora_alpha=config["alpha"],
        lora_dropout=config["dropout"], target_modules=targets, bias="none",
    ))
    if hasattr(model, "enable_input_require_grads"):
        model.enable_input_require_grads()
    return model


def finite_history(history: list[dict[str, Any]]) -> bool:
    for entry in history:
        for key in ("loss", "eval_loss", "grad_norm"):
            value = entry.get(key)
            if value is not None and (not isinstance(value, (int, float)) or not math.isfinite(value)):
                return False
    return True


def generation_probe(model_path: Path, adapter_path: Path, tokenizer: Any, row: dict[str, Any], precision: str, config: dict[str, Any]) -> dict[str, Any]:
    options: dict[str, Any] = {
        "local_files_only": True, "trust_remote_code": False,
        "dtype": torch.bfloat16, "device_map": {"": torch.cuda.current_device()},
    }
    if precision == "NF4_QLORA":
        options["quantization_config"] = quant_config(config)
    base = AutoModelForMultimodalLM.from_pretrained(model_path, **options)
    model = PeftModel.from_pretrained(base, adapter_path, local_files_only=True, is_trainable=False)
    model.eval()
    encoded = tokenizer.apply_chat_template(row["messages"][:2], tokenize=True, add_generation_prompt=True, enable_thinking=False, return_tensors="pt")
    input_ids = encoded if isinstance(encoded, torch.Tensor) else encoded["input_ids"]
    attention = torch.ones_like(input_ids)
    input_ids, attention = input_ids.to(next(model.parameters()).device), attention.to(next(model.parameters()).device)
    with torch.inference_mode():
        generated = model.generate(input_ids=input_ids, attention_mask=attention, max_new_tokens=128, do_sample=False, use_cache=True, pad_token_id=tokenizer.pad_token_id, eos_token_id=tokenizer.eos_token_id)
    raw = tokenizer.decode(generated[0, input_ids.shape[1]:], skip_special_tokens=True).strip()
    try:
        parsed = json.loads(raw)
        valid = isinstance(parsed, dict) and set(parsed) == {"selectedCauseIndexes", "selectedCheckIndexes", "limitation"}
    except json.JSONDecodeError:
        valid = False
    del model, base
    gc.collect()
    torch.cuda.empty_cache()
    return {"validExactT1": valid, "output": raw[:2048]}


def main() -> None:
    args = parse_args()
    config_path, environment_path, architecture_path, report_path = map(resolve_repo, (args.config, args.environment, args.architecture, args.report))
    output_dir, training_dir = outside_repo(args.output_dir, "--output-dir"), outside_repo(args.training_dir, "--training-dir")
    if report_path.exists():
        raise RuntimeError(f"report already exists; overwrite forbidden: {report_path}")
    if args.smoke and args.smoke_max_steps != 10:
        raise RuntimeError("Phase 5.4 smoke test is fixed at 10 optimizer steps")
    if not args.smoke and args.output_dir.name != "P5.4-T1-real-world-r8":
        raise RuntimeError("real adapter output name drifted")
    config, environment, architecture = read_json(config_path), read_json(environment_path), read_json(architecture_path)
    precision = require_config(config, environment, architecture)
    base = model_path(environment)
    dataset = read_json(resolve_repo(Path(config["dataset"]["manifest"])))
    manifest_path = resolve_repo(Path(config["dataset"]["manifest"]))
    train_path, validation_path = resolve_repo(Path(config["dataset"]["train"])), resolve_repo(Path(config["dataset"]["validation"]))
    if sha256_file(manifest_path) != config["dataset"]["manifestSha256"] or sha256_file(train_path) != config["dataset"]["trainSha256"] or sha256_file(validation_path) != config["dataset"]["validationSha256"] or dataset.get("sourceManifestSha256") != config["dataset"]["sourceManifestSha256"]:
        raise RuntimeError("Phase 5.4 dataset hash contract failed")
    train_rows = load_rows(train_path, args.smoke_train_examples if args.smoke else None)
    validation_rows = [] if args.smoke else load_rows(validation_path)
    configure_offline()
    set_seed(config["seed"])
    torch.manual_seed(config["seed"])
    torch.cuda.manual_seed_all(config["seed"])
    tokenizer = AutoTokenizer.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    if tokenizer.pad_token_id is None:
        raise RuntimeError("tokenizer has no pad token")
    train_records, train_tokens = tokenize_rows(train_rows, tokenizer, config["maximumSequenceLength"])
    validation_records, validation_tokens = tokenize_rows(validation_rows, tokenizer, config["maximumSequenceLength"]) if validation_rows else ([], 0)
    if not args.smoke and len(train_rows) != 140 or not args.smoke and len(validation_rows) != 40:
        raise RuntimeError("Phase 5.4 development row count drifted")
    set_seed(config["seed"])
    torch.cuda.reset_peak_memory_stats()
    started, wall_started = datetime.now(timezone.utc).replace(microsecond=0), time.perf_counter()
    model = load_train_model(base, TARGETS, config, precision)
    trainable = sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad)
    total = sum(parameter.numel() for parameter in model.parameters())
    if trainable <= 0:
        raise RuntimeError("LoRA attached zero trainable parameters")
    training_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)
    training_args = TrainingArguments(
        output_dir=str(training_dir), per_device_train_batch_size=1, per_device_eval_batch_size=1,
        gradient_accumulation_steps=16, num_train_epochs=1 if args.smoke else 6,
        max_steps=args.smoke_max_steps if args.smoke else -1, learning_rate=0.0001,
        lr_scheduler_type="linear", warmup_steps=0, optim="paged_adamw_8bit", bf16=True,
        gradient_checkpointing=True, gradient_checkpointing_kwargs={"use_reentrant": False},
        logging_strategy="steps", logging_steps=1 if args.smoke else 10, logging_first_step=True,
        eval_strategy="no" if args.smoke else "epoch", save_strategy="steps" if args.smoke else "epoch",
        eval_steps=None, save_steps=args.smoke_max_steps if args.smoke else 1, save_total_limit=6,
        report_to=[], run_name=RUN_ID + ("-smoke" if args.smoke else ""), seed=520021, data_seed=520021,
        remove_unused_columns=False, label_names=["labels"], dataloader_num_workers=0,
        do_train=True, do_eval=not args.smoke, prediction_loss_only=True, skip_memory_metrics=False, use_cache=False,
    )
    trainer = Trainer(model=model, args=training_args, train_dataset=TokenizedRows(train_records), eval_dataset=None if args.smoke else TokenizedRows(validation_records), data_collator=CausalCollator(tokenizer), processing_class=tokenizer)
    train_result = trainer.train()
    eval_metrics = {} if args.smoke else trainer.evaluate()
    if not finite_history(trainer.state.log_history):
        raise RuntimeError("non-finite loss or gradient norm observed")
    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)
    adapter_hash = directory_sha256(output_dir)
    checkpoints = {item.name: {"sizeBytes": directory_size(item), "sha256": directory_sha256(item)} for item in sorted(training_dir.glob("checkpoint-*")) if item.is_dir()}
    finished = datetime.now(timezone.utc).replace(microsecond=0)
    report: dict[str, Any] = {
        "schemaVersion": "1", "status": "PASS", "runId": RUN_ID, "smoke": args.smoke,
        "precision": precision, "trainingPerformed": True, "lockedFinalLoaded": False,
        "cudaAllocatorConfig": os.environ.get("PYTORCH_CUDA_ALLOC_CONF", "default"),
        "resumeFromCheckpoint": False,
        "startTimeUtc": started.isoformat(), "finishTimeUtc": finished.isoformat(),
        "durationSeconds": round(time.perf_counter() - wall_started, 3),
        "baseRepository": environment["baseModel"]["repository"], "baseRevision": REVISION,
        "configurationSha256": sha256_file(config_path), "environmentSha256": sha256_file(environment_path),
        "architectureSha256": sha256_file(architecture_path), "datasetManifestSha256": sha256_file(manifest_path),
        "trainSha256": sha256_file(train_path), "validationSha256": sha256_file(validation_path),
        "sourceManifestSha256": config["dataset"]["sourceManifestSha256"], "seed": 520021,
        "targetModules": TARGETS, "trainableParameters": trainable, "totalParameters": total,
        "trainablePercentage": 100.0 * trainable / architecture["parameterCount"],
        "trainExamples": len(train_rows), "validationExamples": len(validation_rows),
        "trainTokensPerEpoch": train_tokens, "validationTokens": validation_tokens,
        "trainingSteps": trainer.state.global_step, "epochs": float(trainer.state.epoch or 0.0),
        "trainingMetrics": dict(train_result.metrics), "validationMetrics": eval_metrics,
        "lossHistory": [entry for entry in trainer.state.log_history if "loss" in entry or "eval_loss" in entry],
        "gradientNormHistory": [entry["grad_norm"] for entry in trainer.state.log_history if "grad_norm" in entry],
        "peakGpuVramBytes": torch.cuda.max_memory_allocated(), "peakGpuVramReservedBytes": torch.cuda.max_memory_reserved(),
        "systemMemory": {"totalBytes": psutil.virtual_memory().total, "availableBytesAtFinish": psutil.virtual_memory().available},
        "checkpointSizes": checkpoints, "adapterPath": "<WSL_CACHE>/adapters/phase-5-4/" + output_dir.name,
        "adapterSizeBytes": directory_size(output_dir), "adapterSha256": adapter_hash,
        "networkAccessDuringTraining": False, "platform": platform.platform(), "python": sys.version.split()[0],
        "packages": {name: importlib.metadata.version(name) for name in ("accelerate", "bitsandbytes", "peft", "torch", "transformers")},
    }
    if args.smoke:
        report["smokeInference"] = generation_probe(base, output_dir, tokenizer, train_rows[0], precision, config)
        if not report["smokeInference"]["validExactT1"]:
            report["status"] = "FAILED_SMOKE_INFERENCE_T1"
    write_exclusive(report_path, report)
    print(json.dumps({"status": report["status"], "report": str(report_path), "adapterSha256": adapter_hash}))
    if report["status"] != "PASS":
        raise SystemExit(4)


if __name__ == "__main__":
    main()
