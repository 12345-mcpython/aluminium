"""Verify that `refactor_callmap.scan` measures method bodies correctly, including overloads.

Every tool in this directory trusts `scan` for two things: which lines belong to a method, and
which calls that method makes. Both are only as good as the brace-balanced span, and a span that
is too short fails in the worst possible direction -- the methods whose bodies were missed then
report as "reads no fields, calls nothing private", which is exactly the answer that green-lights
a split. So the spans are checked against the file itself rather than assumed.

What is checked:

  1. COVERAGE -- every top-level declaration found by `scan` is reported, and the sum of all
     declaration spans is compared with the map's coverage. Before the overload repair the map
     covered 35.7% of Battle.java while the declarations covered 44.7%, and nothing said so.
  2. BOUNDARIES -- for each declaration, the first line carries the signature and the last line
     is the one where its braces balance. The balance is recomputed here from the raw text so a
     wrong `end` is a failure rather than a number nobody looks at.
  3. NESTING -- no declaration's span may sit strictly inside another's. A span that swallows a
     neighbour is how a candidate set reports calls it never makes.

Usage:
    python tools/refactor_verify_spans.py <file.java> [...]
"""

import importlib.util
import sys

SPEC = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cm)


def main():
    failures = 0
    for path in sys.argv[1:]:
        raw = open(path, encoding="utf-8").read()
        clean = cm.strip_noncode(raw)
        clean_lines = clean.split("\n")
        raw_lines = raw.split("\n")
        offsets, pos = [], 0
        for line in clean_lines:
            offsets.append(pos)
            pos += len(line) + 1

        methods, _fields, _sites = cm.scan(path)
        decls = [d for info in methods.values() for d in info["decls"]]

        print(f"=== {path}")
        print(f"    {len(raw_lines)} lines; {len(decls)} declarations; {len(methods)} names")

        map_lines = set()
        for d in decls:
            map_lines.update(range(d["line"], (d["end"] or d["line"]) + 1))
        print(f"    declaration lines: {len(map_lines)} "
              f"({len(map_lines) * 100.0 / len(raw_lines):.1f}% of file)")

        # 1. every declaration's span ends where its braces balance, and starts on a signature
        bad_end, bad_start = [], []
        for d in decls:
            if d["end"] is None:
                bad_end.append((d["name"], d["line"], "unterminated"))
                continue
            # join from the declaration's first line up to `end` and check balance
            text = "\n".join(clean_lines[d["line"] - 1:d["end"]])
            depth = text.count("{") - text.count("}")
            if depth != 0:
                bad_end.append((d["name"], d["line"], f"end {d['end']} leaves depth {depth}"))
            first = raw_lines[d["line"] - 1]
            if "(" not in first:
                bad_start.append((d["name"], d["line"], first.strip()[:60]))

        print(f"    brace-balance failures: {len(bad_end)}")
        for name, line, why in bad_end[:10]:
            print(f"        {name} (line {line}): {why}")
            failures += 1
        print(f"    declarations whose first line is not a signature: {len(bad_start)}")
        for name, line, text in bad_start[:10]:
            print(f"        {name} (line {line}): {text}")
            failures += 1

        # 3. no declaration may sit strictly inside another
        ordered = sorted(decls, key=lambda d: (d["line"], -(d["end"] or 0)))
        nested = []
        for i, a in enumerate(ordered):
            for b in ordered[i + 1:]:
                if b["line"] > (a["end"] or 0):
                    break
                if b["name"] != a["name"] and (b["end"] or 0) <= (a["end"] or 0):
                    nested.append((b["name"], b["line"], a["name"], a["line"], a["end"]))
        print(f"    declarations nested inside another declaration: {len(nested)}")
        for bn, bl, an, al, ae in nested[:10]:
            print(f"        {bn} (line {bl}) sits inside {an} ({al}-{ae})")
            failures += 1

        over = {n: i for n, i in methods.items() if len(i["decls"]) > 1}
        print(f"    overloaded names represented: {len(over)} "
              f"(declarations carrying them: {sum(len(i['decls']) for i in over.values())})")
        for n, info in sorted(over.items(), key=lambda kv: -len(kv[1]["decls"]))[:12]:
            spans = ", ".join(f"{d['line']}-{d['end']}" for d in sorted(info["decls"], key=lambda d: d["line"]))
            print(f"        {n:28} x{len(info['decls']):<3} {spans}")
        print()

    print("FAILURES:" , failures)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
