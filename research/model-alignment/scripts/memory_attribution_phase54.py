#!/usr/bin/env python3
"""Stage-scoped BF16 CUDA memory attribution for Phase 5.4.1."""

from __future__ import annotations

import argparse
import atexit
import gc
import importlib.metadata
import json
import math
import os
import platform
import subprocess
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import psutil
import torch
from peft import PeftModel
from transformers import AutoModelForMultimodalLM, AutoTokenizer, Trainer, TrainingArguments

import train_phase54_t1 as training


ROOT = Path(__file__).resolve().parents[1]
REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
DATASET_MANIFEST = "0e2e23241b582fb4f7c5fb25b87a5f08016f28f8be0c7525595e170467f5d2b2"
TRAIN_HASH = "ee52ae93457b9dc7f663f83b1acea37df6c25f437ac84261c951844a5965ba93"
VALIDATION_HASH = "bc8e2b5573c57f6927f0e8547968e29fa3bf0f24b221d7bb402b13ba04680789"


class NvidiaSampler:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.stop_event = threading.Event()
        self.thread: threading.Thread | None = None
        self.samples: list[dict[str, Any]] = []
        self.stopped = False

    def start(self) -> None:
        self.thread = threading.Thread(target=self._run, name="nvidia-smi-sampler", daemon=True)
        self.thread.start()

    def _run(self) -> None:
        while not self.stop_event.is_set():
            timestamp = datetime.now(timezone.utc).isoformat()
            try:
                completed = subprocess.run(
                    ["nvidia-smi", "--query-gpu=index,memory.total,memory.used,memory.free,utilization.gpu", "--format=csv,noheader,nounits"],
                    check=True, capture_output=True, text=True, timeout=5,
                )
                records = []
                for line in completed.stdout.strip().splitlines():
                    parts = [part.strip() for part in line.split(",")]
                    if len(parts) == 5:
                        records.append({"index": int(parts[0]), "totalMiB": int(parts[1]), "usedMiB": int(parts[2]), "freeMiB": int(parts[3]), "utilizationPercent": int(parts[4])})
                self.samples.append({"timestamp": timestamp, "records": records})
            except Exception as failure:
                self.samples.append({"timestamp": timestamp, "error": type(failure).__name__})
            self.stop_event.wait(0.25)

    def stop(self) -> None:
        if self.stopped:
            return
        self.stopped = True
        self.stop_event.set()
        if self.thread is not None:
            self.thread.join(timeout=5)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("x", encoding="utf-8", newline="\n") as destination:
            for sample in self.samples:
                destination.write(json.dumps(sample, separators=(",", ":")) + "\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=("M0", "M1", "M2", "M3", "M4"), required=True)
    parser.add_argument("--base-model", required=True, type=Path)
    parser.add_argument("--adapter", type=Path)
    parser.add_argument("--output-adapter", type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--trace", required=True, type=Path)
    parser.add_argument("--architecture", default="reports/model-architecture.json", type=Path)
    parser.add_argument("--config", default="configs/qlora-phase54-t1-r8.json", type=Path)
    return parser.parse_args()


def resolve_repo(path: Path) -> Path:
    return path.expanduser().resolve() if path.is_absolute() else (ROOT / path).resolve()


def snapshot(stage: str, event: str, extra: dict[str, Any] | None = None) -> dict[str, Any]:
    torch.cuda.synchronize()
    free, total = torch.cuda.mem_get_info()
    stats = torch.cuda.memory_stats()
    reserved = torch.cuda.memory_reserved()
    allocated = torch.cuda.memory_allocated()
    result: dict[str, Any] = {
        "stage": stage,
        "event": event,
        "timestampUtc": datetime.now(timezone.utc).isoformat(),
        "pid": os.getpid(),
        "cudaDevice": torch.cuda.current_device(),
        "deviceTotalBytes": total,
        "deviceFreeBytes": free,
        "currentAllocatedBytes": allocated,
        "peakAllocatedBytes": torch.cuda.max_memory_allocated(),
        "currentReservedBytes": reserved,
        "peakReservedBytes": torch.cuda.max_memory_reserved(),
        "reservedMinusAllocatedBytes": max(0, reserved - allocated),
        "activeBytes": stats.get("active_bytes.all.current"),
        "inactiveSplitBytes": stats.get("inactive_split_bytes.all.current"),
        "requestedBytes": stats.get("requested_bytes.all.current"),
        "allocationRetries": stats.get("num_alloc_retries", 0),
        "oomCount": stats.get("num_ooms", 0),
        "processRssBytes": psutil.Process().memory_info().rss,
        "systemRamAvailableBytes": psutil.virtual_memory().available,
        "memoryStatsKeys": sorted(stats.keys()),
    }
    if extra:
        result.update(extra)
    return result


def load_base(path: Path) -> Any:
    if path.name != REVISION:
        raise RuntimeError("base revision mismatch")
    return AutoModelForMultimodalLM.from_pretrained(path, local_files_only=True, trust_remote_code=False, dtype=torch.bfloat16, device_map={"": torch.cuda.current_device()})


def load_adapter(path: Path, adapter: Path) -> Any:
    return PeftModel.from_pretrained(path, adapter, local_files_only=True, is_trainable=False)


def attach_lora(model: Any, config: dict[str, Any]) -> Any:
    model.config.use_cache = False
    from peft import LoraConfig, TaskType, get_peft_model
    result = get_peft_model(model, LoraConfig(task_type=TaskType.CAUSAL_LM, r=8, lora_alpha=16, lora_dropout=0.05, target_modules=config["targetModules"], bias="none"))
    if hasattr(result, "enable_input_require_grads"):
        result.enable_input_require_grads()
    return result


def load_and_verify_rows(path: Path, expected_hash: str, tokenizer: Any, limit: int | None = None) -> tuple[list[dict[str, Any]], list[dict[str, Any]], int]:
    if training.sha256_file(path) != expected_hash:
        raise RuntimeError(f"dataset checksum mismatch: {path}")
    rows = training.load_rows(path, limit)
    records, tokens = training.tokenize_rows(rows, tokenizer, 2048)
    return rows, records, tokens


def summarize_sampler(samples: list[dict[str, Any]]) -> dict[str, Any]:
    records = [record for sample in samples for record in sample.get("records", [])]
    return {
        "sampleCount": len(samples),
        "minimumDeviceFreeMiB": min((record["freeMiB"] for record in records), default=None),
        "maximumUsedMiB": max((record["usedMiB"] for record in records), default=None),
        "maximumUtilizationPercent": max((record["utilizationPercent"] for record in records), default=None),
    }


def main() -> None:
    arguments = parse_args()
    report_path = resolve_repo(arguments.report)
    trace_path = resolve_repo(arguments.trace)
    if report_path.exists() or trace_path.exists():
        raise FileExistsError("memory attribution reports never overwrite")
    base_path = arguments.base_model.expanduser().resolve()
    config = training.read_json(resolve_repo(arguments.config))
    architecture = training.read_json(resolve_repo(arguments.architecture))
    if config.get("targetModules") != architecture.get("selectedTargetModules"):
        raise RuntimeError("target module drift")
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported():
        raise RuntimeError("CUDA/BF16 unavailable")
    os.environ.update({"HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1", "HF_HUB_DISABLE_TELEMETRY": "1", "WANDB_DISABLED": "true"})
    tokenizer = AutoTokenizer.from_pretrained(base_path, local_files_only=True, trust_remote_code=False)
    if tokenizer.pad_token_id is None:
        raise RuntimeError("tokenizer has no pad token")
    torch.cuda.reset_peak_memory_stats()
    sampler = NvidiaSampler(trace_path)
    sampler.start()
    atexit.register(sampler.stop)
    if arguments.stage == "M0":
        stage_model = load_base(base_path)
        rows = []
        records = []
        tokens = 0
    elif arguments.stage == "M1":
        stage_model = attach_lora(load_base(base_path), config)
        rows = []
        records = []
        tokens = 0
    elif arguments.stage == "M2":
        train_path = resolve_repo(Path(config["dataset"]["train"]))
        if training.sha256_file(train_path) != TRAIN_HASH:
            raise RuntimeError(f"dataset checksum mismatch: {train_path}")
        rows_all = training.load_rows(train_path)
        scored: list[tuple[int, dict[str, Any]]] = []
        for row in rows_all:
            prefix = tokenizer.apply_chat_template(row["messages"][:2], tokenize=True, add_generation_prompt=True, enable_thinking=False, return_dict=False)
            full = tokenizer.apply_chat_template(row["messages"], tokenize=True, add_generation_prompt=False, enable_thinking=False, return_dict=False)
            scored.append((len(full), row))
        rows = [row for _, row in sorted(scored, key=lambda item: (-item[0], f"{item[1].get('familyId')}/v{item[1].get('variant')}"))[:16]]
        records, tokens = training.tokenize_rows(rows, tokenizer, 2048)
        stage_model = attach_lora(load_base(base_path), config)
    elif arguments.stage in {"M3", "M4"}:
        if arguments.adapter is None or not arguments.adapter.is_dir():
            raise RuntimeError("M3/M4 require an adapter")
        base = load_base(base_path)
        stage_model = load_adapter(base, arguments.adapter.expanduser().resolve())
        rows_path = Path(config["dataset"]["train"] if arguments.stage == "M3" else config["dataset"]["validation"])
        rows, records, tokens = load_and_verify_rows(resolve_repo(rows_path), TRAIN_HASH if arguments.stage == "M3" else VALIDATION_HASH, tokenizer, 1 if arguments.stage == "M3" else None)
    else:
        raise AssertionError(arguments.stage)
    snapshots: list[dict[str, Any]] = [snapshot(arguments.stage, "after_load")]
    if arguments.stage == "M2":
        training_args = TrainingArguments(output_dir=str(resolve_repo(arguments.output_adapter or Path("/tmp/unused"))), per_device_train_batch_size=1, gradient_accumulation_steps=16, max_steps=10, num_train_epochs=1, learning_rate=0.0001, lr_scheduler_type="linear", warmup_steps=0, optim="paged_adamw_8bit", bf16=True, gradient_checkpointing=True, gradient_checkpointing_kwargs={"use_reentrant": False}, logging_strategy="steps", logging_steps=1, save_strategy="no", eval_strategy="no", report_to=[], seed=520021, data_seed=520021, remove_unused_columns=False, label_names=["labels"], dataloader_num_workers=0, do_train=True, do_eval=False, prediction_loss_only=True, use_cache=False)
        trainer = Trainer(model=stage_model, args=training_args, train_dataset=training.TokenizedRows(records), data_collator=training.CausalCollator(tokenizer), processing_class=tokenizer)
        result = trainer.train()
        if not math.isfinite(float(result.training_loss)):
            raise RuntimeError("M2 loss is not finite")
        snapshots.append(snapshot(arguments.stage, "after_training_before_save", {"trainingLoss": result.training_loss, "trainingSteps": trainer.state.global_step}))
        if arguments.output_adapter is None:
            raise RuntimeError("M2 requires --output-adapter")
        output_adapter = arguments.output_adapter.expanduser().resolve()
        output_adapter.mkdir(parents=True, exist_ok=True)
        trainer.save_model(output_adapter)
        tokenizer.save_pretrained(output_adapter)
        snapshots.append(snapshot(arguments.stage, "after_save", {"adapterSha256": training.directory_sha256(output_adapter)}))
    elif arguments.stage in {"M3", "M4"}:
        stage_model.eval()
        for row in rows:
            encoded = tokenizer.apply_chat_template(row["messages"][:2], tokenize=True, add_generation_prompt=True, enable_thinking=False, return_tensors="pt")
            input_ids = encoded if isinstance(encoded, torch.Tensor) else encoded["input_ids"]
            attention = torch.ones_like(input_ids)
            device = next(stage_model.parameters()).device
            with torch.inference_mode():
                output = stage_model.generate(input_ids=input_ids.to(device), attention_mask=attention.to(device), max_new_tokens=128, do_sample=False, use_cache=True, pad_token_id=tokenizer.pad_token_id, eos_token_id=tokenizer.eos_token_id)
            json.loads(tokenizer.decode(output[0, input_ids.shape[1]:], skip_special_tokens=True))
        snapshots.append(snapshot(arguments.stage, "after_generation", {"rowsEvaluated": len(rows)}))
    sampler.stop()
    final = snapshots[-1]
    report = {
        "schemaVersion": "1", "status": "PASS", "stage": arguments.stage,
        "pid": os.getpid(), "baseRevision": REVISION, "lockedFinalLoaded": False,
        "trainRows": len(rows) if arguments.stage in {"M2", "M3"} else None,
        "validationRows": len(rows) if arguments.stage == "M4" else 0,
        "tokens": tokens, "snapshots": snapshots, "nvidiaSmiSummary": summarize_sampler(sampler.samples),
        "nvidiaSmiSamplingIntervalSeconds": 0.25,
        "cudaAllocatorConfig": os.environ.get("PYTORCH_CUDA_ALLOC_CONF", "default"),
        "tracePath": "<WSL_CACHE>/phase-5-4-1-memory/" + trace_path.name,
        "packages": {name: importlib.metadata.version(name) for name in ("torch", "transformers", "peft", "accelerate", "bitsandbytes")},
        "platform": platform.platform(),
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with report_path.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(report, destination, ensure_ascii=False, indent=2)
        destination.write("\n")
    print(json.dumps({"status": "PASS", "stage": arguments.stage, "report": str(report_path), "peakAllocated": final["peakAllocatedBytes"], "peakReserved": final["peakReservedBytes"]}))
    del stage_model, tokenizer
    gc.collect()
    torch.cuda.empty_cache()


if __name__ == "__main__":
    main()
