"""1309's per-wave energy, as content (round 20 of the goal).

Clause: 「领域展开期间进入战斗后，每个波次开始时知更鸟恢复 5 点能量」 (知更鸟 秘技 沉醉吧，朋友, skill 130907, Maze).

Only the JSON is touched here -- the judge is a hand-written Java file, which is the discipline this session arrived at after two
scripts broke on quote nesting.

⚠ Why this one and not the 18 others: 12 documents state a 「每个波次开始时…」 clause and only 1310 has a WAVE_START rule today, so this
is an unshipped, plainly writable reader. The per-wave LIMIT form (「每个波次最多触发 1 次」, 1506) is a different, larger piece.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1309.json"
RULE_ID = "technique_field_recovers_five_at_each_wave"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
if not isinstance(doc, list):
    sys.exit("REFUSING: this file is not a bare rule array")
if any(isinstance(rule, dict) and rule.get("id") == RULE_ID for rule in doc):
    sys.exit("REFUSING: the rule is already there")

doc.append({
    "on": "WAVE_START",
    "id": RULE_ID,
    "when": ["self has_state 秘技"],
    "do": [{"op": "GAIN_ENERGY", "amount": 5, "target": "self"}],
    "source": ("1309 知更鸟 秘技 沉醉吧，朋友 (skill 130907, Maze): 「施放秘技后，在自身周围展开持续 15 秒的特殊领域…"
               "领域展开期间进入战斗后，**每个波次开始时知更鸟恢复 5 点能量**」"),
    "note": ("「领域展开期间进入战斗后，每个波次开始时知更鸟恢复 **5** 点能量」⇒ `WAVE_START` ＋ `self has_state 秘技` "
             "⇒ `GAIN_ENERGY{amount: 5, target: self}` ✓。⚠ 「秘技」那半用的是本仓**已有的惯例** ✓"
             "（`1408` 的秘技条也写 `self has_state 秘技` ✓，且 `applyTechniqueStates()` 跑在任何规则之前 ✓）。"
             "⚠ 仍登记 ✓：「我方制造的领域效果最多存在 1 个」（同句末尾 ✗）。"),
})

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the per-wave energy clause is in 1309 (%d rules)" % len(doc))
