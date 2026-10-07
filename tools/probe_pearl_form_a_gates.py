"""Find which of the form-A rule's three gates is failing.

Measured: the clause never fires (the slot-8 enhanced basic deals the same damage with the archetype's Attack at 1000
and at 3000, and the same as slot 9). Its gates are `actor == self`, `actor has_state 美学底本`, `from_skill_id == 8` -
and `from_skill_id` is the one whose content on DEALING_DAMAGE is not settled (an earlier round found `TriggerTable`
documenting SLOT while `Battle`'s own javadoc says "The row, not the slot", and both spellings changed the number in a
probe).

This relaxes them one at a time and prints the damage each time, so the failing gate is identified by which relaxation
makes the number move.

Usage:
    python tools/probe_pearl_form_a_gates.py
"""
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RESULT = pathlib.Path("build/test-results/test/"
                      "TEST-com.laosun.aluminium.test.content.characters.PearlFormAExtraDamageTest.xml")
RULE_ID = "enhanced_basic_form_a_extra_elation_damage"

VARIANTS = {
    "as shipped": None,
    "gate 3 relaxed (from_skill_id dropped)": ("        \"from_skill_id == 8\"\n", ""),
    "gate 2 relaxed (actor has_state dropped)": ("        \"actor has_state 美学底本\",\n", ""),
    "gate 3 as a ROW id (150308)": ("\"from_skill_id == 8\"", "\"from_skill_id == 150308\""),
}


def reading():
    result = subprocess.run([".\\gradlew.bat", "test", "--tests", "*PearlFormAExtraDamageTest*",
                             "--console=plain", "-q"], capture_output=True, text=True)
    if not RESULT.exists():
        return None, result.stderr[-300:]
    root = ET.parse(RESULT).getroot()
    system_out = root.find("system-out")
    for line in (system_out.text or "").splitlines():
        match = re.search(r"slot-8 damage with the archetype's Attack at 3000 = ([\d.]+)", line)
        if match:
            return float(match.group(1)), None
    return None, "no reading line; the suite may not have compiled"


def main():
    original = TARGET.read_text(encoding="utf-8")
    try:
        for label, change in VARIANTS.items():
            text = original
            if change is not None:
                old, new = change
                if text.count(old) != 1:
                    print(f"{label}: anchor occurs {text.count(old)} times, skipping")
                    continue
                text = text.replace(old, new)
            TARGET.write_text(text, encoding="utf-8")
            value, problem = reading()
            print(f"{label:45s} -> {value if value is not None else 'NO READING'} {problem or ''}")
    finally:
        TARGET.write_text(original, encoding="utf-8")
        print("content file restored from the in-memory copy")
    return 0


if __name__ == "__main__":
    sys.exit(main())
