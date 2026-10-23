import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

from collect_run_result import collect
from prepare_run_project import ANDROID, extract_snapshot, prepare


class ProjectBuildTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.source.mkdir()
        self.target = self.root / "kstudio-work"
        self.metadata = self.root / "metadata.json"

    def tearDown(self):
        self.temp.cleanup()

    def write(self, name, text):
        file = self.source / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)

    def test_loose_project_keeps_all_sources_resources_and_diagnostic_paths(self):
        self.write("MainActivity.kt", "package sample.app\nclass MainActivity\n")
        self.write("helpers/Math.kt", "package sample.app\nfun result() = 42\n")
        self.write("res/layout/activity_main.xml", '<TextView xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent"/>')
        meta = prepare(self.source, self.target, self.metadata)
        self.assertTrue((self.target / "app/src/main/java/helpers/Math.kt").is_file())
        self.assertTrue((self.target / "app/src/main/res/layout/activity_main.xml").is_file())
        self.assertEqual(meta["path_map"]["app/src/main/java/MainActivity.kt"], "MainActivity.kt")
        launcher = ET.parse(self.target / "app/src/main/AndroidManifest.xml").find("application/activity")
        self.assertEqual(launcher.get(ANDROID + "name"), "sample.app.MainActivity")
        self.assertEqual(launcher.get(ANDROID + "exported"), "true")

    def test_console_source_is_called_by_compiled_activity(self):
        self.write("Main.kt", 'fun main() { println("Hello") }')
        prepare(self.source, self.target, self.metadata)
        source = (self.target / "app/src/main/java/KStudioConsoleActivity.kt").read_text()
        self.assertIn("import main", source)
        self.assertIn("try { main() }", source)

    def test_console_with_args(self):
        self.write("Main.kt", 'package demo.console\nfun main(args: Array<String>) { println(args.size) }')
        prepare(self.source, self.target, self.metadata)
        self.assertIn("main(emptyArray<String>())", (self.target / "app/src/main/java/KStudioConsoleActivity.kt").read_text())

    def test_existing_gradle_project_preserves_build_dependencies(self):
        self.write("settings.gradle.kts", 'include(":mobile")')
        self.write("mobile/build.gradle.kts", "// custom project")
        self.write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https://services.gradle.org/distributions/gradle-8.10.2-bin.zip")
        self.write("local.properties", "sdk.dir=/wrong/machine")
        result = prepare(self.source, self.target, self.metadata)
        self.assertEqual(result["gradle_version"], "8.10.2")
        self.assertEqual((self.target / "mobile/build.gradle.kts").read_text(), "// custom project")
        self.assertFalse((self.target / "local.properties").exists())

    def test_existing_manifest_permissions_survive(self):
        self.write("MainActivity.kt", "package demo.app\nclass MainActivity")
        self.write("AndroidManifest.xml", '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="demo.app"><uses-permission android:name="android.permission.INTERNET"/><application><activity android:name=".MainActivity"/></application></manifest>')
        prepare(self.source, self.target, self.metadata)
        manifest = ET.parse(self.target / "app/src/main/AndroidManifest.xml")
        self.assertEqual(manifest.find("uses-permission").get(ANDROID + "name"), "android.permission.INTERNET")
        self.assertEqual(len(manifest.findall("application/activity")), 1)

    def test_snapshot_rejects_path_escape_and_bad_checksum(self):
        archive = self.root / "source.zip"
        with zipfile.ZipFile(archive, "w") as snapshot:
            snapshot.writestr("../../escape.kt", "bad")
        with self.assertRaises(ValueError):
            extract_snapshot(archive, self.source)
        with self.assertRaises(ValueError):
            extract_snapshot(archive, self.source, "0" * 64)
        self.assertFalse((self.root.parent / "escape.kt").exists())

    def test_no_entry_point_and_host_package_are_rejected(self):
        self.write("Helper.kt", "fun helper() = 1")
        with self.assertRaisesRegex(ValueError, "MainActivity"):
            prepare(self.source, self.target, self.metadata)
        self.target.rmdir()
        self.write("MainActivity.kt", "package com.nida.kstudio\nclass MainActivity")
        with self.assertRaisesRegex(ValueError, "different package"):
            prepare(self.source, self.target, self.metadata)

    def test_artifact_contains_apk_hash_and_remapped_errors(self):
        self.write("MainActivity.kt", "package demo.app\nclass MainActivity")
        dist = self.root / "dist"
        dist.mkdir()
        prepare(self.source, self.target, dist / "project-metadata.json")
        apk = self.target / "app/build/outputs/apk/debug/app-debug.apk"
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b"test apk")
        (dist / "build.log").write_text(f"e: {self.target}/app/src/main/java/MainActivity.kt:2:1 unresolved reference")
        self.assertTrue(collect(self.target, dist, "request", "source hash"))
        result = json.loads((dist / "run-result.json").read_text())
        self.assertEqual(result["apk_sha256"], hashlib.sha256(b"test apk").hexdigest())
        self.assertIn("/kstudio-work/MainActivity.kt:2:1", (dist / "build.log").read_text())


if __name__ == "__main__":
    unittest.main()
