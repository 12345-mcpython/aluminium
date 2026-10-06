"""Add Pearl (1503)'s extra-turn reward, the half that is measurable today.

The corpus sentence: "该额外回合开始时，【美学底本】获得 30 点【好活当赏】，并获得 60 点笑点，额外回合结束时移除"
- at the START of the extra turn the archetype gains 30 + 60; at the END both are removed.

Shipped here: the START half, on the new `EXTRA_TURN_STARTED` event, with the archetype named by
`holder_of:美学底本` (the same lookup the scale and the target selector use).

NOT shipped, and registered instead: the END half. "额外回合结束时移除" has to say WHICH 60 and WHICH 30, and in
this engine 笑点 is a PARTY-scoped pool (so `SPEND_RESOURCE{spendAll}` would drain everyone else's laughs too) while
好活当赏 is declared SELF on every character that has it (so "the archetype gains 30" points at a counter that does
not exist on anybody else). A guessed binding would settle at a plausible number with no symptom, which is the
failure mode this project refuses.

Usage:
    python tools/ship_pearl_extra_turn.py
    python tools/ship_pearl_extra_turn.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "extra_turn_pays_the_archetype_thirty_and_sixty"

RULE = {
    "on": "EXTRA_TURN_STARTED",
    "id": RULE_ID,
    "when": ["actor has_state 美学底本"],
    "do": [
        {"op": "GAIN_RESOURCE", "resource": "好活当赏", "amount": 30, "target": "actor"},
        {"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 60, "target": "actor"},
    ],
    "source": "1503 真珠 终结技 鉴映灵魂的底色 (150303): 「该额外回合开始时，【美学底本】获得 30 点【好活当赏】，并获得 60 点笑点，额外回合结束时移除」。",
    "note": "⭐⭐ **本轮为这一句新建了两个事件**（引擎侧 `EXTRA_TURN_STARTED` / `EXTRA_TURN_ENDED`）：在这之前，"
            "引擎在**交出额外回合的同一刻就忘掉了它是额外回合**（`Queue.moveExtraTurn()` 消费掉那个待定标志），"
            "所以「该额外回合开始时」在场上**没有任何可判定的时刻** ✓。判据 `ExtraTurnEventTest` 钉住这对事件的边界与顺序 ✓。"
            "⭐ **门写 `actor has_state 美学底本`** ✓：事件的主体就是**拿到额外回合的那个人**（`Queue` 只把它给被指定的单位）"
            "✓，所以「【美学底本】获得」这句话的**主语**直接可读，不需要 `holder_of:` ✓。"
            "⚠⚠ **「额外回合结束时移除」这一半没有写**，登记在下面，理由是**接收者有歧义**而不是懒得写："
            "① `笑点` 在四个文件里都是 `scope: PARTY` ✓（**全队共用一个池**）⇒ `SPEND_RESOURCE{spendAll}` 会**连队友的笑点一起清空** ✗；"
            "② `好活当赏` 在每个有它的角色文件里都是 **SELF**（1503/1505/8009/8010 ✓）⇒ 「【美学底本】获得 30 点」指向的计数"
            "**在别人身上根本不存在** ✗（`GAIN_RESOURCE` 落到一个没声明它的单位上是**静默的 0** ✓）"
            "—— 而 1505 的天赋又明确把这个计数当**角色自己的**用（`self_resource:好活当赏 >= 1` ✓）✓。"
            "⚠ 三处都没有数据能裁决，所以**不猜** ✓：一个猜错的绑定会以一个**量级合理**的错数收场、没有任何症状 ✓。"
            "⚠ 本条只出货**开始那一半**，且**判据只断言这一半**（另一半缺席是**写明**的，不是漏掉 ✓）。",
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
        print("rule already present:", RULE_ID)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    data["rules"].append(RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    if not any(rule["id"] == RULE_ID for rule in check["rules"]):
        print("WRITE DID NOT LAND - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(check['rules'])}")
    print(json.dumps(RULE["do"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
