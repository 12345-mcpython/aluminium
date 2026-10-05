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
 * A field modifier is anchored to the caster who laid it: when she falls, it comes off the enemies.
 *
 * <p>The mechanism is {@code BuffManager.removeBuffsAnchoredTo(clockOwner)}, reached from
 * {@code Battle.removeDeadCombatants}, keyed on {@code buff.ticksOn(clockOwner)} -- whose own comment names the failure
 * it prevents: <i>an anchored buff is a leak -- its clock was somebody else's turns, and that somebody will never
 * take another one</i>.
 *
 * <p>Note: <b>Three measurements, because two are not enough.</b> A first draft compared "with the cut" against "after she
 * fell" and got exactly 1.0 -- both numbers were the same, so the ratio said nothing either way (round 105: measured at
 * {@code turns: 3} and {@code turns: 99}, identical). The cut has to be measured against <b>the same enemy before the
 * ultimate</b>, which is the only number that is free of it.
 *
 * <p>Note: <b>What the anchor IS (rounds 105-10).</b> {@code AbstractBuff} falls back to {@code owner} when
 * {@code tickOwner} is null, and {@code owner} is the caster (the same method compares against {@code instance}, the
 * carrier). So the anchor death is the DEFAULT -- dropping {@code ticks_on: "self"} changes nothing about it.
 * {@code ticks_on} picks <b>whose turn spends the duration</b>, which is a different fact; a judge for THAT is owed.
 *
 * <p>Note: Killing a unit has no setter: {@code takeDamage(9_999_999)} then {@code battle.processRequests()} -- the second
 * call is what lets the engine run its death handling ({@code MemospriteTest}'s pattern).
 */
public class AnchorDeathTest {
    private static final int CASTER = 1003;
    private static final int PROBE = 1002;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    @Test
    public void theCastersDeathTakesTheCutWithIt() {
        Character caster = CharacterFactory.create(CASTER, LEVEL);
        EffectSpec cut = new EffectSpec();
        TriggerSpecs.set(cut, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(cut, "percent", 0.4);
        // Note: 99: processRequests advances turns below, and a short duration would expire on its own
        TriggerSpecs.set(cut, "turns", 99);
        TriggerSpecs.set(cut, "target", "all_enemies");
        TriggerSpecs.set(cut, "ticksOn", "self");     // Note: the Java field name; the data spells it ticks_on
        caster.setTriggerTable(new TriggerTable(CASTER, List.of(
                TriggerSpecs.rule(TriggerEvent.ULT_CAST.name(), List.of("actor == self"), cut))));
        Character probe = CharacterFactory.create(PROBE, LEVEL);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(caster, probe), List.of(victim), new Random() {
            @Override
            public double nextDouble() {
                return 1.0;                       // Note: never crits: the subject is the anchor, not the roll
            }
        });
        battle.startBattle();

        double baseline = hit(battle, probe, victim);                       // Note: no ultimate yet: no cut anywhere
        battle.fireTriggers(TriggerEvent.ULT_CAST, caster, victim, 0, 0);
        double withCut = hit(battle, probe, victim);

        caster.takeDamage(9_999_999);
        battle.processRequests();
        Assertions.assertTrue(caster.isDeath(), "the caster really did fall");
        // Note: ONE discarded swing first. removeDeadCombatants runs at the END of castImmediate, and processRequests
        // alone does NOT run it (Battle:53-582) -- so the swing that TRIGGERS the cleanup also settles its own damage
        // before the cleanup happens, and measuring right there still reads the cut up (measured: 1.40x, twice). This
        // discarded swing lets the cleanup finish; the next one sees the settled state.
        hit(battle, probe, victim);
        double afterFalling = hit(battle, probe, victim);

        System.out.println("[anchor] probe damage to the same enemy -- baseline=" + baseline
                + "  cut up=" + withCut + " (" + (withCut / baseline) + "x)"
                + "  after she fell=" + afterFalling + " (" + (afterFalling / baseline) + "x)");

        Assertions.assertTrue(baseline > 0, "the probe must land at all (" + baseline + ")");
        Assertions.assertEquals(1.4, withCut / baseline, 0.06,
                "the cut is up while she stands");
        Assertions.assertEquals(1.0, afterFalling / baseline, 0.06,
                "⚠ and her death takes it off the enemies -- the leak removeBuffsAnchoredTo prevents");
    }

    private static double hit(Battle battle, Character probe, Enemy victim) {
        double before = victim.getCurrentHp();
        battle.castImmediate(probe.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), probe, List.of(victim));
        return before - victim.getCurrentHp();
    }
}
