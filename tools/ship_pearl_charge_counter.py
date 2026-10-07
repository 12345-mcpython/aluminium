"""Put Pearl's 充能 on the engine's new counter-as-duration vocabulary.

The document: 「【深度学习】具有 3 点充能。真珠施放强化普攻时消耗 1 点充能，行动结束后若充能不足则【深度学习】结束。」

Before this, the three charges were written as `turns: 3` on the state - a registered approximation, because the engine had
no way to say "this lasts N USES". The counter is the exact words: it lives on Pearl, ticks per enhanced basic, and the
state ends when it runs out.

The vocabulary it needed is `GAIN_RESOURCE` with `to_max` (raise to the declared ceiling), which is the sibling of the
existing `RAISE_ENERGY_TO`: re-entering the state must restore the FULL three rather than add three to the remainder.

Rules:
  * a 充能 counter on Pearl, max 3;
  * her Ultimate raises it to max (so a re-cast restores it) - it already applies 【深度学习】;
  * each enhanced basic (slot 8 and slot 9, two families as the corpus writes them) spends 1;
  * the state is removed after her action when the counter is empty.

Usage:
    python tools/ship_pearl_charge_counter.py
    python tools/ship_pearl_charge_counter.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")

CHARGE = "充能"
DEEP = "深度学习"

NEW_RULES = [
    {
        "on": "ULT_CAST",
        "id": "ult_refills_the_deep_study_charges",
        "when": [
            "actor == self"
        ],
        "do": [
            {
                "op": "GAIN_RESOURCE",
                "resource": CHARGE,
                "amount": 3,
                "to_max": True
            }
        ],
        "source": "1503 真珠 终结技 映照万千颜色 (150303): 「【深度学习】具有 #1[i] 点充能」—— #1 = 3。",
        "note": "⭐ **充能计数就是【深度学习】的时长** ✓ —— 文档说的是「具有 **3 点充能**」而不是「持续 3 回合」✓。"
                "⚠ 写法用 `to_max` ✓（抬到声明的上限 ✓）：**重入状态必须补满到 3** ✓，"
                "**不是**在剩余量上加 3 ✓ —— 否则用掉 1 点后再重入会变成 4 点 ✓，状态会比文档多活一个回合 ✓。"
                "⚠ 与既有的 `RAISE_ENERGY_TO` **同族** ✓（那个抬的是能量条 ✓）。",
    },
    {
        "on": "BASIC_ATTACK",
        "id": "form_a_spends_a_charge",
        "when": [
            "actor == self",
            "from_skill_id == 8"
        ],
        "do": [
            {
                "op": "SPEND_RESOURCE",
                "resource": CHARGE,
                "amount": 1,
                "target": "self"
            }
        ],
        "source": "1503 真珠 强化普攻 行笔，幻造星月 (150308): 「真珠施放强化普攻时消耗 1 点充能」。",
        "note": "⚠ **按槽拆成两条**（槽 8 ✓、槽 9 ✓），与她的形态 A／B **同一种写法** ✓ —— "
                "条件 DSL **没有「或」** ✓，而语料本来就把两个形态写成两套 ✓。"
                "⭐ 挂在 `BASIC_ATTACK` ✓（**施放事件** ✓）：`CAST_SETUP` **不经**施放事件 ✓，而夹具要用引擎自己的施放入口 ✓。",
    },
    {
        "on": "BASIC_ATTACK",
        "id": "form_b_spends_a_charge",
        "when": [
            "actor == self",
            "from_skill_id == 9"
        ],
        "do": [
            {
                "op": "SPEND_RESOURCE",
                "resource": CHARGE,
                "amount": 1,
                "target": "self"
            }
        ],
        "source": "1503 真珠 强化普攻 行笔，绘制末浪 (150310): 「真珠施放强化普攻时消耗 1 点充能」。",
        "note": "与 `form_a_spends_a_charge` **同一条款、另一个槽** ✓（见那条的说明 ✓）。",
    },
    {
        "on": "ATTACK_FINISHED",
        "id": "deep_study_ends_when_the_charges_run_out",
        "when": [
            "actor == self",
            "self_resource:充能 <= 0",
            "self has_state 深度学习"
        ],
        "do": [
            {
                "op": "REMOVE_STATE",
                "state": DEEP,
                "target": "self"
            }
        ],
        "source": "1503 真珠 终结技 映照万千颜色 (150303): 「行动结束后若充能不足则【深度学习】结束」。",
        "note": "⭐ **「行动结束后」的落点** ✓ 是 `ATTACK_FINISHED` ✓（`SkillExecutor` 在**一次攻击结算完**之后发它 ✓），"
                "⚠ 而**不是** `TURN_END` ✓ —— 后者是**她的回合**结束 ✓，与「行动结束」不是同一件事 ✓。"
                "⚠ `<= 0` 而不是 `== 0` ✓：计数**不可能**为负 ✓（花费会夹到 0 ✓），但写下界比写等号**更不容易被将来的改动打破** ✓。"
                "⚠ `self has_state 深度学习` 一并写上门 ✓ —— 让这条规则在**没有那个状态时也不做无意义的事** ✓。",
    },
]


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    before = len(data["rules"])
    ids = {r["id"] for r in data["rules"]}

    # The counter itself.
    declared = {r["id"] for r in data["resources"]}
    if CHARGE not in declared:
        data["resources"].append({"id": CHARGE, "max": 3, "initial": 0})

    for rule in NEW_RULES:
        if rule["id"] in ids:
            print("already present:", rule["id"])
            continue
        data["rules"].append(rule)

    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = {r["id"] for r in check["rules"]}
    missing = [r["id"] for r in NEW_RULES if r["id"] not in landed]
    if missing or CHARGE not in {r["id"] for r in check["resources"]}:
        print("WRITE CHECK FAILED:", missing, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4

    counter = next(r for r in check["resources"] if r["id"] == CHARGE)
    print(f"rules: {before} -> {len(check['rules'])}")
    print("  counter:", json.dumps(counter, ensure_ascii=False))

    # The approximation this replaces must be gone: 深度学习 no longer carries `turns: 3`.
    deep_rules = [r for r in check["rules"] if r["id"] == "ult_marks_the_archetype_and_deep_study"]
    for rule in deep_rules:
        for effect in rule["do"]:
            if effect.get("buff") == DEEP:
                print("  NOTE: 【深度学习】 still states turns =", effect.get("turns"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
