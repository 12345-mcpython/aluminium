"""Grow a set of declarations until nothing outside it calls in.

This is the test for "can this move as a unit": start from the methods you want to move,
then repeatedly pull in any other method whose callers are ALL inside the set. When the
set stops growing, it is movable -- a new class holding it needs no back-reference to the
class it left.

Why not reachability: it answers the wrong direction. `resolveTarget` is called by the
dispatcher, so a reachability query says "shared" and reports every helper it can reach,
which for this file is essentially all of them. Caller-closure asks the question that
decides the split.

Methods called from OUTSIDE the set are printed as the interface the new class must
expose (or the helpers that have to stay).

Usage:
    python tools/refactor_closure.py <file.java> <name> [name ...]
"""

import importlib.util
import sys
from collections import defaultdict

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)

spec2 = importlib.util.spec_from_file_location("ex", "tools/refactor_extract.py")
ex = importlib.util.module_from_spec(spec2)
spec2.loader.exec_module(ex)


def main():
    path = sys.argv[1]
    seeds = sys.argv[2:]

    methods, _fields, sites = cm.scan(path)
    found, _lines = ex.declarations(path)

    unknown = [s for s in seeds if s not in methods]
    if unknown:
        raise SystemExit(f"not methods in {path}: {unknown}")

    members = set(seeds)
    changed = True
    while changed:
        changed = False
        for name in methods:
            if name in members:
                continue
            callers = {c for c, callees in sites.items() if name in callees}
            if callers and callers <= members:
                members.add(name)
                changed = True

    def span(names):
        return sum((methods[n]["end"] or 0) - methods[n]["line"] + 1 for n in names)

    ordered = sorted(members, key=lambda n: methods[n]["line"])
    print(f"=== MOVABLE SET from {len(seeds)} seed(s): {len(members)} declarations, {span(members)} lines")
    for n in ordered:
        info = methods[n]
        print(f"     {n:34} line {info['line']:5} span {(info['end'] or 0) - info['line'] + 1:4}")

    # What the new class still has to reach: methods inside that anything outside calls.
    interface = defaultdict(set)
    for caller, callees in sites.items():
        if caller in members:
            continue
        for callee in callees:
            if callee in members:
                interface[callee].add(caller)
    print(f"\n=== the set's INTERFACE (called from outside): {len(interface)}")
    for name in sorted(interface, key=lambda n: methods[n]["line"]):
        callers = sorted(interface[name])
        shown = ", ".join(callers[:6]) + (" ..." if len(callers) > 6 else "")
        print(f"     {name:34} <- {len(callers)} caller(s): {shown}")

    # And what it must call back out to.
    outbound = defaultdict(int)
    for name in members:
        for callee in sites.get(name, ()):
            if callee in methods and callee not in members:
                outbound[callee] += 1
    print(f"\n=== helpers the set CALLS but that stay: {len(outbound)}")
    for name in sorted(outbound, key=lambda n: methods[n]["line"]):
        print(f"     {name:34} line {methods[name]['line']:5}  ({outbound[name]} call site(s))")


if __name__ == "__main__":
    main()
