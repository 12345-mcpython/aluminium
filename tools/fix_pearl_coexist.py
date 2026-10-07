"""Make the rules that share an attribute coexist instead of evicting each other.

What went wrong: `StatModifierBuff.isSameKind` is (attribute, modifierType, sourceRole) - the buff NAME is not part of
it - so two `MODIFY_ATTR` effects on the same attribute from different rules REPLACE each other. Adding Pearl's ten
stat traces therefore silently ate the two clauses that touch the same attributes:

  * the traces' `ELATION_DAMAGE_BOOST +0.10` evicted 洞察万物's computed boost (measured: 0.42 -> 0.10 at 2400 DEF);
  * the traces' `EFFECT_RESISTANCE +0.10` evicted 感知容差's aura (measured: the aura's gain read 0.4 instead of 0.5).

Merging them is NOT an option: the traces are unconditional and the two clauses are gated (on DEF >= 2400 and on
holding the counter), so one rule cannot carry both.

The engine already has the opt-in for exactly this ("an effect may ask NOT to evict - 1408's trace, whose +50% must
live beside her transformation's +80%"): `coexist: true`, which protects the buff in BOTH directions. It is set on the
four stat-trace effects and on the two gated clauses that share an attribute with them.

Usage:
    python tools/fix_pearl_coexist.py
    python tools/fix_pearl_coexist.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")

# Rules whose effects must coexist with the others touching the same attribute, and the attributes involved.
COEXIST_RULES = {
    "stat_traces_the_ten_panel_rows": {
        "ELATION_DAMAGE_BOOST", "EFFECT_RESISTANCE", "DEFENCE", "SPEED",
    },
    "trace_insight_raises_elation_at_2400_defence": {"ELATION_DAMAGE_BOOST"},
    "trace_tolerance_raises_effect_resistance_while_holding": {"EFFECT_RESISTANCE"},
}

NOTE_ADDENDUM = (
    "  ⚠⚠ **必须带 `coexist: true`** ✓：`StatModifierBuff.isSameKind` 的身份是 **(属性, modifierType, sourceRole)** ✓"
    " —— **名字不参与** ✓ —— 所以同属性的两条 `MODIFY_ATTR` 会**互相替换** ✗。实测：行迹的 `ELATION_DAMAGE_BOOST +0.10` "
    "把这一条算出来的值**顶掉**了（2400 防御处读数由 0.42 变 0.10 ✓）；行迹的 `EFFECT_RESISTANCE +0.10` 同样顶掉了感知容差的光环 ✓。"
    "⚠ **不能靠合并解决**：行迹是**无条件**的，而这一条**带门**（防御 ≥ 2400 / 持有【好活当赏】）⇒ 一条规则扛不了两句话 ✓。"
    "⇒ 用引擎既有的 `coexist` ✓（它的注释写的就是这个用途：「一个效果可以要求**不被顶掉**」✓，两个方向都保护 ✓）。"
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
    by_id = {rule["id"]: rule for rule in data["rules"]}
    missing = [rid for rid in COEXIST_RULES if rid not in by_id]
    if missing:
        print("rules not found:", missing)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    changed = []
    for rule_id, attributes in COEXIST_RULES.items():
        rule = by_id[rule_id]
        for effect in rule["do"]:
            if effect.get("attribute") in attributes:
                effect["coexist"] = True
                changed.append((rule_id, effect.get("attribute")))
        if "coexist" not in (rule.get("note") or ""):
            rule["note"] = (rule.get("note") or "") + "\n" + NOTE_ADDENDUM

    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    for rule_id, attributes in COEXIST_RULES.items():
        rule = next(r for r in check["rules"] if r["id"] == rule_id)
        for effect in rule["do"]:
            if effect.get("attribute") in attributes and effect.get("coexist") is not True:
                print("WRITE CHECK FAILED for", rule_id, effect.get("attribute"), "- restoring")
                shutil.copyfile(BACKUP, TARGET)
                return 3
    for rule_id, attribute in changed:
        print(f"  coexist: true on {rule_id} / {attribute}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
