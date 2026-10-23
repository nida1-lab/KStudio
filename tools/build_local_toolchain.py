#!/usr/bin/env python3
"""Build KStudio's bundled, offline ART toolchain from pinned upstream artifacts."""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

KOTLIN = "2.2.10"
CENTRAL = "https://repo.maven.apache.org/maven2/"
JARS = {
    "kotlin-compiler-embeddable": "79628fd5d64b4692a62f98962baab00e9076a37e2cffd6ace33cd0b7be7d1cdf",
    "kotlin-stdlib": "9c67cc79efd6b9215b49d2a4308f5f3433537376c7c88e89bdd6729bd096e61a",
    "kotlin-reflect": "c48db4f8cd1bfd67f72d59255065fb41bfda43ca2942dac4d9deb897aa126ba4",
    "kotlin-script-runtime": "c304a67dba6a61dde560e6310920bed746307c3854f6675c29ac29114731b6b0",
    "kotlin-daemon-embeddable": "b31573d418961fa080760fedcfc90d68ad67a85eafa245c73fe3d551476777ba",
}


def download(url, destination, digest=None, git_blob=None):
    if destination.is_file():
        body = destination.read_bytes()
        if digest and hashlib.sha256(body).hexdigest() == digest:
            return
        if git_blob and hashlib.sha1(b"blob " + str(len(body)).encode() + b"\0" + body).hexdigest() == git_blob:
            return
        if not digest and not git_blob:
            return
    print("Download", url, flush=True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=120) as source:
        body = source.read()
    if digest and hashlib.sha256(body).hexdigest() != digest:
        raise ValueError("Checksum mismatch: " + str(destination))
    if git_blob and hashlib.sha1(b"blob " + str(len(body)).encode() + b"\0" + body).hexdigest() != git_blob:
        raise ValueError("Native tool source revision mismatch")
    destination.write_bytes(body)


def command(*args):
    subprocess.run([str(x) for x in args], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    fingerprint = hashlib.sha256(b"local-v1" + Path(__file__).read_bytes() + (root / "tools/local/PatchCompiler.java").read_bytes() + (root / "tools/local/CompilerCompat.java").read_bytes()).hexdigest()
    stamp = args.out / "stamp"
    if stamp.is_file() and stamp.read_text() == fingerprint:
        print("Bundled toolchain is current")
        return
    cache = root / ".local-toolchain-cache"
    jars = cache / "jars"
    for artifact, sha in JARS.items():
        download(CENTRAL + f"org/jetbrains/kotlin/{artifact}/{KOTLIN}/{artifact}-{KOTLIN}.jar", jars / f"{artifact}.jar", sha)
    for group, artifact, version, sha in [
        ("org/jetbrains/kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0", "9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5"),
        ("org/jetbrains", "annotations", "23.0.0", "7b0f19724082cbfcbc66e5abea2b9bc92cf08a1ea11e191933ed43801eb3cd05"),
        ("stax", "stax-api", "1.0.1", None),
    ]:
        download(CENTRAL + f"{group}/{artifact}/{version}/{artifact}-{version}.jar", jars / f"{artifact}.jar", sha)
    asm = []
    for artifact in ("asm", "asm-commons"):
        file = cache / f"{artifact}.jar"
        download(CENTRAL + f"org/ow2/asm/{artifact}/9.7.1/{artifact}-9.7.1.jar", file)
        asm.append(str(file))
    assets = args.out / "assets/toolchain"
    assets.mkdir(parents=True, exist_ok=True)
    native_commit = "a236fb318c96ea772e1432b2f3082681bb4030f6"
    for abi, blob in [("arm64-v8a", "ee7bf8002debd4ffffcdf49a3f1db1253a8a0b3f"), ("x86_64", "9be855c0027b2881cc5032269b9ba691296358c4")]:
        file = args.out / "jniLibs" / abi / "libaapt2.so"
        download(f"https://raw.githubusercontent.com/sanjarAmin/AIDE-OS/{native_commit}/toolchain/native/src/main/jniLibs/{abi}/libaapt2.so", file, git_blob=blob)
    android = args.sdk / "platforms/android-35/android.jar"
    shutil.copyfile(android, assets / "android.jar")
    shutil.copyfile(jars / "kotlin-stdlib.jar", assets / "kotlin-stdlib.jar")
    shutil.copyfile(jars / "annotations.jar", assets / "annotations.jar")
    notice = args.sdk / "platforms/android-35/NOTICE.txt"
    if notice.is_file():
        shutil.copyfile(notice, assets / "ANDROID-NOTICE.txt")
    for notice in sorted((args.sdk / "build-tools").glob("*/NOTICE.txt")):
        shutil.copyfile(notice, assets / "BUILD-TOOLS-NOTICE.txt")
    licenses = args.out / "assets/licenses"
    for jar in sorted(jars.glob("*.jar")):
        with zipfile.ZipFile(jar) as source:
            for name in source.namelist():
                if not name.endswith('/') and name.startswith("META-INF/") and any(word in name.upper() for word in ("LICENSE", "NOTICE", "COPYRIGHT")):
                    destination = licenses / jar.stem / name.replace("/", "_")
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(source.read(name))
    with tempfile.TemporaryDirectory() as temp:
        temp = Path(temp)
        helper = temp / "helper"
        helper.mkdir()
        shims = temp / "shims"
        shims.mkdir()
        classpath = os.pathsep.join(asm)
        command("javac", "--release", "8", "-cp", classpath, "-d", helper, root / "tools/local/PatchCompiler.java")
        command("javac", "--release", "8", "-d", shims, root / "tools/local/CompilerCompat.java")
        patched = temp / "patched"
        command("java", "-cp", str(helper) + os.pathsep + classpath, "PatchCompiler", jars, patched, shims)
        dex = temp / "dex"
        dex.mkdir()
        tools = sorted((args.sdk / "build-tools").glob("*/lib/d8.jar"))
        if not tools:
            raise RuntimeError("Android D8 build tools are required")
        command("java", "-Xmx5g", "-cp", tools[-1], "com.android.tools.r8.D8", "--release", "--min-api", "30", "--lib", android,
                "--output", dex, *sorted(patched.glob("*.jar")), *sorted(shims.rglob("*.class")))
        with zipfile.ZipFile(assets / "kotlinc.jar", "w", zipfile.ZIP_DEFLATED) as out:
            seen = set()
            services = {}
            for jar in sorted(jars.glob("*.jar")):
                with zipfile.ZipFile(jar) as source:
                    for entry in source.infolist():
                        name = entry.filename
                        if entry.is_dir():
                            continue
                        if name.startswith("META-INF/services/"):
                            services.setdefault(name, []).extend(source.read(name).decode().splitlines())
                            continue
                        if name.endswith(".class") and name != "org/jetbrains/kotlin/cli/common/CompilerSystemProperties.class":
                            continue
                        if name in seen or name.endswith((".SF", ".RSA", ".DSA")):
                            continue
                        seen.add(name)
                        out.writestr(name, source.read(name))
            for name, entries in services.items():
                out.writestr(name, "\n".join(dict.fromkeys(entries)) + "\n")
            for file in dex.glob("*.dex"):
                out.write(file, file.name)
    manifest = {p.name: {"size": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in assets.iterdir() if p.is_file() and p.name != "manifest.json"}
    (assets / "manifest.json").write_text(json.dumps(manifest, indent=2))
    stamp.write_text(fingerprint)
    print("Bundled local toolchain:", json.dumps(manifest), flush=True)


if __name__ == "__main__":
    main()
