package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
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
 * {@code times_from: "event_amount"}: the REPEAT COUNT follows the triggering event.
 *
 * <p>Note: The magnitude half was withdrawn the same day: {@code scale: "event_amount"} duplicated the spelling that already
 * exists -- {@link EffectSpec}'s {@code amount_from_event} (+ {@code amount_percent}), used by four shipped rules
 * (1312 on {@code SKILL_POINT_SPENT}, 1505 twice on {@code ENERGY_GAINED}, 1506 on {@code RESOURCE_CHANGED}). A second
 * name for a thing the engine already had is exactly what this project refuses to keep. What is genuinely new is the
 * count: the existing field carries an AMOUNT, never a number of repetitions.
 *
 * <p>Readers (all with data files): 1408 "每消耗1点[毁伤]造成4次伤害", 1510 "每消耗1点[源能]额外…1次30%",
 * 1513 "每消耗1点[热意]…1次21%".
 */
public class EventAmountTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Spending three points repeats the instance three times; spending none repeats it not at all. */
    @Test
    public void theRepeatCountFollowsTheEvent() {
        double one = damage(1);
        double three = damage(3);
        Assertions.assertTrue(one > 0, "precondition: the instance lands (" + one + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "spending 3 repeats the instance 3 times (" + one + " -> " + three + ")");
        Assertions.assertEquals(0, damage(0), 1e-6, "and a change of 0 repeats it not at all");
    }

    // ==================================================================

    /** What the enemy loses when the rule repeats its instance once per point spent. */
    private static double damage(int spent) {
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
                List.of(new ResourceSpec("充能", 8))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        // Note: `resource_changed:<name>` reads what the battle was TOLD changed, and the SUBJECT must be the enemy: with
        // the carrier as the target, three repetitions were clamped by its own 1358-point health bar (104instead of
        // 3 x 60.83).
        battle.noteChangedResource("充能");
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, enemy, 0, -spent);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
