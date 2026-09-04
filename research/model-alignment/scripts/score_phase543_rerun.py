#!/usr/bin/env python3
"""Documented scorer-only repair for nullable invalid-output accuracy fields."""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any

import score_phase543 as frozen_scorer


FROZEN_SCORER_SHA256 = "6ab28056bbc5bba0e1f86eea98801b2c1dbe3037ee62c99739933c1fbe848637"
CORRECTNESS_FIELDS = ("causeCorrect", "checkCorrect", "jointCorrect")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def coerce_invalid_accuracy(result: dict[str, Any]) -> dict[str, Any]:
    """Count an unscorable invalid output as incorrect in aggregate metrics."""
    for field in CORRECTNESS_FIELDS:
        if result.get(field) is None:
            result[field] = False
    return result


def main() -> None:
    frozen_path = Path(frozen_scorer.__file__).resolve()
    if sha256_file(frozen_path) != FROZEN_SCORER_SHA256:
        raise RuntimeError("frozen scorer checksum drifted")
    original_score_output = frozen_scorer.score_output

    def repaired_score_output(*args: Any, **kwargs: Any) -> dict[str, Any]:
        return coerce_invalid_accuracy(original_score_output(*args, **kwargs))

    frozen_scorer.score_output = repaired_score_output
    frozen_scorer.main()


if __name__ == "__main__":
    main()
