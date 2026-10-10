"""Bisect a Java file whose compilation fails, to find the text that causes it.

Motivation: TriggerVocabulary.java failed with "compact source file should not have
package declaration" -- an error that names the PACKAGE line while being caused by
something further down, so reading the top of the file finds nothing. This locates the
offending region by prefix truncation (smallest failing prefix) and then, if asked, by
line deletion inside that region.

Usage:
    python tools/refactor_bisect.py <file.java> <javac-path> [workdir]
"""

import io
import os
import subprocess
import sys

TEMPLATE_ENV = os.environ.copy()


def compiles(text, javac, workdir):
    """Write the text and compile it; return True when javac accepts it."""
    path = os.path.join(workdir, "Bisect.java")
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    proc = subprocess.run(
        [javac, "-J-Duser.language=en", "-nowarn", "-d", os.path.join(workdir, "out"), path],
        capture_output=True, text=True, encoding="utf-8", errors="replace", env=TEMPLATE_ENV,
    )
    return proc.returncode == 0, (proc.stderr or "") + (proc.stdout or "")


def main():
    src, javac = sys.argv[1], sys.argv[2]
    workdir = sys.argv[3] if len(sys.argv) > 3 else os.path.dirname(os.path.abspath(src))
    os.makedirs(os.path.join(workdir, "out"), exist_ok=True)

    text = io.open(src, encoding="utf-8").read()
    lines = text.split("\n")

    ok, err = compiles(text, javac, workdir)
    print(f"whole file compiles: {ok}")
    if ok:
        return
    print("first error line:", err.strip().split("\n")[0] if err.strip() else "(none)")

    # Largest prefix that still compiles: everything after it is implicated.
    lo, hi = 0, len(lines)
    while lo < hi:
        mid = (lo + hi + 1) // 2
        good, _ = compiles("\n".join(lines[:mid]), javac, workdir)
        if good:
            lo = mid
        else:
            hi = mid - 1
    print(f"largest compiling prefix: {lo} lines")
    print("the first offending line is around:")
    for i in range(max(0, lo - 3), min(len(lines), lo + 4)):
        print(f"  {i + 1:5} |{lines[i]}|")


if __name__ == "__main__":
    main()
