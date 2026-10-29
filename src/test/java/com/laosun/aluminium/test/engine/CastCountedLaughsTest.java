package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code cast_counted_laughs}: the laugh count a COMMANDED CAST is settled with - 「使其立即施放 1 次固定计入 20 笑点的欢愉技」.
 *
 * <p><b>Why the instance-level {@code counted_laughs} cannot say it.</b> A commanded cast settles damage out of the
 * <b>skill's own row</b> (8009's ultimate commands the ally's Elation skill), so there is no effect of ours carrying the
 * number: it belongs to the cast, and every Elation fold during that cast has to read it - the settlement itself and any
 * rider firing on the cast's own events. Readers (3 files): 8009's and 8010's ultimates and 1513's trace.
 *
 * <p><b>What this file pins.</b> The commanded cast is settled with the stated count rather than the snapshot the party
 * happens to hold (20 is 1.3846x an empty snapshot), the count does NOT outlive its cast (the same unit's next Elation
 * cast is back to the snapshot), and a cast that settles anything but Elation damage refuses the number instead of
 * folding it into nothing.
 */
public class CastCountedLaughsTest {
    private static final int CASTER = 1505;
    private static final String ELATION = "ELATION_SKILL";
    private static final String SKILL = "SKILL";
    private static final double FACTOR_20 = 1 + 20 * 5.0 / (20 + 240);
    private static final double FACTOR_NONE = 1.0;

    /**
     * The cast states the count: while the snapshot is empty the fold is the stated 20 and not 1.0.
     */
    @Test
    public void theCommandedCastFoldsTheStatedCount() {
        double stated = commandedCastDamage(20, ELATION);
        double plain = commandedCastDamage(null, ELATION);
        System.out.println("[cast_laughs] commanded with 20: " + stated + " ; without: " + plain + " ; ratio "
                + (stated / plain) + " (expected " + FACTOR_20 + ")");
        Assertions.assertEquals(FACTOR_20 / FACTOR_NONE, stated / plain, 1e-6,
                "the cast is settled with the count the rule stated, not with the snapshot");
    }

    /**
     * ⚠ The count belongs to ITS cast. The engine's cast window is closed before the cast's own events are delivered (so
     * a rider can still read it) and cleared after them - this is the half that keeps the next cast honest.
     */
    @Test
    public void theCountDoesNotLeakPastItsCast() {
        Battle battle = build(ELATION, 20);
        Character commander = battle.characters().get(1);
        Character caster = battle.characters().getFirst();
        Enemy enemy = battle.enemyUnits().getFirst();

        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, commander, commander, 0, 0));
        battle.processRequests();
        double commanded = before - enemy.getCurrentHp();

        double beforeOwn = enemy.getCurrentHp();
        battle.castImmediate(caster.getSkills().get(SkillType.ELATION_SKILL), caster, List.of(enemy));
        battle.processRequests();
        double own = beforeOwn - enemy.getCurrentHp();

        System.out.println("[cast_laughs] commanded " + commanded + " ; her own next cast " + own + " ; ratio "
                + (own / commanded) + " (expected " + (FACTOR_NONE / FACTOR_20) + ")");
        Assertions.assertEquals(FACTOR_NONE / FACTOR_20, own / commanded, 1e-6,
                "the next cast folds the snapshot again - the count did not outlive its own cast");
    }

    /**
     * A cast that settles anything but Elation damage refuses the number: nothing would fold it.
     */
    @Test
    public void aCastThatSettlesSomethingElseRefusesTheCount() {
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> commandedCastDamage(20, SKILL));
        System.out.println("[cast_laughs] a NORMAL cast refused the count: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("Elation damage and nowhere else"),
                "the message says where a laugh count can be folded");
    }

    /**
     * The bookkeeping the last two cases measure: the claim is consumed by the cast that follows it.
     */
    @Test
    public void theClaimDoesNotSurviveTheCastThatConsumedIt() {
        Battle battle = build(ELATION, 20);
        Character commander = battle.characters().get(1);
        Assertions.assertNull(battle.castCountedLaughs(), "precondition: no cast has claimed anything yet");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, commander, commander, 0, 0));
        battle.processRequests();
        System.out.println("[cast_laughs] after the commanded cast the window holds "
                + battle.castCountedLaughs());
        Assertions.assertNull(battle.castCountedLaughs(),
                "the cast's own count goes away with the cast's outcome");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The settled damage of one commanded cast, with (or without) the laugh count the rule states.
     */
    private static double commandedCastDamage(Integer countedLaughs, String skill) {
        Battle battle = build(skill, countedLaughs);
        Character commander = battle.characters().get(1);
        Enemy enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, commander, commander, 0, 0));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /**
     * A party whose second unit commands the first one's {@code skill}, with the laugh count when one is stated.
     *
     * <p>⚠ The caster's CRIT_CHANCE is pinned to 0 so the two readings differ by the laugh factor alone: a crit roll
     * would make the ratio a coin toss rather than a measurement (the same reason the project's other comparative cases
     * fix the random source).
     */
    private static Battle build(String skill, Integer countedLaughs) {
        Character commander = Character.fromAttributes("commander", 1000, 100, 100, 100);
        Character caster = CharacterFactory.create(CASTER, 80);
        caster.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        EffectSpec cast = new EffectSpec();
        TriggerSpecs.set(cast, "op", "CAST_SKILL");
        TriggerSpecs.set(cast, "skill", skill);
        TriggerSpecs.set(cast, "target", "ally_cid:" + CASTER);
        if (countedLaughs != null) {
            TriggerSpecs.set(cast, "castCountedLaughs", countedLaughs);
        }
        // ⚠ TURN_START, and it is safe here: her own table has no rule on that event (measured), so the only thing this
        // firing delivers is the fixture's commanded cast.
        commander.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.TURN_START.name(), List.of(), cast))));
        Battle battle = new Battle(List.of(caster, commander), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
