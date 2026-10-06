package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
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
 * Hanya (寒鸦)'s [承负] family, from her own file: the state, the two-mark refund and the traces that hang off it.
 *
 * <p><b>What it needed.</b> A counter with a threshold ({@code ADD_STACK} + {@code target_stacks:<name>} - "每 2 次...后" (after every 2 times) and
 * "触发 2 次后自动解除" (removed automatically after triggering twice) are both of that shape), and <b>the cast category on {@code ALLY_ATTACK}</b>: "施放 2 次普攻、战技、
 * 终结技" (casting basic attacks, Skills or ultimates 2 times) must count <i>casts</i>, which only that event knows - counting on {@code DEALING_DAMAGE} would count hits and
 * pay out early on a multi-hit skill, with nothing to report.
 */
public class HanyaBurdenTest {
    private static final int HANYA = 1215;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** "[承负] applies only to the most recently applied target" ([承负]仅对最新被施加的目标生效): the second cast takes it off the first enemy. */
    @Test
    public void theSkillMovesBurdenToTheLatestTarget() {
        Fixture f = new Fixture();
        f.castSkillOn(f.first);
        Assertions.assertTrue(f.first.getBuffManager().hasState("承负"), "precondition: the first enemy carries it");

        f.castSkillOn(f.second);

        Assertions.assertFalse(f.first.getBuffManager().hasState("承负"),
                "「仅对最新被施加的目标生效」 (applies only to the latest applied target) -- the removal IS that clause (there is no single-holder flag)");
        Assertions.assertTrue(f.second.getBuffManager().hasState("承负"));
    }

    /** Two qualifying attacks reach the threshold, and that clears the state ("触发 2 次后自动解除", removed automatically after triggering twice). */
    @Test
    public void twoAlliedAttacksReachTheRefundThreshold() {
        Fixture f = new Fixture();
        f.castSkillOn(f.first);

        f.allyAttacks(f.first, com.laosun.aluminium.enums.SkillCategory.NORMAL);
        Assertions.assertTrue(f.first.getBuffManager().hasState("承负"),
                "one attack is not two: 「每 2 次…后」 (after every 2 times) has not been reached");

        f.allyAttacks(f.first, com.laosun.aluminium.enums.SkillCategory.BPSKILL);
        Assertions.assertFalse(f.first.getBuffManager().hasState("承负"),
                "the second qualifying cast reached the threshold, the party got a skill point and 【承负】 (Burden) came off");
    }

    /** Note: The counter counts <b>casts</b>, not hits: a multi-hit skill marks once. */
    @Test
    public void aMultiHitCastCountsOnce() {
        Fixture f = new Fixture();
        f.castSkillOn(f.first);

        f.allyAttacks(f.first, com.laosun.aluminium.enums.SkillCategory.NORMAL, 3);
        Assertions.assertTrue(f.first.getBuffManager().hasState("承负"),
                "three hits of ONE basic attack are still one 「次」 (one occurrence) -- the counter is on the attack event, not on the "
                        + "damage instances");
    }

    /** The whole family is present, and nothing else was written. */
    @Test
    public void herFileCarriesTheBurdenFamily() {
        Assertions.assertEquals(4, TriggerTables.of(HANYA).ruleCount(TriggerEvent.ALLY_ATTACK),
                "three mark rules (one per qualifying slot) plus the threshold rule that answers them");
        Assertions.assertEquals(3, TriggerTables.of(HANYA).ruleCount(TriggerEvent.DEALING_DAMAGE),
                "the talent's boost, one rule per slot");
        Assertions.assertEquals(2, TriggerTables.of(HANYA).ruleCount(TriggerEvent.KILL),
                "Eidolon 1 (the carrier\'s kill advances her) and the Traces (行迹) Netherworld (幽府) trace (a kill on a Burden (【承负】) target refunds one more)");
        Assertions.assertEquals(2, TriggerTables.of(HANYA).ruleCount(TriggerEvent.SKILL_CAST),
                "the Burden (承负) application and Eidolon 2's speed boost");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character hanya = CharacterFactory.create(HANYA, LEVEL, true, null, null, 0);
        /** The teammate (队友) the marks are about: an ally OTHER than the carrier, hence a field of its own. */
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        private final Enemy second = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture() {
            battle = new Battle(List.of(hanya, ally),
                    List.of(first, second), fixed());
            battle.startBattle();
        }

        private void castSkillOn(Enemy target) {
            battle.castImmediate(hanya.getSkills().get(SkillType.SKILL), hanya, List.of(target));
        }

        private void allyAttacks(Enemy target, com.laosun.aluminium.enums.SkillCategory category) {
            allyAttacks(target, category, 1);
        }

        /** One attack by Hanya herself ("我方目标" -- our targets -- includes her), stating the cast category the event now carries. */
        private void allyAttacks(Enemy target, com.laosun.aluminium.enums.SkillCategory category, int hits) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, target, hits, 0, category);
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
