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
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 10 \u300c" + name + "\u300d\uff08\u6570\u636e\u69fd\u4f4d 13\uff0cSkillID 1141513\uff09\uff1a"
               "\u300c**\u5bf9\u5f00\u62d3\u8005\u2022\u8bb0\u5fc6\u65bd\u653e\u65f6\uff0c\u4f7f\u5f00\u62d3\u8005\u2022\u8bb0\u5fc6\u7684\u653b\u51fb\u529b\u63d0\u9ad8\uff0c"
               "\u63d0\u9ad8\u6570\u503c\u7b49\u540c\u4e8e**\u5fb7\u8c2c\u6b4c\u751f\u547d\u4e0a\u9650**\u7684 #1%\uff0c\u540c\u65f6\u4f7f\u5176\u66b4\u51fb\u7387\u63d0\u9ad8\uff0c"
               "\u63d0\u9ad8\u6570\u503c\u7b49\u540c\u4e8e**\u5fb7\u8c2c\u6b4c\u66b4\u51fb\u7387**\u7684 #2%\u3002**\u8be5\u6548\u679c\u5bf9\u8ff7\u8ff7\u4e5f\u751f\u6548\u3002**\u300d"),
    "note": ("\u2b50 \u4e24\u4e2a\u4e3b\u4f53\u5404\u81ea\u53d6\u81ea\u54ea\u91cc\uff0c\u90fd\u662f**\u91cf\u8fc7**\u7684\uff1a"
             "\u5360\u6bd4 #1/#2 \u6765\u81ea**\u65bd\u653e\u6280\u80fd\u7684\u53c2\u6570**\uff08`percent_from_cast_param`\uff0c\u6309\u65bd\u653e\u8005\u7684**\u5f53\u524d\u7b49\u7ea7**\u53d6\u884c\uff09\uff1b"
             "\u800c\u5b83\u4eec\u6240**\u4e58**\u7684\u5c5e\u6027\u6765\u81ea**\u52a8\u4f5c\u65b9**\uff08`actor_attr:`\uff0c\u5373\u5fb7\u8c2c\u6b4c\u81ea\u5df1\uff09\u2014\u2014"
             "`self_attr:` \u8bfb\u7684\u662f\u6301\u6709\u8005\u3001`summon_attr:` \u8bfb\u7684\u662f\u6301\u6709\u8005\u7684\u5fc6\u7075\uff08\u5bf9 8007 \u800c\u8a00\u662f\u8ff7\u8ff7\uff09\uff0c\u4e24\u8005\u90fd\u4e0d\u662f\u5fb7\u8c2c\u6b4c\u3002"),
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
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 10 \u300c" + name + "\u300d\uff08\u6570\u636e\u69fd\u4f4d 13\uff0cSkillID 1141513\uff09\uff1a"
               "\u5b83\u7684\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
    "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\uff08`SkillExecutor.canDeliver`\uff09\uff0c\u6574\u6761\u5fc6\u7075\u6280\u80fd\u5c31\u6c38\u8fdc\u65bd\u653e\u4e0d\u4e86\u3002",
}
io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)
