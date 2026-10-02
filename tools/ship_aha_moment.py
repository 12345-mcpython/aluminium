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
PENDING = "\u5f85\u6f14"
MOMENT = "\u963f\u54c8\u65f6\u523b"
REWARD = "\u597d\u6d3b\u5f53\u8d4f"
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
        "note": ("\u2b50 2026-10-02\uff1a**\u963f\u54c8\u65f6\u523b\u7684\u5f85\u6f14\u8ba1\u6570** \u2713 \u2014\u2014 \u6587\u6863\uff1a\u300c\u963f\u54c8\u65f6\u523b**\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a"
                 "\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f**\u300d\u2713 \u21d2 \u6bcf\u5f00\u59cb\u4e00\u4e2a\u6b22\u6986\u6280 +1 \u2713\u3001\u6bcf\u7ed3\u675f\u4e00\u4e2a -1 \u2713\uff0c"
                 "\u5f52\u96f6\u65f6**\u6458\u6389\u300c\u963f\u54c8\u65f6\u523b\u300d\u72b6\u6001** \u2713 \u21d2 \u5f15\u64ce\u81ea\u5df1\u5c31\u4f1a\u53d1 `STATE_ENDED` \u2713\u3002"),
    })

rules = doc["rules"]
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in IDS)]

rules.append({
    "on": "CAST_SETUP", "id": "elation_moment_start", "when": ["from_category ElationDamage"],
    "do": [
        {"op": "APPLY_BUFF", "buff": MOMENT, "turns": 2, "target": "self"},
        {"op": "GAIN_RESOURCE", "resource": PENDING, "amount": 1, "target": "self"},
    ],
    "source": ("\u6587\u6863\uff08GLOSSARY\uff09\uff1a\u300c\u963f\u54c8\u884c\u52a8\u65f6\u53d1\u52a8\u963f\u54c8\u65f6\u523b\uff0c\u5e76\u4f7f\u53ef\u65bd\u653e\u6b22\u6986\u6280\u7684\u5355\u4f4d"
               "\u5404\u81ea\u65bd\u653e1\u6b21\u6b22\u6986\u6280\u300d\uff1b\u4ee5\u53ca\u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u3002"),
    "note": ("\u2b50 \u672c\u6761\u53ea\u505a\u201c**\u5f00\u59cb\u8bb0\u8d26**\u201d\u2713\uff1a\u6b22\u6986\u6280\u5f00\u59cb\u65bd\u653e \u21d2 \u6302\u4e0a\u300c\u963f\u54c8\u65f6\u523b\u300d\u72b6\u6001 \u2713 \u5e76\u628a\u5f85\u6f14\u8ba1\u6570 +1 \u2713\u3002"),
})
rules.append({
    "on": "ATTACK_FINISHED", "id": "elation_moment_step", "when": ["from_category ElationDamage"],
    "do": [{"op": "SPEND_RESOURCE", "resource": PENDING, "amount": 1, "target": "self"}],
    "source": "\u6587\u6863\uff1a\u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u3002",
    "note": "\u2b50 \u6bcf\u4e00\u4e2a\u6b22\u6986\u6280**\u7ed3\u675f**\u5c31\u628a\u5f85\u6f14\u8ba1\u6570 -1 \u2713\uff08\u4e8b\u4ef6=ATTACK_FINISHED \u2713\uff0c\u672c\u6bb5\u5df2\u786e\u8ba4\u5b83\u643a\u5e26**\u7ed3\u7b97\u5b8c\u6210**\u8bed\u4e49 \u2713\uff09\u3002",
})
rules.append({
    "on": "RESOURCE_CHANGED", "id": "elation_moment_close",
    "when": ["resource_changed:" + PENDING, "self_resource:" + PENDING + " <= 0"],
    "do": [{"op": "REMOVE_STATE", "buff": MOMENT, "target": "self"}],
    "source": "\u6587\u6863\uff1a\u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u3002",
    "note": ("\u2b50\u2b50 \u5f52\u96f6\u65f6**\u6458\u6389\u90a3\u4e2a\u72b6\u6001** \u2713 \u21d2 \u5f15\u64ce\u5c31\u4f1a\u53d1 `STATE_ENDED(\"\u963f\u54c8\u65f6\u523b\")` \u2713"
             "\uff08\u672c\u6bb5\u5df2\u5efa\u7684\u94fe\uff1a\u72b6\u6001\u88ab\u6458\u65f6**\u5148\u62a5\u540e\u6458** \u2713\uff09\u3002"),
})
rules.append({
    "on": "STATE_ENDED", "id": "elation_moment_reward", "when": ["self state_ended " + MOMENT],
    "do": [{"op": "APPLY_BUFF", "buff": REWARD, "turns": 2, "target": "self"}],
    "source": "\u6587\u6863\uff1a\u300c\u963f\u54c8\u65f6\u523b\u7ed3\u675f\u65f6\uff0c\u4f7f\u53c2\u6f14\u7684\u89d2\u8272\u83b7\u5f97\u672c\u6b21\u8ba1\u5165\u7b11\u70b9\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u72b6\u6001\uff0c\u6301\u7eed2\u56de\u5408\u3002\u300d",
    "note": "\u2b50 \u672c\u6bb5\u7b2c\u4e00\u4e2a**\u771f** `STATE_ENDED` \u8bfb\u8005 \u2713\uff08\u8bfb\u8005\u539f\u6587\u6765\u81ea `1505` \u4e0e `GLOSSARY` \u2713\uff09\u3002",
})

doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: the moment is a state, closed by the pending counter")
