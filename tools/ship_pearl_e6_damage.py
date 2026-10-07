"""Ship Eidolon 6's extra Elation damage - the same sentence shape as form A, at 240%.

E6 (rank 150306, param [0.2, 2.4]): "【深度学习】时，我方全体目标的全属性抗性穿透提高 20%（已出货）。真珠的强化普攻额外造成
240% 的冰属性欢愉伤害，该次欢愉伤害使用【美学底本】的属性值计算。"

So the second half is form A's shape at 2.4 instead of 0.6, carrying `min_eidolon: 6`, and it needs the SAME gate
correction learned this round: the marker 【美学底本】 is on the AIM while the rule's `actor` is Pearl, so the gate is
about HER (深度学习) and the magnitude is about the holder (holder_attr:美学底本:ATTACK).

Usage:
    python tools/ship_pearl_e6_damage.py
    python tools/ship_pearl_e6_damage.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "eidolon6_extra_elation_damage"

RULE = {
    "on": "DEALING_DAMAGE",
    "id": RULE_ID,
    "min_eidolon": 6,
    "when": [
        "actor == self",
        "actor has_state 深度学习",
        "from_skill_id == 8"
    ],
    "do": [
        {
            "op": "DAMAGE",
            "scale": "holder_attr:美学底本:ATTACK",
            "percent": 2.4,
            "element": "Ice",
            "damage_type": "ELATION",
            "target": "target"
        }
    ],
    "source": "1503 真珠 星魂 6 用一副躯壳，演算生命（rank 150306，Param = [0.2, 2.4]）: 「【深度学习】时…真珠的强化普攻额外造成 240% 的冰属性欢愉伤害，该次欢愉伤害使用【美学底本】的属性值计算」。⭐ 2.4 = 240% ✓（同行的 0.2 是已出货的全属性抗性穿透 ✓）。",
    "note": "⭐ 与 `enhanced_basic_form_a_extra_elation_damage` **同形** ✓（同一事件、同一门、同一 scale ✓），只差 `percent` 2.4 与 `min_eidolon: 6` ✓。⚠⚠ **门是照本轮的教训写的** ✓：门要说**真珠自己**满足的事实（`actor has_state 深度学习` ✓），**不能**写 `actor has_state 美学底本` ✗ —— 后者是**被指定的队友**身上的标记 ✓，而 `actor` 是造成伤害的真珠 ✓（实测：写错主语时条文**从不触发** ✓，伤害与另一形态完全相同 ✓）。⭐ 「【深度学习】时」这句的门**恰好就是** `actor has_state 深度学习` ✓，与原文一致 ✓。",
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
