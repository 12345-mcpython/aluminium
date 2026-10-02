package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The {@code hp_at_most} condition (2026-10-02): 「当前生命值百分比小于等于 X」, read off the unit itself.
 *
 * <p>Eight-plus clauses state it and every one of them says 小于等于, so the boundary is pinned from BOTH sides: just
 * below the share fires, just above it does not, and EXACTLY at it does.
 */
public class HpAtMostTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Pay 1 energy whenever this unit starts a turn at or below half health. */
    @Test
    public void theBoundaryIsInclusive() {
        double share = 0.5;
        Assertions.assertTrue(fires(share * 0.98), "just below the share fires");
        Assertions.assertTrue(fires(share), "EXACTLY at the share fires (every clause says inclusive)");
        Assertions.assertFalse(fires(share * 1.02), "just above the share does not fire");
    }

    // ==================================================================

    /** True when the rule paid out on a turn start at {@code share} of the unit's maximum health. */
    private static boolean fires(double share) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_ENERGY");
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("TURN_START",
                List.of("actor == self", "self_hp_at_most:0.5"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.applyTrueDamage(enemy, owner, DamageElement.FIRE, owner.getMaxHp() * (1 - share));
        double before = owner.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getCurrentEnergy() > before;
    }
}
