"""Run the coverage test's OWN normalisation on the file's condition, so the expected value is not a guess."""
import json
import pathlib
import re

PATH = pathlib.Path("src/main/resources/characters/1506.json")
OUT = pathlib.Path("tools/_normalise_check.txt")


def normalise(text):
    matcher = re.compile(r"([<>]=?\s*)(\d+)(?:\.0)?$").search(text)
    return re.sub(r"([<>]=?\s*)(\d+)(?:\.0)?$", r"\1\2.0", text) if matcher else text


data = json.loads(PATH.read_text(encoding="utf-8"))
lines = []
for rule in data["rules"]:
    if rule["id"] != "p1506_skill_grants_laughs":
        continue
    lines.append(f"raw when: {rule['when']!r}")
    lines.append("normalised: " + repr([normalise(c) for c in rule["when"]]))
    raw = pathlib.Path(PATH).read_text(encoding="utf-8")
    lines.append("")
    lines.append("does the file TEXT contain 'from_skill_id == 2.0'? " + str("from_skill_id == 2.0" in raw))
    lines.append("does the file TEXT contain 'from_skill_id == 2\"'? " + str('from_skill_id == 2"' in raw))
    for index, line in enumerate(raw.splitlines(), 1):
        if "from_skill_id" in line:
            lines.append(f"  raw line {index}: {line.strip()}")
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"written {OUT}")
