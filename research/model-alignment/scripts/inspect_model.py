#!/usr/bin/env python3
"""Inspect actual Qwen3.5 module names from an offline official checkpoint."""

from __future__ import annotations

import argparse
import json
import os
from collections import Counter, defaultdict
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
        trust_remote_code=False,
        dtype=torch.bfloat16,
        device_map="cpu",
    )
    linear_names = [name for name, module in model.named_modules() if isinstance(module, torch.nn.Linear)]
    suffix_counts = Counter(name.rsplit(".", 1)[-1] for name in linear_names)
    text_linear_names = [
        name
        for name in linear_names
        if not any(
            token in name.casefold()
            for token in ("visual", "vision", "merger", "embed_tokens", "lm_head")
        )
    ]
    text_suffix_counts = Counter(name.rsplit(".", 1)[-1] for name in text_linear_names)
    samples: dict[str, list[str]] = defaultdict(list)
    for name in linear_names:
        suffix = name.rsplit(".", 1)[-1]
        if len(samples[suffix]) < 5:
            samples[suffix].append(name)
    target_modules = sorted(
        suffix
        for suffix, count in text_suffix_counts.items()
        if count >= 2 and not any(token in suffix.casefold() for token in ("head", "embed", "vision"))
    )
    model_config = model.config.to_dict()
    text_config = model_config.get("text_config", {})
    report = {
        "schemaVersion": "1",
        "status": "INSPECTED_AND_SELECTED",
        "baseRepository": environment["baseModel"]["repository"],
        "baseRevision": environment["baseModel"]["revision"],
        "environmentSha256": sha256_file(environment_path),
        "modelClass": type(model).__name__,
        "modelType": model_config.get("model_type"),
        "architectures": model_config.get("architectures", []),
        "textModelType": text_config.get("model_type"),
        "textDtype": text_config.get("dtype"),
        "hiddenSize": text_config.get("hidden_size"),
        "textLayerCount": text_config.get("num_hidden_layers"),
        "contextLength": text_config.get("max_position_embeddings"),
        "layerTypes": text_config.get("layer_types", []),
        "parameterCount": sum(parameter.numel() for parameter in model.parameters()),
        "linearModuleCount": len(linear_names),
        "linearSuffixCounts": dict(sorted(suffix_counts.items())),
        "textLinearModuleCount": len(text_linear_names),
        "textLinearSuffixCounts": dict(sorted(text_suffix_counts.items())),
        "linearModuleSamples": {key: samples[key] for key in sorted(samples)},
        "candidateTargetModules": target_modules,
        "selectedTargetModules": target_modules,
        "targetSelectionRule": "All repeated text-only language-model projection suffixes; visual, embedding, and output-head modules excluded.",
    }
    output = (ROOT / arguments.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"status": report["status"], "report": str(output)}, separators=(",", ":")))


if __name__ == "__main__":
    main()
