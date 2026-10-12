"""Find methods in a class that can move to a sibling, given the class uses INSTANCE state.

The Trigger* extractions were static classes, so "does this call anything that stays" was the
whole question. A stateful class adds a second one: an extracted method may not read a
private field, so a subsystem is movable only when it reaches the rest of the class through
its PUBLIC API -- which is exactly the delegation facade the split is supposed to preserve.

A THIRD one is access, and it is invisible to both of the above. A body can read no field and
call only methods that travel with it, and still be immovable, because it touches something
PRIVATE that neither reads nor calls: a private nested enum, a private nested type used as a
parameter, or a private method reached from another half of the class. `Battle` is the worked
example -- the DOT/regeneration pass reads nothing private, yet it settles through
`applyDamage(target, damage, EnergyGrant.KILL_ONLY)`, and `EnergyGrant` is a private nested
enum, so the whole group has to stay. Measurement alone cannot see that; the invariant that
pins it lives in a test (`DamagePipelineTest` counts the public methods taking a `Damage`).

So this report is a SCREEN, not an oracle: it narrows the candidates, and the ones it keeps
still have to be checked against the invariants that are written down as tests.

Reports, for a candidate set of methods:

  * fields read directly  -- a blocker: those are instance state, not API;
  * public methods called -- harmless: that is the facade working;
  * non-public methods called -- a blocker unless they travel with the set;
  * non-public TYPES named -- a blocker: a private nested enum/type cannot be named from the
    sibling class at all, whether or not the set's own calls stay inside the set.

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

# A nested type that is not public. Naming one from a sibling class is a compile error, and it
# is the one blocker that survives both of the other two checks, so it gets its own scan.
NON_PUBLIC_NESTED = re.compile(
    r"^ {4}(?P<mods>(?:(?:private|protected|static|final|abstract|sealed)\s+)*)"
    r"(?:(?P<kind>enum|interface|record|class)\s+(?P<name>[A-Z]\w*))"
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

    # Non-public nested types. `private` is optional in the pattern because a nested member of
    # an interface/class defaults to package-private there but NOT in a class -- in a class a
    # bare `enum X` is package-private, which IS nameable from a sibling in the same package.
    # Only `private` and `protected` are real blockers, so the modifier list is checked rather
    # than the bare kind keyword.
    non_public_types = {}
    for i, line in enumerate(lines, start=1):
        m = NON_PUBLIC_NESTED.match(line)
        if m and re.search(r"\b(private|protected)\b", m.group("mods")):
            non_public_types[m.group("name")] = (i, m.group("kind"))

    return methods, sites, lines, public, fields, non_public_types


def classify(names, methods, sites, lines, public, fields, non_public_types=frozenset()):
    """What a candidate set reaches that is not in the set."""
    inside = set(names)
    body = []
    for n in inside:
        # EVERY declaration, not just the first: the delegating overload is the one that reads
        # nothing, so measuring it alone is how a group that calls a private settles-entry
        # reports as clean.
        for d in methods[n]["decls"]:
            if d["end"] is not None:
                body.extend(lines[d["line"] - 1: d["end"]])
    code = [l for l in body if not l.lstrip().startswith(("//", "*", "/*"))]
    text = "\n".join(code)

    read_fields = []
    for name, lineno in fields.items():
        if re.search(r"(?<![\w.])" + re.escape(name) + r"(?![\w])", text):
            read_fields.append((name, lineno))

    # A private nested type named inside the moving text cannot be named from the new class.
    named_private_types = []
    for name, (lineno, kind) in non_public_types.items():
        if re.search(r"(?<![\w.])" + re.escape(name) + r"(?![\w])", text):
            named_private_types.append((name, lineno, kind))

    outside_calls = defaultdict(int)
    for n in inside:
        for callee in sites.get(n, ()):
            if callee in methods and callee not in inside:
                outside_calls[callee] += 1

    blocked = sorted(c for c in outside_calls if c not in public)
    return read_fields, dict(outside_calls), blocked, named_private_types


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    methods, sites, lines, public, fields, non_public_types = analyse(path)

    if "--suggest" in args:
        i = args.index("--suggest")
        minimum = int(args[i + 1]) if len(args) > i + 1 else 2
        # Rank by "calls no non-public method outside itself, reads no field, names no private type".
        scored = []
        for name in methods:
            read_fields, calls, blocked, priv = classify(
                [name], methods, sites, lines, public, fields, non_public_types)
            scored.append((len(read_fields), len(blocked), len(priv), name, len(calls)))
        scored.sort()
        print(f"{path}: {len(methods)} methods, {len(public)} public, {len(fields)} instance fields, "
              f"{len(non_public_types)} non-public nested types\n")
        print(f"{'method':34} {'fields':>7} {'blk calls':>10} {'priv types':>11} {'API calls':>10}")
        for nfields, nblocked, npriv, name, ncalls in scored:
            if nfields == 0 and nblocked == 0 and npriv == 0:
                print(f"{name:34} {nfields:>7} {nblocked:>10} {npriv:>11} {ncalls:>10}")
        return

    i = args.index("--set")
    names = args[i + 1:]
    missing = [n for n in names if n not in methods]
    if missing:
        raise SystemExit(f"not methods in {path}: {missing}")

    read_fields, calls, blocked, priv = classify(
        names, methods, sites, lines, public, fields, non_public_types)
    span = sum((d["end"] or 0) - d["line"] + 1 for n in names for d in methods[n]["decls"])
    over = [n for n in names if len(methods[n]["decls"]) > 1]
    print(f"=== CANDIDATE: {len(names)} methods, {span} lines")
    if over:
        print(f"    ({len(over)} of them are overloaded names; all their bodies were measured)")
    print(f"\nfields read directly ({len(read_fields)}) -- must be 0 to move as a static helper:")
    for name, lineno in read_fields:
        print(f"     {name:30} declared line {lineno}")
    print(f"\nnon-public TYPES named ({len(priv)}) -- cannot be named from the new class:")
    for name, lineno, kind in priv:
        print(f"     {name:30} {kind}, declared line {lineno}")
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
