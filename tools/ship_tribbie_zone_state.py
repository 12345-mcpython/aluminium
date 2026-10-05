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
STATE = "结界"      # 结界 -- the game's own word, and the same word 1203 already uses for a zone state

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
    "source": ("1403 缇宝 终结技 猜猜这里住着谁 (140303, Ultra/AllAttack)："
               "「开启【" + STATE + "】…**" + STATE + "持续期间**，敌方目标受到的伤害提高 #2%…"
               "【" + STATE + "】持续 **#4 回合**」。"),
    "note": ("⭐ 为什么要这个状态：本文件已经把**易伤**那半写成了敌人身上的 "
             "`MODIFY_DAMAGE_TAKEN`（`ult_zone_enemy_vulnerability`）—— 那是一个**减益**，"
             "所以「**" + STATE + "持续期间**」这句话**无处可指**。"
             "⭐ 持续 #4 在 140303 的**每一级都是 2**，所以写 2 是数据说的话。"
             "⭐ 而 1415 的「门径」之诗正好需要它：「缇宝施放追加攻击触发**"
             "缇宝的" + STATE + "的附加伤害**时…」。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))
