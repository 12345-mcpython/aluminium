"""Try shipping the two EXTRA-TURN reward rules (grant + removal) into Pearl's file.

The sentences (her ultimate, params #8 = 30 and #7 = 60, constant at every level):
  「该额外回合开始时，【美学底本】获得 30 点【好活当赏】，并获得 60 点笑点，额外回合结束时移除」

Shape:
  * both rules carry `actor has_state 美学底本`, because the archetype is the unit whose extra turn it is - and the
    engine fires EXTRA_TURN_STARTED/ENDED with exactly that unit as the actor;
  * the GRANT targets `actor`, which is the spelling this round added;
  * the REMOVAL uses the new REMOVE_RESOURCE, which clamps instead of erroring - the author's ruling is "take back what
    is left of what was granted", and the pool is meant to be consumed by her own talent in the meantime.

Usage:
    python tools/ship_pearl_extra_turn_reward.py
    python tools/ship_pearl_extra_turn_reward.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
GRANT = "extra_turn_pays_the_archetype"
REFUND = "extra_turn_takes_the_reward_back"

COMMON_SOURCE = ("1503 真珠 终结技 鉴映灵魂的底色 (150303): 「该额外回合开始时，【美学底本】获得 #8[i] 点【好活当赏】，"
                 "并获得 #7[i] 点笑点，额外回合结束时移除」—— #8 = **30**、#7 = **60**（Lv1 到 Lv15 恒定）。")

GRANT_RULE = {
    "on": "EXTRA_TURN_STARTED",
    "id": GRANT,
    "when": ["actor has_state 美学底本"],
    "do": [
        {"op": "GAIN_RESOURCE", "resource": "好活当赏", "amount": 30},
        {"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 60},
    ],
    "source": COMMON_SOURCE,
    "note": "⭐⭐ 这一条**卡了两轮**，本轮才写得出来 ✓，缺的是**两件**：① 事件 ✓（`EXTRA_TURN_STARTED` ✓，以**【美学底本】持有者**为 actor ✓ —— 而**门**问的正是他 ✓）；② ⭐ **接收者的记账归属** ✓。⚠⚠ 第一版写 `target: \"actor\"`（把 30 点打到**底本**身上）✗ ⇒ **静默加 0** ✓ 且 `REMOVE_RESOURCE` **报错** ✓。⭐ 根因由**百科原文**定谳 ✓：「使参演阿哈时刻的角色获得【好活当赏】**状态**，并将本次阿哈时刻的**笑点计入该状态**…多个【好活当赏】计入的笑点数**合并计算**」✓ ⇒ 【好活当赏】是**一个计数**（本项目用 `资源` 表达 ✓），而**这份计数在真珠手里**（本文件声明、上限 50、她的天赋拿它当抵御值 ✓）⇒ 那 30 点**进她的池** ✓。⚠ 门仍用 `actor has_state 美学底本` ✓：问的是**谁的额外回合** ✓，给的是**她的计数** ✓ —— 两件事，各有各的单位 ✓。⭐ `笑点` 是 `PARTY` 作用域 ✓，默认 `self` 也会落到队伍池 ✓（`partyCounterOrNull` ✓）。",
}

REMOVE_RULE = {
    "on": "EXTRA_TURN_ENDED",
    "id": REFUND,
    "when": ["actor has_state 美学底本"],
    "do": [
        {"op": "REMOVE_RESOURCE", "resource": "好活当赏", "amount": 30},
        {"op": "REMOVE_RESOURCE", "resource": "笑点", "amount": 60},
    ],
    "source": COMMON_SOURCE,
    "note": "⭐ 用本轮的 **`REMOVE_RESOURCE`** ✓（**不是** `SPEND_RESOURCE` ✗）：作者裁决「花掉部分就**撤回剩余的**」✓ ⇒ **钳制** ✓、绝不因不足而报错 ✓。⚠ 而 `SPEND_RESOURCE` 的设计恰恰相反 ✓（作者信以为够的花费**不足就是错误** ✓）—— 对**代价**是对的 ✓，对**撤回**是错的 ✓，因为这一池本来就是**要被花掉的** ✓（真珠自己的天赋拿它当抵御值 ✓）。⚠ **撤回的是「当前值里的那一份」** ✓：若这 30 点被花掉后又**重新赚到**，撤回会把新赚的一起拿走 ✓；要做到精确就得把这次授予**当成独立批次**记账 ✓，而本句的两个数**固定且成对**（授予 30/60 ✓、同一回合结束时移除 30/60 ✓）⇒ **没有东西可记** ✓。",
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
    present = [rid for rid in (GRANT, REFUND) if any(r["id"] == rid for r in data["rules"])]
    if present:
        print("already present:", present)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    data["rules"].append(GRANT_RULE)
    data["rules"].append(REMOVE_RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    ids = [r["id"] for r in check["rules"]]
    problems = []
    for rid in (GRANT, REFUND):
        if rid not in ids:
            problems.append(f"{rid} did not land")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(ids)}")
    for rid in (GRANT, REFUND):
        rule = next(r for r in check["rules"] if r["id"] == rid)
        print(f"  {rid}: on={rule['on']} when={rule['when']}")
        for effect in rule["do"]:
            print("     ", json.dumps(effect, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
