"""Corpus-wide: does any scanned declaration start on a line that is not code?

The defect the guard exists for is a span whose first line is comment text (a `/**` or a `* ...`
line), because every consumer slices the body from there. This walks the whole source tree and
reports every such declaration, so the guard is judged on the corpus rather than on the one file
that prompted it.

Usage:
    python tools/refactor_javadoc_starts.py [src/main/java]
"""

import glob
import importlib.util
import os
import sys

SPEC = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cm)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
    files = sorted(glob.glob(os.path.join(root, "**", "*.java"), recursive=True))
    offenders = []
    total_decls = 0
    for path in files:
        raw = open(path, encoding="utf-8").read()
        lines = raw.split("\n")
        methods, _fields, _sites = cm.scan(path)
        for name, info in methods.items():
            for d in info["decls"]:
                total_decls += 1
                start = d["line"]
                text = lines[start - 1].strip() if start - 1 < len(lines) else ""
                # A comment-only starting line is the failure. `*` covers javadoc middles,
                # `/**` the open, `*/` the close, `//` a line comment.
                if text.startswith(("*", "/**", "*/", "//")) or text == "":
                    offenders.append((path, name, start, text[:70]))
    print(f"scanned {len(files)} files, {total_decls} declarations")
    print(f"declarations starting on a comment or blank line: {len(offenders)}")
    for path, name, start, text in offenders[:40]:
        print(f"    {os.path.relpath(path, root):58} {name:28} line {start:5} | {text}")
    return 1 if offenders else 0


if __name__ == "__main__":
    sys.exit(main())
