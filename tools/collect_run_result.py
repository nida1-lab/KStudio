#!/usr/bin/env python3
"""Collect one installable debug APK and readable diagnostics for the phone."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path
from prepare_run_project import remap_log


def collect(work: Path, dist: Path, request_id: str, source_sha: str):
    dist.mkdir(exist_ok=True)
    metadata = dist / "project-metadata.json"
    log = dist / "build.log"
    if log.exists() and metadata.exists():
        remap_log(log, metadata)
    apks = sorted(p for p in work.glob("**/build/outputs/apk/**/debug/*.apk") if "androidTest" not in p.parts)
    if len(apks) > 1:
        universal = [p for p in apks if "universal" in p.name]
        apks = universal if len(universal) == 1 else apks
    result = {"request_id": request_id, "source_sha256": source_sha}
    if len(apks) == 1:
        target = dist / "project.apk"
        shutil.copyfile(apks[0], target)
        result["apk_sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
    elif len(apks) > 1:
        with log.open("a") as output:
            output.write("\nMultiple debug APKs found. Configure a universal APK or keep a single application module.\n")
    (dist / "run-result.json").write_text(json.dumps(result))
    return "apk_sha256" in result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("work", type=Path)
    parser.add_argument("dist", type=Path)
    parser.add_argument("request_id")
    parser.add_argument("source_sha")
    args = parser.parse_args()
    if not collect(args.work, args.dist, args.request_id, args.source_sha):
        raise SystemExit(1)
