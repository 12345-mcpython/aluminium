"""Tribbie's zone, as a STATE on him -- the handle his own sentences and 1415's ode both need (2026-10-02).

His ultimate (140303), verbatim: 「开启<b>结界</b>，并对敌方全体造成等同于缇宝 #1% 生命上限的量子属性伤害。\n<b>结界持续期间</b>，敌方目标受到的伤害提高 #2%。
受到我方目标攻击后，每有1名目标受到攻击，会对被攻击目标中当前生命值最高的目标造成1次等同于缇宝 #3% 生命上限的量子属性附加伤害。\n结界持续 #4 回合，自身每回合开始时结界持续回合数减1。」

Our content already models the VULNERABILITY half (`ult_zone_enemy_vulnerability`: `MODIFY_DAMAGE_TAKEN` 0.3 for 2 turns on the enemies), which is why 「结界持续期间」 had nothing to point at: the zone is a debuff on the ENEMIES, and no state says the zone is open. 1415's ode of passage needs exactly that
-- 「缇宝施放追加攻击触发<b>缇宝的结界的附加伤害</b>时，会额外造成 #1 次附加伤害」 -- and so does the zone's own additional-damage clause, which is registered rather than written
(it needs #3 of HIS ultimate row -- a level-dependent parameter of a skill the rider does not name -- plus the selector 「被攻击目标中当前生命值最高的目标」).

The duration is #4 = 2 at every level of 140303, so two turns is what the data says.
"""
import io
import json
import sys

TB = "E:/turnbasedgamedata"
CHARS = "src/main/resources/characters/1403.json"
STATE = "\u7ed3\u754c"      # 结界 -- the game's own word, and the same word 1203 already uses for a zone state

rows = json.load(io.open(TB + "/ExcelOutput/AvatarSkillConfig.json", encoding="utf-8"))
levels = [r for r in rows if r["SkillID"] == 140303]
if not levels:
    sys.exit("REFUSING: no 140303 rows")
turns = {p.get("Value") for r in levels for i, p in enumerate(r.get("ParamList") or []) if i == 3}
if turns != {2}:
    sys.exit("REFUSING: #4 is %s, not 2 at every level -- so a literal would be an approximation" % turns)
print("ok   #4 (the zone's duration) is 2 at all %d levels" % len(levels))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "ult_zone_state"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "ULT_CAST",
    "when": ["actor == self"],
    "do": [{"op": "APPLY_BUFF", "buff": STATE, "turns": 2, "target": "self"}],
    "source": ("1403 \u7f07\u5b9d \u7ec8\u7ed3\u6280 \u731c\u731c\u8fd9\u91cc\u4f4f\u7740\u8c01 (140303, Ultra/AllAttack)\uff1a"
               "\u300c\u5f00\u542f\u3010" + STATE + "\u3011\u2026**" + STATE + "\u6301\u7eed\u671f\u95f4**\uff0c\u654c\u65b9\u76ee\u6807\u53d7\u5230\u7684\u4f24\u5bb3\u63d0\u9ad8 #2%\u2026"
               "\u3010" + STATE + "\u3011\u6301\u7eed **#4 \u56de\u5408**\u300d\u3002"),
    "note": ("\u2b50 \u4e3a\u4ec0\u4e48\u8981\u8fd9\u4e2a\u72b6\u6001\uff1a\u672c\u6587\u4ef6\u5df2\u7ecf\u628a**\u6613\u4f24**\u90a3\u534a\u5199\u6210\u4e86\u654c\u4eba\u8eab\u4e0a\u7684 "
             "`MODIFY_DAMAGE_TAKEN`\uff08`ult_zone_enemy_vulnerability`\uff09\u2014\u2014 \u90a3\u662f\u4e00\u4e2a**\u51cf\u76ca**\uff0c"
             "\u6240\u4ee5\u300c**" + STATE + "\u6301\u7eed\u671f\u95f4**\u300d\u8fd9\u53e5\u8bdd**\u65e0\u5904\u53ef\u6307**\u3002"
             "\u2b50 \u6301\u7eed #4 \u5728 140303 \u7684**\u6bcf\u4e00\u7ea7\u90fd\u662f 2**\uff0c\u6240\u4ee5\u5199 2 \u662f\u6570\u636e\u8bf4\u7684\u8bdd\u3002"
             "\u2b50 \u800c 1415 \u7684\u300c\u95e8\u5f84\u300d\u4e4b\u8bd7\u6b63\u597d\u9700\u8981\u5b83\uff1a\u300c\u7f07\u5b9d\u65bd\u653e\u8ffd\u52a0\u653b\u51fb\u89e6\u53d1**"
             "\u7f07\u5b9d\u7684" + STATE + "\u7684\u9644\u52a0\u4f24\u5bb3**\u65f6\u2026\u300d\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))
