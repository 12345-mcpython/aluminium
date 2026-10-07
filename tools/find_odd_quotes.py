"""Why does line 119 fail to parse? Print every line carrying an odd number of quotes before it.

An odd count means a literal was left open, which makes the NEXT string look like a syntax error - so the reported line is
a symptom, not the cause.
"""
import pathlib

PATH = pathlib.Path("tools/ship_1506.py")
OUT = pathlib.Path("tools/_odd_quotes.txt")

body = PATH.read_text(encoding="utf-8").splitlines()
lines = []
running = 0
for index, line in enumerate(body, 1):
    count = line.count('"')
    running += count
    if count % 2 == 1:
        lines.append(f"line {index}: {count} quotes (running {running})")
        lines.append(f"    {line.strip()[:160]}")
lines.append("")
lines.append(f"total lines {len(body)}; total quotes {running} (even = balanced)")
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"written {OUT}")
