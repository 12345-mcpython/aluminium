"""Rename the numbered test classes, and update every reference to them (2026-10-02).

DRY by default; pass --apply. Run this only when no other writer is in the tree.

Driven by tools/_rename_map2.txt (old -> new, produced by build_rename_map2.py from tbgd + TextMapEN). Two moves, in this order:
  1. `git mv` each file to its new class name inside the same directory (packages are a later, separate step);
  2. replace the bare class name everywhere it is mentioned -- other test sources, the docs that cite tests as evidence (GAPS.md, engine.md, ROADMAP.md, GAPS_LOG.md, EXPRESSION.md, HANDOFF.md),
     the production-code comments that name them, and the one-shot scripts under tools/.

Refusals rather than guesses: it stops if an old name has no file, if a new name already exists, or if a proposed rename would collide with an existing class.
"""
import io
import os
import re
import subprocess
import sys

APPLY = "--apply" in sys.argv
MAP = "tools/_rename_map2.txt"
KEEP = {"March7thHuntTest", "March7thKitTest", "March7thLevelTest"}      # the 7 is part of "March 7th", not an id

renames = {}
for ln in io.open(MAP, encoding="utf-8").read().split("\n"):
    m = re.match(r"^\s+(\S+)\s+\S+\s+\S*\s*-> (\S+)$", ln)
    if m:
        renames[m.group(1)] = m.group(2)

tracked = subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
paths = {os.path.basename(f)[:-5]: f for f in tracked if f.startswith("src/test/java/") and f.endswith(".java")}

problems = []
for old, new in renames.items():
    if old in KEEP:
        problems.append("%s is marked keep" % old)
    if old not in paths:
        problems.append("%s: no file" % old)
    if new in paths:
        problems.append("%s -> %s: target name already exists" % (old, new))
seen = {}
for old, new in renames.items():
    seen.setdefault(new, []).append(old)
for new, olds in seen.items():
    if len(olds) > 1:
        problems.append("%s is claimed by %s" % (new, ", ".join(olds)))
if problems:
    print("REFUSING (%d problems):" % len(problems))
    for p in problems[:20]:
        print("  " + p)
    sys.exit(1)

print("renames to apply: %d" % len(renames))
if not APPLY:
    for old, new in list(renames.items())[:12]:
        print("  %s -> %s" % (old, new))
    print("  ... (%d more)" % max(0, len(renames) - 12))
    print("DRY RUN -- nothing written")
    sys.exit(0)

# 1. move the files
for old, new in renames.items():
    src = paths[old]
    dst = os.path.join(os.path.dirname(src), new + ".java")
    subprocess.run(["git", "mv", src, dst], check=True)
print("moved %d files" % len(renames))

# 2. rewrite every mention
targets = [f for f in tracked if f.endswith((".java", ".md", ".py"))]
changed = 0
mentions = 0
for f in targets:
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    original = text
    for old, new in renames.items():
        text, n = re.subn(r"\b" + re.escape(old) + r"\b", new, text)
        mentions += n
    if text != original:
        io.open(f, "w", encoding="utf-8", newline="").write(text)
        changed += 1
print("updated %d mentions across %d files" % (mentions, changed))
