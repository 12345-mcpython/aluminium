"""Read the failure message out of the surefire XML, which is not well-formed enough for an XML parser."""
import glob
import pathlib
import re

OUT = pathlib.Path("tools/_err.txt")
messages = []
for path in glob.glob("build/test-results/test/*.xml"):
    text = pathlib.Path(path).read_text(encoding="utf-8", errors="replace")
    for match in re.finditer(r'message="([^"]{0,400})"', text):
        messages.append(f"{pathlib.Path(path).name}: {match.group(1)}")
OUT.write_text("\n".join(messages[:6]) or "no messages found", encoding="utf-8")
print(f"written {OUT} with {len(messages)} message(s)")
