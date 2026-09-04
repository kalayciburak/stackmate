#!/usr/bin/env python3
"""Build the reviewed Phase 5.5.2 atomic contrastive dataset."""

from __future__ import annotations

import argparse
from pathlib import Path

import atomic_contrastive_dataset as dataset


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", type=Path, default=dataset.DEFAULT_TRAIN)
    parser.add_argument("--validation", type=Path, default=dataset.DEFAULT_VALIDATION)
    parser.add_argument("--source-manifest", type=Path, default=dataset.DEFAULT_SOURCE_MANIFEST)
    parser.add_argument("--ontology", type=Path, default=dataset.DEFAULT_ONTOLOGY)
    parser.add_argument("--prompt", type=Path, default=dataset.DEFAULT_PROMPT)
    parser.add_argument("--inventory", type=Path, default=dataset.DEFAULT_INVENTORY)
    parser.add_argument("--output", type=Path, default=dataset.DEFAULT_OUTPUT)
    parser.add_argument("--review-report", type=Path, default=dataset.DEFAULT_REVIEW_REPORT)
    return parser.parse_args()


def main() -> None:
    values = arguments()
    generated = dataset.build_dataset(
        train_path=values.train.resolve(),
        validation_path=values.validation.resolve(),
        source_manifest_path=values.source_manifest.resolve(),
        ontology_path=values.ontology.resolve(),
        prompt_path=values.prompt.resolve(),
        inventory_path=values.inventory.resolve(),
    )
    output = values.output.resolve()
    paths = dataset.OutputPaths(
        output / "families.jsonl",
        output / "train.jsonl",
        output / "validation.jsonl",
        output / "pair-reviews.jsonl",
        output / "manifest.json",
        values.review_report.resolve(),
    )
    manifest = dataset.write_outputs(
        generated,
        paths,
        train_path=values.train.resolve(),
        validation_path=values.validation.resolve(),
        source_manifest_path=values.source_manifest.resolve(),
        ontology_path=values.ontology.resolve(),
        prompt_path=values.prompt.resolve(),
        inventory_path=values.inventory.resolve(),
        build_script_path=Path(__file__).resolve(),
    )
    print(dataset.canonical_json({"status": "PASS", "counts": manifest["counts"]}))


if __name__ == "__main__":
    main()
