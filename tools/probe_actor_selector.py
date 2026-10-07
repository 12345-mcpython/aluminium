"""Compare `target: actor` against `target: self` on the SAME fixture shape, to find out which half is broken.

Measured so far: the rule fires (`fired=1`) and the payment does not arrive (`allyGot=0`), with the ally declaring the
counter (`has=true`). Two candidate causes - the selector resolving to the wrong unit, or the gain not landing - and
this separates them by changing exactly one field.

Usage:
    python tools/probe_actor_selector.py            # patch the judge to use `self`, run, report
    python tools/probe_actor_selector.py restore
"""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

JUDGE = pathlib.Path("src/test/java/com/laosun/aluminium/test/content/characters/PearlExtraTurnRewardTest.java")
RESULT = pathlib.Path("build/test-results/test/"
                      "TEST-com.laosun.aluminium.test.content.characters.PearlExtraTurnRewardTest.xml")
PROBE_MODULE = pathlib.Path("tools/_actor_probe_original.java.txt")


def reading():
    subprocess.run([".\\gradlew.bat", "test", "--tests", "*PearlExtraTurnRewardTest.theActorSelector*",
                    "--console=plain", "-q"], capture_output=True, text=True)
    if not RESULT.exists():
        return "no result file"
    root = ET.parse(RESULT).getroot()
    out = root.find("system-out")
    for line in (out.text or "").splitlines():
        if "[actor_selector]" in line:
            return line.strip()
    return "no reading line"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        subprocess.run(["git", "checkout", "HEAD", "--", str(JUDGE)], capture_output=True, text=True)
        print("judge restored from HEAD")
        return 0

    original = JUDGE.read_text(encoding="utf-8")
    try:
        for selector in ("self", "actor"):
            text = original.replace('TriggerSpecs.set(effect, "target", "actor");',
                                    f'TriggerSpecs.set(effect, "target", "{selector}");')
            if text == original and selector == "self":
                print("anchor not found; the fixture may spell target differently")
                return 1
            JUDGE.write_text(text, encoding="utf-8")
            print(f"target: {selector:6s} -> {reading()}")
    finally:
        JUDGE.write_text(original, encoding="utf-8")
        print("judge restored from the in-memory copy")
    return 0


if __name__ == "__main__":
    sys.exit(main())
