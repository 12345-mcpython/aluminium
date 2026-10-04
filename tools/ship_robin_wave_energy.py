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
    "when": ["self has_state \u79d8\u6280"],
    "do": [{"op": "GAIN_ENERGY", "amount": 5, "target": "self"}],
    "source": ("1309 \u77e5\u66f4\u9e1f \u79d8\u6280 \u6c89\u9189\u5427\uff0c\u670b\u53cb (skill 130907, Maze): \u300c\u65bd\u653e\u79d8\u6280\u540e\uff0c\u5728\u81ea\u8eab\u5468\u56f4\u5c55\u5f00\u6301\u7eed 15 \u79d2\u7684\u7279\u6b8a\u9886\u57df\u2026"
               "\u9886\u57df\u5c55\u5f00\u671f\u95f4\u8fdb\u5165\u6218\u6597\u540e\uff0c**\u6bcf\u4e2a\u6ce2\u6b21\u5f00\u59cb\u65f6\u77e5\u66f4\u9e1f\u6062\u590d 5 \u70b9\u80fd\u91cf**\u300d"),
    "note": ("\u300c\u9886\u57df\u5c55\u5f00\u671f\u95f4\u8fdb\u5165\u6218\u6597\u540e\uff0c\u6bcf\u4e2a\u6ce2\u6b21\u5f00\u59cb\u65f6\u77e5\u66f4\u9e1f\u6062\u590d **5** \u70b9\u80fd\u91cf\u300d\u21d2 `WAVE_START` \uff0b `self has_state \u79d8\u6280` "
             "\u21d2 `GAIN_ENERGY{amount: 5, target: self}` \u2713\u3002\u26a0 \u300c\u79d8\u6280\u300d\u90a3\u534a\u7528\u7684\u662f\u672c\u4ed3**\u5df2\u6709\u7684\u60ef\u4f8b** \u2713"
             "\uff08`1408` \u7684\u79d8\u6280\u6761\u4e5f\u5199 `self has_state \u79d8\u6280` \u2713\uff0c\u4e14 `applyTechniqueStates()` \u8dd1\u5728\u4efb\u4f55\u89c4\u5219\u4e4b\u524d \u2713\uff09\u3002"
             "\u26a0 \u4ecd\u767b\u8bb0 \u2713\uff1a\u300c\u6211\u65b9\u5236\u9020\u7684\u9886\u57df\u6548\u679c\u6700\u591a\u5b58\u5728 1 \u4e2a\u300d\uff08\u540c\u53e5\u672b\u5c3e \u2717\uff09\u3002"),
})

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the per-wave energy clause is in 1309 (%d rules)" % len(doc))
