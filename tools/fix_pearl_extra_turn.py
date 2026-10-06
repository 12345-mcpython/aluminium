"""Rewrite Pearl (1503)'s extra-turn reward to the half that is expressible, after the loader refused the other.

What the loader said, verbatim in effect: `Op GAIN_RESOURCE names an unknown "target" selector 'actor'`. That is
correct and useful - the reward's recipient is the unit TAKING the extra turn, and this engine's target selectors do
not include "whoever this event is about" for that op. So the 好活当赏 half cannot be aimed at the archetype at all,
and aiming it anywhere else would credit the wrong unit.

Shipped: the 笑点 half, exactly 60, on the archetype's extra turn.
Registered: the 好活当赏 half (no selector reaches a resource that is declared SELF), and the removal half
(笑点 is a party pool, so "spend all" would drain the team's).

Usage:
    python tools/fix_pearl_extra_turn.py
    python tools/fix_pearl_extra_turn.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "extra_turn_pays_the_archetype_thirty_and_sixty"

NOTE = (
    "⭐⭐ **本轮为这一句新建了两个事件**（引擎侧 `EXTRA_TURN_STARTED` / `EXTRA_TURN_ENDED`）：在这之前，引擎"
    "**在交出额外回合的同一刻就忘掉了它是额外回合**（`Queue.moveExtraTurn()` 消费掉那个待定标志 ✓），"
    "所以「该额外回合开始时」在场上**没有任何可判定的时刻** ✓。判据 `ExtraTurnEventTest` 钉住这对事件的边界与顺序 ✓，"
    "`PearlExtraTurnRewardTest` 钉住这一条内容 ✓。"
    "⭐ **门写 `actor has_state 美学底本`** ✓：事件的主体就是**拿到额外回合的那个人** ✓（`Queue` 只把它给被指定的单位 ✓），"
    "所以「【美学底本】获得」这句的**主语**直接可读 ✓。"
    "⚠⚠ **这一条只写了 60 点笑点那半，另外两半都登记了**，理由都是**本引擎没有对应写法**，不是省略："
    "① **「获得 30 点【好活当赏】」没有写法** ✗ —— 接收者是**拿走额外回合的那个单位**，而 `GAIN_RESOURCE` 的 `target` "
    "**不收 `actor`** ✓（装载器原话：`names an unknown \"target\" selector 'actor'` ✓，实测 ✓）；而 `好活当赏` 在每个有它的"
    "角色文件里都是 **SELF**（1503/1505/8009/8010 ✓）⇒ 写到别人身上是**静默的 0** ✓，写到真珠身上则是**另一句话** ✗。"
    "② **「额外回合结束时移除」没有写法** ✗ —— 它要说清**移除哪 60 点、哪 30 点**；而 `笑点` 是 `scope: PARTY` 的**全队池** ✓"
    "（`SPEND_RESOURCE{spendAll}` 会连队友一起清空 ✗）、`好活当赏` 又是 SELF ✓ ⇒ 两者都只能**近似** ✓。"
    "⚠ 一个猜错的绑定会以一个**量级合理**的错数收场、没有任何症状 ✓ ⇒ **不猜** ✓，登记 ✓。"
)


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rule = next((r for r in data["rules"] if r["id"] == RULE_ID), None)
    if rule is None:
        print("rule not found:", RULE_ID)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    rule["do"] = [{"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 60, "target": "actor"}]
    # `actor` is not a selector for this op either, so the target is dropped: the effect lands on the rule's own
    # subject, which for this event IS the unit taking the extra turn - the sentence's subject.
    rule["do"] = [{"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 60}]
    rule["note"] = NOTE

    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    fixed = next((r for r in check["rules"] if r["id"] == RULE_ID), None)
    if fixed is None or len(fixed["do"]) != 1 or fixed["do"][0].get("amount") != 60:
        print("WRITE CHECK FAILED - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print("do:", json.dumps(fixed["do"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
