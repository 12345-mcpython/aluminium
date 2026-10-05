"""Complete 1008's survival trace (2026-10-02): the sentence's second half had no condition.

The rule already paid 「消灭敌方目标时…回复等同于自身生命上限 20% 的生命值」, but the sentence is 「消灭敌方目标时，**若当前
生命值百分比小于等于 30%**，则…」 -- so it healed unconditionally, i.e. at health levels the document excludes. The
`self_hp_at_most` condition now exists, so the clause can say what it means.

Reads the file, inserts the condition, and rewrites the note to say what is and is not covered. ASCII source, CJK via
escapes.
"""
import io
import json

PATH = "src/main/resources/characters/1008.json"
COND = "self_hp_at_most:0.3"

doc = json.load(io.open(PATH, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rule = next(r for r in rules if r.get("id") == "trace_survival_heal")
if COND in rule.get("when", []):
    print("already states the condition")
    raise SystemExit(0)

rule["when"] = list(rule.get("when", [])) + [COND]
rule["note"] = (
    "「消灭敌方目标时，**若当前生命值百分比小于等于 30%**，"
    "则立即回复等同于自身生命上限 **20%** 的生命值」—— "
    "事件 `KILL` ✓ ＋ `HEAL scale: owner_max_hp percent: 0.2` ✓ ＋ 条件 "
    "**`self_hp_at_most:0.3`** ✓（分数写法：文档说 30%、文件写 0.3 ✓）。"
    "⚠ **本条修正了一个错误行为** ✗：在补上这个条件之前，"
    "它**无条件地在每一次击杀后回血** ✗（即使血量很高 ✗）"
    "—— 即“数字对、时机错” ✗。"
)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2))
print("ok   1008 trace_survival_heal now states self_hp_at_most:0.3")
