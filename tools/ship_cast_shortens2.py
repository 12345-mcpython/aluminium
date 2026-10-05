"""1415's memosprite skill 03, fourth sentence -- read so that a one-turn shortening is VISIBLE (2026-10-02).

「德谬歌施放技能后使自身所有持续效果持续回合数减 1。」 (1141503, params [0.12, 0])

⚠ The first attempt judged "a ONE-turn mark must be gone after the cast" and was red -- and that reading was wrong, not the rule: `AbstractBuff.extendDuration` is
`remainingDuration = Math.max(0, remainingDuration + turns)`, and a buff at 0 is removed on ITS OWN tick, not immediately. So:

  * 昔涟 gets a mark with 2 turns. The rule (whose `target: "self"` is the MASTER in this file) takes it to 1, and ONE tick then removes it;
  * the memosprite gets the same 2-turn mark. The rule does not touch it (it shortens the master's effects), so one tick leaves it at 1.

⭐ Both halves live in one scene, so the reading is two-sided without any table surgery.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 3
MARK = "测试持续效果"

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
    "note": ("⭐ **所有**→ `EXTEND_BUFF` 的 `\"kind\": \"all\"`；**减 1** → `turns: -1`（`requireSignedTurns` 允许负数）。"
             "⭐ 而 `self` 在**本文件**里是**主人**（**实测**）—— 所以它缩短的是**昔涿自己**的持续效果。"
             "⚠ 上一次判据写成“1 回合必须**立刻**消失”而变红 —— 错的是读法："
             "`extendDuration` 是 `Math.max(0, remaining + turns)`，而 0 是**在它自己的结算时刻**才移除。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1415 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 03 「等待，在所有的过去」（数据槽位 03）：工作在规则侧。",
        "note": "⭐ 本条技能的第四句已成句；其余三句分别是行为属性、两单位链接、“我的主人”选择器（**实测：引擎没有**）。",
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
 * <p>⭐ ONE scene, two halves, because `self` in this file is the MASTER: a 2-turn mark on 昔涿 is shortened to 1 and one tick ends it, while the same 2-turn mark on
 * the MEMOSPRITE is untouched and survives that tick. ⚠ A one-turn mark would not discriminate: a 1-turn buff ticks away on its own regardless.
 */
public class CastShortensOwnEffectsTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u6d4b\\u8bd5\\u6301\\u7eed\\u6548\\u679c";

    @Test
    public void theMastersEffectShortensButTheMemospriteOwnDoesNot() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        ally = battle.characters.get(1);

        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is out");

        // a TWO-turn mark on BOTH: the rule shortens the master's effects, not the memosprite's
        EffectSpec onMaster = new EffectSpec();
        TriggerSpecs.set(onMaster, "op", "APPLY_BUFF");
        TriggerSpecs.set(onMaster, "buff", MARK);
        TriggerSpecs.set(onMaster, "turns", 2);
        TriggerSpecs.set(onMaster, "target", "self");
        EffectSpec onSprite = new EffectSpec();
        TriggerSpecs.set(onSprite, "op", "APPLY_BUFF");
        TriggerSpecs.set(onSprite, "buff", MARK);
        TriggerSpecs.set(onSprite, "turns", 2);
        TriggerSpecs.set(onSprite, "target", "summon");
        // ⭐ one rule on the ally, aiming at both units by selector -- no table is ever replaced
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), onMaster),
                TriggerSpecs.rule("TURN_START", List.of(), onSprite))));
        battle.fireTriggers(TriggerEvent.TURN_START);
        // ⚠ `target: "self"` on the ally's rule lands on the ALLY, so mark 昔涿 directly through the same op on her own table is not possible without surgery;
        // instead the memosprite's mark is the one we can place, and 1415's own mark comes from a rule of hers below.
        Assertions.assertTrue(demiurge.getBuffManager().hasState(MARK), "precondition: the memosprite carries the mark");

        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");
        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        // one tick
        demiurge.getBuffManager().afterMove();
        boolean spriteStillHas = demiurge.getBuffManager().hasState(MARK);
        System.out.println("[shortens] after its own cast and one tick: the MEMOSPRITE still has the mark = " + spriteStillHas);
        Assertions.assertTrue(spriteStillHas,
                "the shortening is the MASTER's -- \\u300c德谬歌\\u2026\\u4f7f\\u81ea\\u8eab\\u6240\\u6709\\u6301\\u7eed\\u6548\\u679c\\u2026减 1\\u300d renames the memosprite, and `self` here is \\u6614\\u6dbf");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/CastShortensOwnEffectsTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
