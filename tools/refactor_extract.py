"""Move whole declarations between Java files, byte-exactly.

Why not by hand: this project has already paid for the lesson (GAPS.md, "逐字节搬运,
不要手打"). Thirty lines of javadoc retyped is how "move a method" silently becomes
"edit the documentation". So every move here is a slice of the original text.

What counts as a declaration: a method (4-space indent, brace-balanced) or a static
field (4-space indent). Each one's leading javadoc -- the contiguous ``/** ... */``
block above it, after any blank lines -- travels with it, because a javadoc left
behind is exactly the "untethered block" defect already logged against this file.

A method keeps its ``public``/``private`` modifiers only when told to; for a move
into a new class the caller passes ``--visibility package`` to strip them, which is
what lets the new class and the original share helpers without a public API.

Usage:
    python tools/refactor_extract.py list <file.java>
    python tools/refactor_extract.py move <src.java> <dst.java> <name> [name ...]
                                  [--visibility package] [--keep-header]
    python tools/refactor_extract.py cut  <src.java> <name> [name ...]   # remove, no dst
"""

import importlib.util
import re
import sys

spec = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)

# Fields are taken from refactor_callmap.scan, which already finds them (including ones split
# over several lines). Re-scanning here was the bug: a local pattern that skipped any
# line containing "(" -- meant to avoid mistaking a call for a declaration -- also
# skipped every constant whose initialiser is `Set.of(...)`, and those are exactly the
# vocabulary this tool exists to move.
FIELD = re.compile(r"^ {1,6}(?:(?:public|private|protected)\s+)?")


def declarations(path):
    """name -> {kind, first, last} where first includes the leading javadoc."""
    methods, fields, _sites = cm.scan(path)
    lines = open(path, encoding="utf-8").read().split("\n")

    found = {}
    for name, info in methods.items():
        if info["end"] is None:
            raise SystemExit(f"unterminated body for {name} in {path}")
        found[name] = {"kind": "method", "start": info["line"], "end": info["end"]}

    for lineno, name in fields:
        if name in found:
            continue
        # refactor_callmap reports the line the NAME is declared on, but a field's initialiser
        # routinely runs on for dozens of lines (`Set.of(` with the whole vocabulary in
        # it). Slicing to that line moves the declaration WITHOUT its values and leaves
        # the values behind -- which does not fail to compile, it silently produces an
        # empty set. Scan forward to the statement's own terminator instead.
        last = lineno
        while last <= len(lines) and not lines[last - 1].rstrip().endswith((";", "}")):
            last += 1
        last = min(last, len(lines))
        # Walk up to the line where the declaration really begins (its modifiers).
        # The walk must NOT climb into the block comment above: a `* ...` javadoc line
        # can look like a continuation, and then the "declaration" starts inside the
        # comment -- which both corrupts the moved text and defeats the javadoc
        # attachment below. Blank lines likewise end the walk.
        first = lineno
        while first - 1 >= 1:
            above = lines[first - 2]
            stripped = above.strip()
            # A comment line ends the walk at every one of its shapes: `/**` OPENS a block
            # and looks exactly like a declaration start (`indent` + no `;`), so missing
            # this one case walks the "declaration" up into the javadoc above it and moves
            # the wrong text. `*` covers the middles, `*/` the close, `//` a line comment.
            if (stripped == "" or stripped.startswith("/") or stripped.startswith("*")
                    or stripped.endswith("*/")):
                break
            if FIELD.match(above) and not above.rstrip().endswith(";"):
                first -= 1
            else:
                break
        found[name] = {"kind": "field", "start": first, "end": last}

    # Attach the leading javadoc: walk up over blanks, then over a `*/`-terminated block.
    for name, info in found.items():
        i = info["start"] - 1
        while i - 1 >= 0 and lines[i - 1].strip() == "":
            i -= 1
        if i - 1 >= 0 and lines[i - 1].strip().endswith("*/"):
            j = i - 1
            while j > 0 and not lines[j - 1].strip().startswith("/*"):
                j -= 1
            if lines[j - 1].strip().startswith("/*"):
                info["first"] = j
                continue
        info["first"] = info["start"]
    return found, lines


def block(lines, info):
    return lines[info["first"] - 1 : info["end"]]


def strip_visibility(text_lines):
    """Drop `public`/`private`/`protected` so the moved member is package-private.

    Applies to FIELDS as well as methods: the vocabulary constants lived in the
    interpreter and are read from both classes, so a moved `private static final Set`
    fails to compile at every call site. The modifier sits on the declaration's first
    line, which for a wrapped field is the line naming the type, not the value.
    """
    out = []
    for line in text_lines:
        out.append(re.sub(
            r"^(\s+)(?:public|private|protected)\s+(static\s|final\s|[\w.<>])",
            r"\1\2",
            line,
        ))
    return out


def move_lines(lines, spans):
    """Everything except the given (first, last) 1-based spans, keeping order."""
    drop = set()
    for first, last in spans:
        drop.update(range(first, last + 1))
    return [ln for i, ln in enumerate(lines, start=1) if i not in drop]


def parse_names(argv):
    names, flags = [], []
    for arg in argv:
        (flags if arg.startswith("--") or arg in ("package",) else names).append(arg)
    return names, flags


def main():
    mode = sys.argv[1]
    if mode == "list":
        found, lines = declarations(sys.argv[2])
        for name, info in sorted(found.items(), key=lambda kv: kv[1]["start"]):
            span = info["end"] - info["first"] + 1
            print(f"{name:34} {info['kind']:6} lines {info['first']:5}-{info['end']:<5} ({span} incl. doc)")
        return

    if mode == "show":
        # Print the exact slice a move would take, so a wrong javadoc attachment is
        # caught by reading rather than by a corrupted file.
        found, lines = declarations(sys.argv[2])
        for name in sys.argv[3:]:
            info = found.get(name)
            if info is None:
                print(f"!! {name} not found")
                continue
            print(f"===== {name}  lines {info['first']}-{info['end']} =====")
            for i in range(info["first"], info["end"] + 1):
                print(f"{i:6} |{lines[i - 1]}|")
        return

    src, dst = sys.argv[2], sys.argv[3] if mode == "move" else None
    rest = sys.argv[4:] if mode == "move" else sys.argv[3:]
    names, flags = parse_names(rest)
    package_visibility = "--visibility" in flags

    found, lines = declarations(src)
    missing = [n for n in names if n not in found]
    if missing:
        raise SystemExit(f"not found in {src}: {missing} (refusing to write a half-move)")

    picked = [found[n] for n in names]
    spans = [(i["first"], i["end"]) for i in picked]

    moved = []
    for info in picked:
        body = block(lines, info)
        if package_visibility:
            body = strip_visibility(body)
        moved.append("\n".join(body))

    if dst:
        # Insert INSIDE the destination's top-level type, i.e. before its final closing
        # brace. Appending after it produces top-level field declarations, and javac
        # answers that with "compact source file should not have package declaration" --
        # an error naming the PACKAGE line while the real fault is 200 lines below it.
        existing = ""
        if __import__("os").path.exists(dst):
            existing = open(dst, encoding="utf-8").read()
        payload = "\n\n".join(moved)
        if existing.strip():
            body = existing.rstrip("\n").split("\n")
            close = None
            for i in range(len(body) - 1, -1, -1):
                if body[i].strip() == "}":
                    close = i
                    break
            if close is None:
                raise SystemExit(
                    f"{dst} has no closing brace to insert before (refusing to append at top level)")
            text = "\n".join(body[:close]) + "\n\n" + payload + "\n" + "\n".join(body[close:]) + "\n"
        else:
            text = payload + "\n"
        open(dst, "w", encoding="utf-8", newline="\n").write(text)

    kept = move_lines(lines, spans)
    open(src, "w", encoding="utf-8", newline="\n").write("\n".join(kept))

    total = sum(i["end"] - i["first"] + 1 for i in picked)
    print(f"moved {len(picked)} declaration(s), {total} lines out of {src}")
    for info, name in zip(picked, names):
        print(f"   {name:34} lines {info['first']}-{info['end']}")


if __name__ == "__main__":
    main()
