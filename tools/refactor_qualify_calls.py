"""Qualify bare calls to helpers that live in another class.

Used when a moved declaration still calls a handful of helpers that stayed behind: those
become `Owner.name(...)`. Only call sites are rewritten -- a name followed by `(` and not
already preceded by `.` -- so a field read, a string literal or a javadoc mention is left
alone.

The owner's own name is skipped, so `TriggerInterpreter.normalizeOp(` is not rewritten to
`TriggerInterpreter.TriggerInterpreter.normalizeOp(`.

Usage:
    python tools/refactor_qualify_calls.py <file.java> <Owner> <name> [name ...]
"""

import re
import sys


def main():
    path, owner = sys.argv[1], sys.argv[2]
    names = sys.argv[3:]
    lines = open(path, encoding="utf-8").read().split("\n")

    total = 0
    for name in names:
        pattern = re.compile(r"(?<![\w.])" + re.escape(name) + r"\s*\(")
        for i, line in enumerate(lines):
            stripped = line.lstrip()
            # Skip comment lines: a mention there is prose, and rewriting it would change
            # documentation rather than code.
            if stripped.startswith(("//", "*", "/*")):
                continue
            new, n = pattern.subn(f"{owner}.{name}(", line)
            if n:
                lines[i] = new
                total += n
                print(f"  line {i + 1:5}: {name} -> {owner}.{name}  ({n})")

    open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
    print(f"qualified {total} call site(s) in {path}")


if __name__ == "__main__":
    main()
