"""Mutations for Pearl's ten stat-trace rows.

Anchors are PER LINE (the JSON keeps one field per line, so a multi-field anchor matches nothing - measured: the
first version of this script searched for `"attribute": "DEFENCE", "percent": 0.225` and found 0 hits, which read as
four surviving mutants). Line numbers are resolved by scanning; restore goes through `git checkout`; every apply
proves it landed.

Usage:
    python tools/mut_pearl_stat_traces.py              # list (rewrites the line numbers first)
    python tools/mut_pearl_stat_traces.py <name>
    python tools/mut_pearl_stat_traces.py restore
"""
import pathlib
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RULE_ID = "stat_traces_the_ten_panel_rows"

# name -> (anchor text on ONE line, replacement)
MUTATIONS = {
    # The Defence sum: 22.5% -> 32.5% (a wrong sum, which is what a mis-added trace looks like).
    "defence_sum": ('"percent": 0.225,', '"percent": 0.325,'),
    # Speed written as a PERCENT instead of a flat delta - the order-of-magnitude mistake this op exists to avoid.
    "speed_as_percent": ('"amount": 9.0,', '"percent": 9.0,'),
    # The effect-RES sum: 4% only, dropping the 6% row.
    "effect_res_sum": ('"percent": 0.1,', '"percent": 0.04,'),
    # The Elation sum: 6% instead of 10%, dropping the 4% row.
    "elation_sum": ('"percent": 0.1,', '"percent": 0.06,'),
}

# Mutations that must target a SPECIFIC line, because their anchor text occurs more than once inside the rule.
PINNED = {"effect_res_sum": "EFFECT_RESISTANCE", "elation_sum": "ELATION_DAMAGE_BOOST"}


def rule_span(lines):
    """The 1-based [start, end] of the stat-trace rule object, by brace depth."""
    id_line = next(i for i, l in enumerate(lines) if f'"id": "{RULE_ID}"' in l)
    start = id_line
    while start > 0 and "{" not in lines[start - 1]:
        start -= 1
    start = max(0, start - 1)
    depth = 0
    for i in range(start, len(lines)):
        depth += lines[i].count("{") - lines[i].count("}")
        if depth == 0:
            return start + 1, i + 1
    raise SystemExit("unterminated rule " + RULE_ID)


def resolve():
    lines = TARGET.read_text(encoding="utf-8").splitlines()
    lo, hi = rule_span(lines)
    resolved = {}
    for name, (anchor, replacement) in MUTATIONS.items():
        owner = PINNED.get(name)
        hits = []
        for i in range(lo - 1, hi):
            if anchor not in lines[i]:
                continue
            if owner is not None and owner not in lines[i - 1]:
                continue
            hits.append(i + 1)
        if len(hits) != 1:
            raise SystemExit(f"{name}: expected one hit in {lo}-{hi}, found {hits}")
        resolved[name] = (hits[0], anchor, replacement)
    return resolved


def restore():
    result = subprocess.run(["git", "checkout", "--", str(TARGET)], capture_output=True, text=True)
    print("restored (git)" if result.returncode == 0 else f"git failed: {result.stderr.strip()}")
    return 0 if result.returncode == 0 else 1


def main():
    resolved = resolve()
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(MUTATIONS):
        print(__doc__)
        for name, (line_no, anchor, _) in resolved.items():
            print(f"  {name:18s} line {line_no}")
        return 1
    if sys.argv[1] == "restore":
        return restore()

    name = sys.argv[1]
    line_no, anchor, replacement = resolved[name]
    lines = TARGET.read_text(encoding="utf-8").splitlines(keepends=True)
    if anchor not in lines[line_no - 1]:
        print(f"NOT APPLIED: {TARGET}:{line_no} does not hold {anchor!r}")
        return 2
    lines[line_no - 1] = lines[line_no - 1].replace(anchor, replacement)
    TARGET.write_text("".join(lines), encoding="utf-8")
    landed = TARGET.read_text(encoding="utf-8").splitlines()[line_no - 1]
    if replacement not in landed:
        print("EDIT DID NOT LAND - restoring")
        restore()
        return 3
    print(f"applied {name} at line {line_no}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
