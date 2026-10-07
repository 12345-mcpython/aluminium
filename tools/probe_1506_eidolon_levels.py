"""Map 1506's E3/E5 skill ids to slots, and find how existing content raises a slot's level.

`eidolons.json` states `skill_add_level` as {skillId: +N}. The ids are the per-character skill ids (150602 …), so the slot
vocabulary they correspond to has to be established - and 欢愉技 (ELATION_SKILL) is the one slot that is NOT a plain
SkillType in the data.
"""
import json
import pathlib

OUT = pathlib.Path("tools/_1506_eidolon_levels.txt")
DATA = pathlib.Path("src/main/resources/data")
CHARS = pathlib.Path("src/main/resources/characters")


def main():
    eid = json.loads((DATA / "eidolons.json").read_text(encoding="utf-8"))["1506"]
    skills = json.loads((DATA / "skills.json").read_text(encoding="utf-8"))["1506"]
    by_id = {row.get("skill_id") or row.get("id"): slot for slot, row in skills.items()}

    lines = ["== 1506 skills: slot -> id/name/type =="]
    for slot, row in sorted(skills.items(), key=lambda kv: int(kv[0])):
        ident = row.get("skill_id") or row.get("id")
        lines.append(f"   slot {slot:>2} | id {ident} | {row.get('attack_type')} | "
                     f"{json.dumps(row.get('name'), ensure_ascii=False)}")

    for rank in ("3", "5"):
        row = eid.get(rank)
        adds = row.get("skill_add_level") or {}
        lines.append("")
        lines.append(f"== E{rank} skill_add_level ==")
        for skill_id, plus in adds.items():
            slot = by_id.get(int(skill_id)) if skill_id.isdigit() else None
            lines.append(f"   {skill_id} +{plus} -> slot {slot}")

    lines.append("")
    lines.append("== how shipped content raises a SLOT level (RAISE_SKILL_LEVEL rows) ==")
    seen = 0
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for rule in (data["rules"] if isinstance(data, dict) else data):
            for effect in rule.get("do", []):
                if effect.get("op") == "RAISE_SKILL_LEVEL":
                    lines.append(f"   {path.stem} {rule.get('id', rule.get('on'))}: "
                                 f"skill={effect.get('skill')} +{effect.get('amount')}")
                    seen += 1
                    if seen >= 14:
                        break
            if seen >= 14:
                break
        if seen >= 14:
            break
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
