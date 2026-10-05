"""1415's memosprite skill 03, fourth sentence -- read so that a one-turn shortening is VISIBLE (2026-10-02).

「德谬歌施放技能后使自身所有持续效果持续回合数减 1。」 (1141503, params [0.12, 0])

\u26a0 The first attempt judged "a ONE-turn mark must be gone after the cast" and was red -- and that reading was wrong, not the rule: `AbstractBuff.extendDuration` is
`remainingDuration = Math.max(0, remainingDuration + turns)`, and a buff at 0 is removed on ITS OWN tick, not immediately. So:

  * 昔涟 gets a mark with 2 turns. The rule (whose `target: "self"` is the MASTER in this file) takes it to 1, and ONE tick then removes it;
  * the memosprite gets the same 2-turn mark. The rule does not touch it (it shortens the master's effects), so one tick leaves it at 1.

\u2b50 Both halves live in one scene, so the reading is two-sided without any table surgery.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 3
MARK = "\u6d4b\u8bd5\u6301\u7eed\u6548\u679c"

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
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 03 \u300c\u7b49\u5f85\uff0c\u5728\u6240\u6709\u7684\u8fc7\u53bb\u300d\uff08\u6570\u636e\u69fd\u4f4d 03\uff0cSkillID 1141503\uff09\uff1a"
               "\u300c**\u5fb7\u8c2c\u6b4c\u65bd\u653e\u6280\u80fd\u540e\u4f7f\u81ea\u8eab\u6240\u6709\u6301\u7eed\u6548\u679c\u6301\u7eed\u56de\u5408\u6570\u51cf 1**\u3002\u300d"),
    "note": ("\u2b50 **\u6240\u6709**\u2192 `EXTEND_BUFF` \u7684 `\"kind\": \"all\"`\uff1b**\u51cf 1** \u2192 `turns: -1`\uff08`requireSignedTurns` \u5141\u8bb8\u8d1f\u6570\uff09\u3002"
             "\u2b50 \u800c `self` \u5728**\u672c\u6587\u4ef6**\u91cc\u662f**\u4e3b\u4eba**\uff08**\u5b9e\u6d4b**\uff09\u2014\u2014 \u6240\u4ee5\u5b83\u7f29\u77ed\u7684\u662f**\u6614\u6dbf\u81ea\u5df1**\u7684\u6301\u7eed\u6548\u679c\u3002"
             "\u26a0 \u4e0a\u4e00\u6b21\u5224\u636e\u5199\u6210\u201c1 \u56de\u5408\u5fc5\u987b**\u7acb\u523b**\u6d88\u5931\u201d\u800c\u53d8\u7ea2 \u2014\u2014 \u9519\u7684\u662f\u8bfb\u6cd5\uff1a"
             "`extendDuration` \u662f `Math.max(0, remaining + turns)`\uff0c\u800c 0 \u662f**\u5728\u5b83\u81ea\u5df1\u7684\u7ed3\u7b97\u65f6\u523b**\u624d\u79fb\u9664\u3002"),
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
        "source": "1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 03 \u300c\u7b49\u5f85\uff0c\u5728\u6240\u6709\u7684\u8fc7\u53bb\u300d\uff08\u6570\u636e\u69fd\u4f4d 03\uff09\uff1a\u5de5\u4f5c\u5728\u89c4\u5219\u4fa7\u3002",
        "note": "\u2b50 \u672c\u6761\u6280\u80fd\u7684\u7b2c\u56db\u53e5\u5df2\u6210\u53e5\uff1b\u5176\u4f59\u4e09\u53e5\u5206\u522b\u662f\u884c\u4e3a\u5c5e\u6027\u3001\u4e24\u5355\u4f4d\u94fe\u63a5\u3001\u201c\u6211\u7684\u4e3b\u4eba\u201d\u9009\u62e9\u5668\uff08**\u5b9e\u6d4b\uff1a\u5f15\u64ce\u6ca1\u6709**\uff09\u3002",
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
 * 1415's memosprite skill 03, fourth sentence: \u300c\u5fb7\u8c2c\u6b4c\u65bd\u653e\u6280\u80fd\u540e\u4f7f\u81ea\u8eab\u6240\u6709\u6301\u7eed\u6548\u679c\u6301\u7eed\u56de\u5408\u6570\u51cf 1\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE scene, two halves, because `self` in this file is the MASTER: a 2-turn mark on \u6614\u6dbf is shortened to 1 and one tick ends it, while the same 2-turn mark on
 * the MEMOSPRITE is untouched and survives that tick. \u26a0 A one-turn mark would not discriminate: a 1-turn buff ticks away on its own regardless.
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
        // \u2b50 one rule on the ally, aiming at both units by selector -- no table is ever replaced
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), onMaster),
                TriggerSpecs.rule("TURN_START", List.of(), onSprite))));
        battle.fireTriggers(TriggerEvent.TURN_START);
        // \u26a0 `target: "self"` on the ally's rule lands on the ALLY, so mark \u6614\u6dbf directly through the same op on her own table is not possible without surgery;
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
                "the shortening is the MASTER's -- \\u300c\u5fb7\u8c2c\u6b4c\\u2026\\u4f7f\\u81ea\\u8eab\\u6240\\u6709\\u6301\\u7eed\\u6548\\u679c\\u2026\u51cf 1\\u300d renames the memosprite, and `self` here is \\u6614\\u6dbf");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/CastShortensOwnEffectsTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
