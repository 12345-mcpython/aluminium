"""1415's memosprite skill 12 「献予「浪漫」之诗」 -- the two effects that last until a state ends (2026-10-02).

The sentence: 「阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，<b>持续至阿格莱雅退出【至高之姿】状态</b>。」

Four rules, and every part is a shipped spelling:
  * `#2` / `#3` run with the skill level (0.36 -> 1.008 and 0.18 -> 0.504 over the ten rows of `11415/14`), so they are read through
    `percent_from_cast_param`;
  * `ALL_DAMAGE_TYPE_BOOST` (「造成的伤害提高」) and `DEFENCE_IGNORE` (1302 / 1303 already ship 「无视目标 …% 的防御力」);
  * the modifiers carry the GAME'S OWN name for this buff (`GlobalModifiers`: `MServant_CyreneServant_00_AmazingBuff_Aglaea`), because the
    lifetime is spelled by naming them again: `AbstractBuff.buffName` plus `BuffManager.removeState(String)` takes a named buff off;
  * the lifetime itself is a companion rule on `STATE_ENDED` reading `self state_ended <至高之姿>`, which `1402.json` already has as a state.
⚠ Split by subject, as the engine itself demands: a `target: "summon"` effect needs the memosprite on the field (`self_summon_count >= 1`), and
gating the whole rule would have made her own half conditional on 衣匠 being out.
"""
import io
import json
import sys

CHARS = "src/main/resources/characters/1402.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 14
STANCE = "至高之姿"
HANDLE = "MServant_CyreneServant_00_AmazingBuff_Aglaea"
BOOST, PIERCE = "ALL_DAMAGE_TYPE_BOOST", "DEFENCE_IGNORE"
ENOUGH = "self_summon_count >= 1"

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if len(rows) < 3 or rows[0][1] == rows[-1][1] or rows[0][2] == rows[-1][2]:
    sys.exit("REFUSING: #2/#3 do not run with level, so this reading would be wrong")
print("ok   #2 %s -> %s ; #3 %s -> %s over %d rows" % (rows[0][1], rows[-1][1], rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
existing = {r.get("id") for r in rules}
for new_id in ("memosprite_ode_of_romance_raises_damage_and_pierces_defence",
               "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
               "memosprite_ode_of_romance_ends_with_her_stance",
               "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker"):
    if new_id in existing:
        sys.exit("REFUSING: %s is already there" % new_id)

SOURCE = ("1415 昔涟 忆灵技能 12 「献予「浪漫」之诗」（数据槽位 14）："
          "「阿格莱雅与衣匠造成的伤害提高 #2% 并无视目标 #3% 的防御，"
          "持续至阿格莱雅退出【" + STANCE + "】状态。」")
HANDLE_NOTE = ("⭐ 两个修饰都带**名字**（`buff`），用的是游戏自己的修饰名 "
               "`" + HANDLE + "`（取自 `GlobalModifiers`）—— 因为时长是靠**点名再摘一次**写的："
               "`AbstractBuff.buffName` + `BuffManager.removeState(String)` 会把带该名字的 buff 摘下来。"
               "⭐ 数值都走 `percent_from_cast_param`（#2 ⇒ 索引 1，#3 索引 2），因为它们**随等级变**。")

def named(target):
    return [{"op": "MODIFY_ATTR", "attribute": BOOST, "percent_from_cast_param": 1,
             "permanent": True, "buff": HANDLE, "target": target},
            {"op": "MODIFY_ATTR", "attribute": PIERCE, "percent_from_cast_param": 2,
             "permanent": True, "buff": HANDLE, "target": target}]

rules.append({
    "id": "memosprite_ode_of_romance_raises_damage_and_pierces_defence",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": named("self"),
    "source": SOURCE,
    "note": HANDLE_NOTE + " ⚠ 这一条只管**她自己**，因为「对衣匠也生效」需要忆灵在场，而那个条件不该压在她身上。",
})
rules.append({
    "id": "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT), ENOUGH],
    "do": named("summon"),
    "source": SOURCE,
    "note": "「**阿格莱雅与衣匠**造成的伤害提高…」—— 衣匠那一半。" + HANDLE_NOTE,
})
rules.append({
    "id": "memosprite_ode_of_romance_ends_with_her_stance",
    "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "target": "self"}],
    "source": SOURCE,
    "note": ("⭐ 这就是「**持续至阿格莱雅退出【" + STANCE + "】状态**」的时长："
             "一条看着 `STATE_ENDED` 的伴随规则。引擎自己的时长只有 `turns`／`permanent`／"
             "一个闭集 `until`（cast_end、next_attack、next_skill、turn_end），都说不了这句。"),
})
rules.append({
    "id": "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker",
    "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE, ENOUGH],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "target": "summon"}],
    "source": SOURCE,
    "note": "同上，但摘的是衣匠身上那份。",
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1402 now carries all four rules (%d rules)" % len(rules))
