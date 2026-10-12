"""Verify the lifetime extraction lost no text: every non-blank line of the old file still exists.

Byte-exactness is the guardrail REFACTOR.md asks for (a retyped javadoc is how "move a method"
silently becomes "edit the documentation"), and the demo diff cannot see a lost COMMENT. This
compares the pre-move file (read from git, as bytes -- `>` redirection writes UTF-16 on Windows
PowerShell and that has cost this project a round) against the concatenation of the package now.

Qualification of the moved helpers is normalised away, because that is the one intended edit.

Usage:
    python tools/verify_lifetime_extraction.py <rev>:<path> <glob-dir>
"""

import glob
import re
import subprocess
import sys


def read_from_git(rev_path):
    rev, path = rev_path.split(":", 1)
    return subprocess.run(["git", "show", f"{rev}:{path}"], capture_output=True, check=True).stdout.decode("utf-8")


def norm(line):
    line = re.sub(r"\bTriggerLifetimes\.", "", line)
    line = re.sub(r"\bTriggerInterpreter\.(unticked|eventBound|withLifetime|withTickOwner)",
                  r"\1", line)
    return re.sub(r"\s+", " ", line).strip()


def main():
    old = read_from_git(sys.argv[1])
    directory = sys.argv[2]
    new = "".join(open(p, encoding="utf-8").read()
                  for p in sorted(glob.glob(f"{directory}/*.java")))

    new_lines = {norm(l) for l in new.split("\n") if norm(l)}
    old_nonblank = [l for l in old.split("\n") if l.strip()]
    missing = [l for l in old_nonblank if norm(l) not in new_lines]

    print(f"old non-blank lines: {len(old_nonblank)}")
    print(f"lines not found in {directory} now: {len(missing)}")
    for line in missing[:20]:
        print(f"    {line.strip()[:110]}")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
