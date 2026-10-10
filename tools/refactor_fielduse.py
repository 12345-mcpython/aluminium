"""Attribute each static field of one Java file to the regions that read it.

Companion to refactor_callmap.py: a split has to move the CONSTANTS as well as the methods,
and a constant read by two regions cannot move into either. This prints, per field,
the region-level read counts, so the shared vocabulary is identified up front
instead of surfacing as a compile error in the middle of an extraction.

Regions are given as name=startLine-endLine.

Usage:
    python tools/refactor_fielduse.py <file.java> validate=354-1204 apply=1289-1415 fire=1426-1515
"""

import re
import sys

FIELD = re.compile(
    r"^\s{4}(?:private|public|protected)?\s*static\s+(?:final\s+)?"
    r"[\w.<>\[\], ?]+\s+(?P<name>\w+)\s*(?=|;)"
)


def main():
    path = sys.argv[1]
    regions = {}
    for spec in sys.argv[2:]:
        name, span = spec.split("=")
        lo, hi = span.split("-")
        regions[name] = (int(lo), int(hi))

    lines = open(path, encoding="utf-8").read().split("\n")
    fields = []
    for i, line in enumerate(lines, start=1):
        m = FIELD.match(line)
        if m:
            fields.append((i, m.group("name")))

    print(f"{path}: {len(fields)} static fields, regions {list(regions)}\n")
    print(f"{'field':34} {'line':>5}  " + "  ".join(f"{n:>8}" for n in regions) + "   verdict")
    for lineno, name in fields:
        word = re.compile(r"(?<![\w.])" + re.escape(name) + r"(?![\w])")
        counts = {}
        for rname, (lo, hi) in regions.items():
            counts[rname] = sum(len(word.findall(lines[k])) for k in range(lo - 1, min(hi, len(lines))))
        total = sum(counts.values())
        users = [r for r, c in counts.items() if c]
        if len(users) == 0:
            verdict = "unused in regions (class-level doc?)"
        elif len(users) == 1:
            verdict = f"ONLY {users[0]}"
        else:
            verdict = "SHARED: " + ",".join(users)
        cells = "  ".join(f"{counts[r]:>8}" for r in regions)
        print(f"{name:34} {lineno:5}  {cells}   {verdict}   (total {total})")


if __name__ == "__main__":
    main()
