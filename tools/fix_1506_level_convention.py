"""1506's missing `level_convention`: her skills were all being read at Lv1.

The project pins a character's skill levels with one rule per file, because a skill STARTS at level 1 and nothing else
raises it: 63 of 94 characters carry `level_convention` with COMMON/SKILL/ULTRA/TALENT +9, which tops each slot up to the
last row of its own table (the clamp is the table's length, so +9 is "to the maximum" for a 10-row skill and leaves a
15-row skill at 10 - the two-step spellings in 1001/1002 show the pattern).

1506 had NO such rule, so every number of hers has been computed at Lv1: 战技 0.8 instead of 2.0, 强化普攻 1.2 instead of
3.36, 欢愉技·强化 0.45 instead of 1.125. That is a defect in the SHIPPED content, not in this batch.

⚠ Slots 8/10/12/18 (the enhanced basic and its 盲盒 sub-skills, 10 rows each) are NOT reachable: `RAISE_SKILL_LEVEL` takes
a `SkillType` name, and those four slots have no `SkillType` of their own. Registered rather than approximated.
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")

RULE = {
    "on": "BATTLE_START",
    "id": "p1506_level_convention",
    "do": [
        {"op": "RAISE_SKILL_LEVEL", "skill": "COMMON", "amount": 9},
        {"op": "RAISE_SKILL_LEVEL", "skill": "SKILL", "amount": 14},
        {"op": "RAISE_SKILL_LEVEL", "skill": "ULTRA", "amount": 14},
        {"op": "RAISE_SKILL_LEVEL", "skill": "TALENT", "amount": 14},
        {"op": "RAISE_SKILL_LEVEL", "skill": "ELATION_SKILL", "amount": 14},
        {"op": "RAISE_SKILL_LEVEL", "skill": "ELATION_EXTRA", "amount": 14},
    ],
    "source": "1506 银狼LV.999 技能等级口径：普攻最高 10 级（`skills.json` 槽1 有 10 行）、战技／终结技／天赋／"
              "欢愉技最高 15 级（各 15 行）；本项目一律取满级（与 1002／1004／1005 等 63 个角色的 `level_convention` 同一口径）。",
    "note": "⚠⚠ **这一条是补的，而它修的是一个比她其余缺口都严重的缺陷** ✓ —— **实测**：全库 **63/94** 个角色都带 "
            "`level_convention` ✓，而**她此前没有** ✓ ⇒ **她的技能等级一直是 1** ✓（技能**默认 1 级** ✓，没有别的规则会抬它 ✓）"
            "⇒ ⚠ **她的战技读 `0.8` 而不是 `2.0`** ✓、**强化普攻读 `1.2` 而不是 `3.36`** ✓、**欢愉技·强化读 `0.45` 而不是 `1.125`** ✓ "
            "—— **倍率、伤害、治疗全部按 Lv1 在算** ✓，而且**没有任何症状** ✓（判据此前只读状态与计数 ✓，不读倍率 ✓）。"
            "⚠ **上界不用写第二个 op** ✓：引擎把等级夹在该技能表自己的**行数**上 ✓（`CanHit.skillLevel` 的 `min(level, rows)` ✓）"
            "⇒ 给 15 行的技能写 `+9` 会停在 **10** ✓，所以这里**按各自行数写**（10 行写 9 ✓、15 行写 14 ✓）✓。"
            "⚠⚠ **槽 8／10／12／18 够不着** ✓（强化普攻与它的盲盒子技能 ✓，各 10 行 ✓）—— "
            "`RAISE_SKILL_LEVEL` 收的是 **`SkillType` 名字** ✓，而那四个槽**没有对应的 `SkillType`** ✓ ⇒ **照实登记** ✓"
            "（⚠ 这也是她星魂 3 里 `150608`／`150610`／`150612`／`150618` 四项**无处可去**的同一个原因 ✓）。",
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup")
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored")
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written")

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    before = len(data["rules"])
    if any(r["id"] == RULE["id"] for r in data["rules"]):
        print("already present")
        return 0
    data["rules"].append(RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == RULE["id"]), None)
    if landed is None or len(landed["do"]) != len(RULE["do"]):
        print("WRITE CHECK FAILED")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(check['rules'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
