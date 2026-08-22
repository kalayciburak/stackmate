#!/usr/bin/env python3
"""Run the bounded, offline Phase 5.2 QLoRA experiment."""

from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.metadata
import importlib.util
import json
import math
import os
import platform
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import psutil
import torch
from peft import (
    LoraConfig,
    PeftModel,
    TaskType,
    get_peft_model,
    prepare_model_for_kbit_training,
)
from torch.utils.data import Dataset
from transformers import (
    AutoModelForMultimodalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    Trainer,
    TrainingArguments,
    set_seed,
)

from dataset_contract import ROOT, sha256_file


EXPECTED_RUNS = {
    "F1-qlora-r8": {"rank": 8, "alpha": 16},
    "F2-qlora-r16": {"rank": 16, "alpha": 32},
}
COMMON_EXPECTATIONS = {
    "dropout": 0.05,
    "learningRate": 0.0001,
    "epochs": 2,
    "perDeviceTrainBatchSize": 1,
    "perDeviceValidationBatchSize": 1,
    "gradientAccumulationSteps": 16,
    "gradientCheckpointing": True,
    "maximumSequenceLength": 2048,
    "seed": 520021,
}


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def directory_size(path: Path) -> int:
    return sum(item.stat().st_size for item in path.rglob("*") if item.is_file())


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


def resolve_repo_path(value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (ROOT / path).resolve()


def require_outside_repo(value: str, name: str) -> Path:
    path = Path(value).expanduser().resolve()
    if not path.is_absolute():
        raise ValueError(f"{name} must be an absolute path")
    if path == ROOT or ROOT in path.parents:
        raise ValueError(f"{name} must remain outside the Git repository: {path}")
    return path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--environment", default="configs/environment-v1.json")
    parser.add_argument("--architecture", default="reports/model-architecture.json")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--training-dir", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--smoke-train-examples", type=int, default=4)
    parser.add_argument("--smoke-validation-examples", type=int, default=2)
    parser.add_argument("--smoke-max-steps", type=int, default=3)
    parser.add_argument("--smoke-max-new-tokens", type=int, default=128)
    parser.add_argument("--resume-from-checkpoint")
    return parser.parse_args()


def check_config(config: dict[str, Any], environment: dict[str, Any]) -> None:
    run_id = config.get("runId")
    if run_id not in EXPECTED_RUNS:
        raise ValueError(f"Only the planned F1/F2 runs are permitted: {run_id}")
    for key, expected in COMMON_EXPECTATIONS.items():
        if config.get(key) != expected:
            raise ValueError(f"{run_id} changed locked setting {key}: {config.get(key)} != {expected}")
    for key, expected in EXPECTED_RUNS[run_id].items():
        if config.get("rank" if key == "rank" else "alpha") != expected:
            raise ValueError(f"{run_id} changed locked setting {key}")
    quantization = config.get("quantization", {})
    expected_quantization = {
        "bits": 4,
        "type": "nf4",
        "doubleQuantization": True,
        "computeDtype": "bfloat16",
    }
    if quantization != expected_quantization:
        raise ValueError(f"Unexpected quantization configuration: {quantization}")
    if config.get("environment") != "configs/environment-v1.json":
        raise ValueError("Run must use the locked environment configuration")
    if config.get("targetModulesSource") != "reports/model-architecture.json":
        raise ValueError("Run must use the inspected architecture report")
    if environment.get("maximumSequenceLength") != COMMON_EXPECTATIONS["maximumSequenceLength"]:
        raise ValueError("Environment sequence limit drifted")


def model_path_from_environment(environment: dict[str, Any]) -> Path:
    setting = environment["baseModel"]["localPathEnvironment"]
    value = os.environ.get(setting, "").strip() or os.environ.get("BASE_MODEL_PATH", "").strip()
    if not value:
        raise RuntimeError(f"BLOCKED: {setting} or BASE_MODEL_PATH is not set")
    path = Path(value).expanduser().resolve()
    if path.name != environment["baseModel"]["revision"]:
        raise RuntimeError(f"Base model is not pinned to the expected snapshot: {path}")
    required = environment["baseModel"]["requiredFiles"]
    missing = [name for name in required if not (path / name).is_file()]
    if missing:
        raise RuntimeError(f"Base model is incomplete: {missing}")
    return path


def selected_targets(architecture: dict[str, Any]) -> list[str]:
    targets = architecture.get("selectedTargetModules")
    if not isinstance(targets, list) or not targets or not all(isinstance(item, str) for item in targets):
        raise RuntimeError("Architecture report has no selected target modules")
    samples = architecture.get("linearModuleSamples", {})
    for target in targets:
        for sample in samples.get(target, []):
            if any(token in sample.casefold() for token in ("visual", "vision", "merger", "embed", "lm_head")):
                raise RuntimeError(f"Visual/output module leaked into LoRA target {target}: {sample}")
    return sorted(set(targets))


def load_rows(path: Path, limit: int | None = None) -> list[dict[str, Any]]:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    if limit is not None:
        rows = rows[:limit]
    if not rows:
        raise ValueError(f"No rows loaded from {path}")
    return rows


def tokenize_rows(
    rows: list[dict[str, Any]], tokenizer: Any, maximum_length: int
) -> tuple[list[dict[str, Any]], int]:
    records: list[dict[str, Any]] = []
    token_count = 0
    for row in rows:
        messages = row["messages"]
        prefix = tokenizer.apply_chat_template(
            messages[:2],
            tokenize=True,
            add_generation_prompt=True,
            enable_thinking=False,
            return_dict=False,
        )
        full = tokenizer.apply_chat_template(
            messages,
            tokenize=True,
            add_generation_prompt=False,
            enable_thinking=False,
            return_dict=False,
        )
        if full[: len(prefix)] != prefix:
            raise ValueError(f"Assistant boundary mismatch: {row['exampleId']}")
        if len(full) > maximum_length:
            raise ValueError(f"Truncation would be required: {row['exampleId']}")
        labels = [-100] * len(prefix) + full[len(prefix) :]
        if all(value == -100 for value in labels):
            raise ValueError(f"Empty assistant target: {row['exampleId']}")
        records.append(
            {
                "input_ids": full,
                "attention_mask": [1] * len(full),
                "labels": labels,
                "exampleId": row["exampleId"],
            }
        )
        token_count += len(full)
    return records, token_count


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
        input_ids: list[list[int]] = []
        attention: list[list[int]] = []
        labels: list[list[int]] = []
        for item in features:
            padding = maximum - len(item["input_ids"])
            input_ids.append(item["input_ids"] + [self.pad_token_id] * padding)
            attention.append(item["attention_mask"] + [0] * padding)
            labels.append(item["labels"] + [-100] * padding)
        return {
            "input_ids": torch.tensor(input_ids, dtype=torch.long),
            "attention_mask": torch.tensor(attention, dtype=torch.long),
            "labels": torch.tensor(labels, dtype=torch.long),
        }


def load_quantized_model(model_path: Path, targets: list[str], config: dict[str, Any]) -> Any:
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA is unavailable; QLoRA is not permitted on CPU")
    if not torch.cuda.is_bf16_supported():
        raise RuntimeError("BF16 is unavailable; the locked run requires BF16")
    quantization = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )
    model = AutoModelForMultimodalLM.from_pretrained(
        model_path,
        local_files_only=True,
        trust_remote_code=False,
        quantization_config=quantization,
        dtype=torch.bfloat16,
        device_map={"": torch.cuda.current_device()},
    )
    model.config.use_cache = False
    model = prepare_model_for_kbit_training(
        model,
        use_gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
    )
    lora = LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=int(config["rank"]),
        lora_alpha=int(config["alpha"]),
        lora_dropout=float(config["dropout"]),
        target_modules=targets,
        bias="none",
    )
    model = get_peft_model(model, lora)
    if hasattr(model, "enable_input_require_grads"):
        model.enable_input_require_grads()
    return model


def finite_history(history: list[dict[str, Any]]) -> bool:
    numeric_keys = ("loss", "eval_loss", "grad_norm")
    for entry in history:
        for key in numeric_keys:
            value = entry.get(key)
            if value is not None and (not isinstance(value, (int, float)) or not math.isfinite(value)):
                return False
    return True


def build_training_arguments(
    config: dict[str, Any], training_dir: Path, smoke: bool, smoke_steps: int
) -> TrainingArguments:
    return TrainingArguments(
        output_dir=str(training_dir),
        per_device_train_batch_size=int(config["perDeviceTrainBatchSize"]),
        per_device_eval_batch_size=int(config["perDeviceValidationBatchSize"]),
        gradient_accumulation_steps=int(config["gradientAccumulationSteps"]),
        num_train_epochs=float(config["epochs"]),
        max_steps=smoke_steps if smoke else -1,
        learning_rate=float(config["learningRate"]),
        lr_scheduler_type="linear",
        warmup_steps=0,
        optim="paged_adamw_8bit",
        bf16=True,
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        logging_strategy="steps",
        logging_steps=1 if smoke else 10,
        logging_first_step=True,
        eval_strategy="steps" if smoke else "epoch",
        eval_steps=1 if smoke else None,
        save_strategy="steps" if smoke else "epoch",
        save_steps=1 if smoke else 1,
        save_total_limit=2,
        report_to=[],
        run_name=config["runId"] + ("-smoke" if smoke else ""),
        seed=int(config["seed"]),
        data_seed=int(config["seed"]),
        remove_unused_columns=False,
        label_names=["labels"],
        dataloader_num_workers=0,
        do_train=True,
        do_eval=True,
        prediction_loss_only=True,
        skip_memory_metrics=False,
        use_cache=False,
    )


def generation_probe(
    model_path: Path,
    adapter_path: Path,
    tokenizer: Any,
    row: dict[str, Any],
    maximum_new_tokens: int,
) -> dict[str, Any]:
    quantization = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )
    fresh_base = AutoModelForMultimodalLM.from_pretrained(
        model_path,
        local_files_only=True,
        trust_remote_code=False,
        quantization_config=quantization,
        dtype=torch.bfloat16,
        device_map={"": torch.cuda.current_device()},
    )
    model = PeftModel.from_pretrained(
        fresh_base,
        adapter_path,
        local_files_only=True,
        is_trainable=False,
    )
    model.eval()
    prompt = tokenizer.apply_chat_template(
        row["messages"][:2],
        tokenize=True,
        add_generation_prompt=True,
        enable_thinking=False,
        return_tensors="pt",
    )
    if isinstance(prompt, torch.Tensor):
        input_ids = prompt
        attention_mask = torch.ones_like(input_ids)
    else:
        input_ids = prompt["input_ids"]
        attention_mask = prompt["attention_mask"]
    device = next(model.parameters()).device
    input_ids = input_ids.to(device)
    attention_mask = attention_mask.to(device)
    with torch.inference_mode():
        generated = model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_new_tokens=maximum_new_tokens,
            do_sample=False,
            use_cache=True,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    continuation = generated[0, input_ids.shape[1] :]
    text = tokenizer.decode(continuation, skip_special_tokens=True).strip()
    parsed: Any = None
    valid_json = False
    try:
        parsed = json.loads(text)
        valid_json = isinstance(parsed, dict)
    except json.JSONDecodeError:
        pass
    del model, fresh_base
    gc.collect()
    torch.cuda.empty_cache()
    return {"validJson": valid_json, "output": text[:4096]}


def main() -> None:
    arguments = parse_args()
    config_path = resolve_repo_path(arguments.config)
    environment_path = resolve_repo_path(arguments.environment)
    architecture_path = resolve_repo_path(arguments.architecture)
    report_path = resolve_repo_path(arguments.report)
    output_dir = require_outside_repo(arguments.output_dir, "--output-dir")
    training_dir = require_outside_repo(arguments.training_dir, "--training-dir")
    config = read_json(config_path)
    environment = read_json(environment_path)
    architecture = read_json(architecture_path)
    check_config(config, environment)
    model_path = model_path_from_environment(environment)
    targets = selected_targets(architecture)
    tokenizer = AutoTokenizer.from_pretrained(
        model_path, local_files_only=True, trust_remote_code=False
    )
    if tokenizer.pad_token_id is None:
        raise RuntimeError("Tokenizer has no pad token")
    train_limit = arguments.smoke_train_examples if arguments.smoke else None
    validation_limit = arguments.smoke_validation_examples if arguments.smoke else None
    train_rows = load_rows(ROOT / "datasets/train.jsonl", train_limit)
    validation_rows = load_rows(ROOT / "datasets/validation.jsonl", validation_limit)
    train_records, train_tokens = tokenize_rows(
        train_rows, tokenizer, int(config["maximumSequenceLength"])
    )
    validation_records, validation_tokens = tokenize_rows(
        validation_rows, tokenizer, int(config["maximumSequenceLength"])
    )

    set_seed(int(config["seed"]))
    if torch.cuda.is_available():
        torch.cuda.reset_peak_memory_stats()
    started = datetime.now(timezone.utc).replace(microsecond=0)
    wall_started = time.perf_counter()
    model = load_quantized_model(model_path, targets, config)
    trainable = sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad)
    total = sum(parameter.numel() for parameter in model.parameters())
    if trainable <= 0:
        raise RuntimeError("LoRA attached zero trainable parameters")
    print(json.dumps({"runId": config["runId"], "trainableParameters": trainable, "totalParameters": total}))
    training_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)
    trainer = Trainer(
        model=model,
        args=build_training_arguments(
            config, training_dir, arguments.smoke, arguments.smoke_max_steps
        ),
        train_dataset=TokenizedRows(train_records),
        eval_dataset=TokenizedRows(validation_records),
        data_collator=CausalCollator(tokenizer),
        processing_class=tokenizer,
    )
    train_result = trainer.train(resume_from_checkpoint=arguments.resume_from_checkpoint)
    eval_metrics = trainer.evaluate()
    if not finite_history(trainer.state.log_history):
        raise RuntimeError("Non-finite loss or gradient norm observed")
    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)
    adapter_hash = directory_sha256(output_dir)
    finished = datetime.now(timezone.utc).replace(microsecond=0)
    wall_seconds = time.perf_counter() - wall_started
    train_metrics = dict(train_result.metrics)
    train_runtime = float(train_metrics.get("train_runtime", wall_seconds))
    epochs = float(config["epochs"])
    if arguments.smoke:
        epochs = float(trainer.state.epoch or 0.0)
    estimated_tokens = train_tokens * max(1.0, epochs)
    checkpoints = {
        item.name: {
            "sizeBytes": directory_size(item),
            "sha256": directory_sha256(item),
        }
        for item in sorted(training_dir.glob("checkpoint-*"))
        if item.is_dir()
    }
    report = {
        "schemaVersion": "1",
        "status": "PASS",
        "runId": config["runId"],
        "smoke": arguments.smoke,
        "resumedFromCheckpoint": (
            None
            if arguments.resume_from_checkpoint is None
            else "<WSL_CACHE>/training/phase-5-2/"
            + training_dir.name
            + "/"
            + Path(arguments.resume_from_checkpoint).name
        ),
        "startTimeUtc": started.isoformat(),
        "finishTimeUtc": finished.isoformat(),
        "durationSeconds": round(wall_seconds, 3),
        "baseRepository": environment["baseModel"]["repository"],
        "baseRevision": environment["baseModel"]["revision"],
        "baseModelPath": "<WSL_HF_CACHE>/models--Qwen--Qwen3.5-2B/snapshots/" + environment["baseModel"]["revision"],
        "configurationSha256": sha256_file(config_path),
        "environmentSha256": sha256_file(environment_path),
        "datasetLockSha256": sha256_file(ROOT / "datasets/lock.json"),
        "datasetManifestSha256": read_json(ROOT / "datasets/lock.json")["datasetManifestSha256"],
        "baseModelManifestSha256": sha256_file(ROOT / "reports/base-model-manifest.json"),
        "modelArchitectureSha256": sha256_file(architecture_path),
        "seed": config["seed"],
        "targetModules": targets,
        "quantization": config["quantization"],
        "computeDtype": "bfloat16",
        "trainableParameters": trainable,
        "totalParameters": architecture.get("parameterCount", total),
        "loadedParameterCount": total,
        "trainablePercentage": 100.0 * trainable / architecture.get("parameterCount", total),
        "trainExamples": len(train_records),
        "validationExamples": len(validation_records),
        "trainTokensPerEpoch": train_tokens,
        "validationTokens": validation_tokens,
        "trainingSteps": trainer.state.global_step,
        "epochs": epochs,
        "learningRate": config["learningRate"],
        "trainingMetrics": train_metrics,
        "validationMetrics": eval_metrics,
        "lossHistory": [entry for entry in trainer.state.log_history if "loss" in entry or "eval_loss" in entry],
        "gradientNormHistory": [entry["grad_norm"] for entry in trainer.state.log_history if "grad_norm" in entry],
        "tokensPerSecond": estimated_tokens / train_runtime if train_runtime > 0 else 0.0,
        "peakGpuVramBytes": torch.cuda.max_memory_allocated() if torch.cuda.is_available() else 0,
        "systemMemory": {
            "totalBytes": psutil.virtual_memory().total,
            "availableBytesAtFinish": psutil.virtual_memory().available,
        },
        "checkpointSizes": checkpoints,
        "adapterPath": "<WSL_CACHE>/adapters/phase-5-2/" + output_dir.name,
        "adapterSizeBytes": directory_size(output_dir),
        "adapterSha256": adapter_hash,
        "warnings": [
            "flash-linear-attention and causal-conv1d fast paths were not installed; Transformers used the torch implementation."
        ]
        if importlib.util.find_spec("fla") is None
        else [],
        "networkAccessDuringTraining": False,
        "platform": platform.platform(),
        "python": sys.version.split()[0],
        "packages": {
            name: importlib.metadata.version(name)
            for name in (
                "accelerate",
                "bitsandbytes",
                "peft",
                "torch",
                "transformers",
            )
        },
    }
    if arguments.smoke:
        report["smokeInference"] = generation_probe(
            model_path,
            output_dir,
            tokenizer,
            validation_rows[0],
            arguments.smoke_max_new_tokens,
        )
        if not report["smokeInference"]["validJson"]:
            report["status"] = "FAILED_SMOKE_INFERENCE_JSON"
    write_json(report_path, report)
    print(json.dumps({"status": report["status"], "report": str(report_path), "adapterSha256": adapter_hash}))
    if report["status"] != "PASS":
        raise SystemExit(4)


if __name__ == "__main__":
    main()
