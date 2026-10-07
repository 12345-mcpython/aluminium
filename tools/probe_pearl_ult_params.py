"""Verify Pearl's ULTIMATE advancement tiers against its own parameter row.

Her ultimate's text says the archetype is advanced 10%/15%/30% at 1/2/3 Elation allies (the corpus and the shipped
rules both say so). Its Lv10 parameter row is [3, 0.3, 20, 0.1, 0.15, 0.3, 60, 30], and the text references
#3 #4 #5 #6 #7 #8 #2 and #1 - so which columns are the three advancement tiers has to be read off the text, not
guessed. If the row says 30% at the first tier, then the shipped 0.1/0.15/0.3 is wrong three times over.

This prints the row, the resolved text, and every column next to its placeholder.
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_ult_params.txt")
ULT = 150303


def main():
    textmap = json.loads((TBGD / "TextMap" / "TextMapCHS.json").read_text(encoding="utf-8"))
    rows = json.loads((TBGD / "ExcelOutput" / "AvatarSkillConfig.json").read_text(encoding="utf-8"))
    mine = sorted([r for r in rows if r.get("SkillID") == ULT], key=lambda r: r["Level"])

    lines = []
    for row in mine:
        params = [p.get("Value") for p in (row.get("ParamList") or [])]
        text = textmap.get(str((row.get("SkillDesc") or {}).get("Hash")))
        lines.append(f"== {ULT} Lv{row['Level']} ==")
        for i, value in enumerate(params, start=1):
            lines.append(f"   #{i} = {value}")
        lines.append(f"   text: {text}")
        lines.append("")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))
    print("levels:", [r["Level"] for r in mine])


if __name__ == "__main__":
    main()
