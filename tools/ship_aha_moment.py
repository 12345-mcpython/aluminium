"""The Aha Moment as a real state, so the engine fires STATE_ENDED when it ends (2026-10-02).

Documented: 「阿哈行动时发动阿哈时刻…阿哈时刻持续至本次最后一个欢愉技施放结束。阿哈时刻结束时，使参演的角色获得本次计入笑点的
【好活当赏】状态，持续2回合。」 The engine has no "Aha" unit (measured), and summons have no turn of their own (measured), so the
moment is modelled by its END CONDITION -- which the document states outright. Everything below is shipped spelling:

  1. start : CAST_SETUP + from_category ElationDamage -> APPLY_BUFF{阿哈时刻} + GAIN_RESOURCE{待演 +1}
  2. per end: ATTACK_FINISHED + from_category ElationDamage -> SPEND_RESOURCE{待演 -1}
  3. close : RESOURCE_CHANGED{待演} + self_resource:待演 <= 0 -> REMOVE_STATE{阿哈时刻}
             -> the engine's own tick path reports STATE_ENDED("阿哈时刻") before removing it
  4. reward: STATE_ENDED + state_ended 阿哈时刻 -> APPLY_BUFF{好活当赏}  (1505's reader, inside her own file)

The reward lives here rather than in 1505's file because a rule can only use resources/states that its own file declares;
the reader in 1505 can follow once this path is proven.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1513.json"
PENDING = "待演"
MOMENT = "阿哈时刻"
REWARD = "好活当赏"
IDS = ("elation_moment_start", "elation_moment_step", "elation_moment_close", "elation_moment_reward")

doc = json.load(io.open(DATA, encoding="utf-8"))

# 1) declare the pending counter as a party resource
res = doc.setdefault("resources", [])
if not any(isinstance(r, dict) and r.get("id") == PENDING for r in res):
    res.append({
        "id": PENDING,   # the key is `id`; an unknown key here makes the loader reject the whole table
        "max": 999,
        "initial": 0,
        "scope": "PARTY",
        "note": ("⭐ 2026-10-02：**阿哈时刻的待演计数** ✓ —— 文档：「阿哈时刻**持续至本次最后一个"
                 "欢榆技施放结束**」✓ ⇒ 每开始一个欢榆技 +1 ✓、每结束一个 -1 ✓，"
                 "归零时**摘掉「阿哈时刻」状态** ✓ ⇒ 引擎自己就会发 `STATE_ENDED` ✓。"),
    })

rules = doc["rules"]
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in IDS)]

rules.append({
    "on": "CAST_SETUP", "id": "elation_moment_start", "when": ["from_category ElationDamage"],
    "do": [
        {"op": "APPLY_BUFF", "buff": MOMENT, "turns": 2, "target": "self"},
        {"op": "GAIN_RESOURCE", "resource": PENDING, "amount": 1, "target": "self"},
    ],
    "source": ("文档（GLOSSARY）：「阿哈行动时发动阿哈时刻，并使可施放欢榆技的单位"
               "各自施放1次欢榆技」；以及「阿哈时刻持续至本次最后一个欢榆技施放结束」。"),
    "note": ("⭐ 本条只做“**开始记账**”✓：欢榆技开始施放 ⇒ 挂上「阿哈时刻」状态 ✓ 并把待演计数 +1 ✓。"),
})
rules.append({
    "on": "ATTACK_FINISHED", "id": "elation_moment_step", "when": ["from_category ElationDamage"],
    "do": [{"op": "SPEND_RESOURCE", "resource": PENDING, "amount": 1, "target": "self"}],
    "source": "文档：「阿哈时刻持续至本次最后一个欢榆技施放结束」。",
    "note": "⭐ 每一个欢榆技**结束**就把待演计数 -1 ✓（事件=ATTACK_FINISHED ✓，本段已确认它携带**结算完成**语义 ✓）。",
})
rules.append({
    "on": "RESOURCE_CHANGED", "id": "elation_moment_close",
    "when": ["resource_changed:" + PENDING, "self_resource:" + PENDING + " <= 0"],
    "do": [{"op": "REMOVE_STATE", "buff": MOMENT, "target": "self"}],
    "source": "文档：「阿哈时刻持续至本次最后一个欢榆技施放结束」。",
    "note": ("⭐⭐ 归零时**摘掉那个状态** ✓ ⇒ 引擎就会发 `STATE_ENDED(\"阿哈时刻\")` ✓"
             "（本段已建的链：状态被摘时**先报后摘** ✓）。"),
})
rules.append({
    "on": "STATE_ENDED", "id": "elation_moment_reward", "when": ["self state_ended " + MOMENT],
    "do": [{"op": "APPLY_BUFF", "buff": REWARD, "turns": 2, "target": "self"}],
    "source": "文档：「阿哈时刻结束时，使参演的角色获得本次计入笑点的【好活当赏】状态，持续2回合。」",
    "note": "⭐ 本段第一个**真** `STATE_ENDED` 读者 ✓（读者原文来自 `1505` 与 `GLOSSARY` ✓）。",
})

doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: the moment is a state, closed by the pending counter")
