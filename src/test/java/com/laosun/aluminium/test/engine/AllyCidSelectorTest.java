package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
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
 * <p>Reader: 1415's sky ode -- "德谬歌施放忆灵技时，使<b>风堇</b>获得2层…" -- a rule that lives in the memosprite's file, where `self` is the MASTER, so it has to reach another
 * character. The closed selector set had positions (`party_first`, `next_ally`) and predicates (`lowest_hp_ally`), but nothing that NAMES one.
 *
 * <p>The reading is exactly the selector's contract, in one battle: the named cid gets the stacks and a DIFFERENT ally present gets none. The rule is in-test so the
 * judge is about the selector and nothing else -- in particular it does not depend on any state's stack cap.
 */
public class AllyCidSelectorTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1415;
    private static final int NAMED = 1409;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "测试层数";

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
        TriggerSpecs.set(stacks, "amount", 2.0);
        TriggerSpecs.set(stacks, "maxStacks", 5);          // stated here so the reading is about the SELECTOR, not about a cap
        TriggerSpecs.set(stacks, "permanent", Boolean.TRUE);
        TriggerSpecs.set(stacks, "target", "ally_cid:" + NAMED);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), stacks))));
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START);

        int namedHas = named.getBuffManager().stacksOf(MARK);
        int otherHas = other.getBuffManager().stacksOf(MARK);
        System.out.println("[ally_cid] the NAMED character has " + namedHas + " ; a different ally has " + otherHas);

        Assertions.assertEquals(2, namedHas, "「使**风堇**获得 2 层」-- the named cid, and the count the rule states");
        Assertions.assertEquals(0, otherHas,
                "and a different ally gets nothing -- a fallback to \"the owner\" (or to everybody) would pass one half and fail this one");
    }
}
