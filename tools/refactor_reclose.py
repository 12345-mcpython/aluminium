"""Move top-level declarations into the class that should have contained them.

The companion to a botched extraction: when `refactor_extract.py` appended moved declarations
after the destination class's closing brace instead of before it, they end up at the top
level and javac rejects the file with an error about the package line. This repairs that
shape -- everything after the first top-level `}` becomes the tail of that class body.

It refuses unless the file really has that shape: a top-level `}` with non-blank content
after it, and every one of those trailing declarations indented. A file whose braces are
balanced is left alone, so this cannot quietly rewrite a healthy file.

Usage:
    python tools/refactor_reclose.py <file.java>
"""

import io
import sys


def main():
    path = sys.argv[1]
    lines = io.open(path, encoding="utf-8").read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()

    close = None
    for i, line in enumerate(lines):
        if line == "}":
            close = i
            break
    if close is None:
        raise SystemExit(f"{path}: no top-level closing brace; nothing to repair")

    tail = lines[close + 1:]
    if not [l for l in tail if l.strip()]:
        print(f"{path}: already well-formed (nothing after the closing brace)")
        return

    body = [l for l in tail if l.strip()]
    if not all(l.startswith(" ") for l in body):
        raise SystemExit(f"{path}: content after the closing brace is not indented; refusing")

    repaired = lines[:close] + body + ["}"]
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(repaired) + "\n")
    print(f"{path}: moved {len(body)} line(s) inside the class (closing brace now last)")


if __name__ == "__main__":
    main()
