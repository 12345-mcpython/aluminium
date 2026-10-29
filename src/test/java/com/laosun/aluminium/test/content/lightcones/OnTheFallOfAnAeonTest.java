package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 24000: every attack the wearer makes stacks 8% attack (up to four), and breaking a weakness adds 12% damage for two
 * turns.
 *
 * <p>The wearer is Dan Heng -- chosen because it carries no self-stacking mechanic of its own (a lesson measured on 21010).
 * Every claim is read on the attribute, and the "nothing happened" cases are compared against that same battle's baseline.
 */
public class OnTheFallOfAnAeonTest {
    private static final int CONE = 24000;
    private static final int WEARER = 1002;
    private static final int ALLY = 1003;
    private static final int LEVEL = 80;
    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
    }

    private void attack() {
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 0));
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
        // A percent modifier is a share of the PRE-BONUS base, not of the panel it is read on (measured: the delta is
        // 86.0832, i.e. 8% of 106.04, while the panel reads 1269.22), so the exact claims are the LAYER ARITHMETIC
        // (four layers are four of one) and the numbers themselves, which the spec half below pins.
        Assertions.assertTrue(one > base, "an attack adds attack");
        Assertions.assertEquals(4 * (one - base), many - base, 1e-6, "and the layers stop at four");
    }

    @Test
    public void breakingAWeaknessLiftsTheDamage() {
        build(true);
        double before = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, wearer, enemy, 0, 0));
        double afterBreak = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        build(false);
        double plainAfterBreak = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, wearer, enemy, 0, 0));
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
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0));
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, null, 0, 0));
        System.out.println("[24000] after a skill and a turn start: " + attackValue());
        Assertions.assertEquals(before, attackValue(), 1e-9, "the trigger is an attack (false case)");
    }

    @Test
    public void theSpecPinsTheNumbers() {
        // 2026-10-19: the per-attack attack is a stack buffer plus a per_stack share now (the old modifier's `max_stacks`
        // was honoured by nothing).
        var rules = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                .getTriggerTable().rulesFor(TriggerEvent.ALLY_ATTACK).stream()
                .filter(r -> r.id().startsWith("cone24000_")).toList();
        Assertions.assertEquals(1, rules.size(), "one per-attack rule");
        var stack = rules.getFirst().effects().get(0);
        var share = rules.getFirst().effects().get(1);
        System.out.println("[24000] stack=\"" + stack.getBuff() + "\" cap=" + stack.stackCap()
                + " ; share per_stack=\"" + share.getPerStack() + "\" percent=" + share.getPercent());
        Assertions.assertEquals(4, stack.stackCap(), "four layers at most");
        Assertions.assertEquals("星神层数", share.getPerStack(), "per layer");
        Assertions.assertEquals(0.08, share.getPercent(), 1e-9, "8% per layer at rank 1");
    }
}
