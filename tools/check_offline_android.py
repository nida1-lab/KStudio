#!/usr/bin/env python3
"""Run the device compiler checks and retain a small, useful failure report."""
import pathlib
import subprocess
import sys


def main():
    report = pathlib.Path("offline-device-checks.txt")
    command = ["adb", "shell", "am", "instrument", "-w",
               "com.nida.kstudio.test/com.nida.kstudio.RunDeviceChecks"]
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, timeout=600)
        output = result.stdout
        passed = result.returncode == 0 and "KSTUDIO_OFFLINE_BUILD_OK" in output
    except subprocess.TimeoutExpired as error:
        output = (error.stdout or b"").decode(errors="replace") if isinstance(error.stdout, bytes) else (error.stdout or "")
        output += "\nDevice checks exceeded ten minutes.\n"
        passed = False
    if not passed:
        logs = subprocess.run(["adb", "logcat", "-d", "-t", "500"],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
        output += "\n--- Android logcat ---\n" + logs.stdout
    report.write_text(output, encoding="utf-8")
    print(output[-24000:])
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
