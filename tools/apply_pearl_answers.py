"""Two decisions from the author's answers, applied together.

1. WITHDRAW the art barrier's energy refund. The corpus says "使其【下一次】施放终结技时，为真珠固定恢复 90 点能量"
   - "the NEXT time" - and the shipped rule fires on EVERY ultimate the barrier's holder casts, because expressing
   "next" needs the engine to remove a state from the unit that CAUSED the event. The author rejected the
   approximation, so the rule goes rather than staying in the tree looking finished. (The barrier's own half - the
   mark landing on an Elation archetype - stays: that sentence is exact.)

2. The talent's damage reduction becomes Lv10's value. The corpus's headline number (15%) is the Lv1 row; the file's
   own level convention puts her talent at Lv10, and the row reads #4 = 0.375 at Lv15 / 0.15 at Lv1, i.e. 0.30 at
   Lv10. The author chose the level-10 reading, which is also the convention every other number in the file uses.

Usage:
    python tools/apply_pearl_answers.py
    python tools/apply_pearl_answers.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
REFUND = "art_barrier_refunds_ninety_energy_once"
TALENT = "talent_reduces_damage_taken_below_half_hp"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    ids = [r["id"] for r in rules]
    if REFUND not in ids or TALENT not in ids:
        print("expected both rules; refund:", REFUND in ids, "talent:", TALENT in ids)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(rules)
    data["rules"] = [r for r in rules if r["id"] != REFUND]

    talent = next(r for r in data["rules"] if r["id"] == TALENT)
    for effect in talent["do"]:
        if effect.get("op") == "MODIFY_DAMAGE_TAKEN":
            effect["percent"] = -0.30
    talent["source"] = ("1503 真珠 天赋 吞纳沙砾，重塑生机 (150304): 「我方目标当前生命值百分比小于等于 #1[i]% 时，"
                        "受到的伤害降低 #4[i]%」（#1 = 0.5、#4 在 Lv1 = 0.15 / Lv15 = 0.375 ⇒ **Lv10 ≈ 0.30**）。")
    talent["note"] = ((talent.get("note") or "")
                      + "\n  ⭐ **取值口径已定（作者裁决）**：用 **Lv10 ≈ 0.30** ✓，与其它句子一致 ✓"
                        "（语料正文里的 15% 是 **Lv1** 那一行 ✓）。")

    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    left = [r["id"] for r in check["rules"]]
    problems = []
    if REFUND in left:
        problems.append("the refund rule is still there")
    if before - len(left) != 1:
        problems.append(f"expected to drop exactly one rule, dropped {before - len(left)}")
    fixed = next(r for r in check["rules"] if r["id"] == TALENT)
    if fixed["do"][0].get("percent") != -0.30:
        problems.append(f"the talent reads {fixed['do'][0].get('percent')}, expected -0.30")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(left)} (withdrew {REFUND})")
    print("talent percent:", fixed["do"][0]["percent"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
