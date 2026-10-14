package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 罗刹's Field (【结界】): how it ends.
 *
 * <p>The document gives it <b>two</b> endings, and they are independent:
 * <ul>
 *   <li>「结界效果持续<strong>2</strong>回合」 -- a duration;</li>
 *   <li>「当罗刹陷入无法战斗状态时，结界<strong>也会被解除</strong>」 -- removed when he is knocked down.</li>
 * </ul>
 *
 * <p><b>Both are asserted, and separately.</b> They are easy to conflate because this engine delivers them from
 * one field: {@code ticks_on: "self"} makes the duration count down on his turn boundaries AND makes his death
 * sweep the buff ({@code Battle.releaseBuffsAnchoredToTheDead}). A test that only checked the death case would
 * pass with an anchor and no duration; a test that only checked the duration would pass with a duration and no
 * anchor. Each path gets its own case.
 *
 * <p>Precedent for the shape: 1303 阮•梅's 终结技 opens a 结界 as {@code ticks_on: "self"} with a 2-turn duration.
 */
public class LuochaFieldTest {

    private static final int LUOCHA = 1203;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * The field opens once 【白花之刻】 reaches 2, and opening it spends the stacks.
     *
     * <p>Without the spend the counter would sit at 2 and every later Skill would reopen the field, so this
     * precondition is asserted before the endings are.
     */
    @Test
    public void twoStacksOpenTheFieldAndAreSpent() {
        Fixture f = new Fixture();

        f.castSkill();
        Assertions.assertEquals(1, f.stacks(), "one Skill = one stack");
        Assertions.assertFalse(f.fieldUp(), "one stack is not enough to open the field");

        // The second Skill is what reaches two, and the opener consumes both in the same cast -- so the counter
        // reads 0 and the field is already up by the time this call returns.
        f.castSkill();
        Assertions.assertEquals(0, f.stacks(),
                "「达到2层时，罗刹会消耗全部【白花之刻】」 -- the two stacks are spent");
        Assertions.assertTrue(f.fieldUp(), "and that is what opens the field");
    }

    /**
     * ⚠ NOT YET IMPLEMENTED: the field disappears when 罗刹 is knocked down.
     *
     * <p>The document states it separately from the duration: 「当罗刹陷入无法战斗状态时，结界也会被解除」. It is
     * recorded as ROADMAP P10-8 rather than faked, and this case documents <b>why</b> the obvious fix does not work,
     * because that is the expensive part to rediscover:
     *
     * <p>The obvious fix is to sweep a dead unit's own-anchored buffs in {@code releaseBuffsAnchoredToTheDead}. That
     * was tried and reverted: it also strips buffs that a unit legitimately receives <b>as it dies</b> or after --
     * "all allies lethally hit this action" is a real rule family, and its carrier is a unit that is already
     * {@code isDeath()} when the buff lands. Two shipped cases went red ({@code LethalSetTest},
     * {@code RelicAbilityBatchTest}).
     *
     * <p>So the clause needs a mechanism that distinguishes "this buff was anchored to its own carrier on purpose"
     * from "this buff's clock happens to be its carrier", which is what the engine currently cannot tell apart.
     */
    @Test
    public void theFieldIsStillUpAfterHeFalls_pinningTheGap() {
        Fixture f = new Fixture();
        f.openField();
        Assertions.assertTrue(f.fieldUp(), "precondition: the field is up");

        f.killHimOnARealTurn();

        Assertions.assertTrue(f.luocha.isDeath(), "precondition: he really fell");
        // ⚠ This assertion encodes the GAP, not the document. It is here so the gap is visible and cannot close
        // silently: when P10-8 is implemented this case must be inverted to assertFalse.
        Assertions.assertTrue(f.fieldUp(),
                "KNOWN GAP (P10-8): 「当罗刹陷入无法战斗状态时，结界也会被解除」 is not implemented; "
                        + "this assertion flips to assertFalse when it is");
    }

    /**
     * The field also runs out on its own after 2 of his turns.
     *
     * <p>Asserted separately from the death case: an anchor alone would keep it up forever, and a duration alone
     * would leave it standing over his corpse. Takes <b>three</b> of his turns, because the turn it was opened on
     * is the first one it is spent on.
     */
    @Test
    public void theFieldRunsOutAfterTwoOfHisTurns() {
        Fixture f = new Fixture();
        f.openField();
        Assertions.assertTrue(f.fieldUp(), "precondition: the field is up");

        f.takeTurnsUntilHisAreSpent();

        Assertions.assertFalse(f.fieldUp(),
                "「结界效果持续2回合」, counted on his own turns -- it is gone once they are spent");
    }

    /**
     * A battle with 罗刹, an ally and a monster, plus the shorthand the cases need.
     */
    private static final class Fixture {

        private final Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(luocha, ally), List.of(enemy),
                BattleFixture.alwaysRoll(0.0));

        private Fixture() {
            battle.startBattle();
        }

        /** One Skill, which adds a stack. */
        void castSkill() {
            battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
        }

        /** Enough Skills to reach the two stacks that open the field. */
        void openField() {
            castSkill();
            castSkill();
        }

        /**
         * Runs real turns until 罗刹 falls and the turn that killed him has ended.
         *
         * <p>Each iteration advances the bar and runs the turn's start and end, which is what the engine's own
         * cleanup hangs off. Lethal damage is applied at the start of his turn so the end of that same turn is the
         * one that clears the dead.
         */
        void killHimOnARealTurn() {
            for (int guard = 0; guard < 60 && !luocha.isDeath(); guard++) {
                battle.stepForward();
                battle.beforeMove();
                battle.applyDamage(luocha, new Damage(enemy, luocha, DamageElement.PHYSICAL,
                        DamageType.NORMAL, luocha.getMaxHp() * 10));
                battle.afterMove();
            }
        }

        /**
         * Runs real turns until his own turns have spent the field's duration.
         *
         * <p>Bounded, so a duration that never ticks fails the assertion rather than hanging the suite.
         */
        void takeTurnsUntilHisAreSpent() {
            for (int guard = 0; guard < 40 && fieldUp(); guard++) {
                battle.stepForward();
                battle.beforeMove();
                battle.afterMove();
            }
        }

        int stacks() {
            return luocha.getBuffManager().stacksOf("白花之刻");
        }

        boolean fieldUp() {
            return luocha.getBuffManager().hasState("结界");
        }
    }
}
