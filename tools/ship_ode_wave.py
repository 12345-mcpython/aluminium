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
        "source": ("1415 昔涟 忆灵技能 8（数据槽位 16）：那句把状态挂在他身上的部分。"
                   "（对应 tbgd 的 `MServant_CyreneServant_00_AmazingBuff_Mydeimos`：一个持续到战斗结束的 modifier。）"),
        "note": "★ 持久（`permanent: true`），因为它是“这句话还在他身上”这个事实本身。",
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
        "source": ("1415 昔涟 忆灵技能 8：「若施放前目标被消灭则对**新入场**的敌方目标施放」。"
                   "（tbgd 里它就是 `OnWaveMonster` ⇒ `TurnInsertAction{TargetType: ModifierOwnerEntity, AutoCast: true}`："
                   "有波次怪物入场，则**他自己**再行动一次；打谁是**技能自己**的事。）"),
        "note": ("⚠ 本条只写了原句里**能被证实**的那半：「对**新入场**的敌方目标施放」。"
                 "❗「若施放前**目标被消灭**」那半**没写** ✗："
                 "游戏的谓词是 `ByTargetAliveState{ModifierOwnerEntity}`（看的是**万敌自己**还在不在），"
                 "而不是看某个敌人死没死 ✗。"),
    })

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1404 now carries both rules")
print("     ids: %s" % [r.get("id") for r in rules if str(r.get("id", "")).startswith("memosprite_ode_")])
