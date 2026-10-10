"""Report which static fields a set of methods reads, and which it only reads.

Companion to refactor_closure.py: a movable method set is only movable if the constants it
reads either travel with it or are already declared somewhere both can see. This lists,
for the named methods, every static field of the file they mention in code (comment lines
excluded), so a constant needed only by the moved text can be moved too and a shared one
is known up front.

Usage:
    python tools/refactor_setconsts.py <file.java> <method> [method ...]
"""

import importlib.util
import re
import sys

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)


def main():
    path = sys.argv[1]
    names = set(sys.argv[2:])
    methods, fields, sites = cm.scan(path)
    lines = open(path, encoding="utf-8").read().split("\n")

    body = []
    for n in sorted(names, key=lambda n: methods[n]["line"]):
        info = methods[n]
        body.extend(lines[info["line"] - 1: info["end"]])
    # Drop comment-only lines: a field named in prose is not a read.
    code = [l for l in body if not l.lstrip().startswith(("//", "*", "/*"))]
    text = "\n".join(code)

    inside = set(names)
    print(f"field reads inside {len(names)} method(s):")
    hits = []
    for lineno, name in fields:
        if name in inside:
            continue
        pattern = re.compile(r"(?<![\w.])" + re.escape(name) + r"(?![\w])")
        count = len(pattern.findall(text))
        if count:
            hits.append((lineno, name, count))
    if not hits:
        print("   (none -- the set reads no static field of this file)")
    for lineno, name, count in hits:
        print(f"   {name:34} declared line {lineno:5}  {count} read(s)")


if __name__ == "__main__":
    main()
