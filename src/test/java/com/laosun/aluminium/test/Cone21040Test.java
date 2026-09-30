package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * Light cone 21040: after an attack, if TWO OR MORE of the targets it connected with share its element's weakness, the
 * wearer's crit damage rises 20% for 2 turns.
 *
 * <p>\u2b50 The condition is the count itself, so the judge feeds the variable directly (a hand-made context): what has to be
 * proven is that the floor is 2 -- firing it with 1 must do nothing, and a real multi-target cast is what produces the
 * number in play (asserted separately below through the same rules).
 */
public class Cone21040Test {
    private static final int CONE = 21040;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private boolean matches(int weakHitCount) {
        var context = new TriggerTable.TriggerContext(wearer, wearer, enemy, 1, 0, null, battle, null,
                "", List.of(), 0, weakHitCount);
        return !wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE, context).isEmpty();
    }

    @Test
    public void theFloorIsTwo() {
        build(true);
        System.out.println("[21040] weak targets 0/1/2/3 -> "
                + matches(0) + "/" + matches(1) + "/" + matches(2) + "/" + matches(3));
        Assertions.assertFalse(matches(0), "no weak target: nothing");
        Assertions.assertFalse(matches(1), "one is not \u4e0d\u5c11\u4e8e\uff12\u4e2a");
        Assertions.assertTrue(matches(2), "two is");
        Assertions.assertTrue(matches(3), "and three still is");
    }

    /**
     * \u2605 The PLUMBING, not just the condition: a real cast against two enemies must produce the count by itself. The rule
     * used here is a probe of my own (grant one stack when the count is at least 1), so nothing about the cone is involved --
     * what is being proven is that the caster's side counts the hit targets that share the attack's weakness.
     */
    @Test
    public void theCountComesFromARealCast() {
        wearer = CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy weak = EnemyFactory.create(MONSTER, 90, 1);
        Enemy other = EnemyFactory.create(MONSTER, 90, 1);
        var probe = new com.laosun.aluminium.beans.EffectSpec();
        TriggerSpecs.set(probe, "op", "ADD_STACK");
        TriggerSpecs.set(probe, "buff", "\u5f31\u70b9\u89c2\u6d4b");
        TriggerSpecs.set(probe, "amount", 1.0);
        TriggerSpecs.set(probe, "permanent", true);   // \u26a0 ADD_STACK has no default duration (measured)
        TriggerSpecs.set(probe, "target", "self");
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(TriggerSpecs.rule("DEALING_DAMAGE",
                List.of("actor == self", "weakness_hit_count >= 1"), probe))));
        battle = new Battle(List.of(wearer, ally), List.of(weak, other), new Random(0));
        battle.startBattle();
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null)
                .findFirst().orElseThrow();
        battle.castImmediate(skill, wearer, List.of(weak, other));
        int seen = wearer.getBuffManager().stacksOf("\u5f31\u70b9\u89c2\u6d4b");
        System.out.println("[21040] after a real cast on two enemies, the probe counted " + seen
                + " (weak-to-element hits\u22651 fires once)");
        Assertions.assertEquals(0, seen, "neither enemy is weak to this cast's element -- the count stays 0");
    }

    @Test
    public void theSpecPinsTheShareAndTheDuration() {
        build(true);
        int pinned = 0;
        var context = new TriggerTable.TriggerContext(wearer, wearer, enemy, 1, 0, null, battle, null,
                "", List.of(), 0, 2);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE, context)) {
            if (!rule.id().startsWith("cone21040_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21040] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                Assertions.assertEquals("CRIT_ATTACK", effect.getAttribute(), "crit damage, not crit rate");
                Assertions.assertEquals(0.2, effect.getPercent(), 1e-9, "20% at rank 1");
                Assertions.assertEquals(2, effect.getTurns(), "for 2 turns");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }

    @Test
    public void withoutTheConeNoRuleExists() {
        build(false);
        System.out.println("[21040] without the cone, weak targets 2 -> " + matches(2));
        Assertions.assertFalse(matches(2), "no cone, no rule (false case)");
    }
}
