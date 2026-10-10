"""Print the helper set that can move with a given seam, and what it must still see.

Combines _callmap (reachability) with _extract (declaration spans) to answer, for one
extraction: which declarations are exclusively reached from the roots being moved, which
are shared with what stays, and which constants are referenced only from the moved text.

The shared list is the part that has to be checked by hand: a shared helper stays put and
becomes package-private, while a shared CONSTANT cannot stay in the reducing class if the
moved class is the one that needs it.

Usage:
    python tools/refactor_seam.py <file.java> --move validate --stay apply fire
"""

import importlib.util
import re
import sys

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)

spec2 = importlib.util.spec_from_file_location("ex", "tools/refactor_extract.py")
ex = importlib.util.module_from_spec(spec2)
spec2.loader.exec_module(ex)


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    moving = args[args.index("--move") + 1: args.index("--stay")]
    staying = args[args.index("--stay") + 1:]

    methods, _fields, sites = cm.scan(path)
    found, lines = ex.declarations(path)

    def reach(roots):
        out = set()
        for r in roots:
            out |= cm.closure([r], sites, methods)
        return out

    moving_r = reach(moving)
    staying_r = reach(staying)

    exclusive = sorted(moving_r - staying_r, key=lambda n: methods[n]["line"])
    shared = sorted(moving_r & staying_r, key=lambda n: methods[n]["line"])

    def span(names):
        return sum((methods[n]["end"] or 0) - methods[n]["line"] + 1 for n in names)

    print(f"=== MOVES with {'/'.join(moving)}: {len(exclusive)} declarations, {span(exclusive)} lines")
    for n in exclusive:
        print(f"     {n:32} line {methods[n]['line']:5} span {(methods[n]['end'] or 0) - methods[n]['line'] + 1:4}")

    print(f"\n=== STAYS (shared with {'/'.join(staying)}): {len(shared)}")
    for n in shared:
        print(f"     {n:32} line {methods[n]['line']:5}")

    # Constants: referenced from the moving text only?
    body = []
    for n in exclusive:
        info = methods[n]
        body.extend(lines[info["line"] - 1: info["end"]])
    body_text = "\n".join(body)
    stay_text = []
    for n in staying_r:
        if n in methods:
            info = methods[n]
            stay_text.extend(lines[info["line"] - 1: info["end"]])
    stay_body = "\n".join(stay_text)

    print("\n=== CONSTANTS referenced by the moving text")
    only_moving, both = [], []
    for lineno, name in _fields:
        word = re.compile(r"(?<![\w.])" + re.escape(name) + r"(?![\w])")
        in_move = any(word.search(l) for l in body)
        in_stay = any(word.search(l) for l in stay_text)
        if in_move and not in_stay:
            only_moving.append((lineno, name))
        elif in_move and in_stay:
            both.append((lineno, name))
    print(f"  move-only ({len(only_moving)}):  " + ", ".join(n for _, n in only_moving))
    print(f"  needed by BOTH ({len(both)}): " + ", ".join(n for _, n in both))

    # Helpers the moving text calls that are NOT in the moved set -- these must exist as
    # package-private in the reducing class, or the extraction will not compile.
    print("\n=== HELPERS the moved text calls but that STAY")
    outside = set()
    for n in exclusive:
        for callee in sites.get(n, ()):
            if callee in methods and callee not in exclusive and callee != n:
                outside.add(callee)
    for n in sorted(outside, key=lambda n: methods[n]["line"]):
        print(f"     {n:32} line {methods[n]['line']:5}")


if __name__ == "__main__":
    main()
