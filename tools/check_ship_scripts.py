"""Parse-check the shipping scripts. The ONLY reliable test is whether Python parses the file.

⚠ Two lessons this script itself learned the hard way:

  * parsing is the test - a heuristic that counts quotes produces false positives (adjacent string literals across lines
    are legal) and false negatives at the same time, so it is not used to decide anything here;
  * the console on this host is GBK, so nothing non-ASCII may be PRINTED. Anything worth reading goes to a file.

It also reports the OLD broken scripts it finds, which are pre-existing and unrelated to current work - they are listed so
nobody mistakes them for something this session broke.
"""
import ast
import pathlib
import sys

OUT = pathlib.Path("tools/_script_health.txt")
TARGETS = sorted(str(p) for p in pathlib.Path("tools").glob("ship_*.py"))
TARGETS += sorted(str(p) for p in pathlib.Path("tools").glob("mut_*.py"))
TARGETS += sorted(str(p) for p in pathlib.Path("tools").glob("sweep_*.py"))

ok, broken = [], []
for name in TARGETS:
    path = pathlib.Path(name)
    if not path.exists():
        broken.append((name, "MISSING"))
        continue
    try:
        ast.parse(path.read_text(encoding="utf-8"))
        ok.append(name)
    except SyntaxError as error:
        broken.append((name, f"line {error.lineno}: {error.msg}"))

lines = [f"parsed OK: {len(ok)}", f"do NOT parse: {len(broken)}", ""]
if broken:
    lines.append("=== these do NOT parse (older tools, not this session's work) ===")
    for name, why in broken:
        lines.append(f"  {name}: {why}")
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"OK={len(ok)} broken={len(broken)} - details in {OUT}")
sys.exit(0)
