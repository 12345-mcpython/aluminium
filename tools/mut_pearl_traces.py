"""Mutations for Pearl (1503)'s trace/Eidolon rules just shipped.

Line-targeted and self-verifying for the reasons tools/mut_pearl_ult.py records: anchors written as
\\uXXXX escapes match nothing in these files (they hold literal Chinese), and a mutant that never applied
reads exactly like a surviving mutant.

Usage:
    python tools/mut_pearl_traces.py                 # list
    python tools/mut_pearl_traces.py <name>
    python tools/mut_pearl_traces.py restore
"""
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")

EDITS = {
    # 洞察万物: the threshold itself.
    "insight_threshold": (710, '"self_attr:DEFENCE >= 2400"', '"self_attr:DEFENCE >= 3400"'),
    # 洞察万物: the per-100-points growth rate.
    "insight_per_point": (716, '"percent": 0.0003,', '"percent": 0.0006,'),
    # 洞察万物: the flat 32% half (an `amount` on the excess scale).
    "insight_base": (720, '"amount": 0.32,', '"amount": 0.42,'),
    # 洞察万物: the ceiling -- `cap_amount` is last in its object, so the anchor carries no comma.
    "insight_cap": (721, '"cap_amount": 0.68', '"cap_amount": 0.98'),
    # 洞察万物: the healing link (20% of her Elation).
    "insight_healing": (735, '"percent": 0.2,', '"percent": 0.3,'),
    # 感知容差: the effect-resistance amount (the 0.5 at line 753; line 83 is another rule's).
    "tolerance_percent": (753, '"percent": 0.5,', '"percent": 0.4,'),
    # 星魂 6: the penetration amount.
    "eidolon6_percent": (775, '"percent": 0.2,', '"percent": 0.3,'),
    # 星魂 6: its gate (which is also the sentence's "while deep-studying").
    "eidolon6_gate": (779, '"self has_state 深度学习"', '"self has_state 美学底本"'),
}


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(EDITS):
        print(__doc__)
        print("mutations:", ", ".join(sorted(EDITS)))
        return 1

    if sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    name = sys.argv[1]
    line_no, expected, replacement = EDITS[name]
    lines = TARGET.read_text(encoding="utf-8").splitlines(keepends=True)
    actual = lines[line_no - 1].strip()
    if actual != expected:
        print("LINE", line_no, "does not hold the expected anchor; nothing written")
        print("  expected:", expected)
        print("  actual  :", actual)
        return 2

    lines[line_no - 1] = lines[line_no - 1].replace(expected, replacement)
    TARGET.write_text("".join(lines), encoding="utf-8")

    landed = TARGET.read_text(encoding="utf-8").splitlines()[line_no - 1].strip()
    if landed != replacement:
        print("EDIT DID NOT LAND: line", line_no, "reads", landed, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print("applied", name, "at line", line_no, "->", landed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
