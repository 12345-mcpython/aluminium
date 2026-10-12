"""Measure what `refactor_callmap.scan` loses to same-name overloading.

`scan` keys methods by NAME, so of two declarations sharing a name only the first survives in
the map (`methods.setdefault`). Every consumer then believes the survivor's span is the whole
name: `refactor_movable.py` reads only the delegating half's fields (finding none) and
`refactor_spans.py` reports a truncated size.

This reports, per file: the names with more than one top-level declaration, the span the map
kept, and the span it dropped -- so the size of the blind spot is a number rather than a worry.

Usage:
    python tools/refactor_overloads.py <file.java> [...]
"""

import importlib.util
import re
import sys

SPEC = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cm)


def all_decls(path):
    """Every top-level (indent 4) declaration, in file order, overloads included."""
    raw = open(path, encoding="utf-8").read()
    clean = cm.strip_noncode(raw)
    clean_lines = clean.split("\n")
    offsets, pos = [], 0
    for line in clean_lines:
        offsets.append(pos)
        pos += len(line) + 1

    def line_of(offset):
        lo, hi = 0, len(offsets) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if offsets[mid] <= offset:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    out = []
    for m in cm.DECL.finditer(clean):
        if m.group("indent") != "    ":
            continue
        start = line_of(m.start())
        if cm.STATEMENT_STARTS.match(clean_lines[start - 1]):
            continue
        brace = clean.find("{", m.start("name"))
        depth, end = 0, None
        for k in range(line_of(brace) - 1, len(clean_lines)):
            line_end = offsets[k] + len(clean_lines[k])
            lo = max(brace, offsets[k])
            hi = min(line_end, len(clean))
            if lo < hi:
                segment = clean[lo:hi]
                depth += segment.count("{") - segment.count("}")
            if depth <= 0:
                end = k + 1
                break
        out.append({"name": m.group("name"), "line": start, "end": end, "args": m.group("args")})
    return out


def main():
    for path in sys.argv[1:]:
        decls = all_decls(path)
        methods, _fields, _sites = cm.scan(path)
        by_name = {}
        for d in decls:
            by_name.setdefault(d["name"], []).append(d)

        over = {n: ds for n, ds in by_name.items() if len(ds) > 1}
        total = sum(1 for _ in open(path, encoding="utf-8"))
        kept_lines = sum((m["end"] or 0) - m["line"] + 1 for m in methods.values())
        real_lines = sum((d["end"] or 0) - d["line"] + 1 for d in decls)

        print(f"=== {path}")
        print(f"    file {total} lines; {len(decls)} declarations; {len(methods)} names in the map")
        print(f"    overloaded names: {len(over)}")
        print(f"    span covered by the map: {kept_lines} lines "
              f"({kept_lines * 100.0 / total:.1f}% of file)")
        print(f"    span covered by all declarations: {real_lines} lines "
              f"({real_lines * 100.0 / total:.1f}% of file)")

        lost = 0
        for name, ds in sorted(over.items(), key=lambda kv: -sum((d["end"] or 0) - d["line"] + 1 for d in kv[1])):
            keeper = methods.get(name)
            dropped = [d for d in ds if d["line"] != (keeper or {}).get("line")]
            lost += sum(max(0, (d["end"] or 0) - d["line"] + 1) for d in dropped)
            kept_span = f"{(keeper['end'] or 0) - keeper['line'] + 1}" if keeper else "?"
            print(f"\n    {name}: {len(ds)} declarations")
            print(f"        KEPT    {keeper['line']:>5}-{keeper['end']:<5} ({kept_span:>4} lines)  args({keeper['args'][:60]})")
            for d in dropped:
                size = (d["end"] or 0) - d["line"] + 1
                flag = "  <== UNDER-MEASURED" if size > int(kept_span) else ""
                print(f"        DROPPED {d['line']:>5}-{d['end']:<5} ({size:>4} lines)  args({d['args'][:60]}){flag}")
        print(f"\n    total lines invisible to every consumer: {lost}")
        print()


if __name__ == "__main__":
    main()
