"""Ship both clauses that the archetype-attribute answer unblocks.

Given: "【美学底本】的通用伤害计算基准用【美学底本】的攻击力" - i.e. the extra damage reads the ARCHETYPE's ATTACK,
which is exactly what `holder_attr:美学底本:ATTACK` reads (the engine-side spelling added earlier this session).

  * Form A (attribute 1503101's sentence in the ultimate): after the enhanced basic, deal an extra 60% Ice Elation
    damage computed from the archetype's ATTACK. The 60% is the ultimate row's #2 at Lv10 (1.4/0.2... measured: #2 =
    0.6 at Lv10, 0.75 at max, 0.3 at Lv1), and the level convention this file already ships pins her at Lv10.
  * Eidolon 6: the same shape at 240% (`RankAbility` param [0.2, 2.4] -> 2.4).

Both hang on DEALING_DAMAGE, gated so they do not react to their own instance:
  * `actor == self` - her own attack;
  * `actor has_state 美学底本` - the carrier is the archetype (the rider is conditional on the archetype being real);
  * `from_skill_id == 8` - the enhanced basic form that carries the extra damage (form B, slot 9, has none).
⚠ The third gate is also what stops the recursion: the engine's guard "a trigger table is probably reacting to its own
effect" is what caught the first attempt at the Elation payout, and a rule whose own DAMAGE re-enters DEALING_DAMAGE
must fail one of its own conditions on that second pass.

Usage:
    python tools/ship_pearl_archetype_attack.py
    python tools/ship_pearl_archetype_attack.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
FORM_A = "enhanced_basic_form_a_extra_elation_damage"
E6 = "eidolon6_extra_elation_damage"

FORM_A_RULE = {
    "on": "DEALING_DAMAGE",
    "id": FORM_A,
    "when": [
        "actor == self",
        "actor has_state 美学底本",
        "from_skill_id == 8"
    ],
    "do": [
        {
            "op": "DAMAGE",
            "scale": "holder_attr:美学底本:ATTACK",
            "percent": 0.6,
            "element": "Ice",
            "damage_type": "ELATION",
            "target": "target"
        }
    ],
    "source": "1503 真珠 终结技 鉴映灵魂的底色 (150303) 的强化普攻端: 「若【美学底本】为「欢愉」命途，则强化为【行笔，幻造星月】，且攻击后，额外造成 #2[i]% 的冰属性欢愉伤害，该次欢愉伤害使用【美学底本】的属性值计算」（#2：Lv1 = 0.3、Lv10 = 0.6、Lv15 = 0.75；本文件已有等级口径把四个槽位钉在 Lv10）。⭐ 「使用【美学底本】的属性值计算」= **【美学底本】的攻击力**（用户提供的裁决 ✓），写法是 `holder_attr:美学底本:ATTACK` ✓ —— 它读**状态持有者自己**的属性 ✓，与 `holder_of:` 选择器共用同一处查找 ✓。",
    "note": "⭐⭐ **本条同时用上了本会话新增的两件引擎能力**：`holder_attr:<状态>:<属性>` ✓（读状态持有者的属性 ✓）与 `ADD_DAMAGE` 能带 `damage_type` ✓。⚠ 但它用的是 **`DAMAGE`** 而不是 `ADD_DAMAGE`：原文说「额外**造成**」= **一笔独立实例** ✓（`ADD_DAMAGE` 是「把量加进正在结算的那一笔」✓，语义不同 ✓）；代价是它自己也会发 `DEALING_DAMAGE` ✓ ⇒ **靠门把它自己挡在门外** ✓：`from_skill_id == 8` 在第二趟（这一笔自己产生的实例）不再成立 ✓（⚠ **待判据确认** ✓ —— 若引擎给规则驱动的实例也带上 8，递归守卫会喊出来 ✓）。",
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
    existing = {rule["id"] for rule in data["rules"]}
    clash = [rid for rid in (FORM_A, E6) if rid in existing]
    if clash:
        print("already present:", clash)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    data["rules"].append(FORM_A_RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == FORM_A), None)
    if landed is None:
        print("WRITE DID NOT LAND - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(check['rules'])}")
    print("  +", json.dumps(landed["do"][0], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
