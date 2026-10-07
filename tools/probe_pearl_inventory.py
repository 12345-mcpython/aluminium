"""Inventory Pearl's ability rows, so "what is still unwritten" is grounded in the data she does have.

The dump has her ExcelOutput rows (skill parameter tables, statuses, trace rows) even though it lacks her ability
SCRIPT. That distinction decides which gaps are "needs a number" (the rows have it) and which are "needs the script".
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_inventory.txt")
# SkillList from her AvatarConfig row.
SKILL_IDS = [150301, 150302, 150303, 150304, 150306, 150307, 150308, 150310, 150320]


def main():
    lines = []
    rows = json.loads((TBGD / "ExcelOutput" / "AvatarSkillConfig.json").read_text(encoding="utf-8"))
    lines.append("== her ability rows (SkillTriggerKey = which sentence) ==")
    for skill_id in SKILL_IDS:
        mine = [r for r in rows if r.get("SkillID") == skill_id]
        if not mine:
            lines.append(f"  {skill_id}: NO ROW")
            continue
        first = mine[0]
        levels = sorted(r.get("Level") for r in mine)
        lines.append(f"  {skill_id}  trigger={first.get('SkillTriggerKey')}  max={first.get('MaxLevel')}"
                     f"  levels={levels[0]}..{levels[-1]}  paramList={'param_list' in first}")

    lines.append("")
    lines.append("== the parameter table for each (level 1 and max) ==")
    for skill_id in SKILL_IDS:
        mine = [r for r in rows if r.get("SkillID") == skill_id]
        if not mine:
            continue
        for want in (min(r.get("Level") for r in mine), max(r.get("Level") for r in mine)):
            row = next(r for r in mine if r.get("Level") == want)
            params = row.get("ParamList")
            lines.append(f"  {skill_id} Lv{want}: {json.dumps(params, ensure_ascii=False)[:300]}")

    lines.append("")
    lines.append("== statuses she installs (AvatarStatusConfig) ==")
    statuses = json.loads((TBGD / "ExcelOutput" / "AvatarStatusConfig.json").read_text(encoding="utf-8"))
    for row in statuses:
        blob = json.dumps(row, ensure_ascii=False)
        if "Pearl" in blob:
            lines.append("  " + blob[:300])

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
