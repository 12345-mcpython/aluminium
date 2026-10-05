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

⭐ It is only writable NOW because of item 57: the Elation skill's row starts with a HIT COUNT, and until that reading
existed a commanded Elation cast settled as one 800% instance. ⭐ `CAST_SKILL` resolves the skill from the RESOLVED TARGET
(「使其」), which is exactly this sentence.

⚠ Registered, not approximated: 「**固定计入 #5 = 20 笑点**」 (the Aha gauge, a different counter from the 笑点 resource) and
「若欢愉技施放前敌方目标被消灭则对**新入场**的敌方目标发动」.

⚠ Shape note: 8009/8010 were BARE LISTS, which have nowhere to declare a resource, so each file becomes
`{ "resources": [...], "rules": [...] }` -- the shape 1513 already uses. Same rules, same order.
"""
import io
import json

LAUGH = "笑点"
GIFT = "好活当赏"
SOURCE = ("8009／8010 终结技（数据 slot 3，`param_list` = [0.3…0.6, 3, 0.5, 10, 20, 5] ✓）："
          "「获得 **5** 个笑点…若目标拥有欢愉技，目标额外获得 **10** 点【好活当赏】，"
          "并**使其立即施放 1 次**…欢愉技」")

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
            "source": ("%s 终结技：「获得 5 个笑点」。⚠ 与 1502／1505／1513 "
                       "共用同名的**队伍级**计数 ✓（数据里没有上限 ⇒ `Integer.MAX_VALUE` ✓）" % cid),
        })

    rules.append({
        "on": "ULT_CAST",
        "id": "ult_elation_laughs_and_the_gift",
        "when": ["actor == self"],
        "do": [{"op": "GAIN_RESOURCE", "resource": LAUGH, "amount": 5}],
        "source": SOURCE,
        "note": ("「获得 **5** 个笑点」（#6 = 5 ✓）—— 笑点是**队伍级**计数 ✓，"
                 "本文件因此**自己声明**它 ✓（与 1513 同一做法 ✓）。"),
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
        "note": ("「若目标拥有欢愉技，目标额外获得 **10** 点【好活当赏】，并**使其立即施放 1 次**…欢愉技」✓"
                 "（#4 = 10 ✓）。⭐ **只有第 57 件之后才写得出** ✓：欢愉技的行**首列是次数** ✓，"
                 "在那之前被命令的欢愉施放会结算成**一发 800%** ✗。⭐ `CAST_SKILL` 从**解析出的目标**取技能 ✓"
                 "（「使**其**」✓）。⚠ **仍登记**：「固定计入 **20** 笑点」（阿哈量表，与笑点**资源**不同 ✗）"
                 "与「若欢愉技施放前敌方目标被消灭则对**新入场**的敌方目标发动」✗。"
                 "⚠ 且【好活当赏】是**按持有者**声明的 ✗（在 1505 的文件里 ✓）⇒ "
                 "目标若未声明它，这一半落不下 ✗（已如实记下 ✓）。"),
    })

    doc["rules"] = rules
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("ok   %s.json: dict shape, declares %s, and the two ult rules" % (cid, LAUGH))
