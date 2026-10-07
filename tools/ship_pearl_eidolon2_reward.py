"""Ship Eidolon 2's boost to the extra turn's reward: 好活当赏 30 -> 60 and 笑点 60 -> 120.

「并使终结技提供的额外回合开始时获得的【好活当赏】和笑点提高 100%」(rank 150302, Param [0.15, 1] - the 1 is the
100%).

Two clauses, and the second is a CHANGE to a shipped one rather than an addition:

  * GRANT: one more rule on EXTRA_TURN_STARTED gated on `eidolon >= 2`, additive like Eidolon 4's doubling, so the
    base 30/60 plus another 30/60 is 60/120. The shipped grant is untouched - it is what every rank pays;
  * TAKING BACK: the shipped removal's amounts become 60/120. It cannot be paired the way the grant is: two removals
    (one for 30/60, one for a further 30/60) both fire at Eidolon 2, and the second finds the counter already drained -
    the CLAMP that makes a removal safe is exactly what makes a second one a no-op, so the boost would never come off.
    One removal asking for the full 60/120 is correct at EVERY rank, because the clamp takes only what is there: at
    rank 0-1 that is the 30/60 that was granted. That is the same mechanism the author ruled on for this clause
    (「花掉部分就撤回剩余的」), so both ranks keep the documented behaviour.

Note: This is why the change is stated rather than hidden - the lower ranks ask for more than they were given, and the
clamp is what makes the answer right rather than accidental.

Usage:
    python tools/ship_pearl_eidolon2_reward.py
    python tools/ship_pearl_eidolon2_reward.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
GRANT_ID = "extra_turn_pays_the_archetype_at_e2"
BASE_REMOVE = "extra_turn_takes_the_reward_back"

E2_SOURCE = ("1503 真珠 星魂 2 截取，顷刻破晓的光斑（rank 150302，Param = [0.15, 1]）: 「…并使终结技提供的额外回合"
             "开始时获得的【好活当赏】和笑点提高 100%」⇒ 30 → 60、60 → 120。")

GRANT = {
    "on": "EXTRA_TURN_STARTED",
    "id": GRANT_ID,
    "min_eidolon": 2,
    "when": [
        "eidolon >= 2",
        "actor has_state 美学底本"
    ],
    "do": [
        {"op": "GAIN_RESOURCE", "resource": "好活当赏", "amount": 30},
        {"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 60}
    ],
    "source": E2_SOURCE,
    "note": "⭐ 与 `extra_turn_pays_the_archetype` **相加** ✓（不是替换 ✓）：后者是**每个星魂都有的基础奖励** ✓，这一条只加上那 100% 的增量 ✓ ⇒ E2 起 30+30 = **60**、60+60 = **120** ✓。⚠ 门**两条都要** ✓：`eidolon >= 2` 写进**内容** ✓（星魂 4 那轮学到的教训：只靠规则级 `min_eidolon` 会让两条规则逐字节相同而被「同一句写两遍」守卫拒掉 ✓），`actor has_state 美学底本` 与兄弟条款一致 ✓。",
}

REMOVE_NOTE = ("\n  ⚠⚠ **本条的数值已改**（随星魂 2 出货 ✓）：撤回量改为**含加成后的授予量**（60/120）✓，"
               "而**不是**基线授予量（30/60）✓。⚠ 为什么**不能**改成「再新增一条只撤加成的规则」✓："
               "两条撤回会在 E2 **同时触发** ✓，第二条会发现**计数已被第一条抽干** ✓ —— 让每条撤回都安全的**钳制** ✓ "
               "恰恰让第二次撤回变成**空操作** ✓ ⇒ 那 100% 的加成**永远撤不掉** ✓。"
               "⭐ **一条按 60/120 撤回在每个档位都对** ✓：钳制只拿走**实际存在**的那一份 ✓ "
               "⇒ 在 E0–E1 就是当初授予的 30/60 ✓。这正是作者对撤回的裁决（「花掉部分就撤回剩余的」✓）✓。")


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    ids = [r["id"] for r in data["rules"]]
    if GRANT_ID in ids:
        print("already present")
        return 2
    if BASE_REMOVE not in ids:
        print("the shipped removal rule is missing:", BASE_REMOVE)
        return 3

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    for rule in data["rules"]:
        if rule["id"] != BASE_REMOVE:
            continue
        for effect in rule["do"]:
            if effect.get("resource") == "好活当赏":
                effect["amount"] = 60
            elif effect.get("resource") == "笑点":
                effect["amount"] = 120
        rule["note"] = (rule.get("note") or "") + REMOVE_NOTE
    data["rules"].append(GRANT)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    problems = []
    landed = next((r for r in check["rules"] if r["id"] == GRANT_ID), None)
    if landed is None:
        problems.append("the E2 grant did not land")
    fixed = next((r for r in check["rules"] if r["id"] == BASE_REMOVE), None)
    amounts = {e.get("resource"): e.get("amount") for e in (fixed or {}).get("do", [])}
    if amounts.get("好活当赏") != 60 or amounts.get("笑点") != 120:
        problems.append(f"the shipped removal reads {amounts}, expected 60/120")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(check['rules'])}")
    print("  +", GRANT_ID, "->", json.dumps(GRANT["do"], ensure_ascii=False))
    print("  ~", BASE_REMOVE, "->", amounts)
    return 0


if __name__ == "__main__":
    sys.exit(main())
