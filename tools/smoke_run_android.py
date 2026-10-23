#!/usr/bin/env python3
"""Install the compiled APK, run it, and verify actual Kotlin behavior on ART."""
import re
import subprocess
import sys
import time
from xml.etree import ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/kstudio-ui.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/kstudio-ui.xml"))


def wait_for(text):
    last = ""
    for _ in range(20):
        try:
            root = hierarchy()
            last = ET.tostring(root, encoding="unicode")
            # A cold emulator may show a launcher ANR while the tested Activity
            # is already running underneath it. Dismiss only the launcher ANR;
            # never dismiss an ANR from the app whose behavior we are checking.
            if any(node.get("resource-id") == "android:id/alertTitle" and
                   node.get("text", "").startswith("Quickstep") for node in root.iter("node")):
                close = next(node for node in root.iter("node") if node.get("resource-id") == "android:id/aerr_close")
                tap(close)
                continue
            if any(text in node.get("text", "") for node in root.iter("node")):
                return root
        except (ET.ParseError, subprocess.CalledProcessError):
            pass
        time.sleep(1)
    print(last)
    print(adb("logcat", "-d", "-s", "AndroidRuntime:E"))
    raise AssertionError(f"Compiled app did not display {text!r}")


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def main():
    apk, fixture = sys.argv[1:]
    package = {"run-counter": "com.example.kstudiorun", "run-console": "com.kstudio.project"}[fixture]
    adb("install", "-r", "-t", apk)
    activity = "MainActivity" if fixture == "run-counter" else "KStudioConsoleActivity"
    adb("shell", "am", "force-stop", package)
    print(adb("shell", "am", "start", "-W", "-n", f"{package}/.{activity}"))
    if fixture == "run-counter":
        root = wait_for("Count: 0")
        button = next(node for node in root.iter("node") if node.get("text") == "ADD")
        tap(button)
        wait_for("Count: 1")
        print("KSTUDIO_ANDROID_RUN_OK: Kotlin click handler changed Count: 0 -> Count: 1")
    else:
        wait_for("KSTUDIO_CONSOLE_OK")
        wait_for("1, 4, 9, 16, 25")
        print("KSTUDIO_CONSOLE_RUN_OK: compiled Kotlin functions, ranges, map, println and check executed")


if __name__ == "__main__":
    main()
