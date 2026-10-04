"""8009 / 8010's ultimate, the half that needs an Elation skill on the target (2026-10-02, item 58).

Document, verbatim (`data/skills.json` 8009/8010 slot 3, params `[#1,#2,#3,#4,#5,#6]` = `[0.3..0.6, 3, 0.5, 10, 20, 5]`):
  「获得 **#6** 个笑点，使指定我方单体暴击伤害提高 **#1%**，持续 **#2** 回合，并解除该目标的控制类负面状态。
    若目标拥有欢愉技，目标额外获得 **#4** 点【好活当赏】，并**使其立即施放 1 次固定计入 #5 笑点的欢愉技**…
    若目标不拥有欢愉技，使其行动提前 **#3%**。」
Already shipped: the crit-damage half, the "does NOT have an Elation skill -> advance" branch, the talent's energy.
This shipment adds the two clauses that were blocked, and the laugh grant from the first line:
  * 「获得 **5** 个笑点」                      -> `GAIN_RESOURCE`
  * 「目标额外获得 **10** 点【好活当赏】」      -> `GAIN_RESOURCE` on the TARGET
  * 「使其立即施放 1 次…欢愉技」               -> `CAST_SKILL { skill: ELATION_SKILL, target: target }`

\u2b50 It is only writable NOW because of item 57: the Elation skill's row starts with a HIT COUNT, and until that reading
existed a commanded Elation cast settled as one 800% instance. \u2b50 `CAST_SKILL` resolves the skill from the RESOLVED TARGET
(\u300c使其\u300d), which is exactly this sentence.

\u26a0 Registered, not approximated: \u300c**固定计入 #5 = 20 笑点**\u300d (the Aha gauge, a different counter from the \u7b11\u70b9 resource) and
\u300c若欢愉技施放前敌方目标被消灭则对**新入场**的敌方目标发动\u300d.

\u26a0 Shape note: 8009/8010 were BARE LISTS, which have nowhere to declare a resource, so each file becomes
`{ "resources": [...], "rules": [...] }` -- the shape 1513 already uses. Same rules, same order.
"""
import io
import json

LAUGH = "\u7b11\u70b9"
GIFT = "\u597d\u6d3b\u5f53\u8d4f"
SOURCE = ("8009\uff0f8010 \u7ec8\u7ed3\u6280\uff08\u6570\u636e slot 3\uff0c`param_list` = [0.3\u20260.6, 3, 0.5, 10, 20, 5] \u2713\uff09\uff1a"
          "\u300c\u83b7\u5f97 **5** \u4e2a\u7b11\u70b9\u2026\u82e5\u76ee\u6807\u62e5\u6709\u6b22\u6109\u6280\uff0c\u76ee\u6807\u989d\u5916\u83b7\u5f97 **10** \u70b9\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\uff0c"
          "\u5e76**\u4f7f\u5176\u7acb\u5373\u65bd\u653e 1 \u6b21**\u2026\u6b22\u6109\u6280\u300d")

for cid in ("8009", "8010"):
    path = "src/main/resources/characters/%s.json" % cid
    doc = json.load(io.open(path, encoding="utf-8"))
    if isinstance(doc, dict):
        rules = doc.get("rules", [])
    else:
        rules = doc
        doc = {}
    rules[:] = [r for r in rules if not (isinstance(r, dict) and r.get("id", "").startswith("ult_elation_"))]

    resources = doc.setdefault("resources", [])
    if not any(r.get("id") == LAUGH for r in resources):
        resources.append({
            "id": LAUGH,
            "max": 2147483647,
            "initial": 0,
            "scope": "PARTY",
            "source": ("%s \u7ec8\u7ed3\u6280\uff1a\u300c\u83b7\u5f97 5 \u4e2a\u7b11\u70b9\u300d\u3002\u26a0 \u4e0e 1502\uff0f1505\uff0f1513 "
                       "\u5171\u7528\u540c\u540d\u7684**\u961f\u4f0d\u7ea7**\u8ba1\u6570 \u2713\uff08\u6570\u636e\u91cc\u6ca1\u6709\u4e0a\u9650 \u21d2 `Integer.MAX_VALUE` \u2713\uff09" % cid),
        })

    rules.append({
        "on": "ULT_CAST",
        "id": "ult_elation_laughs_and_the_gift",
        "when": ["actor == self"],
        "do": [{"op": "GAIN_RESOURCE", "resource": LAUGH, "amount": 5}],
        "source": SOURCE,
        "note": ("\u300c\u83b7\u5f97 **5** \u4e2a\u7b11\u70b9\u300d\uff08#6 = 5 \u2713\uff09\u2014\u2014 \u7b11\u70b9\u662f**\u961f\u4f0d\u7ea7**\u8ba1\u6570 \u2713\uff0c"
                 "\u672c\u6587\u4ef6\u56e0\u6b64**\u81ea\u5df1\u58f0\u660e**\u5b83 \u2713\uff08\u4e0e 1513 \u540c\u4e00\u505a\u6cd5 \u2713\uff09\u3002"),
    })
    rules.append({
        "on": "ULT_CAST",
        "id": "ult_elation_gift_and_cast",
        "when": ["actor == self", "target has_skill ELATION_SKILL"],
        "do": [
            {"op": "GAIN_RESOURCE", "resource": GIFT, "amount": 10, "target": "target"},
            {"op": "CAST_SKILL", "skill": "ELATION_SKILL", "target": "target"},
        ],
        "source": SOURCE,
        "note": ("\u300c\u82e5\u76ee\u6807\u62e5\u6709\u6b22\u6109\u6280\uff0c\u76ee\u6807\u989d\u5916\u83b7\u5f97 **10** \u70b9\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\uff0c\u5e76**\u4f7f\u5176\u7acb\u5373\u65bd\u653e 1 \u6b21**\u2026\u6b22\u6109\u6280\u300d\u2713"
                 "\uff08#4 = 10 \u2713\uff09\u3002\u2b50 **\u53ea\u6709\u7b2c 57 \u4ef6\u4e4b\u540e\u624d\u5199\u5f97\u51fa** \u2713\uff1a\u6b22\u6109\u6280\u7684\u884c**\u9996\u5217\u662f\u6b21\u6570** \u2713\uff0c"
                 "\u5728\u90a3\u4e4b\u524d\u88ab\u547d\u4ee4\u7684\u6b22\u6109\u65bd\u653e\u4f1a\u7ed3\u7b97\u6210**\u4e00\u53d1 800%** \u2717\u3002\u2b50 `CAST_SKILL` \u4ece**\u89e3\u6790\u51fa\u7684\u76ee\u6807**\u53d6\u6280\u80fd \u2713"
                 "\uff08\u300c\u4f7f**\u5176**\u300d\u2713\uff09\u3002\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u300c\u56fa\u5b9a\u8ba1\u5165 **20** \u7b11\u70b9\u300d\uff08\u963f\u54c8\u91cf\u8868\uff0c\u4e0e\u7b11\u70b9**\u8d44\u6e90**\u4e0d\u540c \u2717\uff09"
                 "\u4e0e\u300c\u82e5\u6b22\u6109\u6280\u65bd\u653e\u524d\u654c\u65b9\u76ee\u6807\u88ab\u6d88\u706d\u5219\u5bf9**\u65b0\u5165\u573a**\u7684\u654c\u65b9\u76ee\u6807\u53d1\u52a8\u300d\u2717\u3002"
                 "\u26a0 \u4e14\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u662f**\u6309\u6301\u6709\u8005**\u58f0\u660e\u7684 \u2717\uff08\u5728 1505 \u7684\u6587\u4ef6\u91cc \u2713\uff09\u21d2 "
                 "\u76ee\u6807\u82e5\u672a\u58f0\u660e\u5b83\uff0c\u8fd9\u4e00\u534a\u843d\u4e0d\u4e0b \u2717\uff08\u5df2\u5982\u5b9e\u8bb0\u4e0b \u2713\uff09\u3002"),
    })

    doc["rules"] = rules
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("ok   %s.json: dict shape, declares %s, and the two ult rules" % (cid, LAUGH))
