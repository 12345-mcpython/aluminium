"""Report the line span of candidate method groups in a class, for choosing a split seam.

Why this exists: `refactor_movable.py` answers "what does a candidate READ", which decides
whether a group CAN move. It does not answer "how big is the group", which decides whether
moving it is worth a commit. Choosing a seam needs both, and the second one is not visible in
the movable report.

The scan comes from `refactor_callmap.py` (bracket-balanced, not regex), because the span is
only trustworthy if the method body was actually delimited rather than matched.

Usage:
    python tools/refactor_spans.py <file.java> --set name1 name2 ...
    python tools/refactor_spans.py <file.java> --top [n]

With --top it lists the n largest methods, which is how a seam whose name is unknown gets found.
"""

import importlib.util
import sys

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)


def span_of(methods, name):
    info = methods[name]
    end = info["end"] or info["line"]
    return info["line"], end, end - info["line"] + 1


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    methods, _fields, _sites = cm.scan(path)
    lines = sum(1 for _ in open(path, encoding="utf-8"))

    if "--top" in args:
        i = args.index("--top")
        n = int(args[i + 1]) if len(args) > i + 1 else 20
        ranked = sorted(methods, key=lambda m: span_of(methods, m)[2], reverse=True)
        print(f"{path}: {lines} lines, {len(methods)} methods\n")
        print(f"{'method':38} {'lines':>6}  span")
        for m in ranked[:n]:
            start, end, size = span_of(methods, m)
            print(f"{m:38} {size:>6}  {start}-{end}")
        return

    if "--set" not in args:
        raise SystemExit(__doc__)

    i = args.index("--set")
    names = args[i + 1:]
    missing = [n for n in names if n not in methods]
    if missing:
        raise SystemExit(f"not methods in {path}: {missing}")

    spans = [span_of(methods, n) for n in names]
    total = sum(s[2] for s in spans)
    lo = min(s[0] for s in spans)
    hi = max(s[1] for s in spans)
    print(f"=== {len(names)} methods, {total} lines total, span {lo}-{hi} ({hi - lo + 1} lines of file)")
    print(f"    ({total * 100.0 / lines:.1f}% of the file's {lines} lines)\n")
    for n, (start, end, size) in zip(names, spans):
        print(f"    {n:38} {size:>6}  {start}-{end}")

    # A gap is a method that sits between two members of the set. A large gap means the group is
    # scattered, which costs more churn to extract than a contiguous one.
    inside = sorted((s[0], s[1]) for s in spans)
    gaps = []
    for (a_start, a_end), (b_start, _b_end) in zip(inside, inside[1:]):
        if b_start > a_end + 1:
            gaps.append((a_end + 1, b_start - 1, b_start - a_end - 1))
    print(f"\n{len(gaps)} gap(s) between members (lines that would stay behind):")
    for start, end, size in gaps:
        print(f"    {start}-{end}  ({size} lines)")
    if not gaps:
        print("    (none -- the group is contiguous)")


if __name__ == "__main__":
    main()
