"""Find methods in a class that can move to a sibling, given the class uses INSTANCE state.

The Trigger* extractions were static classes, so "does this call anything that stays" was the
whole question. A stateful class adds a second one: an extracted method may not read a
private field, so a subsystem is movable only when it reaches the rest of the class through
its PUBLIC API -- which is exactly the delegation facade the split is supposed to preserve.

Reports, for a candidate set of methods:

  * fields read directly  -- a blocker: those are instance state, not API;
  * public methods called -- harmless: that is the facade working;
  * non-public methods called -- a blocker unless they travel with the set.

With --suggest it ranks every method by how close it is to movable, so a subsystem can be
picked by evidence rather than by its name.

Usage:
    python tools/refactor_movable.py <file.java> --set m1 m2 ...
    python tools/refactor_movable.py <file.java> --suggest [minMethods]
"""

import importlib.util
import re
import sys
from collections import defaultdict

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)

PUBLIC_DECL = re.compile(
    r"^ {4}public\s+(?:static\s+)?(?:final\s+)?(?:<[^>]+>\s*)?"
    r"[\w.<>\[\], ?]+\s+(?P<name>[a-z]\w*)\s*\("
)
FIELD_DECL = re.compile(
    r"^ {4}(?P<mods>(?:(?:public|private|protected|static|final|transient|volatile)\s+)*)"
    r"[\w.<>\[\], ?]+\s+(?P<name>[a-z]\w*)\s*(?:=|;)"
)


# An instance field is a 4-space-indented declaration that is NOT static and is NOT a
# statement. The statement guard matters: a naive type+name pattern also matches
# `return monsters;` and `int paid = ...` inside a method body, which is how the first
# version of this reported 132 "fields" for a class with far fewer.
INSTANCE_FIELD = re.compile(
    r"^ {4}(?P<mods>(?:(?:public|private|protected|final|transient|volatile)\s+)*)"
    r"(?P<type>[A-Z]\w*(?:<[^>]*>)?(?:\[\])?|int|long|double|boolean|char|byte|short|float)"
    r"\s+(?P<name>[a-z]\w*)\s*(?:=|;)"
)
STATEMENT = re.compile(
    r"^ {4}(?:if|for|while|switch|catch|else|do|try|return|throw|assert|synchronized|"
    r"final|var|new|case|default|break|continue|yield|else)\b"
)


def analyse(path):
    methods, _fields, sites = cm.scan(path)
    lines = open(path, encoding="utf-8").read().split("\n")

    public = set()
    for line in lines:
        m = PUBLIC_DECL.match(line)
        if m:
            public.add(m.group("name"))

    # Instance fields of the class (4-space indent, not static, not a statement).
    fields = {}
    for i, line in enumerate(lines, start=1):
        if STATEMENT.match(line):
            continue
        m = INSTANCE_FIELD.match(line)
        if m and "static" not in m.group("mods"):
            fields[m.group("name")] = i

    return methods, sites, lines, public, fields


def classify(names, methods, sites, lines, public, fields):
    """What a candidate set reaches that is not in the set."""
    inside = set(names)
    body = []
    for n in inside:
        info = methods[n]
        body.extend(lines[info["line"] - 1: info["end"]])
    code = [l for l in body if not l.lstrip().startswith(("//", "*", "/*"))]
    text = "\n".join(code)

    read_fields = []
    for name, lineno in fields.items():
        if re.search(r"(?<![\w.])" + re.escape(name) + r"(?![\w])", text):
            read_fields.append((name, lineno))

    outside_calls = defaultdict(int)
    for n in inside:
        for callee in sites.get(n, ()):
            if callee in methods and callee not in inside:
                outside_calls[callee] += 1

    blocked = sorted(c for c in outside_calls if c not in public)
    return read_fields, dict(outside_calls), blocked


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    methods, sites, lines, public, fields = analyse(path)

    if "--suggest" in args:
        i = args.index("--suggest")
        minimum = int(args[i + 1]) if len(args) > i + 1 else 2
        rows = []
        for name in methods:
            readable = re.sub(r"(?<!^)(?=[A-Z])", " ", name)
            rows.append((name, readable))
        # Rank by "calls no non-public method outside itself, reads no field".
        scored = []
        for name, _ in rows:
            read_fields, calls, blocked = classify([name], methods, sites, lines, public, fields)
            scored.append((len(read_fields), len(blocked), name, len(calls)))
        scored.sort()
        print(f"{path}: {len(methods)} methods, {len(public)} public, {len(fields)} instance fields\n")
        print(f"{'method':34} {'fields':>7} {'blocking calls':>15} {'API calls':>10}")
        for nfields, nblocked, name, ncalls in scored:
            if nfields == 0 and nblocked == 0:
                print(f"{name:34} {nfields:>7} {nblocked:>15} {ncalls:>10}")
        return

    i = args.index("--set")
    names = args[i + 1:]
    missing = [n for n in names if n not in methods]
    if missing:
        raise SystemExit(f"not methods in {path}: {missing}")

    read_fields, calls, blocked = classify(names, methods, sites, lines, public, fields)
    span = sum((methods[n]["end"] or 0) - methods[n]["line"] + 1 for n in names)
    print(f"=== CANDIDATE: {len(names)} methods, {span} lines")
    print(f"\nfields read directly ({len(read_fields)}) -- must be 0 to move as a static helper:")
    for name, lineno in read_fields:
        print(f"     {name:30} declared line {lineno}")
    print(f"\nnon-public methods called that would NOT travel ({len(blocked)}):")
    for name in blocked:
        print(f"     {name:30} called {calls[name]}x")
    if not blocked:
        print("     (none -- movable)")
    print(f"\npublic API calls ({len([c for c in calls if c in public])}) -- the facade, fine:")
    for name in sorted(c for c in calls if c in public):
        print(f"     {name:30} called {calls[name]}x")


if __name__ == "__main__":
    main()
