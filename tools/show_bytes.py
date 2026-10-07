"""Show the raw characters of the offending lines, written to a file (the console here is GBK and cannot print them)."""
import pathlib

OUT = pathlib.Path("tools/_bytes_report.txt")
lines_out = []

for name in ("tools/ship_1506.py", "tools/ship_pearl_charge_counter.py"):
    path = pathlib.Path(name)
    if not path.exists():
        lines_out.append(f"{name} MISSING")
        continue
    body = path.read_text(encoding="utf-8").splitlines()
    lines_out.append(f"== {name} ({len(body)} lines)")
    for index, line in enumerate(body, 1):
        stripped = line.strip()
        if not stripped.startswith(('"note"', '"source"')):
            continue
        # A JSON literal must be exactly one quoted string per line here: quote count 4 is `"key": "value"`.
        count = stripped.count('"')
        if count != 4:
            lines_out.append(f"  line {index} has {count} quotes: {stripped}")
            # Point at the offending characters, as code points, so nothing depends on console encoding.
            marks = "".join("^" if ch == '"' else " " for ch in stripped)
            lines_out.append(f"      {marks}")

OUT.write_text("\n".join(lines_out), encoding="utf-8")
print(f"written {OUT} with {len(lines_out)} line(s)")
