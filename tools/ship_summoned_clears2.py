"""1415's memosprite skill 05 「你好，世界♪」 -- 「德谬歌被召唤时，解除我方全体控制类负面状态。」 (2026-10-02), second attempt.

Measured this round, and it is why the first attempt never fired:
  * `Battle.fireSummoned()` IS reached -- it sits inside `processRequests()` (L1346), with the comment 「which is what a rule answering 「被召唤时」 needs」 -- and it
    fires `fireTriggers(SUMMONED, summon, null, 0, 0)`, so the ACTOR is the summoned unit;
  * a rule living in `characters/1415.json` has the MEMOSPRITE's master scope, so its `self` is not the summon: the gate for 「德谬歌被召唤时」 is `actor is_summon`,
    the vocabulary the odes already use.

The op is `DISPEL` with a debuff CLASS -- the engine's own comment points at this very sentence ("1415's memosprite skill 8"), and 「所有」 spells no count.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 5

doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "summoned_clears_every_control"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "SUMMONED",
    "when": ["actor is_summon"],
    "do": [{"op": "DISPEL", "kind": "control", "target": "all_allies"}],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 05 \u300c\u4f60\u597d\uff0c\u4e16\u754c\u266a\u300d\uff08\u6570\u636e\u69fd\u4f4d 05\uff0cSkillID 1141505\uff09\uff1a"
               "\u300c\u5fb7\u8c2c\u6b4c\u88ab\u53ec\u5524\u65f6\uff0c**\u89e3\u9664\u6211\u65b9\u5168\u4f53\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u3002\u300d"),
    "note": ("\u2b50 \u539f\u53e5**\u6ca1\u6709\u4efb\u4f55\u6570\u503c**\uff08\u53c2\u6570\u8868\u662f\u7a7a\u7684\uff09\u3002\u2b50 \u95e8\u662f `actor is_summon`\uff08**\u5b9e\u6d4b**\uff1a"
             "`SUMMONED` \u7684 `actor` \u662f**\u88ab\u53ec\u5524\u8005**\uff0c\u800c\u672c\u6587\u4ef6\u7684 `self` \u662f\u5b83\u7684**\u4e3b\u4eba** \u2014\u2014 \u6240\u4ee5 `actor == self` \u6c38\u8fdc\u4e0d\u6210\u7acb\uff09\u3002"
             "\u2b50 \u201c**\u6240\u6709**\u63a7\u5236\u7c7b\u201d\u662f**\u6309\u7c7b\u522b\u626b**\uff08`AbstractBuff.debuffClass()`\uff09\u3002"),
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
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 05 \u300c\u4f60\u597d\uff0c\u4e16\u754c\u266a\u300d\uff08\u6570\u636e\u69fd\u4f4d 05\uff09\uff1a"
                   "\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\uff0c\u6574\u6761\u5fc6\u7075\u6280\u80fd\u5c31\u6c38\u8fdc\u65bd\u653e\u4e0d\u4e86\u3002",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 05 「你好，世界♪」: 「德谬歌被召唤时，解除我方全体控制类负面状态。」 (2026-10-02).
 *
 * <p>⭐ The reading is the CLASS, not "something was removed": a control on an ally is gone after the summon, and a DOT on him is still there. A sweep that took
 * everything off would pass the first half while being wrong about the second.
 */
public class SummonedClearsControlTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CONTROL = "\u51bb\u7ed3";
    private static final String DOT = "\u88c2\u4f24";

    @Test
    public void onlyTheControlClassGoes() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        Character finalAlly = battle.characters.get(1);

        EffectSpec control = new EffectSpec();
        TriggerSpecs.set(control, "op", "APPLY_CONTROL");
        TriggerSpecs.set(control, "control", CONTROL);
        TriggerSpecs.set(control, "turns", 3);
        TriggerSpecs.set(control, "target", "self");
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "buff", DOT);
        TriggerSpecs.set(dot, "turns", 3);
        TriggerSpecs.set(dot, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(dot, "percent", 0.1);
        TriggerSpecs.set(dot, "element", "Ice");
        TriggerSpecs.set(dot, "kind", "dot");
        TriggerSpecs.set(dot, "target", "self");
        finalAlly.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), control),
                TriggerSpecs.rule("BATTLE_START", List.of(), dot))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);

        Assertions.assertTrue(finalAlly.getBuffManager().hasState(CONTROL), "precondition: the control landed");
        Assertions.assertTrue(finalAlly.getBuffManager().hasState(DOT), "precondition: the DOT landed");

        battle.summonServant(cyrene);          // \\u26a0 `processRequests` is what reaches `fireSummoned`
        battle.processRequests();

        boolean controlAfter = finalAlly.getBuffManager().hasState(CONTROL);
        boolean dotAfter = finalAlly.getBuffManager().hasState(DOT);
        System.out.println("[summoned_clears] control -> " + controlAfter + " ; dot -> " + dotAfter);

        Assertions.assertFalse(controlAfter,
                "\\u300c\\u5fb7\\u8c2c\\u6b4c\\u88ab\\u53ec\\u5524\\u65f6\\uff0c\\u89e3\\u9664\\u6211\\u65b9\\u5168\\u4f53**\\u63a7\\u5236\\u7c7b**\\u8d1f\\u9762\\u72b6\\u6001\\u300d-- the control is gone");
        Assertions.assertTrue(dotAfter,
                "and the DOT is NOT -- \\u300c\\u63a7\\u5236\\u7c7b\\u300d names one class, not every debuff");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SummonedClearsControlTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
