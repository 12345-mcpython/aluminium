"""Ship Eidolon 4: the Elation skill's damage multiplier is raised by 100% of its original value.

「欢愉技为我方全体目标提供的欢愉伤害倍率提高原倍率的 100%」(rank 150304, Param [1]) - i.e. the four tier percentages
double: 10%/15%/20%/40% become 20%/30%/40%/80%.

HOW IT IS WRITTEN, and why no new vocabulary was needed. Two measured facts decide it:

  * `ADD_DAMAGE` ACCUMULATES (`Damage.addFlat` does `flatAddend += value`), so a rule that simply "doubled" on top of
    the shipped tier would produce THREE times the tier, not two;
  * `min_eidolon` is a RULE-level gate (the interpreter skips the whole rule), so the additional clause can be given the
    Eidolon 4 condition without touching the shipped four.

Raising the multiplier BY 100% of itself is, literally, adding the original percentage a second time - so four more
rules, each carrying the SAME percentage as its tier and gated on Eidolon 4, sum with the shipped four to exactly
double. No `max_eidolon` is needed and nothing about the shipped clauses moves.

Usage:
    python tools/ship_pearl_eidolon4.py
    python tools/ship_pearl_eidolon4.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")

# tier -> the percentage the shipped rule already pays (Lv10 of the Elation skill's row)
TIERS = {
    1: 0.10,
    2: 0.15,
    3: 0.20,
    4: 0.40,
}
SOURCE = ("1503 真珠 星魂 4 解译，唇角模糊的笑意（rank 150304，Param = [1]）: 「欢愉技为我方全体目标提供的欢愉伤害"
          "倍率提高原倍率的 100%」。⭐ 四档（10%/15%/20%/40%）⇒ 20%/30%/40%/80%。")
NOTE = ("⭐⭐ **不需要新词汇** ✓：`ADD_DAMAGE` 是**累加**的 ✓（`Damage.addFlat` 就是 `flatAddend += value` ✓）"
        "⇒ 再挂一条**同样百分比**的规则 ✓，与已出货那条相加 ⇒ **正好 2 倍** ✓ —— 而「提高**原倍率的** 100%」"
        "字面就是**再加一份原值** ✓。⚠ 反过来**只加「翻倍」的写法会错** ✓：若这条写的是 2×（0.2/0.3/0.4/0.8 ✓），"
        "与基线的 0.1/0.15/0.2/0.4 相加会变成 **3 倍** ✗。⚠ 门用 `min_eidolon: 4` ✓ —— 已确证它是**规则级门** ✓"
        "（解释器直接 `continue` 跳过整条规则 ✓）⇒ 已出货的四条**一个字都不用动** ✓，也不必引入 `max_eidolon` ✓。")


def rule_for(tier: int) -> dict:
    return {
        "on": "DEALING_DAMAGE",
        "id": f"eidolon4_doubles_the_elation_skill_at_{tier}_elation_allies",
        "min_eidolon": 4,
        # Note: The SAME two conditions the shipped tier rules carry, PLUS the rank as a condition. Both halves matter:
        # being "the doubling of that clause" means it applies only where that clause applies (an armed attacker, at that
        # tier), and the rank belongs in the CONTENT because a rule-level `min_eidolon` alone left the two rules
        # byte-identical and the duplicate guard refused them as "one sentence written twice" - measured.
        "when": [
            "eidolon >= 4",
            "actor has_state 渲染理性，解构欢榆",
            f"allies_with_path:欢愉 >= {tier}"
        ],
        "do": [
            {
                "op": "ADD_DAMAGE",
                "scale": "elation_base",
                "percent": TIERS[tier],
                "damage_type": "ELATION"
            }
        ],
        "source": SOURCE,
        "note": NOTE,
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
    ids = [r["id"] for r in data["rules"]]
    if any(rid.startswith("eidolon4_doubles") for rid in ids):
        print("already present")
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    for tier in TIERS:
        data["rules"].append(rule_for(tier))
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    added = [r for r in check["rules"] if r["id"].startswith("eidolon4_doubles")]
    problems = []
    if len(added) != len(TIERS):
        problems.append(f"expected {len(TIERS)} new rules, found {len(added)}")
    for rule in added:
        percent = rule["do"][0].get("percent")
        tier = int(rule["id"].rsplit("_", 3)[0].rsplit("_", 1)[-1]) if False else None
        if rule.get("min_eidolon") != 4:
            problems.append(f"{rule['id']} is not gated on Eidolon 4")
        if percent not in TIERS.values():
            problems.append(f"{rule['id']} pays {percent}, which is not one of the tier values")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(check['rules'])}")
    for rule in added:
        print(f"  {rule['id']}: min_eidolon={rule['min_eidolon']} percent={rule['do'][0]['percent']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
