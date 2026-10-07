"""Mutations for Pearl's ten stat-trace rows.

Anchor method, after three failed attempts at something cleverer: each mutation is a MULTI-LINE context pattern that
occurs EXACTLY ONCE in the whole file, and the apply step asserts that count before writing and after writing. No line
numbers to go stale, no brace balancing to get wrong, and no "skip the duplicate" filter to silently skip a mutant
(the failure modes actually hit: a brace span that stopped early left the search window too small, so one mutant was
never applied while the others looked applied; and `git checkout -- <path>` restores the INDEX, not HEAD, so a mutant
that had been staged came back as the "authoritative" version).

Restore is `git checkout HEAD -- <path>`, which is the committed version by definition.

Usage:
    python tools/mut_pearl_stat_traces.py              # list
    python tools/mut_pearl_stat_traces.py <name>
    python tools/mut_pearl_stat_traces.py restore
"""
import pathlib
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")

# name -> (the exact multi-line text to find, its replacement, what the mutation means)
MUTATIONS = {
    "defence_sum": (
        '          "attribute": "DEFENCE",\n          "percent": 0.225,',
        '          "attribute": "DEFENCE",\n          "percent": 0.325,',
        "the Defence sum 22.5% -> 32.5% (a mis-added trace row)",
    ),
    "speed_as_percent": (
        '          "attribute": "SPEED",\n          "amount": 9.0,',
        '          "attribute": "SPEED",\n          "percent": 9.0,',
        "Speed as a share of base instead of a flat +9",
    ),
    "effect_res_sum": (
        '          "attribute": "EFFECT_RESISTANCE",\n          "percent": 0.1,',
        '          "attribute": "EFFECT_RESISTANCE",\n          "percent": 0.04,',
        "the effect-RES sum drops the 6% row (0.10 -> 0.04)",
    ),
    "elation_sum": (
        '          "attribute": "ELATION_DAMAGE_BOOST",\n          "percent": 0.1,\n'
        '          "permanent": true,\n          "target": "self",\n          "coexist": true',
        '          "attribute": "ELATION_DAMAGE_BOOST",\n          "percent": 0.06,\n'
        '          "permanent": true,\n          "target": "self",\n          "coexist": true',
        "the Elation sum drops the 4% row (0.10 -> 0.06)",
    ),
}


def restore():
    result = subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)],
                            capture_output=True, text=True)
    if result.returncode != 0:
        print("git checkout failed:", result.stderr.strip())
        return 1
    print("restored", TARGET, "from HEAD")
    return 0


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(MUTATIONS):
        print(__doc__)
        for name, (_, _, meaning) in MUTATIONS.items():
            print(f"  {name:18s} {meaning}")
        return 1
    if sys.argv[1] == "restore":
        return restore()

    name = sys.argv[1]
    anchor, replacement, meaning = MUTATIONS[name]
    text = TARGET.read_text(encoding="utf-8")
    count = text.count(anchor)
    if count != 1:
        print(f"NOT APPLIED: the anchor for {name} occurs {count} times, expected exactly 1")
        return 2

    TARGET.write_text(text.replace(anchor, replacement), encoding="utf-8")

    after = TARGET.read_text(encoding="utf-8")
    if after.count(replacement) != 1 or after.count(anchor) != 0:
        print("EDIT DID NOT LAND - restoring")
        restore()
        return 3
    print(f"applied {name}: {meaning}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
