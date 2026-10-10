"""Repair the whitespace a declaration removal leaves, and qualify moved constants.

Two jobs, both purely textual, run over one file:

  1. ``--collapse-blanks``: three or more consecutive blank lines become one. A move
     leaves the gaps where declarations used to be (thirteen blank lines at one seam in
     TriggerInterpreter); the deletion is right, the whitespace is debris. Only blank
     lines are touched, so no code or comment can be altered by this.

  2. ``--qualify``: rewrite unqualified references to named constants as
     ``<Owner>.<NAME>``, skipping comment lines and lines that declare the name. This is
     what makes moved constants compile at their new home -- and doing it by script
     rather than by hand is deliberate: this file has 60+ references and a missed one is
     a compile error at best.

Usage:
    python tools/refactor_tidy.py <file.java> [--collapse-blanks] [--qualify Owner NAME ...]
"""

import re
import sys


def is_comment(line):
    s = line.strip()
    return s.startswith("//") or s.startswith("*") or s.startswith("/*") or s.endswith("*/")


def declares(line, name):
    """True when this line declares `name` (so it must not be qualified)."""
    s = line.strip()
    if not re.match(r"^(?:(?:public|private|protected|static|final|transient|volatile)\s+)*"
                    r"[\w.<>\[\], ?]+\s+" + re.escape(name) + r"\s*(?:=|;|\()", s):
        return False
    return True


def collapse_blanks(lines):
    out, run = [], 0
    for line in lines:
        if line.strip() == "":
            run += 1
            if run == 1:
                out.append("")
        else:
            run = 0
            out.append(line)
    # A single trailing newline, and none leading.
    while out and out[0] == "":
        out.pop(0)
    while out and out[-1] == "":
        out.pop()
    return out


def qualify(lines, owner, names):
    changed = 0
    out = []
    for line in lines:
        if is_comment(line):
            out.append(line)
            continue
        new = line
        for name in names:
            if declares(new, name):
                continue
            pattern = re.compile(r"(?<![\w.])" + re.escape(name) + r"(?![\w])")
            new, n = pattern.subn(f"{owner}.{name}", new)
            changed += n
        out.append(new)
    return out, changed


def main():
    path = sys.argv[1]
    args = sys.argv[2:]
    lines = open(path, encoding="utf-8").read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()

    if "--collapse-blanks" in args:
        before = len(lines)
        lines = collapse_blanks(lines)
        print(f"collapse-blanks: {before} -> {len(lines)} lines")

    if "--qualify" in args:
        i = args.index("--qualify")
        rest = args[i + 1:]
        owner, names = rest[0], rest[1:]
        lines, changed = qualify(lines, owner, names)
        print(f"qualify: {changed} reference(s) -> {owner}.<name>")

    open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
