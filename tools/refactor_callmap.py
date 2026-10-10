"""Map the call graph of one Java file, to find a safe seam for splitting it.

Why this exists: TriggerInterpreter is 6813 lines and 158 static helpers. Finding
"which helpers belong to validate() and which are shared with the apply path" by
reading is where a split goes wrong silently, so it is measured instead.

Two things are extracted with balanced-delimiter scans rather than regexes:

  * method bodies, keyed by name -- but only TOP-LEVEL declarations (indent 4),
    so nested lambdas and anonymous classes do not look like methods;
  * field/constant declarations (indent 4, ``static final``).

A call is counted only when the name is followed by ``(`` and is not preceded by
``.`` -- so ``battle.applyDamage(...)`` is not mistaken for a local call.

Usage:
    python tools/refactor_callmap.py <file.java> [rootMethod ...]

With roots, prints the transitive closure of each root and, for every callee in
that closure, the set of roots that reach it. That set is exactly what decides
whether a helper can move into a new class alone (one root) or is shared (two+).
"""

import re
import sys
from collections import Counter, defaultdict

# A declaration's return type must be a real type, not a keyword: without the
# uppercase/generic requirement, `if (x instanceof Y) {` parses as a method named
# `if` and quietly becomes a node in the graph. Every misspelt node then reports as
# "shared", which is the one answer that stops a split.
#
# The indent is ` {4}` and NOT `\s{4}`. `\s` also matches a newline, so with the
# MULTILINE anchor `^` it backtracks one line and captures indent == "\n   " for any
# declaration whose PRECEDING line ends in a non-space -- which is most of them. Those
# declarations then fail the `indent == "    "` test in scan() and vanish from the graph,
# which reads as "nothing calls this" rather than as a parse error. It hid the seven
# package-private helpers the validator extraction introduced.
DECL = re.compile(
    r"^(?P<indent> {4})(?:public|private|protected)?\s*(?:static\s+)?"
    r"(?:final\s+)?(?P<type>(?:<[^>]+>\s*)?(?:[A-Z]\w*(?:<[^>]*>)?(?:\[\])?|void|int|long|double|boolean|char|byte|short|float))\s+"
    r"(?P<name>[a-z]\w*)\s*\((?P<args>[^)]*)\)\s*\{",
    re.DOTALL | re.MULTILINE,
)
FIELD = re.compile(
    r"^ {4}(?:private|public|protected)?\s*static\s+(?:final\s+)?"
    r"[\w.<>\[\], ?]+\s+(?P<name>\w+)\s*(?:=|;)"
)
CALL = re.compile(r"(?<![\w.])(?P<name>\w+)\s*\(")
CONTROL = {"if", "for", "while", "switch", "catch", "return", "synchronized", "new"}

# A body span is found by brace counting, so a control statement misread as a
# declaration makes the braces balance at its own closing brace and the enclosing
# method's span ends early -- which silently TRUNCATES its call list and makes a
# shared helper look exclusive (the one wrong answer that breaks a split). These
# keywords are therefore rejected before the declaration pattern is tried.
STATEMENT_STARTS = re.compile(
    r"^\s{4}(?:if|for|while|switch|catch|else|do|try|return|throw|assert|synchronized|"
    r"final|var|new|case|default|break|continue|yield)\b"
)


def strip_noncode(text):
    """Blank out string literals, chars and comments so braces can be balanced."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"' and text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end + 3
            out.append(re.sub(r"[^\n]", " ", text[i:end]))
            i = end
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append(" " * (j + 1 - i))
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            out.append(" " * (j + 1 - i))
            i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i)
            j = n if j < 0 else j + 2
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def scan(path):
    raw = open(path, encoding="utf-8").read()
    clean = strip_noncode(raw)
    clean_lines = clean.split("\n")

    # Declarations wrap (a four-parameter signature is routinely split over two
    # lines), so a line-at-a-time match misses methods entirely -- and a method
    # missing from the graph reads as "nothing calls it", which is how a shared
    # helper gets mislabelled as exclusive. Match over the joined text instead and
    # convert offsets back to line numbers.
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

    methods, fields = {}, []
    for m in DECL.finditer(clean):
        if m.group("indent") != "    ":
            continue
        start = line_of(m.start())
        if STATEMENT_STARTS.match(clean_lines[start - 1]):
            continue
        # Brace-count from the declaration's OWN opening brace, not from the start of
        # its first line: a wrapped signature puts `) {` on a later line, and counting
        # from the first line then reads a zero balance and ends the body on the
        # declaration itself -- a one-line span that attributes no calls.
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
        name = m.group("name")
        # First declaration wins: an overload keeps the earliest body, which is
        # enough to attribute calls; overloads are reported separately.
        methods.setdefault(name, {"line": start, "end": end, "args": m.group("args")})

    for lineno, line in enumerate(clean_lines, start=1):
        fm = FIELD.match(line)
        if fm:
            fields.append((lineno, fm.group("name")))

    # Attribute each call site to the method whose span contains it.
    sites = defaultdict(set)
    owner_of = {}
    for name, info in methods.items():
        for k in range(info["line"] - 1, info["end"]):
            owner_of[k] = name
    for lineno, line in enumerate(clean_lines):
        owner = owner_of.get(lineno)
        if owner is None:
            continue
        for call in CALL.finditer(line):
            callee = call.group("name")
            if callee not in CONTROL:
                sites[owner].add(callee)
    return methods, fields, sites


def closure(roots, sites, methods):
    seen, queue = set(), list(roots)
    while queue:
        cur = queue.pop()
        if cur in seen or cur not in methods:
            continue
        seen.add(cur)
        queue.extend(sites.get(cur, ()))
    return seen


def main():
    path = sys.argv[1]
    roots = sys.argv[2:]
    methods, fields, sites = scan(path)
    print(f"{path}: {len(methods)} methods, {len(fields)} static fields")

    if not roots:
        by_size = sorted(methods.items(), key=lambda kv: (kv[1]["end"] or 0) - kv[1]["line"], reverse=True)
        print("\n-- largest methods --")
        for name, info in by_size[:15]:
            span = (info["end"] or 0) - info["line"]
            print(f"  {span:5} lines  {name}  (line {info['line']})")
        return

    print("\n-- reachable from: " + ", ".join(roots) + " --")
    reached = {r: closure([r], sites, methods) for r in roots}
    owners = defaultdict(set)
    for root, names in reached.items():
        for name in names:
            owners[name].add(root)

    for target in roots:
        only = sorted(n for n, o in owners.items() if o == {target} and n != target)
        print(f"\n[{target}] exclusive helpers: {len(only)}")
        for name in only:
            info = methods[name]
            print(f"    {name:34} line {info['line']:5} span {(info['end'] or 0) - info['line']:4}")

    shared = sorted(n for n, o in owners.items() if len(o) > 1)
    print(f"\nSHARED between roots: {len(shared)}")
    for name in shared:
        info = methods[name]
        print(f"    {name:34} line {info['line']:5} <- {','.join(sorted(owners[name]))}")


if __name__ == "__main__":
    main()
