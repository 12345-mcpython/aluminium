"""Mutations for Pearl (1503)'s trace / Eidolon rules.

THREE LESSONS ARE BAKED INTO THIS SCRIPT, each one measured the hard way in a single round:

1.  **Anchors must use the bytes the file holds.** Chinese written as `\\uXXXX` escapes matches nothing -
    the content files hold literal characters. Two mutants silently never applied, and a green run read as
    a survivor.
2.  **Line numbers shift on every edit.** They are RESOLVED, not memorised: run
    `python tools/resolve_pearl_anchors.py` after changing 1503.json and it rewrites the table below.
3.  **A backup goes stale, and `restore` then undoes real work.** That happened here: an old .bak was
    copied back over a rule that had been fixed since, three times. Restore now goes through `git checkout`,
    which is the authoritative version.

Every apply PROVES the edit landed by re-reading the line; a mutation that did not apply is reported as
NOT APPLIED rather than as a survivor.

Usage:
    python tools/mut_pearl_traces.py                  # list
    python tools/mut_pearl_traces.py <name>           # apply
    python tools/mut_pearl_traces.py restore          # git checkout the file
"""
import pathlib
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")

# name -> (line number, expected text, replacement text). Line numbers are rewritten by
# tools/resolve_pearl_anchors.py, which scans the file rather than trusting these.
EDITS = {
    "insight_threshold": (710, '"self_attr:DEFENCE >= 2400"', '"self_attr:DEFENCE >= 3400"'),
    "insight_per_point": (716, '"percent": 0.0003,', '"percent": 0.0006,'),
    "insight_base": (720, '"amount": 0.32,', '"amount": 0.42,'),
    "insight_cap": (721, '"cap_amount": 0.68', '"cap_amount": 0.98'),
    "insight_healing": (735, '"percent": 0.2,', '"percent": 0.3,'),
    "tolerance_gate": (748, '"self_resource:好活当赏 >= 1"', '"self_resource:好活当赏 >= 999"'),
    "tolerance_percent": (754, '"percent": 0.5,', '"percent": 0.4,'),
    "eidolon6_percent": (773, '"percent": 0.2,', '"percent": 0.3,'),
    "eidolon6_gate": (777, '"self has_state 深度学习"', '"self has_state 美学底本"'),
}


def restore():
    """Put the file back to what git has, NOT to a backup that may predate a fix."""
    result = subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)],
                            capture_output=True, text=True)
    if result.returncode == 0:
        print("restored", TARGET, "(git)")
        return 0
    print("git checkout failed:", result.stderr.strip())
    return 1


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(EDITS):
        print(__doc__)
        print("mutations:", ", ".join(sorted(EDITS)))
        return 1

    if sys.argv[1] == "restore":
        return restore()

    name = sys.argv[1]
    line_no, expected, replacement = EDITS[name]
    lines = TARGET.read_text(encoding="utf-8").splitlines(keepends=True)
    if line_no > len(lines):
        print(f"NOT APPLIED: line {line_no} is past the end of the file ({len(lines)} lines)")
        return 2
    actual = lines[line_no - 1].strip()
    if actual != expected:
        print(f"NOT APPLIED: line {line_no} does not hold the anchor")
        print("  expected:", expected)
        print("  actual  :", actual)
        print("  (re-run tools/resolve_pearl_anchors.py to refresh the line numbers)")
        return 2

    lines[line_no - 1] = lines[line_no - 1].replace(expected, replacement)
    TARGET.write_text("".join(lines), encoding="utf-8")

    landed = TARGET.read_text(encoding="utf-8").splitlines()[line_no - 1].strip()
    if landed != replacement:
        print("EDIT DID NOT LAND: line", line_no, "reads", landed, "- restoring")
        restore()
        return 3
    print("applied", name, "at line", line_no, "->", landed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
