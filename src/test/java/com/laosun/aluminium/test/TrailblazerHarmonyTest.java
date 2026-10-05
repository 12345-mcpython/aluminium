package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 开拓者-同谐 (8006), from her own file (2026-09-28): [伴舞], the break trace and Eidolon 1/4.
 *
 * <p><b>What it needed.</b> Nothing new: {@code ticks_on: "self"} carries "the Trailblazer's duration is reduced by 1 at the start of each of her turns" (the caster's
 * clock, the same field 星期日's [蒙福者] uses), `BREAK` is "when an enemy target's weakness is broken", `once_per_battle` is "the first time", and
 * Eidolon 4's "equal to 15% of the Trailblazer's Break Effect" is the ordinary derived scale read off the rule owner.
 *
 * <p><b>What is registered</b> (the file's notes): the super-break conversion (no super-break damage type), trace 随波逐流
 * (a timed boost would hit every bounce hit, not only the first) and 剧院之帽 (no action-delay op).
 */
public class TrailblazerHarmonyTest {
    private static final int HARMONY = 8006;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** [伴舞] lands on the whole side and carries the break-effect boost. */
    @Test
    public void herUltimateGrantsDanceToEveryone() {
        Fixture f = new Fixture();
        double before = f.ally.getAttribute(AttributeType.BREAKING_EFFECT).get();

        f.castUltimate();

        Assertions.assertTrue(f.ally.getBuffManager().hasState("伴舞"), "「为我方全体附上【伴舞】效果」");
        Assertions.assertTrue(f.harmony.getBuffManager().hasState("伴舞"), "…including the caster (「我方全体」)");
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.BREAKING_EFFECT).get() > before,
                "「持有【伴舞】的我方目标击破特攻提高30%」");
    }

    /**
     * Note: The duration runs on HER clock: her turn shortens it, an ally's does not.
     *
     * <p>"apply the [伴舞] effect to all of our side, lasting 3 turns, the Trailblazer's duration reduced by 1 at the start of each of her turns" - the state sits on every ally
     * while the clock belongs to the caster, which is what {@code "ticks_on": "self"} states (the same field 星期日's
     * [蒙福者] uses). The harness detail that made this case fail twice is in {@link Fixture#fullTurnOf}.
     */
    @Test
    public void theDurationTicksOnHerOwnTurns() {
        Fixture f = new Fixture();
        f.castUltimate();

        f.fullTurnOf(f.ally);
        Assertions.assertTrue(f.ally.getBuffManager().hasState("伴舞"),
                "an ally's turn does not shorten it: \"**the Trailblazer**'s turn start\"");

        f.fullTurnOf(f.harmony);
        f.fullTurnOf(f.harmony);
        f.fullTurnOf(f.harmony);
        Assertions.assertFalse(f.ally.getBuffManager().hasState("伴舞"),
                "three of HER turns run the 3-turn state out, even on the allies carrying copies");
    }

    /** The talent pays energy on any break, and Eidolon 4 passes her break effect to the others. */
    @Test
    public void herTalentAndFourthEidolonAreAsStated() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, TriggerTables.of(HARMONY).ruleCount(TriggerEvent.BREAK),
                "「当有敌方目标的弱点被击破时，开拓者立即恢复10点能量」");

        Fixture atFour = new Fixture(4);
        Assertions.assertTrue(atFour.ally.getAttribute(AttributeType.BREAKING_EFFECT).get()
                        > f.ally.getAttribute(AttributeType.BREAKING_EFFECT).get(),
                "Eidolon 4: \"raise the Break Effect of the teammates other than oneself, the raise being equal to 15% of the Trailblazer's Break Effect\"");
        Assertions.assertEquals(f.harmony.getAttribute(AttributeType.BREAKING_EFFECT).get(),
                atFour.harmony.getAttribute(AttributeType.BREAKING_EFFECT).get(), 1e-6,
                "…and \"other than oneself\" means she is not boosted by it");
    }

    /** The registered clauses stay registered. */
    @Test
    public void herFileCarriesWhatItSays() {
        Assertions.assertEquals(1, TriggerTables.of(HARMONY).ruleCount(TriggerEvent.ULT_CAST));
        Assertions.assertEquals(1, TriggerTables.of(HARMONY).ruleCount(TriggerEvent.SKILL_CAST),
                "Eidolon 1 (the registered super-break clauses are absent on purpose)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character harmony;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            harmony = CharacterFactory.create(HARMONY, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(harmony, ally), List.of(enemy), fixed());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(harmony.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), harmony,
                    List.of(ally, harmony));
        }

        /**
         * One unit's <b>whole turn</b>, run through the engine rather than fired by hand.
         *
         * <p>Note: Both halves are needed, and that is the whole lesson of this case: a `TURN_START` fired by hand does not run
         * the foreign-buff tick at all, and driving only `beforeMove()` does not run it for a <b>late</b> buff either - 
         * `APPLY_BUFF` creates a late one, and "the Trailblazer's duration is reduced by 1 at the start of each of her turns" is delivered by
         * `tickForeignBuffs(actor, false)` inside `afterMove`. Two earlier versions of this case failed on exactly that.
         */
        private void fullTurnOf(Character unit) {
            battle.currentMove = battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == unit)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for that unit"));
            battle.beforeMove();
            battle.afterMove();
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
