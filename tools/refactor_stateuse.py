"""Measure how much instance state each candidate subsystem of a class touches.

Companion to refactor_movable.py, which answers "does this method call anything that stays".
For a stateful class the harder question is the reverse one -- an extracted method may not read
a private field, so a subsystem is movable only if it reaches the rest of the class through the
facade. This counts, per candidate group, the DISTINCT private fields its methods read, so a
group that reads two fields can be moved by passing those two as parameters while a group that
reads thirty cannot be moved at all.

Fields are the ones refactor_callmap reports, and reads are counted on code lines only (a field
named in a comment is not a read).

Usage:
    python tools/refactor_stateuse.py <file.java> group=m1,m2,m3 [group2=...]
"""

import importlib.util
import re
import sys
from collections import defaultdict

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)


def main():
    path = sys.argv[1]
    groups = {}
    for arg in sys.argv[2:]:
        name, members = arg.split("=", 1)
        groups[name] = members.split(",")

    methods, fields, sites = cm.scan(path)
    lines = open(path, encoding="utf-8").read().split("\n")
    field_names = [n for _lineno, n in fields]

    print(f"{path}: {len(methods)} methods, {len(field_names)} static fields in the map")
    print(f"{'group':26} {'methods':>8} {'span':>6} {'fields read':>12}   detail")
    rows = []
    for group, members in groups.items():
        missing = [m for m in members if m not in methods]
        if missing:
            print(f"{group:26} !! not methods: {missing}")
            continue
        body = []
        for m in members:
            info = methods[m]
            body.extend(lines[info["line"] - 1: info["end"]])
        code = "\n".join(l for l in body if not l.lstrip().startswith(("//", "*", "/*")))

        read = []
        for fname in field_names:
            if re.search(r"(?<![\w.])" + re.escape(fname) + r"(?![\w])", code):
                read.append(fname)
        span = sum((methods[m]["end"] or 0) - methods[m]["line"] + 1 for m in members)
        rows.append((len(read), group, len(members), span, read))

    for nread, group, nm, span, read in sorted(rows):
        detail = ", ".join(read[:6]) + (" ..." if len(read) > 6 else "")
        print(f"{group:26} {nm:>8} {span:>6} {nread:>12}   {detail}")


if __name__ == "__main__":
    main()
