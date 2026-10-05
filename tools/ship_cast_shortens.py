"""1415's memosprite skill 03 「等待，在所有的过去」, its fourth sentence (2026-10-02).

Verbatim (1141503, params [0.12, 0]): 「德谬歌的速度保持为0，并且不会出现在行动序列上，在场时为界外。昔涟的生命值百分比变化时，德谬歌的生命值百分比也会相应变化。德谬歌在场时，昔涟与德谬歌的生命上限提高 #1%。
<b>德谬歌施放技能后使自身所有持续效果持续回合数减1。</b>」

That last sentence is the one today's vocabulary can carry WHOLE:
  * `EXTEND_BUFF` with `"kind": "all"` is exactly 「使自身**所有**…」 -- the engine's own note names that reader (1506's talent and its E2);
  * `requireSignedTurns` means the count may be NEGATIVE, which is what 减1 needs;
  * and the gate is `actor is_summon`: a rule in `characters/1415.json` has the memosprite's MASTER as `self`, so `actor == self` would never hold for its own cast (measured last round on SUMMONED).

⛔ Registered rather than written from the same sentence: 「速度保持为0…在场时为界外」 (a property of the memosprite's row, not a rule), 「生命值百分比…相应变化」 (needs a link
between two units' HP), and 「昔涟与德谬歌的生命上限提高 #1%」 (needs a "my master" selector -- measured: the engine has none).
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 3

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if not rows:
    sys.exit("REFUSING: slot %d has no parameter rows" % SLOT)
print("ok   slot %d rows: %s .. %s" % (SLOT, rows[0], rows[-1]))

doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "after_it_casts_every_ongoing_effect_shortens_by_one"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "SKILL_CAST",
    "when": ["actor is_summon"],
    "do": [{"op": "EXTEND_BUFF", "kind": "all", "turns": -1, "target": "self"}],
    "source": ("1415 昔涟 忆灵技能 03 「等待，在所有的过去」（数据槽位 03，SkillID 1141503）："
               "「**德谬歌施放技能后使自身所有持续效果持续回合数减 1**。」"),
    "note": ("⭐ **所有**→ `EXTEND_BUFF` 的 `\"kind\": \"all\"`（引擎自己的注释点名了这个读者：1506 的天赋与其 E2）。"
             "⭐ **减 1** → `turns: -1`（`requireSignedTurns` 允许负数）。⭐ **门是 `actor is_summon`**："
             "本文件的 `self` 是它的**主人**，所以 `actor == self` 对它自己的施放永远不成立（**实测**）。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1415 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    print("ok   skill_effects.json already has 11415/%d" % SLOT)
else:
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": ("1415 昔涟 忆灵技能 03 「等待，在所有的过去」（数据槽位 03）："
                   "工作在**规则侧**，所以是 `Rules` 形状。"),
        "note": ("⭐ 本条技能的**第四句**已成句；其余三句（速度为0、生命百分比联动、生命上限 +12%）"
                 "分别是行为属性、需要两单位链接、需要“我的主人”选择器（**实测：引擎没有**）。"),
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 03, fourth sentence: 「德谬歌施放技能后使自身所有持续效果持续回合数减 1」 (2026-10-02).
 *
 * <p>⭐ Two scenes that differ by exactly one thing: whether his table still carries that rule. A one-turn effect is put on him first, so the reading is unambiguous --
 * after his own skill cast it is GONE, and in the control scene it is still there.
 */
public class CastShortensOwnEffectsTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "测试持续";
    private static final String RULE = "after_it_casts_every_ongoing_effect_shortens_by_one";

    @Test
    public void itsOwnCastTakesATurnOffEveryEffect() {
        boolean withRule = effectSurvivesItsCast(true);
        boolean withoutRule = effectSurvivesItsCast(false);
        System.out.println("[shortens] after its own cast, the one-turn effect survives: with the rule = " + withRule
                + " ; without it = " + withoutRule);

        Assertions.assertTrue(withoutRule, "precondition: the effect outlives the cast when the rule is absent");
        Assertions.assertFalse(withRule,
                "\\u300c\\u5fb7\\u8c2c\\u6b4c\\u65bd\\u653e\\u6280\\u80fd\\u540e\\u4f7f\\u81ea\\u8eab\\u6240\\u6709\\u6301\\u7eed\\u6548\\u679c\\u6301\\u7eed\\u56de\\u5408\\u6570\\u51cf 1\\u300d-- one turn comes off, so a one-turn effect ends");
    }

    private static boolean effectSurvivesItsCast(boolean withRule) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        if (!withRule) {
            var kept = new java.util.ArrayList<com.laosun.aluminium.beans.TriggerSpec>();
            for (var r : demiurge.getTriggerTable().rulesFor(TriggerEvent.SKILL_CAST)) {
                if (!RULE.equals(r.getId())) {
                    kept.add(r);
                }
            }
            // keep every OTHER rule of its table as well
            for (var event : TriggerEvent.values()) {
                if (event == TriggerEvent.SKILL_CAST) {
                    continue;
                }
                kept.addAll(demiurge.getTriggerTable().rulesFor(event));
            }
            demiurge.setTriggerTable(new TriggerTable(CYRENE, kept));
        }

        // a ONE-turn effect on the memosprite itself
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "turns", 1);
        TriggerSpecs.set(mark, "target", "self");
        demiurge.getTriggerTable();
        demiurge.setTriggerTable(new TriggerTable(CYRENE, new java.util.ArrayList<>(demiurge.getTriggerTable().rulesFor(TriggerEvent.SKILL_CAST))));
        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: the memosprite carries slot 1");

        // apply the mark directly, then let it cast
        com.laosun.aluminium.models.buff.StatModifierBuff unused = null;
        var applied = new EffectSpec();
        TriggerSpecs.set(applied, "op", "APPLY_BUFF");
        TriggerSpecs.set(applied, "buff", MARK);
        TriggerSpecs.set(applied, "turns", 5);
        TriggerSpecs.set(applied, "target", "self");
        battle.fireTriggers(TriggerEvent.TURN_START);

        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return demiurge.getBuffManager().hasState(MARK);
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/CastShortensOwnEffectsTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
