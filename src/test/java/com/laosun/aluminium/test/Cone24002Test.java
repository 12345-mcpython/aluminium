package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 24002, its first sentence: after taking a hit, an UNSHIELDED wearer gains a shield worth 16% of its max HP for
 * 2 turns, at most once every 3 turns.
 *
 * <p>\u2b50 \u300c\u672a\u6301\u6709\u62a4\u76fe\u300d is written {@code !self has_shield}: the {@code !} prefix negates any party condition, and
 * {@code has_shield} is one. The cooldown is the rule-level turn counter, so the second hit must change nothing.
 */
public class Cone24002Test {
    private static final int CONE = 24002;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int COOLDOWN = 3;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void takeHit(Battle battle) {
        battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, wearer, 1, 100);
    }

    @Test
    public void anUnshieldedHitGrantsAMaxHpShield() {
        Battle battle = battle(true);
        double expected = wearer.getMaxHp() * 0.16;
        Assertions.assertEquals(0.0, wearer.getShield(), 1e-9, "nothing yet");
        takeHit(battle);
        double shield = wearer.getShield();
        System.out.println("[24002] after a hit: shield=" + shield + " expected=" + expected + " maxHp="
                + wearer.getMaxHp());
        Assertions.assertEquals(expected, shield, 1e-6, "the shield is 16% of the wearer's own max HP");
    }

    @Test
    public void anAlreadyShieldedWearerGetsNothing() {
        Battle battle = battle(true);
        battle.grantShield(wearer, 500);
        double before = wearer.getShield();
        takeHit(battle);
        System.out.println("[24002] already shielded: " + before + " -> " + wearer.getShield());
        Assertions.assertEquals(before, wearer.getShield(), 1e-9, "\u672a\u6301\u6709\u62a4\u76fe is the gate (false case)");
    }

    @Test
    public void theCooldownStopsASecondGrant() {
        Battle battle = battle(true);
        takeHit(battle);
        double afterFirst = wearer.getShield();
        // drop the shield without touching the cooldown: a raw write is exactly what an unshielded state is
        wearer.setShield(0);
        takeHit(battle);
        System.out.println("[24002] second hit within " + COOLDOWN + " turns: shield=" + wearer.getShield()
                + " (first was " + afterFirst + ")");
        Assertions.assertEquals(0.0, wearer.getShield(), 1e-9,
                "the cooldown is " + COOLDOWN + " turns, so an immediate second hit grants nothing");
    }

    @Test
    public void theSpecPinsTheShareTheDurationAndTheCooldown() {
        Battle battle = battle(true);
        int pinned = 0;
        // \u26a0 The gate is `target == self` (the one HIT is the target), so the context must name the wearer as the target.
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.TAKING_HIT,
                new TriggerTable.TriggerContext(wearer, enemy, wearer, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone24002_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if (!"SHIELD".equals(effect.getOp())) {
                    continue;
                }
                pinned++;
                System.out.println("[24002] spec scale=" + effect.getScale() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns());
                Assertions.assertEquals(0.16, effect.getPercent(), 1e-9, "16% at rank 1");
                Assertions.assertEquals(2, effect.getTurns(), "for 2 turns");
            }
        }
        Assertions.assertEquals(1, pinned, "one shielding rule from this cone");
    }

    @Test
    public void withoutTheConeNothingHappens() {
        Battle battle = battle(false);
        takeHit(battle);
        Assertions.assertEquals(0.0, wearer.getShield(), 1e-9, "no cone, no shield (false case)");
    }
}
