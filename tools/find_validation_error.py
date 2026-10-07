"""Find the sentence that names the validation failure, wherever it sits in the XML."""
import glob
import pathlib

OUT = pathlib.Path("tools/_err.txt")
NEEDLES = ("Invalid trigger", "IllegalArgument", "unknown", "Unknown", "neither", "not a", "op ")

found = []
for path in sorted(glob.glob("build/test-results/test/*.xml")):
    text = pathlib.Path(path).read_text(encoding="utf-8", errors="replace")
    for needle in NEEDLES:
        index = text.find(needle)
        if index >= 0:
            found.append(f"{pathlib.Path(path).name}: ...{text[max(0, index - 120):index + 260]}...")
            break
    if len(found) >= 3:
        break
OUT.write_text("\n\n".join(found) or "no needle found", encoding="utf-8")
print(f"written {OUT} with {len(found)} hit(s)")
