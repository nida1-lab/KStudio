#!/usr/bin/env python3
"""Prepare a real Gradle Android project from a KStudio project snapshot.

Existing Gradle projects keep their build files. Loose Kotlin/Java + res projects
get an Android Gradle wrapper project. No Kotlin source is interpreted or mocked.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import stat
import zipfile
from pathlib import Path, PurePosixPath
from xml.etree import ElementTree as ET

IGNORED = {".git", ".gradle", ".idea", ".kstudio", "build", "node_modules"}
ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = re.compile(r"(?m)^\s*package\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)")


def extract_snapshot(archive: Path, destination: Path, expected_sha: str = "") -> str:
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if expected_sha and digest != expected_sha:
        raise ValueError("Source snapshot checksum mismatch")
    with zipfile.ZipFile(archive) as snapshot:
        entries = snapshot.infolist()
        if len(entries) > 1000 or sum(item.file_size for item in entries) > 25 * 1024 * 1024:
            raise ValueError("Source snapshot exceeds 1000 files / 25 MB")
        seen = set()
        for entry in entries:
            path = PurePosixPath(entry.filename)
            if (path.is_absolute() or ".." in path.parts or "\\" in entry.filename
                    or not path.parts or ":" in path.parts[0] or path in seen
                    or stat.S_ISLNK(entry.external_attr >> 16)):
                raise ValueError(f"Invalid snapshot path: {entry.filename}")
            seen.add(path)
            if any(part in IGNORED for part in path.parts) or path.name == "local.properties":
                continue
            if entry.file_size > 10 * 1024 * 1024:
                raise ValueError(f"File exceeds 10 MB: {path}")
            target = destination.joinpath(*path.parts)
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with snapshot.open(entry) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)
    return digest


def write(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def console_activity(package: str, args: bool) -> str:
    # This Activity is compiled alongside the user's source. Its call to main()
    # uses ordinary Kotlin name resolution and the real Kotlin compiler.
    invocation = "main(emptyArray<String>())" if args else "main()"
    return f'''package {package}

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import java.io.OutputStream
import java.io.PrintStream

class KStudioConsoleActivity : Activity() {{
    override fun onCreate(savedInstanceState: Bundle?) {{
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {{
            textSize = 16f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(24, 32, 24, 24)
        }}
        setContentView(ScrollView(this).apply {{ addView(output) }})
        val buffer = StringBuilder()
        val stream = object : OutputStream() {{
            override fun write(value: Int) {{ write(byteArrayOf(value.toByte()), 0, 1) }}
            override fun write(bytes: ByteArray, offset: Int, length: Int) {{
                synchronized(buffer) {{
                    buffer.append(String(bytes, offset, length, Charsets.UTF_8))
                    if (buffer.length > 200000) buffer.delete(0, buffer.length - 200000)
                }}
                runOnUiThread {{ output.text = synchronized(buffer) {{ buffer.toString() }} }}
            }}
        }}
        System.setOut(PrintStream(stream, true, "UTF-8"))
        System.setErr(PrintStream(stream, true, "UTF-8"))
        Thread {{
            try {{ {invocation} }} catch (failure: Throwable) {{ failure.printStackTrace() }}
        }}.start()
    }}
}}
'''


def prepare(source: Path, target: Path, metadata_path: Path) -> dict:
    if target.exists():
        raise ValueError("Build destination must not already exist")
    target.mkdir(parents=True)
    if any((source / name).is_file() for name in ("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle")):
        shutil.copytree(source, target, dirs_exist_ok=True,
                        ignore=shutil.ignore_patterns(*IGNORED, "local.properties"))
        props = source / "gradle/wrapper/gradle-wrapper.properties"
        version = "8.9"
        if props.is_file():
            match = re.search(r"gradle-(\d+(?:\.\d+){1,2})-(?:bin|all)\.zip", props.read_text())
            if match:
                version = match[1]
        result = {"mode": "gradle", "gradle_version": version, "path_map": {}}
        write(metadata_path, json.dumps(result))
        return result

    kotlin = sorted(source.rglob("*.kt"))
    java = sorted(source.rglob("*.java"))
    all_sources = [p for p in kotlin + java if not any(part in IGNORED for part in p.relative_to(source).parts)]
    if not all_sources:
        raise ValueError("Kotlin/Java source was not found in this project")

    activity = next((p for p in all_sources if p.stem == "MainActivity"), None)
    main_source = next((p for p in kotlin if re.search(r"\bfun\s+main\s*\(", p.read_text(encoding="utf-8"))), None)
    if activity is None and main_source is None:
        raise ValueError("A loose project needs MainActivity.kt/.java or a top-level fun main()")
    entry = activity or main_source
    match = PACKAGE.search(entry.read_text(encoding="utf-8"))
    package = match[1] if match else ""
    namespace = package or "com.kstudio.project"
    if namespace == "com.nida.kstudio":
        raise ValueError("Use a different package name from KStudio (com.nida.kstudio)")

    mapping = {}
    for path in all_sources:
        relative = path.relative_to(source)
        # Preserve paths inside the source tree, avoiding collisions between
        # same-named files in separate packages. The package is read by kotlinc.
        new = Path("app/src/main/java") / relative
        write(target / new, path.read_text(encoding="utf-8"))
        mapping[new.as_posix()] = relative.as_posix()

    for resource in (source / "res", source / "src/main/res", source / "app/src/main/res"):
        if resource.is_dir():
            shutil.copytree(resource, target / "app/src/main/res", dirs_exist_ok=True)
            for path in resource.rglob("*"):
                if path.is_file():
                    mapping[(Path("app/src/main/res") / path.relative_to(resource)).as_posix()] = path.relative_to(source).as_posix()
    for asset in (source / "assets", source / "src/main/assets", source / "app/src/main/assets"):
        if asset.is_dir():
            shutil.copytree(asset, target / "app/src/main/assets", dirs_exist_ok=True)

    if activity:
        if not package:
            raise ValueError("MainActivity needs an explicit package declaration")
        activity_name = (package + "." if package else "") + "MainActivity"
    else:
        # The default package is valid Kotlin; keep the generated Activity there
        # so that it can call the user's top-level main without reflection.
        console_package = package or namespace
        args = bool(re.search(r"\bfun\s+main\s*\(\s*\w+\s*:\s*Array", main_source.read_text()))
        wrapper = console_activity(console_package, args)
        if not package:
            wrapper = wrapper.replace("import android.app.Activity", "import main\n\nimport android.app.Activity", 1)
        write(target / "app/src/main/java/KStudioConsoleActivity.kt", wrapper)
        activity_name = (package + "." if package else "") + "KStudioConsoleActivity"

    manifest_source = next((source / p for p in ("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml", "AndroidManifest.xml") if (source / p).is_file()), None)
    if manifest_source:
        document = ET.parse(manifest_source)
        root = document.getroot()
        old_package = root.attrib.pop("package", "")
        if old_package and old_package != namespace:
            raise ValueError("Manifest package and MainActivity package must match in a loose project")
        app = root.find("application")
        if app is None:
            raise ValueError("Manifest must contain an application element")
        has_launcher = any(
            node.find(f"./intent-filter/action[@{ANDROID}name='android.intent.action.MAIN']") is not None
            for node in app.findall("activity") + app.findall("activity-alias")
        )
        if not has_launcher:
            add_launcher(app, activity_name)
        ET.register_namespace("android", ANDROID[1:-1])
        document.write(target / "app/src/main/AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
        mapping["app/src/main/AndroidManifest.xml"] = manifest_source.relative_to(source).as_posix()
    else:
        root = ET.Element("manifest")
        app = ET.SubElement(root, "application", {ANDROID + "label": "KStudio Run", ANDROID + "theme": "@android:style/Theme.Material.Light.NoActionBar"})
        add_launcher(app, activity_name)
        ET.register_namespace("android", ANDROID[1:-1])
        ET.ElementTree(root).write(target / "app/src/main/AndroidManifest.xml", encoding="utf-8", xml_declaration=True)

    write(target / "settings.gradle.kts", '''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "KStudioRun"
include(":app")
''')
    write(target / "build.gradle.kts", '''plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
''')
    write(target / "app/build.gradle.kts", f'''plugins {{ id("com.android.application"); id("org.jetbrains.kotlin.android") }}
android {{
    namespace = "{namespace}"
    compileSdk = 35
    defaultConfig {{ applicationId = "{namespace}"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }}
    compileOptions {{ sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }}
    kotlinOptions {{ jvmTarget = "17" }}
}}
''')
    result = {"mode": "loose", "gradle_version": "8.9", "namespace": namespace, "path_map": mapping}
    write(metadata_path, json.dumps(result))
    return result


def add_launcher(app: ET.Element, name: str):
    node = next((item for item in app.findall("activity") if item.get(ANDROID + "name") in (name, "." + name.rsplit(".", 1)[-1])), None)
    if node is None:
        node = ET.SubElement(app, "activity", {ANDROID + "name": name})
    node.set(ANDROID + "exported", "true")
    filters = ET.SubElement(node, "intent-filter")
    ET.SubElement(filters, "action", {ANDROID + "name": "android.intent.action.MAIN"})
    ET.SubElement(filters, "category", {ANDROID + "name": "android.intent.category.LAUNCHER"})


def remap_log(log_path: Path, metadata_path: Path):
    metadata = json.loads(metadata_path.read_text())
    content = log_path.read_text(errors="replace")
    for generated, original in sorted(metadata["path_map"].items(), key=lambda p: -len(p[0])):
        content = content.replace(generated, original)
    log_path.write_text(content)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--sha256", default="")
    parser.add_argument("--metadata", type=Path, required=True)
    args = parser.parse_args()
    source = args.source
    if source.is_file():
        extracted = args.target.parent / "kstudio-extracted"
        extracted.mkdir()
        extract_snapshot(source, extracted, args.sha256)
        source = extracted
    result = prepare(source, args.target, args.metadata)
    print(json.dumps(result))
    import os
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write("gradle_version=" + result["gradle_version"] + "\n")


if __name__ == "__main__":
    main()
