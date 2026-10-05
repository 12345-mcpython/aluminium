"""Move GAPS.md's append-only per-round log out into GAPS_LOG.md (2026-10-02).

Measured before touching anything:
  * GAPS.md is 4,812,608 bytes / 37,771 lines;
  * only 17 of those lines precede the first `> **2026-...` block -- the file's own title and the start of section 一;
  * so ~37,754 lines are blocks, and the file has drifted from what its first line says it is: 「本文件是**当前**的缺口清单：只列**今天仍然受阻**的东西」.

Nothing is deleted: every block line goes to GAPS_LOG.md verbatim, in order. What stays in GAPS.md is every line that is NOT part of a block, so the structure the file is
supposed to be (title + the numbered sections) survives for the next step.

A block line is one that starts with `> ` -- measured on the samples: both the header and the continuations do.
"""
import io
import sys

SRC = "GAPS.md"
LOG = "GAPS_LOG.md"

lines = io.open(SRC, encoding="utf-8").read().split("\n")
structure, log = [], []
for ln in lines:
    if ln.startswith("> "):
        log.append(ln)
    else:
        structure.append(ln)

print("total lines      : %d" % len(lines))
print("log lines (>)    : %d" % len(log))
print("structure lines  : %d" % len(structure))
if len(log) < 10000:
    sys.exit("REFUSING: only %d log lines found -- the shape is not what was measured" % len(log))
if len(structure) < 500:
    sys.exit("REFUSING: only %d structure lines -- the split would leave nothing to read" % len(structure))

HEADER = """# 逐轮日志（从 `GAPS.md` 拆出，2026-10-02）

> 这些是**按轮追加**的记录块，原文一字未改。它们保留每一次尝试的读数、失败与回滚。
> **当前仍然受阻的东西**在 [`GAPS.md`](GAPS.md)；能力的语汇手册是 [`engine.md`](engine.md)；历史流水是 [`ROADMAP.md`](ROADMAP.md)。

"""

io.open(LOG, "w", encoding="utf-8", newline="\n").write(HEADER + "\n".join(log).rstrip() + "\n")
io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(structure).rstrip() + "\n")
print("wrote %s and rewrote %s" % (LOG, SRC))
