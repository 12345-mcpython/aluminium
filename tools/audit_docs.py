"""Inventory the repository's documentation: size, role, and how much of it is dated history.

Line counts must be taken from the bytes. `Get-Content` reads a UTF-8 file as ANSI on Windows, and
for an LF-only file a newline byte is swallowed as a GBK trail byte, merging lines (GAPS_LOG.md
reported 12011 that way against a true 36358). So this reads with an explicit encoding.

Usage:
    python tools/audit_docs.py
"""

import glob
import os
import re
import subprocess


def tracked_docs():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout
    return [p for p in out.splitlines()
            if p.endswith((".md", ".txt", ".tsv")) and not p.startswith(("src/", "gradle/"))]


def main():
    paths = tracked_docs()
    print(f"{'bytes':>9} {'lines':>7} {'dated':>6} {'H2':>4}  file")
    total_bytes = total_lines = 0
    for path in paths:
        data = open(path, "rb").read()
        text = data.decode("utf-8", errors="replace")
        lines = text.split("\n")
        total_bytes += len(data)
        total_lines += len(lines)
        # "dated" = a line carrying a yyyy-mm-dd stamp: the part of a doc that is history.
        dated = sum(1 for l in lines if re.search(r"20\d\d-\d\d-\d\d", l))
        h2 = sum(1 for l in lines if l.startswith("## "))
        print(f"{len(data):>9} {len(lines):>7} {dated:>6} {h2:>4}  {path}")
    print(f"\nTOTAL {total_bytes} bytes / {total_lines} lines across {len(paths)} tracked docs")

    print("\n-- dated (history) content by file, as a share of the file --")
    rows = []
    for path in paths:
        data = open(path, "rb").read()
        text = data.decode("utf-8", errors="replace")
        lines = text.split("\n")
        dated = sum(1 for l in lines if re.search(r"20\d\d-\d\d-\d\d", l))
        if lines:
            rows.append((dated * 100.0 / len(lines), dated, len(lines), path))
    for share, dated, n, path in sorted(rows, reverse=True):
        print(f"  {share:5.1f}%  {dated:>5}/{n:<6} {path}")


if __name__ == "__main__":
    main()
