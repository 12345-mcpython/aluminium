package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code times_from: "event_amount"}: the REPEAT COUNT follows the triggering event.
 *
 * <p>Note: The magnitude half was withdrawn the same day: {@code scale: "event_amount"} duplicated the spelling that already
 * exists -- {@link EffectSpec}'s {@code amount_from_event} (+ {@code amount_percent}), used by four shipped rules
 * (1312 on {@code SKILL_POINT_SPENT}, 1505 twice on {@code ENERGY_GAINED}, 1506 on {@code RESOURCE_CHANGED}). A second
 * name for a thing the engine already had is exactly what this project refuses to keep. What is genuinely new is the
 * count: the existing field carries an AMOUNT, never a number of repetitions.
 *
 * <p>Note: <b>CORRECTED (2026-10-19).</b> The withdrawal above is still right about the SPELLING, and this class held only
 * the count half for as long as the amount half had no DAMAGE reader. 1408's Eidolon 6 needed one -
 * 「等同于本次攻击总伤害值 #1% 的真实伤害」 - so the reader was added to {@code TriggerMagnitudes.literalBase}
 * (see {@link #theAttackTotalCanBeTheBaseOfATrueRider}), still spelled {@code amount_from_event} + {@code percent}.
 * What that clause also needed was a <em>fact</em>, not a spelling: {@code ATTACK_FINISHED} announces the settled total,
 * and until then it named no skill (so "after THAT skill's attack" could not be said).
 *
 * <p>Readers (all with data files): 1408 "每消耗1点[毁伤]造成4次伤害", 1510 "每消耗1点[源能]额外…1次30%",
 * 1513 "每消耗1点[热意]…1次21%", and 1408's Eidolon 6 for the amount half.
 */
public class EventAmountTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;

    /**
     * Spending three points repeats the instance three times; spending none repeats it not at all.
     */
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

    /**
     * The amount half, now that a reader exists: {@code amount_from_event} + {@code percent} is the BASE of a damage
     * instance, and {@code ATTACK_FINISHED}'s amount is the whole attack's settled total.
     *
     * <p>Reader: 1408's Eidolon 6 - 「对生命值最高的敌方目标额外造成等同于本次攻击总伤害值 36% 的真实伤害」. True damage
     * skips every zone, so the settled rider is exactly {@code 36% x the announced total} - which is why the tolerance here
     * is absolute and the two totals are pinned independently (a rule that read the OTHER event's amount, or that divided
     * by a zone factor, would land on a different number).
     */
    @Test
    public void theAttackTotalCanBeTheBaseOfATrueRider() {
        Assertions.assertEquals(360.0, trueRider(1000), 1e-6, "36% of a 1000-point attack");
        Assertions.assertEquals(720.0, trueRider(2000), 1e-6, "and it follows the total, not a fixed number");
    }

    // ==================================================================

    /**
     * What the enemy loses to a {@code 36%} true rider announced by an attack whose settled total is {@code total}.
     */
    private static double trueRider(double total) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "amountFromEvent", true);
        TriggerSpecs.set(effect, "percent", 0.36);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "damageType", "TRUE");
        TriggerSpecs.set(effect, "target", "target");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ATTACK_FINISHED",
                List.of("actor == self"), effect)), List.of()));
        // Note: A 1,000,000-HP dummy, so the reading is the rider itself and not the target's own health bar (measured:
        // the standard monster is ~1358 HP and would clamp a 2000-point attack's rider).
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireAfterAttack(owner, enemy, List.of(enemy), total);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /**
     * What the enemy loses when the rule repeats its instance once per point spent.
     */
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
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        // Note: `resource_changed:<name>` reads what the battle was TOLD changed, and the SUBJECT must be the enemy: with
        // the carrier as the target, three repetitions were clamped by its own 1358-point health bar (104instead of
        // 3 x 60.83).
        battle.noteChangedResource("充能");
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.RESOURCE_CHANGED, owner, enemy, 0, -spent));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
