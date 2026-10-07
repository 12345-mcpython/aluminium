"""Replace straight `"` inside the Chinese notes with the project's 「」, which never collide with Python's delimiter."""
import ast
import pathlib
import re

PATH = pathlib.Path("tools/ship_1506_zone.py")
text = PATH.read_text(encoding="utf-8")

pattern = re.compile(r'("(?:note|source)": ")(.*)("[,]?)$')

fixed_lines = []
changed = 0
for line in text.splitlines():
    match = pattern.match(line.strip()) if line.strip().startswith(('"note"', '"source"')) else None
    if match and line.count('"') > 4:
        prefix, body, suffix = match.groups()
        # Every inner quote becomes a corner bracket pair, alternating open/close.
        pieces = body.split('"')
        rebuilt = pieces[0]
        for index, piece in enumerate(pieces[1:]):
            rebuilt += ("「" if index % 2 == 0 else "」") + piece
        indent = line[:len(line) - len(line.lstrip())]
        line = indent + prefix + rebuilt + suffix
        changed += 1
    fixed_lines.append(line)

PATH.write_text("\n".join(fixed_lines) + "\n", encoding="utf-8")
print(f"rewrote {changed} line(s)")

try:
    ast.parse(PATH.read_text(encoding="utf-8"))
    print("parses OK")
except SyntaxError as error:
    print(f"STILL BROKEN at line {error.lineno}: {error.msg}")
