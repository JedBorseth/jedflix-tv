#!/usr/bin/env python3
"""Non-destructive D-pad smoke/frames check on a hardware-rendered TV emulator.

Requires Home with a poster focused; never clears app data or starts playback.
Artifacts stay outside the repository by default. Run before/after with the
same browse quality and a warm catalog; emulator results are not physical-TV
performance guarantees.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--output", type=Path, default=Path("/tmp/jedflix-home-check"))
parser.add_argument("--fresh-home", action="store_true", help="Restart without clearing data; start at Trending's first poster")
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def adb(*commands):
    return subprocess.check_output([args.adb, *commands])


def hierarchy(name):
    adb("shell", "uiautomator", "dump", "/sdcard/jedflix-check.xml")
    data = adb("shell", "cat", "/sdcard/jedflix-check.xml")
    (args.output / f"{name}.xml").write_bytes(data)
    return ET.fromstring(data)


def focused(root):
    return next((n for n in root.iter("node") if n.get("focused") == "true"), None)


def focus_title(root):
    node = focused(root)
    assert node is not None, "No focused element"
    assert node.get("resource-id") == "poster-card", "Start with a Home poster focused"
    labels = [n.get("content-desc", "") for n in node.iter("node")]
    return next((x for x in labels if x.startswith("Poster for ")), "")


renderer = adb("shell", "dumpsys", "SurfaceFlinger").decode()
gles = next((x.strip() for x in renderer.splitlines() if "GLES:" in x), "")
assert gles and not re.search("swiftshader|llvmpipe|software", gles, re.I), gles
(args.output / "renderer.txt").write_text(gles + "\n")
print(gles)
if args.fresh_home:
    adb("shell", "am", "force-stop", "com.jedflix.tv")
    adb("shell", "am", "start", "-n", "com.jedflix.tv/.MainActivity")
    for attempt in range(15):
        time.sleep(1)
        screen = hierarchy("arrival")
        if any(n.get("resource-id") == "splash" for n in screen.iter("node")):
            continue
        if any(n.get("resource-id") == "catalog" for n in screen.iter("node")):
            node = focused(screen)
            if node is not None and node.get("resource-id") == "billboard-play":
                adb("shell", "input", "keyevent", "20")
                time.sleep(1)
            break
    else:
        raise AssertionError("Home did not load")
first = hierarchy("start")
assert any(n.get("resource-id") == "catalog" for n in first.iter("node")), "Home required"
focus_title(first)

# Validate Detail -> Back restores the same selected title identity.
adb("shell", "input", "keyevent", "22")
before = focus_title(hierarchy("before-detail"))
adb("shell", "input", "keyevent", "23")
time.sleep(2)
detail = hierarchy("detail")
assert any(n.get("resource-id") == "detail-play" for n in detail.iter("node")), "Detail did not open"
adb("shell", "input", "keyevent", "4")
time.sleep(1)
after = focus_title(hierarchy("returned"))
assert before and before == after, (before, after)

# Stay within rails; pressing Right at the end must not wrap or start anything.
adb("shell", "dumpsys", "gfxinfo", "com.jedflix.tv", "reset")
path = [22] * 18 + [20] + [22] * 12 + [21] * 6 + [20] + [22] * 12 + [19]
start = time.monotonic()
for key in path:
    adb("shell", "input", "keyevent", str(key))
    time.sleep(0.12)
elapsed = time.monotonic() - start
frames = adb("shell", "dumpsys", "gfxinfo", "com.jedflix.tv", "framestats")
(args.output / "gfxinfo.txt").write_bytes(frames)
end = hierarchy("end")
focus_title(end)
assert any(n.get("resource-id") == "catalog" for n in end.iter("node")), "Left Home unexpectedly"
(args.output / "home.png").write_bytes(adb("exec-out", "screencap", "-p"))
print(f"Detail focus restored: {before}; {len(path)} D-pad inputs in {elapsed:.1f}s")
for line in frames.decode().splitlines():
    if re.search(r"Total frames|Janky frames|percentile|Missed Vsync", line):
        print(line.strip())
print(f"Artifacts: {args.output}")
