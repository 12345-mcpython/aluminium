package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code scale: "settled_damage_this_event"}: a share of the damage the whole FIRING settled, not of one instance.
 *
 * <p>Reader: 1506 Silver Wolf LV.999's 【超超超大剑】 - 「对生命值最高的敌方目标额外造成等同于本次总伤害值 20% 的真实伤害」. It sits
 * beside {@code scale: "original_damage"} (which the {@code DAMAGE_SETTLED} event hands one instance to) and they are not
 * one spelling: a group action settles one instance per enemy, so a clause that says "本次总伤害值" asks for a SUM that
 * only the battle holds.
 *
 * <p>⚠ <b>The control is the other half of this judge.</b> The box's own damage goes through the zones, so its number
 * cannot be written down here - it is measured from the SAME fixture with only the box's rule in the table, and the
 * rider's share is asserted against that. Two enemies, because with one enemy "the total" and "my own target's instance"
 * are the same number and the judge could not tell them apart.
 */
public class SettledDamageTotalTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    /**
     * Fixed so the two fixtures below draw the same numbers: only the extra rule differs between them.
     */
    private static final int SEED = 7;

    /**
     * The rider is exactly a fifth of everything the firing settled, and it lands on ONE enemy.
     */
    @Test
    public void theShareIsTakenOfEveryInstanceTheFiringSettled() {
        double base = lossOfTheBoxAlone();
        double[] firings = lossesOfTheBoxAndTheSword(2);
        System.out.println("[settled-total] the box alone settled " + base + ", box + 20% rider = " + firings[0]
                + " then " + firings[1]);
        Assertions.assertTrue(base > 0, "the fixture must settle damage before the rider can take a share of it");
        Assertions.assertEquals(1.2 * base, firings[0], 1e-6,
                "the rider is 20% of the TOTAL the firing settled: 1.1x would be one enemy's instance, 1.0x no rider");
        // The total belongs to THIS firing. A second one must still be a fifth of ONE box, not of everything the battle
        // has dealt so far - which is exactly what a missing per-firing reset reads (1.4x: base + 20% of two boxes).
        Assertions.assertEquals(1.2 * base, firings[1], 1e-6,
                "the running total is emptied at the start of each firing; otherwise this one reads two boxes");
    }

    /**
     * Only true damage may read it.
     *
     * <p>The value is already settled, so a rider that went through the zones again would land at an unknown multiple
     * of the share - and, unlike {@code original_damage}, a sum over several instances has no single factor to divide
     * out. Each of the three missing pieces is refused by name.
     */
    @Test
    public void anythingButTrueDamageIsRefused() {
        EffectSpec plain = sword();
        TriggerSpecs.set(plain, "damageType", null);
        assertRefused(plain, "TRUE");

        EffectSpec noElement = sword();
        TriggerSpecs.set(noElement, "damageType", "TRUE");
        TriggerSpecs.set(noElement, "element", null);
        assertRefused(noElement, "element");

        EffectSpec noPercent = sword();
        TriggerSpecs.set(noPercent, "damageType", "TRUE");
        TriggerSpecs.set(noPercent, "element", "Imaginary");
        TriggerSpecs.set(noPercent, "percent", null);
        assertRefused(noPercent, "percent");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The box's own damage, with no rider in the table at all - the baseline the share is asserted against.
     */
    private static double lossOfTheBoxAlone() {
        return lostAfterFiring(List.of(theBox()), 1)[0];
    }

    /**
     * The same firing, with the 20% true-damage rider as a SECOND rule so it runs after the box has settled.
     */
    private static double[] lossesOfTheBoxAndTheSword(int firings) {
        return lostAfterFiring(List.of(theBox(), swordRule()), firings);
    }

    private static void assertRefused(EffectSpec effect, String names) {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(WOLF, List.of(TriggerSpecs.rule("TURN_START", List.of(), effect))));
        System.out.println("[settled-total] refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains(names),
                "the message must name what is missing, and it says: " + error.getMessage());
    }

    /**
     * One firing per entry, each returning the total HP the whole enemy side lost to it.
     */
    private static double[] lostAfterFiring(List<TriggerSpec> specs, int firings) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster(), BattleFixture.monster()),
                new Random(SEED));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        wolf.setTriggerTable(new TriggerTable(WOLF, specs));
        var enemies = battle.enemyUnits();
        double[] losses = new double[firings];
        for (int firing = 0; firing < firings; firing++) {
            double before = enemies.stream().mapToDouble(e -> e.getCurrentHp()).sum();
            battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
            battle.processRequests();
            losses[firing] = before - enemies.stream().mapToDouble(e -> e.getCurrentHp()).sum();
        }
        return losses;
    }

    private static TriggerSpec theBox() {
        return TriggerSpecs.rule("SKILL_POINT_SPENT", List.of(), box());
    }

    private static TriggerSpec swordRule() {
        return TriggerSpecs.rule("SKILL_POINT_SPENT", List.of(), sword());
    }

    /**
     * 「造成 90% 的虚数属性欢愉伤害」 - the one effect in the control table, and the first in the real one.
     */
    private static EffectSpec box() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.9);
        TriggerSpecs.set(effect, "element", "Imaginary");
        TriggerSpecs.set(effect, "damageType", "ELATION");
        TriggerSpecs.set(effect, "target", "all_enemies");
        return effect;
    }

    /**
     * 【超超超大剑】: a fifth of the settled total, as true damage on the highest-HP enemy.
     */
    private static EffectSpec sword() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "settled_damage_this_event");
        TriggerSpecs.set(effect, "percent", 0.2);
        TriggerSpecs.set(effect, "element", "Imaginary");
        TriggerSpecs.set(effect, "damageType", "TRUE");
        TriggerSpecs.set(effect, "target", "highest_hp_enemy");
        return effect;
    }
}
