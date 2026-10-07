"""Use the clamped spend for Pearl's charge, and say why it is not the strict one.

`SPEND_RESOURCE` is a COST: it refuses when the counter is short. `REMOVE_RESOURCE` is a CLAMP: it takes what is there and
never fails. The charge is the FIRST of those in intent and the SECOND in role - it is what makes 【深度学习】 last, not
what admits the cast - and the two available fixtures say the same thing from the engine's side: they drive an enhanced
basic without ever casting the Ultimate, i.e. with no charges at all.

So the charge is spent clamped. The consequence is deliberate and worth stating: a rule may take a charge-less enhanced
basic, and it simply does not extend the state. Registering that is cheaper than inventing a gate the document does not
state - and the gate would only be observable through a state that the same counter already ends.

Usage:
    python tools/fix_pearl_charge_spend.py
"""
import json
import pathlib
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
FORMS = ("form_a_spends_a_charge", "form_b_spends_a_charge")

REASON = ("⚠ **用夹取（`REMOVE_RESOURCE`）而不是严格花费（`SPEND_RESOURCE`）** —— 两者在引擎里是**两种不同的东西**："
          "严格花费是**成本**（不足即**报错**），夹取是**取走现有**（**永不失败**）。"
          "⭐ **充能是按意图属于前者、按角色属于后者**：它是让【深度学习】**持续**的那个量，**不是**让强化普攻**可用**的门槛。"
          "⚠ **两个既有夹具从引擎一侧印证了这一点**：它们**从未施放终结技**（即 **0 充能**）就驱动了强化普攻。"
          "⇒ **后果是刻意的、并且写在这里**：没有充能时仍然可以打出强化普攻，**只是不会延长那个状态**。"
          "⚠ 登记这个，**好过**发明一个文档没说的门槛；而且那个门槛**只能通过一个状态被观察**，"
          "而那个状态**正是同一个计数在结束**。")


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    changed = 0
    for rule in data["rules"]:
        if rule["id"] not in FORMS:
            continue
        for effect in rule["do"]:
            if effect.get("op") == "SPEND_RESOURCE":
                effect["op"] = "REMOVE_RESOURCE"
                changed += 1
        rule["note"] = REASON
    if not changed:
        print("nothing to change")
        return 0
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    for rule in check["rules"]:
        if rule["id"] in FORMS:
            print(rule["id"], "->", json.dumps(rule["do"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
