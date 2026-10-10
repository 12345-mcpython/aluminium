package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The laugh-point factor of Elation damage: {@code 1 + 笑点×5/(笑点+240)}, read off 【好活当赏】's frozen count.
 *
 * <p>⚠ This case changed sides when the encyclopedia entry corrected the model, and the change is the point of it now.
 * It used to fill the party's 笑点 pool and assert that Elation damage grew - which is what the engine did. The entry
 * says the state takes 「本次阿哈时刻的笑点」 and 「欢愉伤害以【好活当赏】计入的笑点数计算」, so the pool alone is no longer
 * what the factor reads: the count has to have been COUNTED INTO a holder. This case therefore reads three states of
 * the world, and the middle one is the correction:
 *
 * <ol>
 *   <li>no Aha Moment - factor 1.0;</li>
 *   <li>the pool filled but NOBODY holding 【好活当赏】 - factor <b>still 1.0</b>, because a pool that was never counted
 *       into anyone is not what 欢愉伤害 is computed from;</li>
 *   <li>the pool filled AND the state granted - factor 3.5 at 240 points.</li>
 * </ol>
 *
 * <p>It is two-sided by construction: a NORMAL instance must not see the factor at all, at any of the three.
 */
public class ElationLaughMultiplierTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1505;       // declares 好活当赏, so a grant to her is not dropped
        private static final int LAUGHS = 240;      // -> factor 1 + 240*5/(240+240) = 3.5
    private static final String GIFT = "好活当赏";
    private static final String LAUGH = "笑点";

    @Test
    public void theFactorFollowsTheCountedStateAndNotThePoolAlone() {
        double elationNone = damage(false, 0, false);
        double elationPoolOnly = damage(false, LAUGHS, false);
        double elationCounted = damage(false, LAUGHS, true);
        double normalNone = damage(true, 0, false);
        double normalCounted = damage(true, LAUGHS, true);
        System.out.println("[laugh] elation: none " + elationNone + " ; pool only " + elationPoolOnly
                + " (ratio " + (elationPoolOnly / elationNone) + ") ; counted " + elationCounted
                + " (ratio " + (elationCounted / elationNone) + ")");
        System.out.println("[laugh] normal: none " + normalNone + " ; counted " + normalCounted);

        Assertions.assertEquals(1.0, elationPoolOnly / elationNone, 1e-6,
                "a pool that was never counted into a holder is NOT what 欢愉伤害 is computed from");
        Assertions.assertEquals(3.5, elationCounted / elationNone, 1e-6,
                "once 【好活当赏】 counts 240 笑点 into its holder, the factor is 1 + 240*5/480 = 3.5");
        Assertions.assertEquals(normalNone, normalCounted, 1e-9,
                "and a NORMAL instance must not see the Elation factor at all");
    }

    /**
     * The FROZEN part, read as damage: the pool growing after the state arrived does not raise damage.
     *
     * <p>The snapshot cases pin this on the count; this pins it on the number the player sees, because "the count is
     * frozen" and "damage does not move" are different claims and only the second one is the mechanic.
     */
    @Test
    public void thePoolGrowingAfterTheGrantDoesNotRaiseDamage() {
        double frozen = damageWithPoolThenGrowth(LAUGHS, LAUGHS);
        double doubleCounted = damageWithPoolThenGrowth(LAUGHS, LAUGHS * 2);
        System.out.println("[laugh] granted at " + LAUGHS + ": pool left alone " + frozen
                + " ; pool doubled afterwards " + doubleCounted);
        Assertions.assertEquals(frozen, doubleCounted, 1e-9,
                "the count was taken when the state arrived, so the pool growing afterwards changes nothing");
    }

    /** Settles one Elation instance after granting at {@code atGrant} and then raising the pool to {@code after}. */
    private static double damageWithPoolThenGrowth(int atGrant, int after) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        TriggerSpecs.set(instance, "damageType", "ELATION");
        mine.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance))));

        battle.partyResource(LAUGH).gain(atGrant);
        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(gain, "resource", GIFT);
        TriggerSpecs.set(gain, "amount", 10.0);
        TriggerSpecs.set(gain, "target", "self");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("TURN_START", List.of("actor == self"), gain))));
        battle.fireTriggers(TriggerEvent.TURN_START, ally, ally, 0, 0);
        battle.processRequests();
        battle.partyResource(LAUGH).gain(after - battle.partyResourceValue(LAUGH));

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        return before - enemy.getCurrentHp();
    }

    /**
     * Settles one rule-driven instance and returns the damage dealt.
     *
     * @param normal  pick a NORMAL damage type instead of ELATION
     * @param laughs  how much 笑点 the party holds
     * @param counted whether 【好活当赏】 is granted, which is what counts those 笑点 into the holder
     */
    private static double damage(boolean normal, int laughs, boolean counted) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        if (!normal) {
            TriggerSpecs.set(instance, "damageType", "ELATION");
        }
        mine.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance))));

        if (laughs > 0) {
            battle.partyResource(LAUGH).gain(laughs);
        }
        if (counted) {
            // Granted through the engine's own gain path, which is where the count is taken.
            EffectSpec gain = new EffectSpec();
            TriggerSpecs.set(gain, "op", "GAIN_RESOURCE");
            TriggerSpecs.set(gain, "resource", GIFT);
            TriggerSpecs.set(gain, "amount", 10.0);
            TriggerSpecs.set(gain, "target", "self");
            ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                    TriggerSpecs.rule("TURN_START", List.of("actor == self"), gain))));
            battle.fireTriggers(TriggerEvent.TURN_START, ally, ally, 0, 0);
            battle.processRequests();
        }

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        return before - enemy.getCurrentHp();
    }
}
