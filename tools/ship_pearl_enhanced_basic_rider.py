"""Ship the enhanced basic's OWN Elation rider: 「持有【好活当赏】时，额外造成 15% 的冰属性欢愉伤害」.

This is the clause the character page made visible and the file was missing. It is a SECOND rider, not the one already
shipped: the 60% (and Eidolon 6's 240%) comes from the ULTIMATE's row ("攻击后，额外造成 #2% ..."), while this 15% comes
from the ENHANCED BASIC's own row (150308 #5, which is 0.15 at Lv10 and 0.2 at max) - and the two carry different
conditions, so both are real and both apply.

Where the number comes from, and why the level convention does not cover it: the ultimate's #2 is a `percent` in a rule
and the file pins it, but this one is a column of a SKILL ROW, so the skill's own level decides it. The file's level
convention sits her slots at Lv10, at which #5 = 0.15, which is the figure the page prints.

Shape notes:
  * `ADD_DAMAGE` rather than `DAMAGE`: 「额外造成」 on the enhanced basic reads as part of that attack's own instances,
    and the two sibling riders already shipped for the Elation SKILL are `ADD_DAMAGE` for the same reason. It also
    avoids a second settlement per hit on a three-hit attack without a decision about how many instances the rider is;
  * the magnitude is `elation_base` (the table the Elation skill's damage comes from) times 0.15, which is how the
    Elation skill's own payouts are written;
  * the gate is the sentence's own condition, `self_resource:好活当赏 >= 1`, plus the slot that carries the enhanced
    form - the sentence is about the ENHANCED basic, not the ordinary one.

Usage:
    python tools/ship_pearl_enhanced_basic_rider.py
    python tools/ship_pearl_enhanced_basic_rider.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "enhanced_basic_elation_rider_while_holding"

RULE = {
    "on": "DEALING_DAMAGE",
    "id": RULE_ID,
    "when": [
        "actor == self",
        "self_resource:好活当赏 >= 1",
        "from_skill_id == 8"
    ],
    "do": [
        {
            "op": "ADD_DAMAGE",
            "scale": "elation_base",
            "percent": 0.15,
            "element": "Ice",
            "damage_type": "ELATION"
        }
    ],
    "source": "1503 真珠 强化普攻 行笔，幻造星月 (150308): 「对敌方全体造成等同于真珠 #1[i]% 防御力的冰属性伤害，为我方全体目标回复…持有【好活当赏】时，额外造成 #5[i]% 的冰属性欢愉伤害」—— #5：Lv1 = 0.1、**Lv10 = 0.15**、Lv15 = 0.2（本文件已有等级口径把槽位钉在 Lv10 ✓）。",
    "note": "⭐⭐ **一笔被角色文档照出来的遗漏** ✓：它与已出货的 60%（星魂 6 是 240%）**不是同一笔** ✓ —— 那两笔出自**大招行**（「攻击后，额外造成 #2[i]%」✓），这一笔出自**强化普攻自己的行**（`150308` 的 `#5` ✓），而且**两句的条件不同** ✓（这笔是「持有【好活当赏】时」✓）⇒ **两笔都成立、都要写** ✓。⚠ 数值来源也不同：大招那个是规则里的 `percent` ✓（本文件钉死 ✓），这个是**技能行的列** ✓ ⇒ 由**技能等级**决定 ✓；等级口径把槽位放在 Lv10 ⇒ `#5 = 0.15` ✓，正是文档正文印的那个数 ✓。⭐ 用 `ADD_DAMAGE`（加进这一击的实例 ✓）而不是 `DAMAGE`：兄弟条款（欢愉技的四档 ✓）就是 `ADD_DAMAGE` ✓，且强化普攻是**三击全体** ✓ ⇒ 用 `DAMAGE` 会为每一击各开一笔独立实例 ✓，而原文说的是这一击「额外造成」✓。⭐ 量的来源 `elation_base` ✓ 与欢愉技同源 ✓（等级表查表 ✓，不是属性 ✓）。",
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    if any(rule["id"] == RULE_ID for rule in data["rules"]):
        print("already present:", RULE_ID)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    data["rules"].append(RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == RULE_ID), None)
    if landed is None:
        print("WRITE DID NOT LAND - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(check['rules'])}")
    print("  when:", landed["when"])
    print("  do  :", json.dumps(landed["do"][0], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
