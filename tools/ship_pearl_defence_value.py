"""Ship Pearl's talent's SECOND half: the party-wide absorber paid for out of her 好活当赏.

The sentence this is: 「真珠可使用【好活当赏】作为抵御值，每点【好活当赏】等同于 #5[i] 点抵御值，我方目标受到伤害时，真珠可消耗
抵御值为我方目标抵挡 #2[i]% 的伤害」 - measured off her own talent row (150304), #5 = 200 at every level and #2 = 0.6.

Shape notes, all of them forced by the sentence:
  * the grant reaches `all_allies` because that is who is protected ("我方目标");
  * the payer is NOT stated, so it defaults to the rule's owner - Pearl - which is what the sentence says ("真珠可消耗");
    the counter is hers while the absorber sits on the ally, and that split is the whole point of ABSORB_DAMAGE;
  * `permanent: true` because 「真珠的【好活当赏】持续时间无限」: the pool never expires, so the protection lasts the
    battle rather than a turn count;
  * it hangs on BATTLE_START beside the other loadout facts (talents and traces), not on a damage event: it is a
    standing capability, and the engine asks the victim's own buffs at settlement time.

Usage:
    python tools/ship_pearl_defence_value.py
    python tools/ship_pearl_defence_value.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "talent_spends_haohuo_as_defence_value"

RULE = {
    "on": "BATTLE_START",
    "id": RULE_ID,
    "do": [
        {
            "op": "ABSORB_DAMAGE",
            "resource": "好活当赏",
            "unit_amount": 200.0,
            "percent": 0.6,
            "permanent": True,
            "target": "all_allies"
        }
    ],
    "source": "1503 真珠 天赋 吞纳沙砾，重塑生机 (150304): 「真珠可使用【好活当赏】作为抵御值，每点【好活当赏】等同于 #5[i] 点抵御值，我方目标受到伤害时，真珠可消耗抵御值为我方目标抵挡 #2[i]% 的伤害」—— #5 = **200**（各级都是 200）、#2 = **0.6**（Lv1 与 Lv15 都是 0.6）。",
    "note": "⭐⭐ 用的是本会话新增的 `ABSORB_DAMAGE` ✓：**份额（60%）** ＋ **兑换率（每点 200）** ＋ **谁的数字在付（真珠）** ✓ —— 句子的三层意思各占一个字段 ✓。⚠ **不写 `payer`** ✓：默认就是规则所有者（真珠 ✓），而吸收器装在**被保护的队友**身上 ✓ —— 这就是这个 op 存在的理由 ✓（装在哪 ≠ 谁付钱 ✓）。⚠ `permanent: true` 出自同句「真珠的【好活当赏】持续时间无限」✓。⭐ **插入点是结算后、扣血前** ✓（引擎 `Battle.absorbsInto` ✓）⇒ 挡到 0 的一击**不是致命一击** ✓（它在 lethal 判断之前 ✓）。",
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
    effect = landed["do"][0]
    problems = []
    if effect.get("resource") != "好活当赏":
        problems.append(f"resource reads {effect.get('resource')}")
    if effect.get("unit_amount") != 200.0:
        problems.append(f"unit_amount reads {effect.get('unit_amount')}")
    if effect.get("percent") != 0.6:
        problems.append(f"percent reads {effect.get('percent')}")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(check['rules'])}")
    print("  effect:", json.dumps(effect, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
