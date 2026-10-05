"""1415's memosprite skill 05 「你好，世界♪」 -- 「德谬歌被召唤时，解除我方全体控制类负面状态。」 (2026-10-02).

Slot 05 of 11415 (SkillID 1141505, params: none -- the sentence has no numbers at all). Measured before writing:
  * `TriggerEvent.SUMMONED` exists and is fired by `Battle:1366` (`fireTriggers(SUMMONED, summon, null, 0, 0)`), so 「被召唤时」 is addressable;
  * the engine already has a DISPEL op that may name a debuff CLASS -- its own comment points at this very sentence ("1415's memosprite skill 8");
  * `DebuffClass` is {control, dot} and each buff reports its own class (`AbstractBuff.debuffClass()`), so "所有控制类" is a sweep, not a list of names.
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
    "when": ["actor == self"],
    "do": [{"op": "DISPEL", "kind": "control", "target": "all_allies"}],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 05 \u300c\u4f60\u597d\uff0c\u4e16\u754c\u266a\u300d\uff08\u6570\u636e\u69fd\u4f4d 05\uff0cSkillID 1141505\uff09\uff1a"
               "\u300c\u5fb7\u8c2c\u6b4c\u88ab\u53ec\u5524\u65f6\uff0c**\u89e3\u9664\u6211\u65b9\u5168\u4f53\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u3002\u300d"),
    "note": ("\u2b50 \u539f\u53e5\u53ea\u6709\u4e00\u53e5\u3001**\u6ca1\u6709\u4efb\u4f55\u6570\u503c**\uff08\u53c2\u6570\u8868\u662f\u7a7a\u7684\uff09\u3002\u2b50 \u201c**\u6240\u6709**\u63a7\u5236\u7c7b\u201d\u662f**\u6309\u7c7b\u522b\u626b**"
             "\uff08`AbstractBuff.debuffClass()`\uff09\uff0c\u4e0d\u662f\u7f57\u5217\u540d\u5b57 \u2014\u2014 \u5f15\u64ce\u7684 `dispel` \u6ce8\u91ca\u91cc\u5199\u7740 "
             "\u201ca control written tomorrow is covered by a resistance written today\u201d\u3002\u2b50 \u4e8b\u4ef6\u662f `SUMMONED`\uff08`Battle:1366` \u53d1\uff09\u3002"),
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
 * <p>⭐ The reading is the CLASS, not "something was removed": a control on the ally is gone after the summon, and a DOT on him is still there. A sweep that took
 * everything off would satisfy the first half and be wrong about the second.
 */
public class SummonedClearsControlTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CONTROL = "\u51bb\u7ed3";      // a control our content already uses
    private static final String DOT = "\u88c2\u4f24";          // and a DOT

    @Test
    public void onlyTheControlClassGoes() throws Exception {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        ally = battle.characters.get(1);

        // put one of each class on the ally, with an in-test rule that runs at battle start
        Character finalAlly = ally;
        EffectSpec control = new EffectSpec();
        TriggerSpecs.set(control, "op", "APPLY_CONTROL");
        TriggerSpecs.set(control, "control", CONTROL);
        TriggerSpecs.set(control, "turns", 3);
        TriggerSpecs.set(control, "target", "self");
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "buff", DOT);
        TriggerSpecs.set(dot, "turns", 3);
        TriggerSpecs.set(dot, "percent", 0.1);
        TriggerSpecs.set(dot, "target", "self");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), control),
                TriggerSpecs.rule("BATTLE_START", List.of(), dot))));
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START);

        boolean controlBefore = finalAlly.getBuffManager().hasState(CONTROL);
        boolean dotBefore = finalAlly.getBuffManager().hasState(DOT);
        Assertions.assertTrue(controlBefore, "precondition: the control landed");
        Assertions.assertTrue(dotBefore, "precondition: the DOT landed");

        battle.summonServant(cyrene);
        battle.processRequests();

        boolean controlAfter = finalAlly.getBuffManager().hasState(CONTROL);
        boolean dotAfter = finalAlly.getBuffManager().hasState(DOT);
        System.out.println("[summoned_clears] control " + controlBefore + " -> " + controlAfter
                + " ; dot " + dotBefore + " -> " + dotAfter);

        Assertions.assertFalse(controlAfter, "\\u300c\\u89e3\\u9664\\u6211\\u65b9\\u5168\\u4f53**\\u63a7\\u5236\\u7c7b**\\u8d1f\\u9762\\u72b6\\u6001\\u300d-- the control is gone");
        Assertions.assertTrue(dotAfter, "and the DOT is NOT -- \\u300c\\u63a7\\u5236\\u7c7b\\u300d names one class, not every debuff");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SummonedClearsControlTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
