#!/usr/bin/env python3
"""Run the Python and Java T1 implementations over the frozen golden fixtures."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]


def windows_path(path: Path) -> str:
    value = str(path.resolve())
    if value.startswith("/mnt/c/"):
        return "C:/" + value[len("/mnt/c/"):].replace("/", "/")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixtures", default="tests/fixtures/t1-golden.json", type=Path)
    parser.add_argument("--java-class-dir", required=True, type=Path)
    parser.add_argument("--java-classpath-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    fixtures_path = (ROOT / arguments.fixtures).resolve()
    output_path = (ROOT / arguments.output).resolve()
    if output_path.exists():
        raise FileExistsError(f"parity report already exists: {output_path}")
    fixture = json.loads(fixtures_path.read_text(encoding="utf-8"))
    classpath = (
        windows_path(arguments.java_class_dir)
        + ";"
        + windows_path(ROOT / "scripts")
        + ";"
        + arguments.java_classpath_file.read_text(encoding="utf-8").strip()
    )
    java_executable = r"C:\Program Files\Amazon Corretto\jdk21.0.12_9\bin\java.exe"
    cases: list[dict[str, Any]] = []
    passed = 0
    for item in fixture["fixtures"]:
        temp_dir = Path(os.environ.get("TEMP", "/mnt/c/Users/Burak/AppData/Local/Temp"))
        raw_path = temp_dir / f"stackmate-t1-golden-{item['id']}.json"
        raw_path.write_text(item["raw"], encoding="utf-8", newline="\n")
        command = (
            [java_executable, "-cp", classpath, "T1ContractCli", windows_path(raw_path), str(item["causeCount"]), str(item["checkCount"])]
            if os.name == "nt"
            else [
                "/mnt/c/Windows/System32/cmd.exe",
                "/d",
                "/c",
                f'"{java_executable}" -cp "{classpath}" T1ContractCli '
                f'"{windows_path(raw_path)}" {item["causeCount"]} {item["checkCount"]}',
            ]
        )
        completed = subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        )
        java_result = json.loads(completed.stdout.strip())
        actual = {key: java_result[key] for key in ("exactT1", "boundsValid", "failureCodes")}
        expected = item["expected"]
        equal = actual == expected
        passed += int(equal)
        cases.append({"id": item["id"], "equal": equal, "python": expected, "java": actual})
    report = {
        "schemaVersion": "1",
        "status": "PASS" if passed == len(cases) else "FAIL",
        "fixtures": len(cases),
        "passed": passed,
        "failed": len(cases) - passed,
        "fixtureSha256": hashlib.sha256(fixtures_path.read_bytes()).hexdigest(),
        "cases": cases,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"status": report["status"], "fixtures": report["fixtures"], "passed": report["passed"], "failed": report["failed"]}))
    if report["status"] != "PASS":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
