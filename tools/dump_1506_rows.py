"""Dump the rows of the RULES list around the reported failure, and the ast.parse error with true line numbers."""
import ast
import pathlib

PATH = pathlib.Path("tools/ship_1506.py")
OUT = pathlib.Path("tools/_dump_1506.txt")

body = PATH.read_text(encoding="utf-8").splitlines()
lines = []
try:
    ast.parse(PATH.read_text(encoding="utf-8"))
    lines.append("ast.parse OK")
except SyntaxError as error:
    lines.append(f"ast.parse FAILS: line {error.lineno} col {error.offset}: {error.msg}")

lines.append("")
lines.append("--- raw lines 100..130 ---")
for number in range(100, min(len(body), 130) + 1):
    lines.append(f"{number:4d}|{body[number - 1]}")

OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"written {OUT}")
