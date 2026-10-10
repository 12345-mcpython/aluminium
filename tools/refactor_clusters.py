"""Find separable clusters inside one Java file, using DIRECT call edges.

Reachability is too coarse for deciding a split: every helper the dispatcher can
reach looks "shared", so a 6800-line file reports one 99-helper blob. Direct edges
answer the question that actually matters -- if I move this group out, which
callers outside the group would have to see it? -- and a group with no callers
outside itself is movable as a unit.

Reported per group: its size in lines, and every outside caller with the number of
call sites, because a group with one outside caller is easy (promote that one
method) while a group with thirty is not worth splitting.

Usage:
    python tools/refactor_clusters.py <file.java> [minLines]
"""

import importlib.util
import sys
from collections import defaultdict

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)


def unions(sites, names):
    """Connect methods that call each other, transitively."""
    parent = {n: n for n in names}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    for caller, callees in sites.items():
        if caller not in parent:
            continue
        for callee in callees:
            if callee in parent:
                union(caller, callee)
    groups = defaultdict(set)
    for n in names:
        groups[find(n)].add(n)
    return list(groups.values())


def main():
    path = sys.argv[1]
    min_lines = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    methods, _fields, sites = cm.scan(path)

    groups = unions(sites, set(methods))
    rows = []
    for group in groups:
        span = sum((methods[n]["end"] or 0) - methods[n]["line"] + 1 for n in group)
        outside = {}
        for caller, callees in sites.items():
            if caller in group:
                continue
            hits = sum(1 for c in callees if c in group)
            if hits:
                outside[caller] = hits
        rows.append((span, group, outside))

    rows.sort(key=lambda r: -r[0])
    print(f"{path}: {len(methods)} methods, {len(rows)} direct-edge clusters\n")
    for span, group, outside in rows:
        if span < min_lines:
            continue
        names = sorted(group, key=lambda n: methods[n]["line"])
        fan_in = sum(outside.values())
        kind = "LEAF" if not outside else f"{fan_in} call site(s) from {len(outside)} outside method(s)"
        print(f"== {span:5} lines, {len(group):3} methods  [{kind}]")
        for n in names:
            info = methods[n]
            print(f"     {n:34} line {info['line']:5} span {(info['end'] or 0) - info['line'] + 1:4}")
        if outside:
            top = sorted(outside.items(), key=lambda kv: -kv[1])[:10]
            print("     outside callers: " + ", ".join(f"{k}({v})" for k, v in top))
        print()


if __name__ == "__main__":
    main()
