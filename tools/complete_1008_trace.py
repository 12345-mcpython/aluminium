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
    "\u300c\u6d88\u706d\u654c\u65b9\u76ee\u6807\u65f6\uff0c**\u82e5\u5f53\u524d\u751f\u547d\u503c\u767e\u5206\u6bd4\u5c0f\u4e8e\u7b49\u4e8e 30%**\uff0c"
    "\u5219\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 **20%** \u7684\u751f\u547d\u503c\u300d\u2014\u2014 "
    "\u4e8b\u4ef6 `KILL` \u2713 \uff0b `HEAL scale: owner_max_hp percent: 0.2` \u2713 \uff0b \u6761\u4ef6 "
    "**`self_hp_at_most:0.3`** \u2713\uff08\u5206\u6570\u5199\u6cd5\uff1a\u6587\u6863\u8bf4 30%\u3001\u6587\u4ef6\u5199 0.3 \u2713\uff09\u3002"
    "\u26a0 **\u672c\u6761\u4fee\u6b63\u4e86\u4e00\u4e2a\u9519\u8bef\u884c\u4e3a** \u2717\uff1a\u5728\u8865\u4e0a\u8fd9\u4e2a\u6761\u4ef6\u4e4b\u524d\uff0c"
    "\u5b83**\u65e0\u6761\u4ef6\u5730\u5728\u6bcf\u4e00\u6b21\u51fb\u6740\u540e\u56de\u8840** \u2717\uff08\u5373\u4f7f\u8840\u91cf\u5f88\u9ad8 \u2717\uff09"
    "\u2014\u2014 \u5373\u201c\u6570\u5b57\u5bf9\u3001\u65f6\u673a\u9519\u201d \u2717\u3002"
)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2))
print("ok   1008 trace_survival_heal now states self_hp_at_most:0.3")
