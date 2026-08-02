#!/usr/bin/env python3
"""Inspect actual Qwen3.5 module names from an offline official checkpoint."""

from __future__ import annotations

import argparse
import json
import os
from collections import Counter
from pathlib import Path

from dataset_contract import ROOT, sha256_file


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", default="configs/environment-v1.json")
    parser.add_argument("--output", default="reports/model-architecture.json")
    arguments = parser.parse_args()
    environment_path = (ROOT / arguments.environment).resolve()
    environment = json.loads(environment_path.read_text(encoding="utf-8"))
    setting = environment["baseModel"]["localPathEnvironment"]
    value = os.environ.get(setting, "").strip()
    if not value:
        raise SystemExit(f"BLOCKED: {setting} is not set; no network fallback is allowed")
    model_path = Path(value).resolve()

    import torch
    from transformers import AutoModelForMultimodalLM

    model = AutoModelForMultimodalLM.from_pretrained(
        model_path,
        local_files_only=True,
        dtype=torch.bfloat16,
        device_map="cpu",
    )
    linear_names = [name for name, module in model.named_modules() if isinstance(module, torch.nn.Linear)]
    suffix_counts = Counter(name.rsplit(".", 1)[-1] for name in linear_names)
    target_modules = sorted(
        suffix
        for suffix, count in suffix_counts.items()
        if count >= 2 and not any(token in suffix.casefold() for token in ("head", "embed", "vision"))
    )
    report = {
        "schemaVersion": "1",
        "status": "INSPECTED_NOT_SELECTED",
        "baseRepository": environment["baseModel"]["repository"],
        "baseRevision": environment["baseModel"]["revision"],
        "environmentSha256": sha256_file(environment_path),
        "modelClass": type(model).__name__,
        "parameterCount": sum(parameter.numel() for parameter in model.parameters()),
        "linearModuleCount": len(linear_names),
        "linearSuffixCounts": dict(sorted(suffix_counts.items())),
        "candidateTargetModules": target_modules,
        "selectedTargetModules": [],
    }
    output = (ROOT / arguments.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"status": report["status"], "report": str(output)}, separators=(",", ":")))


if __name__ == "__main__":
    main()
