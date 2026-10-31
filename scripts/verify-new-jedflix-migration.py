#!/usr/bin/env python3
"""Check installed-app identity and launcher routing on a disposable TV emulator.

Install app-debug.apk and app-debug-androidTest.apk first. Prepare com.jedflix.stremio
as absent, foreign-signed, or the signed Jedflix release; stock means Stremio is also installed. This script never removes
apps or clears data. Use --ui-only to check an optimized release build instead.
"""
import argparse
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", required=True, help="Path to the supported adb wrapper")
parser.add_argument("--serial", required=True)
parser.add_argument("--fixture", choices=("stock", "absent", "foreign", "jedflix"), required=True)
parser.add_argument("--ui-only", action="store_true")
args = parser.parse_args()
adb = [str(Path(args.adb).resolve()), "-s", args.serial]


def run(*command):
    return subprocess.run(adb + list(command), check=True, capture_output=True, text=True).stdout


if not args.ui_only:
    result = run("shell", "am", "instrument", "-w", "-e", "class",
                 "com.jedflix.tv.data.migration.NewJedflixIdentityTest", "-e", "identityFixture",
                 args.fixture, "com.jedflix.tv.test/androidx.test.runner.AndroidJUnitRunner")
    assert "OK (1 test)" in result, result
    print("PASS: installed-app identity and guarded launch intent")

run("shell", "am", "force-stop", "com.jedflix.stremio")
run("shell", "am", "force-stop", "com.jedflix.tv")
run("shell", "am", "start", "-W", "-n", "com.jedflix.tv/.MainActivity")
expected = {"stock": "Jedflix has a new app", "absent": "Jedflix has a new app", "foreign": "A different Jedflix app is installed", "jedflix": "Home"}[args.fixture]
# The collapsed TV navigation has no Home accessibility label; its catalog shelf
# identifies the same destination once the first-launch catalog has loaded.
expected_labels = {expected, "Popular - Movie"} if args.fixture == "jedflix" else {expected}
deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    run("shell", "uiautomator", "dump", "/sdcard/new-jedflix-migration-test.xml")
    xml = run("exec-out", "cat", "/sdcard/new-jedflix-migration-test.xml")
    try:
        nodes = list(ET.fromstring(xml).iter("node"))
    except ET.ParseError:
        time.sleep(0.5)
        continue
    labels = {node.get(key, "") for node in nodes for key in ("text", "content-desc")}
    assert "Log in to your Stremio account" not in labels, "Legacy Jedflix opened stock Stremio login"
    if labels & expected_labels:
        break
    time.sleep(0.5)
else:
    raise AssertionError(f"Expected migration destination was not reached: {expected}")
expected_package = "com.jedflix.stremio" if args.fixture == "jedflix" else "com.jedflix.tv"
assert any(node.get("package") == expected_package for node in nodes), "Unexpected foreground app"
print(f"PASS: {args.fixture} routes to {expected}")
