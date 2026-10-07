"""Check the two 普攻 rows against the numbers the project ships for them.

The character page prints "对指定敌方单体造成等同于真珠 90% 防御力的冰属性伤害" for the basic (and 100% for the enhanced
form), while the parameter tables are [0.45 .. 1.26] and [0.5 .. 1.4]. 0.90 is row 6 and 1.26 is row 10, so one of the
two is a level-10 reading and the other is not - and which one decides whether the shipped rules are right.

Also prints which slot is which, so "the enhanced form is a separate skill" can be checked against the trigger keys.
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_basic_rows.txt")
# 150301 = basic (Skill01), 150308 = enhanced form A, 150310 = enhanced form B, 150320 = elation skill.
SKILLS = {150301: "普攻 Skill01", 150308: "强化普攻 forms (Skill11)", 150310: "强化普攻 (Skill12)", 150320: "欢愉技 Skill04"}


def main():
    rows = json.loads((TBGD / "ExcelOutput" / "AvatarSkillConfig.json").read_text(encoding="utf-8"))
    lines = []
    for skill_id, label in SKILLS.items():
        mine = sorted([r for r in rows if r.get("SkillID") == skill_id], key=lambda r: r["Level"])
        if not mine:
            lines.append(f"{skill_id} {label}: NO ROW")
            continue
        lines.append(f"== {skill_id} ({label}) ==")
        for row in mine:
            params = [p.get("Value") for p in (row.get("ParamList") or [])]
            lines.append(f"   Lv{row['Level']:2d}  {params}")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT)
    print("\n".join(lines[:30]))


if __name__ == "__main__":
    main()
