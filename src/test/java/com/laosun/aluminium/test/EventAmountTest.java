package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
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
 * {@code event_amount} (2026-10-02): the triggering event's own magnitude, as a value and as a repeat count.
 *
 * <p>Readers (all with data files): 1407's talent 「我方全体每损失1点生命值遐蝶获得1点【新蕊】」, 1413's 「每消耗了1点【忆质】…」,
 * and the per-spent-point instance family 1408 / 1510 / 1513. The engine already hands the number over --
 * {@code RESOURCE_CHANGED} fires with {@code amount = delta} -- and a spend arrives NEGATIVE, so both readings take the
 * absolute value: "每 1 点" counts points.
 */
public class EventAmountTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⭐ A magnitude off the event: heal 1 point per point of health lost. */
    @Test
    public void theMagnitudeFollowsTheEvent() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "scale", "event_amount");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("HP_LOST",
                List.of("actor == self"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.applyTrueDamage(enemy, owner, com.laosun.aluminium.enums.DamageElement.FIRE, 50);
        double before = owner.getCurrentHp();
        Assertions.assertTrue(before < owner.getMaxHp(), "precondition: the unit is hurt");
        battle.fireTriggers(TriggerEvent.HP_LOST, owner, owner, 0, 20);
        battle.processRequests();
        Assertions.assertEquals(20, owner.getCurrentHp() - before, 1e-6, "1 point healed per point lost");
    }

    /** ⭐ A repeat count off the event, driven by a real resource change. */
    @Test
    public void theRepeatCountFollowsTheEvent() {
        double one = loss(1);
        double three = loss(3);
        Assertions.assertTrue(one > 0, "precondition: the instance lands (" + one + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "spending 3 repeats the instance 3 times (" + one + " -> " + three + ")");
        Assertions.assertEquals(0, loss(0), 1e-6, "and a change of 0 repeats it not at all");
    }

    // ==================================================================

    /** The enemy's HP loss when the rule repeats its instance once per point spent. */
    private static double loss(int spent) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpecs.set(effect, "critRate", 0.0);
        TriggerSpecs.set(effect, "critDamage", 0.0);
        TriggerSpecs.set(effect, "timesFrom", "event_amount");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("RESOURCE_CHANGED",
                List.of("actor == self", "resource_changed:充能"), effect)),
                List.of(new com.laosun.aluminium.beans.ResourceSpec("充能", 8))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        // `resource_changed:<name>` reads what the battle was TOLD changed, so a hand-fired event must say so.
        battle.noteChangedResource("充能");
        // ⚠ The SUBJECT is the enemy here, so `target: target` settles on a target with far more health than the
        // carrier -- otherwise three repetitions would be clamped by the caster's own health bar (measured: one
        // repetition took the caster from full to 597 of its 1358, and three then read as 1047 instead of 2282).
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, enemy, 0, -spent);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
