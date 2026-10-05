"""Judge the `ally_cid:` selector on its own, and register the sky-ode stack sentence (2026-10-02).

The sentence 「德谬歌施放忆灵技时，使风堇获得2层…」 needs the state's stack CAP to deliver 2: `StackBuff` is `Math.max(1, maxStacks)`, so with no cap stated the count clamps to 1
-- measured, the named character read 1. And the cap is NOT in the data I could reach: no stack/layer field in the servant-ability files, and no `StatusConfig` row carries that
state's name. So the sentence is registered rather than written with a cap invented to make the number come out.

What ships instead is the CAPABILITY the sentence needed: a selector that names a character outright. Its judge uses an in-test rule with an explicit `max_stacks`, so the
reading is about the selector and nothing else.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
RULE_ID = "memosprite_ode_of_sky_stacks_hyacine_when_it_casts"

doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
kept = [r for r in rules if r.get("id") != RULE_ID]
if len(kept) == len(rules):
    sys.exit("REFUSING: the clause is not there to roll back")
if isinstance(doc, dict):
    doc["rules"] = kept
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(
    json.dumps(doc if isinstance(doc, dict) else kept, ensure_ascii=False, indent=2) + "\n")
print("ok   the stack clause is rolled back (%d -> %d rules)" % (len(rules), len(kept)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(19) in effects.get("11415", {}):
    effects["11415"]["19"]["note"] = ("\u2b50 2026-10-02\uff1a\u540c\u4e00\u6761\u6280\u80fd\u7684**\u80fd\u91cf\u90a3\u534a**\u5df2\u6210\u53e5\uff1b"
                                      "\u800c\u201c\u4f7f\u98ce\u5807\u83b7\u5f97 **2 \u5c42**\u201d\u90a3\u53e5**\u4ecd\u767b\u8bb0** \u2014\u2014 "
                                      "\u5b83\u9700\u8981\u90a3\u4e2a\u72b6\u6001\u7684**\u5c42\u6570\u4e0a\u9650**\uff0c\u800c\u5b83**\u4e0d\u5728\u6211\u80fd\u8bfb\u5230\u7684\u8868\u91cc**"
                                      "\uff08\u5b9e\u6d4b\uff1a\u5fc6\u7075\u6280\u80fd\u8868\u65e0\u5c42\u6570\u5b57\u6bb5\uff1b`StatusConfig` \u65e0\u8be5\u72b6\u6001\u540d\uff09\u3002")
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json 11415/19 note records the registration")

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
 * The `ally_cid:<cid>` selector: naming a character outright (2026-10-02).
 *
 * <p>Reader: 1415's sky ode -- 「德谬歌施放忆灵技时，使<b>风堇</b>获得2层…」 -- a rule that lives in the memosprite's file, where `self` is the MASTER, so it has to reach another
 * character. The closed selector set had positions (`party_first`, `next_ally`) and predicates (`lowest_hp_ally`), but nothing that NAMES one.
 *
 * <p>\u2b50 The reading is exactly the selector's contract, in one battle: the named cid gets the stacks and a DIFFERENT ally present gets none. The rule is in-test so the
 * judge is about the selector and nothing else -- in particular it does not depend on any state's stack cap.
 */
public class AllyCidSelectorTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1415;
    private static final int NAMED = 1409;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u6d4b\\u8bd5\\u5c42\\u6570";

    @Test
    public void theNamedCidGetsItAndADifferentAllyDoesNot() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character named = CharacterFactory.create(NAMED, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(owner, named, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        owner = battle.characters.get(0);
        named = battle.characters.get(1);
        other = battle.characters.get(2);

        EffectSpec stacks = new EffectSpec();
        TriggerSpecs.set(stacks, "op", "ADD_STACK");
        TriggerSpecs.set(stacks, "buff", MARK);
        TriggerSpecs.set(stacks, "amount", 2);
        TriggerSpecs.set(stacks, "max_stacks", 5);          // stated here so the reading is about the SELECTOR, not about a cap
        TriggerSpecs.set(stacks, "permanent", Boolean.TRUE);
        TriggerSpecs.set(stacks, "target", "ally_cid:" + NAMED);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), stacks))));
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START);

        int namedHas = named.getBuffManager().stacksOf(MARK);
        int otherHas = other.getBuffManager().stacksOf(MARK);
        System.out.println("[ally_cid] the NAMED character has " + namedHas + " ; a different ally has " + otherHas);

        Assertions.assertEquals(2, namedHas, "\\u300c\\u4f7f**\\u98ce\\u5807**\\u83b7\\u5f97 2 \\u5c42\\u300d-- the named cid, and the count the rule states");
        Assertions.assertEquals(0, otherHas,
                "and a different ally gets nothing -- a fallback to \\"the owner\\" (or to everybody) would pass one half and fail this one");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/AllyCidSelectorTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written (in-test, no content, no cap of anyone's)")
