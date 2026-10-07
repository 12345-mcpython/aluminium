"""First failure message from each failing suite, written to a file (the console cannot print non-ASCII here)."""
import glob
import pathlib
import re

OUT = pathlib.Path("tools/_err.txt")
lines = []
for path in sorted(glob.glob("build/test-results/test/*.xml")):
    text = pathlib.Path(path).read_text(encoding="utf-8", errors="replace")
    if "<failure" not in text:
        continue
    name = pathlib.Path(path).name
    messages = re.findall(r'<failure message="([^"]{0,300})"', text) or re.findall(r'message="([^"]{0,300})"', text)
    lines.append(f"{name}: {messages[0] if messages else 'no message'}")
    if len(lines) >= 5:
        break
OUT.write_text("\n".join(lines) or "no failing suites", encoding="utf-8")
print(f"written {OUT} with {len(lines)} line(s)")
