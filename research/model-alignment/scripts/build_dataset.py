#!/usr/bin/env python3
"""Build the deterministic Phase 5.2 SFT dataset without reading locked corpora."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from dataset_contract import ROOT, build_examples, canonical_json, count_by, load_config, sha256_file, write_jsonl


def resolve(root: Path, value: str) -> Path:
    return (root / value).resolve()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/dataset-v1.json")
    arguments = parser.parse_args()
    config_path = resolve(ROOT, arguments.config)
    config = load_config(config_path)
    prompt_path = resolve(ROOT, config["prompt"])
    prompt = prompt_path.read_text(encoding="utf-8").strip()
    train, validation = build_examples(config, prompt)
    train_path = resolve(ROOT, config["trainOutput"])
    validation_path = resolve(ROOT, config["validationOutput"])
    write_jsonl(train_path, train)
    write_jsonl(validation_path, validation)
    all_examples = train + validation
    kind_counts = count_by(all_examples, "kind")
    manifest = {
        "schemaVersion": "1",
        "datasetId": config["datasetId"],
        "seed": config["seed"],
        "generator": "scripts/build_dataset.py",
        "generatorSha256": sha256_file(Path(__file__).resolve()),
        "contractSha256": sha256_file((Path(__file__).parent / "dataset_contract.py").resolve()),
        "config": str(config_path.relative_to(ROOT)).replace("\\", "/"),
        "configSha256": sha256_file(config_path),
        "prompt": str(prompt_path.relative_to(ROOT)).replace("\\", "/"),
        "promptSha256": sha256_file(prompt_path),
        "counts": {
            "total": len(all_examples),
            "train": len(train),
            "validation": len(validation),
            "byKind": kind_counts,
            "trainFamilies": len({item["templateFamily"] for item in train}),
            "validationFamilies": len({item["templateFamily"] for item in validation}),
        },
        "ratios": {kind: count / len(all_examples) for kind, count in kind_counts.items()},
        "files": {
            "train": {"path": str(train_path.relative_to(ROOT)).replace("\\", "/"), "sha256": sha256_file(train_path)},
            "validation": {"path": str(validation_path.relative_to(ROOT)).replace("\\", "/"), "sha256": sha256_file(validation_path)},
        },
        "lockedCorpusReadByGenerator": False,
    }
    manifest_path = resolve(ROOT, config["manifestOutput"])
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(canonical_json({"status": "PASS", "manifest": str(manifest_path), "counts": manifest["counts"]}))


if __name__ == "__main__":
    main()
