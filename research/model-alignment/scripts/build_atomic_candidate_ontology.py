#!/usr/bin/env python3
"""Create the reviewed Phase 5.5.2 atomic candidate ontology."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import atomic_candidate_ontology as contract


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--ontology",
        type=Path,
        default=contract.ROOT / "ontology" / "atomic-candidates-v1.json",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=contract.ROOT / "reports" / "phase-5-5-2-ontology-review.json",
    )
    return parser.parse_args()


def write_new(path: Path, value: dict[str, object]) -> None:
    resolved = path.expanduser().resolve()
    if resolved.exists():
        raise FileExistsError(f"refusing to overwrite: {resolved.name}")
    resolved.parent.mkdir(parents=True, exist_ok=True)
    with resolved.open("x", encoding="utf-8", newline="\n") as destination:
        json.dump(value, destination, ensure_ascii=False, indent=2)
        destination.write("\n")


def main() -> None:
    args = arguments()
    ontology = contract.build_ontology()
    write_new(args.ontology, ontology)
    ontology_path = args.ontology.expanduser().resolve()
    ontology_sha256 = contract.sha256_file(ontology_path)
    report = contract.build_review_report(ontology, ontology_sha256)
    write_new(args.report, report)
    print(
        contract.canonical(
            {
                "status": report["status"],
                "ontologySha256": ontology_sha256,
                "counts": ontology["counts"],
            }
        )
    )


if __name__ == "__main__":
    main()
