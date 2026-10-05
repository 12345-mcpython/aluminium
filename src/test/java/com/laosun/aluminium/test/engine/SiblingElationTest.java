package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8009 and 8010, the Elation Trailblazer pair (2026-09-29, round 198): the chosen ally's +50% CRIT DMG, for both ids.
 *
 * <p>The gain is asserted against a hand-built reference at percent 1.0 in the SAME pipeline, so the ratio 0.5 is the claim and the engine's own factors cancel - 
 * the round-19lesson, where asserting a share of a zero base compared nothing.
 */
public class SiblingElationTest {
    private static final int ALLY = 1002;
    /** The ally whose kit really carries an Elation skill (data slot 20) -- the auto-cast's true side. */
    private static final int ELATION_ALLY = 1501;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The content's 0.5 against a reference 1.0, and the control, for BOTH ids. */
    @Test
    public void theUltimateRaisesTheChosenAllysCritDamage() {
        for (int cid : new int[]{8009, 8010}) {
            double content = ultimateGain(cid, 0);
            double reference = ultimateGain(cid, 1);

            Assertions.assertTrue(reference > 0, "cid " + cid + ": the reference must raise it at all");
            Assertions.assertEquals(0.5, content / reference, 0.05,
                    "cid " + cid + ": content " + content + " vs reference " + reference);
        }
    }

    /** Note: "施放攻击后，固定恢复10点能量" -- and the document's number, not just "some energy". */
    @Test
    public void theTalentGivesTenEnergyPerAttack() {
        for (int cid : new int[]{8009, 8010}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
            Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
            battle.startBattle();
            double before = tb.getCurrentEnergy();

            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, tb, enemy, 0, 0);

            Assertions.assertEquals(10.0, tb.getCurrentEnergy() - before, 1e-6,
                    "cid " + cid + ": 「施放攻击后，固定恢复10点能量」");
        }
    }

    /** Census for both ids. */
    @Test
    public void theirFilesCarryTheClauses() {
        for (int cid : new int[]{8009, 8010}) {
            var table = com.laosun.aluminium.data.TriggerTables.of(cid);
            // 2026-10-02: FOUR clauses now, not two -- the document's other branch finally shipped. It waited on the
            // engine's reading of an Elation skill's row ("8 次随机单体 + 最后一次均分", whose leading column is a HIT
            // COUNT): before that, a commanded Elation cast settled as ONE 800% instance. See `ElationRowTest`.
            Assertions.assertEquals(4, table.ruleCount(TriggerEvent.ULT_CAST),
                    "cid " + cid + ": 终结技现在是四条 —— 暴伤 buff、无欢愉技时的行动提前、"
                            + "获得 5 个笑点、以及「若目标拥有欢愉技…使其立即施放 1 次欢愉技」");
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK), "cid " + cid);
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "cid " + cid);
        }
    }

    /**
     * The ultimate's THIRD sentence, end to end: "若目标不拥有欢愉技，使其行动提前50%".
     *
     * <p>No scaffold - 8010's OWN shipped file drives it - and the pair of measurements is the discrimination: 1501
     * carries an Elation skill (a real data row under slot 20) and Dan Heng (丹恒) does not, so the same cast must halve the
     * first one's remaining action value and leave the second's untouched. A guard that always passed would move
     * both; one that never passed would move neither.
     *
     * <p>Note: The value is read from the <b>action bar</b>, not from a buff: "行动提前 50%" is a fraction of the
     * target's <i>remaining</i> wait (the same reading {@code ADVANCE} already had for 1101 / 1210), so the honest
     * assertion is "what is left is half of what was left", not "the target acts".
     */
    @Test
    public void theUltimateAdvancesAnAllyWhoHasNoElationSkill() {
        Assertions.assertTrue(
                CharacterFactory.create(ELATION_ALLY, LEVEL).getSkills().get(SkillType.ELATION_SKILL)
                        .getData().isLoaded(),
                "precondition: " + ELATION_ALLY + " carries a REAL Elation row, which is the guard's true side");
        Assertions.assertFalse(
                CharacterFactory.create(ALLY, LEVEL).getSkills().get(SkillType.ELATION_SKILL).getData().isLoaded(),
                "precondition: " + ALLY + "'s slot 20 is the loader's placeholder, which is the false side");

        double[] plain = advanceAfterUltimate(ALLY);
        double[] elation = advanceAfterUltimate(ELATION_ALLY);

        Assertions.assertTrue(plain[0] > 0, "precondition: the ally still had action value to give away");
        Assertions.assertEquals(plain[0] / 2, plain[1], 1e-9,
                "「使其行动提前50%」: " + plain[0] + " -> " + plain[1] + " (half of the REMAINING wait)");
        Assertions.assertEquals(elation[0], elation[1], 1e-9,
                "and an ally who HAS an Elation skill is the document's other branch: nothing may be advanced ("
                        + elation[0] + " -> " + elation[1] + ")");
    }

    /** Fires 8010's ultimate at {@code allyCid}; returns {remaining action value before, after}. */
    private static double[] advanceAfterUltimate(int allyCid) {
        Character tb = CharacterFactory.create(8010, LEVEL);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(tb, ally), List.of(EnemyFactory.create(MONSTER, 90, 1)), fixed());
        battle.startBattle();
        double before = timeRemaining(battle, ally);
        battle.fireTriggers(TriggerEvent.ULT_CAST, tb, ally, 0, 0);
        return new double[]{before, timeRemaining(battle, ally)};
    }

    /** How much action value the unit still has - the action bar's own answer (same helper as the 1402 judge). */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    /** mode 0 = the shipped file, 1 = a hand-built 100% reference. Returns the chosen ally's CRIT DMG gain. */
    private static double ultimateGain(int cid, int mode) {
        Character tb = CharacterFactory.create(cid, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "CRIT_ATTACK");
            TriggerSpecs.set(effect, "percent", 1.0);
            TriggerSpecs.set(effect, "turns", 3);
            TriggerSpecs.set(effect, "target", "target");
            tb.setTriggerTable(new TriggerTable(cid, List.of(TriggerSpecs.rule(
                    TriggerEvent.ULT_CAST.name(), List.of("actor == self"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, tb, ally, 0, 0);
        return ally.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
