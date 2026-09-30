package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 24000: every attack the wearer makes stacks 8% attack (up to four), and breaking a weakness adds 12% damage for two
 * turns.
 *
 * <p>\u2b50 The wearer is Dan Heng -- chosen because it carries no self-stacking mechanic of its own (a lesson measured on 21010).
 * Every claim is read on the attribute, and the "nothing happened" cases are compared against that same battle\u2019s baseline.
 */
public class Cone24000Test {
    private static final int CONE = 24000;
    private static final int WEARER = 1002;
    private static final int ALLY = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private void attack() {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 0);
    }

    private double attackValue() {
        return wearer.getAttribute(AttributeType.ATTACK).get();
    }

    @Test
    public void attacksStackAttackUpToFour() {
        build(true);
        double base = attackValue();
        attack();
        double one = attackValue();
        for (int i = 0; i < 6; i++) {
            attack();
        }
        double many = attackValue();
        System.out.println("[24000] attack " + base + " -> one attack=" + one + " (delta " + (one - base)
                + ", 8% of the base is " + (0.08 * base) + ") -> after eight attacks=" + many
                + " (delta " + (many - base) + ", four layers would be " + (4 * 0.08 * base) + ")");
        // \u2605 A percent modifier is a share of the PRE-BONUS base, not of the panel it is read on (measured: the delta is
        // 86.0832, i.e. 8% of 1076.04, while the panel reads 1269.7272), so the exact claims are the LAYER ARITHMETIC
        // (four layers are four of one) and the numbers themselves, which the spec half below pins.
        Assertions.assertTrue(one > base, "an attack adds attack");
        Assertions.assertEquals(4 * (one - base), many - base, 1e-6, "and the layers stop at four");
    }

    @Test
    public void breakingAWeaknessLiftsTheDamage() {
        build(true);
        double before = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.BREAK, wearer, enemy, 0, 0);
        double afterBreak = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        build(false);
        double plainAfterBreak = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.BREAK, wearer, enemy, 0, 0);
        double plain = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[24000] all-damage boost before=" + before + " after a break=" + afterBreak
                + " ; without the cone " + plainAfterBreak + " -> " + plain);
        Assertions.assertEquals(before, plainAfterBreak, 1e-9, "both start from their own baseline");
        Assertions.assertEquals(before + 0.12, afterBreak, 1e-9, "a break grants the 12%");
        Assertions.assertEquals(plainAfterBreak, plain, 1e-9, "and no cone means no grant (false case)");
    }

    @Test
    public void anotherEventGrantsNothing() {
        build(true);
        double before = attackValue();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.TURN_START, wearer, null, 0, 0);
        System.out.println("[24000] after a skill and a turn start: " + attackValue());
        Assertions.assertEquals(before, attackValue(), 1e-9, "the trigger is an attack (false case)");
    }

    @Test
    public void theSpecPinsTheNumbers() {
        // \u2605 The shares and the ceiling, read straight off the compiled rule: the behaviour above pins the ARITHMETIC, and
        // these two numbers are what a `8 -> 4 percent` or `max_stacks 4 -> 2` change would otherwise slip past.
        build(true);
        var rules = wearer.getTriggerTable().matching(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, enemy, 1, 0, null,
                        battle, null));
        int pinned = 0;
        for (var rule : rules) {
            if (!rule.id().startsWith("cone24000_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[24000] spec " + rule.id() + " attribute=" + effect.getAttribute()
                        + " percent=" + effect.getPercent() + " maxStacks=" + effect.getMaxStacks());
                Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "8% per layer at rank 1");
                Assertions.assertEquals(4.0, effect.getMaxStacks(), 1e-9, "and four layers at most");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone on an attack");
    }
}
