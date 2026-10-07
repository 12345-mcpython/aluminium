"""Fix the form-A rule's gate: the marker is on the AIM, the actor is Pearl.

Measured: with `actor has_state 美学底本` the clause never fires (the enhanced basic deals 475.398 whether the
archetype's Attack is 1000 or 3000 - identical to the slot-9 form), and relaxing that one condition makes the number
move to 1229.68. The reason is a subject mismatch: 【美学底本】 is applied to the ALLY Pearl's Ultimate designates,
while the rule's `actor` is whoever dealt the damage - Pearl herself, who carries 【深度学习】 instead.

The gate becomes the condition Pearl really satisfies, and the magnitude keeps reading the archetype: that is what
`holder_attr:美学底本:ATTACK` is for, and it is also why this needs BOTH a condition about her and a scale about the
holder.

Usage:
    python tools/fix_pearl_form_a_gate.py
    python tools/fix_pearl_form_a_gate.py restore
"""
import json
import pathlib
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RULE_ID = "enhanced_basic_form_a_extra_elation_damage"
OLD = "actor has_state 美学底本"
NEW = "actor has_state 深度学习"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        data = json.loads(TARGET.read_text(encoding="utf-8"))
        rule = next(r for r in data["rules"] if r["id"] == RULE_ID)
        rule["when"] = [NEW if c == OLD else c for c in rule["when"]]
        TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("reverted the gate to", OLD)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rule = next((r for r in data["rules"] if r["id"] == RULE_ID), None)
    if rule is None:
        print("rule not found:", RULE_ID)
        return 1
    if rule["when"] != ["actor == self", "actor has_state 美学底本", "from_skill_id == 8"]:
        print("unexpected when list:", rule["when"])
        return 2

    rule["when"] = ["actor == self", NEW, "from_skill_id == 8"]
    rule["note"] = (rule.get("note") or "") + (
        "\n  ⚠⚠ **门改对了**（实测）：原写 `actor has_state 美学底本` ✗ ⇒ **条文从不触发** ✓"
        "（强化普攻伤害与槽 9 完全相同：`475.398` ✓，把底本攻击力从 1000 改到 3000 也**纹丝不动** ✓）；"
        "把这一条门去掉后立刻触发（`1229.68` ✓）。⭐ 根因是**主语错位**：`actor` 是**造成伤害的真珠** ✓，"
        "而【美学底本】打在**被她指定的队友**身上 ✓ ⇒ 门要说**她自己满足的事实**（`深度学习` ✓），"
        "而**量**仍指向底本 ✓ —— 这正是 `holder_attr:美学底本:ATTACK` 的用途 ✓，"
        "也是为什么这一条**同时**需要一个关于她的条件与一个关于持有者的 scale ✓。"
    )
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    fixed = next(r for r in check["rules"] if r["id"] == RULE_ID)
    if fixed["when"] != ["actor == self", NEW, "from_skill_id == 8"]:
        print("WRITE CHECK FAILED:", fixed["when"])
        return 3
    print("gate:", rule["when"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
