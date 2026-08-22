#!/usr/bin/env python3
"""Acquire and verify the immutable official StackMate Phase 5.2 base model."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from huggingface_hub import HfApi, snapshot_download


REPOSITORY = "Qwen/Qwen3.5-2B"
REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"
OFFICIAL_ENDPOINT = "https://huggingface.co"
REQUIRED_FILES = (
    "config.json",
    "model.safetensors.index.json",
    "tokenizer.json",
    "tokenizer_config.json",
)
ALLOW_PATTERNS = [
    "config.json",
    "model.safetensors",
    "model.safetensors*",
    "model.safetensors.index.json",
    "tokenizer.json",
    "tokenizer_config.json",
    "chat_template.jinja",
    "vocab.json",
    "merges.txt",
    "preprocessor_config.json",
    "video_preprocessor_config.json",
    "generation_config.json",
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-id", default=REPOSITORY)
    parser.add_argument("--revision", default=REVISION)
    parser.add_argument("--cache-dir", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--full-manifest", required=True, type=Path)
    parser.add_argument("--max-workers", type=int, default=2)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def get_repo_files(repo_id: str, revision: str) -> tuple[Any, list[Any]]:
    api = HfApi(endpoint=OFFICIAL_ENDPOINT)
    info = api.repo_info(repo_id, revision=revision, files_metadata=True)
    if info.id != repo_id:
        raise RuntimeError(f"Unexpected repository returned: {info.id}")
    if info.sha != revision:
        raise RuntimeError(f"Revision was not immutable/resolved: {info.sha}")
    return info, sorted(info.siblings, key=lambda item: item.rfilename)


def selected_metadata(repo_files: list[Any]) -> list[dict[str, Any]]:
    names = {item.rfilename for item in repo_files}
    selected: list[dict[str, Any]] = []
    for item in repo_files:
        name = item.rfilename
        if name in REQUIRED_FILES or name in {
            "config.json",
            "model.safetensors.index.json",
            "tokenizer.json",
            "tokenizer_config.json",
            "chat_template.jinja",
            "vocab.json",
            "merges.txt",
            "preprocessor_config.json",
            "video_preprocessor_config.json",
            "generation_config.json",
        } or name.startswith("model.safetensors"):
            selected.append(
                {
                    "name": name,
                    "size": getattr(item, "size", None),
                    "lfsSha256": getattr(getattr(item, "lfs", None), "sha256", None),
                }
            )
    required_missing = sorted(set(REQUIRED_FILES) - names)
    if required_missing:
        raise RuntimeError(f"Official revision is missing required files: {required_missing}")
    return selected


def main() -> None:
    arguments = parse_args()
    if arguments.repo_id != REPOSITORY or arguments.revision != REVISION:
        raise SystemExit(
            f"This controlled acquisition only permits {REPOSITORY}@{REVISION}"
        )
    if not 1 <= arguments.max_workers <= 4:
        raise SystemExit("--max-workers must be between 1 and 4")

    info, repo_files = get_repo_files(arguments.repo_id, arguments.revision)
    planned = selected_metadata(repo_files)
    planned_bytes = sum(item["size"] or 0 for item in planned)
    print(
        json.dumps(
            {
                "repository": arguments.repo_id,
                "revision": arguments.revision,
                "resolvedRevision": info.sha,
                "plannedFiles": [item["name"] for item in planned],
                "plannedBytes": planned_bytes,
                "plannedMiB": round(planned_bytes / 1024**2, 2),
                "dryRun": arguments.dry_run,
            },
            separators=(",", ":"),
        )
    )

    dry_run = snapshot_download(
        repo_id=arguments.repo_id,
        revision=arguments.revision,
        cache_dir=arguments.cache_dir.expanduser(),
        allow_patterns=ALLOW_PATTERNS,
        ignore_patterns=["*.gguf", "*.bin", "*.onnx", "*.h5", "*.msgpack"],
        max_workers=arguments.max_workers,
        endpoint=OFFICIAL_ENDPOINT,
        token=False,
        dry_run=True,
    )
    if not isinstance(dry_run, list):
        raise RuntimeError("huggingface_hub dry-run returned an unexpected value")
    dry_run_bytes = sum(getattr(item, "file_size", 0) or 0 for item in dry_run)
    print(
        json.dumps(
            {
                "dryRunFiles": len(dry_run),
                "dryRunBytes": dry_run_bytes,
                "dryRunMiB": round(dry_run_bytes / 1024**2, 2),
                "wouldDownload": sum(bool(getattr(item, "will_download", False)) for item in dry_run),
            },
            separators=(",", ":"),
        )
    )
    if arguments.dry_run:
        return

    snapshot = Path(
        snapshot_download(
            repo_id=arguments.repo_id,
            revision=arguments.revision,
            cache_dir=arguments.cache_dir.expanduser(),
            allow_patterns=ALLOW_PATTERNS,
            ignore_patterns=["*.gguf", "*.bin", "*.onnx", "*.h5", "*.msgpack"],
            max_workers=arguments.max_workers,
            endpoint=OFFICIAL_ENDPOINT,
            token=False,
        )
    ).resolve()
    if snapshot.name != arguments.revision:
        raise RuntimeError(f"Resolved snapshot does not end in pinned revision: {snapshot}")
    if not snapshot.is_dir():
        raise RuntimeError(f"Resolved snapshot is not a directory: {snapshot}")

    incomplete = sorted(
        path.relative_to(snapshot).as_posix()
        for path in snapshot.rglob("*")
        if path.is_file() and ".incomplete" in path.name
    )
    if incomplete:
        raise RuntimeError(f"Incomplete files remain in snapshot: {incomplete}")

    files: list[dict[str, Any]] = []
    for item in planned:
        path = snapshot / item["name"]
        if not path.is_file():
            raise RuntimeError(f"Planned file was not downloaded: {item['name']}")
        files.append(
            {
                "name": item["name"],
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
                "repositorySize": item["size"],
                "repositoryLfsSha256": item["lfsSha256"],
            }
        )
    missing = sorted(set(REQUIRED_FILES) - {item["name"] for item in files})
    if missing:
        raise RuntimeError(f"Downloaded snapshot is missing required files: {missing}")

    timestamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    total_bytes = sum(item["size"] for item in files)
    hub_version = importlib.metadata.version("huggingface_hub")
    full_manifest = {
        "schemaVersion": "1",
        "repositoryId": arguments.repo_id,
        "officialRevision": arguments.revision,
        "resolvedRevision": info.sha,
        "officialEndpoint": OFFICIAL_ENDPOINT,
        "resolvedLocalPath": str(snapshot),
        "files": files,
        "totalCheckpointBytes": total_bytes,
        "downloadDateUtc": timestamp,
        "huggingfaceHubVersion": hub_version,
        "allowPatterns": ALLOW_PATTERNS,
        "maxWorkers": arguments.max_workers,
        "networkAccessDuringTraining": False,
    }
    sanitized = {
        **full_manifest,
        "resolvedLocalPath": "<WSL_HF_CACHE>/models--Qwen--Qwen3.5-2B/snapshots/"
        + arguments.revision,
    }
    write_json(arguments.full_manifest.expanduser(), full_manifest)
    write_json(arguments.manifest, sanitized)
    print(
        json.dumps(
            {
                "status": "PASS",
                "snapshot": str(snapshot),
                "manifest": str(arguments.manifest.resolve()),
                "fullManifest": str(arguments.full_manifest.expanduser().resolve()),
                "fileCount": len(files),
                "totalCheckpointBytes": total_bytes,
            },
            separators=(",", ":"),
        )
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as failure:
        print(f"CHECKPOINT_ACQUISITION_FAILED:{type(failure).__name__}:{failure}", file=sys.stderr)
        raise
