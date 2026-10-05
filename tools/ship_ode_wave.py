"""The ode's fifth clause, in the shape the game's own data states (2026-10-02).

What tbgd says, read out of `GlobalModifiers` in `Servant_CyreneServant_00_Ability.json`:

    "MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster"
        listens for "OnWaveMonster" (plus OnEnterBattle / OnCustomEvent / OnListenAllowAction /
        OnListenInsertAbilityFinish) and runs "…_Mydeimos_InsertActionCheck"
        whose predicate is ByTargetAliveState{ModifierOwnerEntity, Mask_AliveOrRevivable}

    TurnInsertAction{TargetType: ModifierOwnerEntity, SkillIndex: 4, AutoCast: true}

So 「若施放前目标被消灭则对新入场的敌方目标施放」 is, in the game, TWO facts:
  * a durable modifier is put on 万敌 when the ode reaches him (`MServant_CyreneServant_00_AmazingBuff_Mydeimos`), and
  * while it is on him, A WAVE MONSTER ENTERING makes him act again (the inserted action is his own; the victims are the skill's
    own business -- exactly what our `CAST_SKILL` already does).
⚠ The one word of the English text this does NOT support is "the target gets defeated": the game's predicate is ByTargetAliveState on the
MODIFIER OWNER (万敌), not on an enemy. That half is registered, not invented.
"""
import io
import json
import sys

SKILLS = "src/main/resources/data/skills.json"
CHARS = "src/main/resources/characters/1404.json"

table = json.load(io.open(SKILLS, encoding="utf-8"))
name = table["11415"]["16"]["name"]["chinese"]
if not name:
    sys.exit("REFUSING: the ode has no name in our data")
print("the ode's name, read from our data: %r" % name)

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])

if not any(r.get("id") == "memosprite_ode_marks_him_for_the_battle" for r in rules):
    rules.append({
        "id": "memosprite_ode_marks_him_for_the_battle",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16"],
        "do": [{"op": "APPLY_BUFF", "buff": name, "permanent": True, "target": "self"}],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 8\uff08\u6570\u636e\u69fd\u4f4d 16\uff09\uff1a\u90a3\u53e5\u628a\u72b6\u6001\u6302\u5728\u4ed6\u8eab\u4e0a\u7684\u90e8\u5206\u3002"
                   "\uff08\u5bf9\u5e94 tbgd \u7684 `MServant_CyreneServant_00_AmazingBuff_Mydeimos`\uff1a\u4e00\u4e2a\u6301\u7eed\u5230\u6218\u6597\u7ed3\u675f\u7684 modifier\u3002\uff09"),
        "note": "\u2605 \u6301\u4e45\uff08`permanent: true`\uff09\uff0c\u56e0\u4e3a\u5b83\u662f\u201c\u8fd9\u53e5\u8bdd\u8fd8\u5728\u4ed6\u8eab\u4e0a\u201d\u8fd9\u4e2a\u4e8b\u5b9e\u672c\u8eab\u3002",
    })

if not any(r.get("id") == "memosprite_ode_restrikes_when_a_wave_monster_enters" for r in rules):
    rules.append({
        "id": "memosprite_ode_restrikes_when_a_wave_monster_enters",
        "on": "WAVE_START",
        "when": ["self has_state " + name],
        "do": [
            {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "turns": 1, "target": "self"},
            {"op": "CAST_SKILL", "skill": "SKILL", "target": "self"},
        ],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 8\uff1a\u300c\u82e5\u65bd\u653e\u524d\u76ee\u6807\u88ab\u6d88\u706d\u5219\u5bf9**\u65b0\u5165\u573a**\u7684\u654c\u65b9\u76ee\u6807\u65bd\u653e\u300d\u3002"
                   "\uff08tbgd \u91cc\u5b83\u5c31\u662f `OnWaveMonster` \u21d2 `TurnInsertAction{TargetType: ModifierOwnerEntity, AutoCast: true}`\uff1a"
                   "\u6709\u6ce2\u6b21\u602a\u7269\u5165\u573a\uff0c\u5219**\u4ed6\u81ea\u5df1**\u518d\u884c\u52a8\u4e00\u6b21\uff1b\u6253\u8c01\u662f**\u6280\u80fd\u81ea\u5df1**\u7684\u4e8b\u3002\uff09"),
        "note": ("\u26a0 \u672c\u6761\u53ea\u5199\u4e86\u539f\u53e5\u91cc**\u80fd\u88ab\u8bc1\u5b9e**\u7684\u90a3\u534a\uff1a\u300c\u5bf9**\u65b0\u5165\u573a**\u7684\u654c\u65b9\u76ee\u6807\u65bd\u653e\u300d\u3002"
                 "\u2757\u300c\u82e5\u65bd\u653e\u524d**\u76ee\u6807\u88ab\u6d88\u706d**\u300d\u90a3\u534a**\u6ca1\u5199** \u2717\uff1a"
                 "\u6e38\u620f\u7684\u8c13\u8bcd\u662f `ByTargetAliveState{ModifierOwnerEntity}`\uff08\u770b\u7684\u662f**\u4e07\u654c\u81ea\u5df1**\u8fd8\u5728\u4e0d\u5728\uff09\uff0c"
                 "\u800c\u4e0d\u662f\u770b\u67d0\u4e2a\u654c\u4eba\u6b7b\u6ca1\u6b7b \u2717\u3002"),
    })

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1404 now carries both rules")
print("     ids: %s" % [r.get("id") for r in rules if str(r.get("id", "")).startswith("memosprite_ode_")])
