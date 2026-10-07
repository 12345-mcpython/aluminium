"""What is 抵御值? Measure it before assuming it needs a new concept.

The open item says: "1 point of the counter = 200 抵御值, and Pearl can spend it to block 60% of the damage an ally
takes, cap 50. 抵御值 has only 4 TextMap entries and no table of its own; status 10015034 is the candidate."

Three things this checks, cheapest first:
  1. the four TextMap entries, so the term's own wording is on the record;
  2. whether the term appears anywhere in the data dump's field names (a property name, a status property, a
     Modifier) rather than only in prose - that is what decides whether it is a new concept or an existing one;
  3. whether any existing AttributeType already means it (the same question that answered EFFECT_RESISTANCE and
     DAMAGE_PENETRATION, both of which turned out to exist while a note claimed they did not).
"""
import json
import pathlib
import re

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_defence_value_probe.txt")
TERM = "抵御值"
# The status the open item names as the candidate for this mechanic.
STATUS = "10015034"
ATTRIBUTES = pathlib.Path("src/main/java/com/laosun/aluminium/enums/AttributeType.java")


def textmap(lines):
    path = TBGD / "TextMap" / "TextMapCHS.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    hits = [(k, v) for k, v in data.items() if TERM in str(v)]
    lines.append(f"== TextMapCHS entries containing {TERM}: {len(hits)} ==")
    for k, v in hits:
        lines.append(f"  key={k}: {v}")


def data_fields(lines):
    lines.append("")
    lines.append(f"== ExcelOutput files whose TEXT contains {TERM} ==")
    for path in sorted((TBGD / "ExcelOutput").glob("*.json")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if TERM in text:
            lines.append(f"  {path.name}")
    lines.append("")
    lines.append(f"== ExcelOutput files mentioning status {STATUS} ==")
    for path in sorted((TBGD / "ExcelOutput").glob("*.json")):
        if STATUS in path.read_text(encoding="utf-8", errors="replace"):
            lines.append(f"  {path.name}")


def status_row(lines):
    lines.append("")
    lines.append(f"== the rows for status {STATUS} ==")
    for path in sorted((TBGD / "ExcelOutput").glob("*.json")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if STATUS not in text:
            continue
        try:
            data = json.loads(text)
        except Exception as exc:                      # noqa: BLE001 - a probe reports, it does not raise
            lines.append(f"  {path.name}: unparseable ({exc})")
            continue
        rows = data if isinstance(data, list) else [data]
        for row in rows:
            blob = json.dumps(row, ensure_ascii=False)
            if STATUS in blob:
                lines.append(f"  {path.name}: {blob[:600]}")


def existing_attributes(lines):
    lines.append("")
    lines.append("== AttributeType names that could already be this (shield / absorb / block / resist) ==")
    text = ATTRIBUTES.read_text(encoding="utf-8")
    for match in re.finditer(r'@SerializedName\("([^"]+)"\)\s+([A-Z_0-9]+)', text):
        serialized, constant = match.group(1), match.group(2)
        if any(word in serialized.lower() for word in ("shield", "absorb", "block", "defence", "defense", "resist")):
            lines.append(f"  {constant} <- \"{serialized}\"")


def main():
    lines = []
    textmap(lines)
    data_fields(lines)
    status_row(lines)
    existing_attributes(lines)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
