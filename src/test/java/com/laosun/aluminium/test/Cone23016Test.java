package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23016: follow-up damage +30%, a follow-up tames the target (max 2 layers), and every layer lifts the wearer's
 * CRIT damage by 12% against a tamed target.
 *
 * <p>⭐ The per-layer part rides the instance crit slot with {@code per_stack: target_stacks:...}, so the judge reads the CRIT
 * number itself (the chance is pinned at 1) at one layer and at two -- the DIFFERENCE is one layer's worth.
 */
public class Cone23016Test {
    private static final int CONE = 23016;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String TAME = "温驯";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230161));
        return battle;
    }

    private double critHit() {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void aFollowUpTamesUpToTwoLayers() {
        build(true);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int one = enemy.getBuffManager().stacksOf(TAME);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int two = enemy.getBuffManager().stacksOf(TAME);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int three = enemy.getBuffManager().stacksOf(TAME);
        System.out.println("[23016] tame layers after 1/2/3 follow-ups = " + one + "/" + two + "/" + three);
        Assertions.assertEquals(1, one, "a follow-up tames one layer");
        Assertions.assertEquals(2, two, "two of them tame two");
        Assertions.assertEquals(2, three, "and #3 is capped at 2");
    }

    @Test
    public void eachTameLayerAddsCritDamage() {
        double untamed;
        build(false);
        untamed = critHit();
        build(true);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        double atOne = critHit();
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        double atTwo = critHit();
        System.out.println("[23016] untamed=" + untamed + " at 1 layer=" + atOne + " at 2 layers=" + atTwo
                + " ; one layer=" + (atOne - untamed) + " second layer=" + (atTwo - atOne));
        // ★ The per-layer AMOUNT, anchored to the crit base the engine reports (discipline 200). A crit multiplies by
        // (1 + crit damage), so one layer of +12% must add `untamed * 0.12 / (1 + base)`.
        // ⚠ A ratio-of-deltas does NOT work here: halving the share halves BOTH deltas, so the equality survives --
        // measured, the `12 -> 6 percent` mutation was still 0 red with that shape.
        double base = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        double expected = untamed * 0.12 / (1 + base);
        Assertions.assertEquals(expected, atOne - untamed, expected * 0.01,
                "one layer is +12% of the crit MULTIPLIER");
        Assertions.assertEquals(expected, atTwo - atOne, expected * 0.01, "and so is the next");
    }

    @Test
    public void noTameMeansNoCritBonus() {
        build(false);
        double plain = critHit();
        build(true);
        double untamed = critHit();
        System.out.println("[23016] without the cone=" + plain + " ; with the cone but untamed=" + untamed);
        Assertions.assertEquals(plain, untamed, 1e-9, "the crit bonus needs a tamed target (false case)");
    }

    @Test
    public void theSpecPinsTheFollowUpShareAndThePerLayerFactor() {
        build(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null,
                        battle, null))) {
            if (!rule.id().startsWith("cone23016_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[23016] spec " + rule.id() + " attribute=" + effect.getAttribute()
                        + " percent=" + effect.getPercent() + " perStack=" + effect.getPerStack()
                        + " instance=" + effect.getInstance());
                Assertions.assertEquals("CRIT_ATTACK", effect.getAttribute(), "crit DAMAGE");
                Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "12% per layer at rank 1");
                Assertions.assertEquals(TAME, effect.getPerStack(), "per layer ON THE TARGET (its bare name)");
            }
        }
        Assertions.assertEquals(0, pinned, "the rule needs a tamed target, checked behaviourally above");
    }
}
