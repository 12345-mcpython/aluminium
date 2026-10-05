package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * `REMOVE_STATE` with `"kind": "own"`: take off only what THIS rule owner applied (2026-10-02).
 *
 * <p>Reader: 1415 memosprite skill 12, whose lifetime is 「持续至阿格莱雅退出【至高之姿】状态」. Removing by NAME alone took a pre-existing
 * `ALL_DAMAGE_TYPE_BOOST` of hers as well, so the removal needed the origin filter `EXTEND_BUFF` has always used.
 *
 * <p>⭐ The reading is the FILTER, not "something was removed": a modifier the owner did NOT apply must survive a `kind: "own"` sweep, and the
 * same sweep without that kind must take it. Both halves run here, because either one alone is satisfiable by a filter that does nothing.
 *
 * <p>Diagnosed on the way here: two applications of the SAME attribute on the same unit REPLACE each other (only one modifier remained), which
 * is why an earlier two-owner reading looked like the filter had failed. This test therefore uses one attribute applied by the other unit.
 */
public class RemoveBySourceTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1402;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;

    private static double attackOf(Character unit) {
        return unit.getAttribute(AttributeType.ATTACK).get();
    }

    /** The owner's `kind: "own"` sweep must NOT touch a modifier somebody else applied. */
    @Test
    public void aKindOwnSweepLeavesAnotherAppliersModifier() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(owner, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        EffectSpec theirs = new EffectSpec();
        TriggerSpecs.set(theirs, "op", "MODIFY_ATTR");
        TriggerSpecs.set(theirs, "attribute", "ATTACK");
        TriggerSpecs.set(theirs, "percent", 0.25);
        TriggerSpecs.set(theirs, "permanent", Boolean.TRUE);
        TriggerSpecs.set(theirs, "target", "all_allies");
        other.setTriggerTable(new TriggerTable(OTHER, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), theirs))));
        battle.fireTriggers(TriggerEvent.TURN_START);
        battle.processRequests();

        double boosted = attackOf(owner);
        double unpolluted = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(boosted > unpolluted, "precondition: the other unit copy really landed");

        EffectSpec strip = new EffectSpec();
        TriggerSpecs.set(strip, "op", "REMOVE_STATE");
        TriggerSpecs.set(strip, "attribute", "ATTACK");
        TriggerSpecs.set(strip, "kind", "own");
        TriggerSpecs.set(strip, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), strip))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);
        battle.processRequests();

        System.out.println("[by_source] applied by the OTHER unit: " + unpolluted + " -> " + boosted
                + " ; after the OWNER kind=own sweep: " + attackOf(owner));
        Assertions.assertEquals(boosted, attackOf(owner), 1e-6,
                "a modifier the owner never applied must survive a kind=own sweep -- that is the whole filter");
    }
}
