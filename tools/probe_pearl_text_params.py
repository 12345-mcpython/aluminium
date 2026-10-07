"""Resolve Pearl's own text placeholders against her parameter rows, so the 抵御值 numbers can be read off the dump.

Why this is worth doing: her ability SCRIPT is missing, but two things are present - the parameter tables
(`AvatarSkillConfig`) and the TextMap entries those tables' `SkillDesc.Hash` point at. A sentence like
「每点【好活当赏】等同于 #5[i] 点抵御值」 therefore HAS a value if #5 is a column of the talent's own row.

The talent row 150304 is [0.5, 0.6, 50, 0.375, 200] at Lv1 and [0.5, 0.6, 50, 0.375, 200] at max, i.e. column 5 is
200 at every level - and the prose's other references (#1 the HP threshold, #2 the reduction, #4 the max reduction)
line up with columns 1, 2 and 4. This prints the resolved text so that reading is checked against the game's own
words rather than against my index arithmetic.
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_text_params.txt")
SKILL_IDS = [150301, 150302, 150303, 150304, 150307, 150308, 150310, 150320]


def main():
    textmap = json.loads((TBGD / "TextMap" / "TextMapCHS.json").read_text(encoding="utf-8"))
    rows = json.loads((TBGD / "ExcelOutput" / "AvatarSkillConfig.json").read_text(encoding="utf-8"))
    lines = []

    for skill_id in SKILL_IDS:
        mine = [r for r in rows if r.get("SkillID") == skill_id]
        if not mine:
            continue
        # Highest level row, and the level-1 row, so level dependence is visible.
        for want in (min(r["Level"] for r in mine), max(r["Level"] for r in mine)):
            row = next(r for r in mine if r["Level"] == want)
            desc = row.get("SkillDesc") or {}
            text = textmap.get(str(desc.get("Hash"))) if desc.get("Hash") else None
            params = [p.get("Value") for p in (row.get("ParamList") or [])]
            lines.append(f"== {skill_id} Lv{want}  params={params} ==")
            lines.append(f"   {text}")
            lines.append("")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
