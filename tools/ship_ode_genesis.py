"""1415's memosprite skill 10 「献予「创世」之诗」 -- the first half, written from the sentence (2026-10-02).

The sentence, verbatim out of tbgd (`AvatarServantSkillConfig` -> `SkillDesc` -> TextMap):
  「整场生效，<b>对开拓者•记忆施放时，使开拓者•记忆的攻击力提高，提高数值等同于德谬歌生命上限的 #1%</b>，同时使其暴击率提高，提高数值等同于德谬歌暴击率的
   #2%。<b>该效果对迷迷也生效。</b>本场战斗中，开拓者•记忆施放强化普攻后，德谬歌立即获得1个额外回合并自动施放【花与箭的舞曲】，若施放前目标被消灭则对新入场的
   敌方目标施放。」

What this lands, and the two capabilities it needed first (both shipped just before this):
  * the SHARE is a parameter of the casting skill and runs with its level -> `cast_skill_param` / `percent_from_cast_param` (99th item);
  * the SUBJECT of the share is the ACTOR (德谬歌 is the one casting), not the rule owner -- and not the owner's own memosprite, which for
    开拓者•记忆 is 迷迷 -> `actor_attr:` (98th item). Getting this wrong would silently read the wrong unit's Max HP.
  * 「该效果对迷迷也生效」 is the same two effects aimed at `"target": "summon"`, the spelling 1402 / 1413 already use.
⚠ NOT written, and registered instead: the rest of the sentence (「强化普攻后…额外回合…自动施放【花与箭的舞曲】」), which needs a reinforcement-basic
attack event and an extra turn, plus the retarget clause it repeats.
"""
import io
import json
import sys

CHARS = "src/main/resources/characters/8007.json"   # 开拓者•记忆: the sentence names it as the one the ode is cast ON
SKILLS = "src/main/resources/data/skills.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 13                                            # data slot for SkillID 1141513 (SkillTriggerKey SkillCY01)

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
table = json.load(io.open(SKILLS, encoding="utf-8"))
name = table["11415"][str(SLOT)]["name"]["chinese"]
print("the ode is %r (slot %d)" % (name, SLOT))

RULE_ID = "memosprite_ode_of_genesis_raises_his_attack_and_crit"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

def boost(attribute, param, target):
    return {
        "op": "MODIFY_ATTR",
        "attribute": attribute,
        "scale": "actor_attr:" + ("HEALTH" if attribute == "ATTACK" else "CRIT_CHANCE"),
        "percent_from_cast_param": param,
        "permanent": True,
        "target": target,
    }

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [
        boost("ATTACK", 0, "self"),
        boost("CRIT_CHANCE", 1, "self"),
        boost("ATTACK", 0, "summon"),
        boost("CRIT_CHANCE", 1, "summon"),
    ],
    "source": ("1415 昔涟 忆灵技能 10 「" + name + "」（数据槽位 13，SkillID 1141513）："
               "「**对开拓者•记忆施放时，使开拓者•记忆的攻击力提高，"
               "提高数值等同于**德谬歌生命上限**的 #1%，同时使其暴击率提高，"
               "提高数值等同于**德谬歌暴击率**的 #2%。**该效果对迷迷也生效。**」"),
    "note": ("⭐ 两个主体各自取自哪里，都是**量过**的："
             "占比 #1/#2 来自**施放技能的参数**（`percent_from_cast_param`，按施放者的**当前等级**取行）；"
             "而它们所**乘**的属性来自**动作方**（`actor_attr:`，即德谬歌自己）——"
             "`self_attr:` 读的是持有者、`summon_attr:` 读的是持有者的忆灵（对 8007 而言是迷迷），两者都不是德谬歌。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   %s now carries %s (%d rules)" % (CHARS, RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
effects.setdefault("11415", {})[str(SLOT)] = {
    "effect": "Rules",
    "source": ("1415 昔涟 忆灵技能 10 「" + name + "」（数据槽位 13，SkillID 1141513）："
               "它的工作在**规则侧**，所以是 `Rules` 形状。"),
    "note": "⭐ 没有条目就不可交付（`SkillExecutor.canDeliver`），整条忆灵技能就永远施放不了。",
}
io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)
