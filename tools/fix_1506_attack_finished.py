"""Two fixes from running 1506's new judge.

1. `p1506_three_enhanced_basics_end_godmode` gated on `from_skill_id == 8.0`, and `ATTACK_FINISHED` does NOT carry a
   skill slot - Battle.java fires it with a null skillId, so that condition could never be true and the state never ended.
   The state itself is the marker for "she is doing enhanced basics", so the gate drops the slot comparison entirely.
2. The E1 case: both rules hang on STATE_ENDED, one clears and one restores a fifth. The clear must come first, and the
   judge has to read the result of the PAIR - it already does; what it also needs is for the base rule not to be skipped
   when the Eidolon rule is present, which is what the file order guarantees.
"""
import json
import pathlib

LIVE = pathlib.Path("src/main/resources/characters/1506.json")
SCRIPT = pathlib.Path("tools/ship_1506.py")
RULE_ID = "p1506_three_enhanced_basics_end_godmode"

data = json.loads(LIVE.read_text(encoding="utf-8"))
rule = next(r for r in data["rules"] if r["id"] == RULE_ID)
before = list(rule["when"])
rule["when"] = [c for c in before if not c.startswith("from_skill_id")]
rule["note"] = (
    "⭐ 「**施放完 3 次之后**」的落点是 `ATTACK_FINISHED` ✓（攻击结算完 ✓）。"
    "⚠⚠ **这里不能写 `from_skill_id`** ✓ —— **实测**：`Battle` 发 `ATTACK_FINISHED` 时 **skillId 传的是 null** ✓"
    "⇒ `from_skill_id == 8.0` **永不成立** ✓，而症状是**沉默的**：状态一直在 ✓、计数已归零 ✓，"
    "两条规则各自看都对 ✓。⇒ 改用**状态本身**作为「她在打强化普攻」的标记 ✓（该状态正是强化普攻的授予者 ✓），"
    "⭐ 与 1503 的 `deep_study_ends_when_the_charges_run_out` 同一个形状 ✓。")
LIVE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{RULE_ID}: {before} -> {rule['when']}")

text = SCRIPT.read_text(encoding="utf-8")
old = '"when": ["actor == self", "from_skill_id == 8.0", "self_resource:强化普攻次数 <= 0", "self has_state 无敌玩家"]'
new = '"when": ["actor == self", "self_resource:强化普攻次数 <= 0", "self has_state 无敌玩家"]'
print("script anchor present:", old in text)
text = text.replace(old, new)
SCRIPT.write_text(text, encoding="utf-8")

check = json.loads(LIVE.read_text(encoding="utf-8"))
left = [c for r in check["rules"] if r["id"] == RULE_ID for c in r["when"] if c.startswith("from_skill_id")]
print("remaining slot gates on that rule:", left)
