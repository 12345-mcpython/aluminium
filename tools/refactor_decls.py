"""List every declaration a class's scan() knows, with visibility, order and declaration count.

The seam report tells you what a candidate group CALLS; it does not show the class layout, which
is what decides whether the group is contiguous (cheap to cut) or scattered (expensive).

Usage:
    python tools/refactor_decls.py <file.java>
"""

import importlib.util
import sys

SPEC = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cm)


def main():
    path = sys.argv[1]
    methods, _fields, _sites = cm.scan(path)
    lines = open(path, encoding="utf-8").read().split("\n")
    total = sum(len(i["decls"]) for i in methods.values())
    over = sum(1 for i in methods.values() if len(i["decls"]) > 1)
    print(f"{path}: {len(methods)} names, {total} declarations, {over} overloaded\n")
    print(f"{'line':>6} {'name':34} {'visibility':10} {'decls':>5} {'lines':>6}")
    for name in sorted(methods, key=lambda k: methods[k]["line"]):
        info = methods[name]
        raw = lines[info["line"] - 1].strip()
        vis = "public" if "public " in raw else ("private" if "private " in raw else "package")
        span = sum((d["end"] or 0) - d["line"] + 1 for d in info["decls"])
        many = f"x{len(info['decls'])}" if len(info["decls"]) > 1 else ""
        print(f"{info['line']:>6} {name:34} {vis:10} {many:>5} {span:>6}")


if __name__ == "__main__":
    main()
