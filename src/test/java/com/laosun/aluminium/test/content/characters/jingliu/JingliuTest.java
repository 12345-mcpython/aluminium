package com.laosun.aluminium.test.content.characters.jingliu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1212 Jingliu, from her own file: the Syzygy stack, its cap, the threshold that fires her Action Advance, and the
 * Ultimate's 300%/150% blast row.
 *
 * <p>The threshold is a {@code self_stacks:} condition - a form the engine already had - and the Advance is observed as
 * round 216's recipe prescribes: the remaining wait must reach 0.
 */
public class JingliuTest {
    private static final int JINGLIU = 1212;
    private static final int LEVEL = 80;

    /**
     * Note: One stack per Skill, stopped at the document's three.
     */
    @Test
    public void theSkillAddsOneStackUpToThree() {
        Character jingliu = CharacterFactory.create(JINGLIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jingliu), List.of(enemy), fixed());
        battle.startBattle();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, jingliu, enemy, 0, 0));
        Assertions.assertEquals(1, jingliu.getBuffManager().stacksOf("朔望"),
                "「并获得1层【朔望】」 (and gains 1 stack of [朔望])");

        for (int i = 0; i < 4; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, jingliu, enemy, 0, 0));
        }
        Assertions.assertEquals(3, jingliu.getBuffManager().stacksOf("朔望"),
                "「【朔望】最多可累计3层」 ([朔望] accumulates at most 3 stacks) -- five casts must still read three");
    }

    /**
     * Note: The threshold: the SECOND cast reaches two stacks and must Advance; the first must not.
     */
    @Test
    public void theAdvanceNeedsTwoStacks() {
        double afterOne = waitAfterCasts(1);
        double afterTwo = waitAfterCasts(2);
        double untouched = waitAfterCasts(0);

        Assertions.assertTrue(untouched > 0, "the fixture must have a wait to skip: " + untouched);
        Assertions.assertEquals(1.0, afterOne / untouched, 1e-6,
                "with ONE stack the Advance must not fire: " + untouched + " -> " + afterOne);
        Assertions.assertEquals(0.0, afterTwo, 1e-6,
                "「当拥有2层【朔望】时…使行动提前100%」 (at 2 stacks of [朔望] ... advances her action by 100%): " + untouched + " -> " + afterTwo);
    }

    /**
     * 1212 镜流's Ultimate: 「对指定敌方单体造成等同于镜流 300% 攻击力的冰属性伤害，并对其相邻目标造成等同于镜流 150% 攻击力的冰属性伤害」.
     *
     * <p><b>Why this is a criterion and not a rule.</b> The file used to register the adjacent half as 「未写（相邻是伤害形状）」 - true
     * of the RULE, and irrelevant: the Ultimate's data row is a {@code Blast}, and the engine's own settlement reads the adjacent
     * column from that row (300% main, 150% neighbours). So the shape is already right, and what was missing was a judge that says so:
     * 150 / 300 = 0.5 on the neighbours.
     */
    @Test
    public void theUltimateSplashesHalfOntoTheNeighbours() {
        Character jingliu = CharacterFactory.create(JINGLIU, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy centre = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        for (Enemy enemy : List.of(left, centre, right)) {
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(jingliu)), new ArrayList<>(List.of(left, centre, right)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        double leftBefore = left.getCurrentHp();
        double centreBefore = centre.getCurrentHp();
        double rightBefore = right.getCurrentHp();
        battle.castImmediate(jingliu.getSkills().get(SkillType.ULTRA), jingliu, List.of(centre));
        battle.processRequests();

        double main = centreBefore - centre.getCurrentHp();
        double leftLoss = leftBefore - left.getCurrentHp();
        double rightLoss = rightBefore - right.getCurrentHp();
        System.out.println("[1212] ult main=" + main + " neighbours=" + leftLoss + "/" + rightLoss);
        Assertions.assertTrue(main > 0, "precondition: the Ultimate hit the main target");
        Assertions.assertEquals(0.5, leftLoss / main, 1e-6,
                "「对其相邻目标造成等同于镜流 150% 攻击力的冰属性伤害」 - against the main target's 300%");
        Assertions.assertEquals(0.5, rightLoss / main, 1e-6, "and on the other side too");
    }

    /**
     * The enhanced Skill （寒川映月，data slot 9） has the same interleaved row {@code [2.5, 1, 1.25]} and therefore the same override.
     *
     * <p>⚠ It is not reachable in battle yet - 【转魄】's skill swap is still a registered gap - so this half judges the TABLE
     * rather than a cast. That is deliberate: the read is wrong whether or not the skill can be cast today, and a later round that
     * implements the swap would otherwise inherit a 20% undershoot with no symptom.
     */
    @Test
    public void theEnhancedSkillNamesTheSameColumnInTheCuratedTable() {
        var spec = com.laosun.aluminium.data.SkillEffects.forSkill(
                CharacterFactory.create(JINGLIU, LEVEL).getSkills().get(SkillType.COMMON) == null
                        ? null
                        : new com.laosun.aluminium.skill.DefaultSkill(JINGLIU, 9, LEVEL));
        Assertions.assertNotNull(spec, "the curated row for 1212/9 must exist");
        Assertions.assertEquals(2, spec.getAdjacentParam(), "寒川映月's neighbours are #3 (125%), not #2 (the stack count)");
    }

    /**
     * Casts the Skill `casts` times (the RULES build the stacks) and returns her remaining wait.
     */
    private static double waitAfterCasts(int casts) {
        Character jingliu = CharacterFactory.create(JINGLIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jingliu), List.of(enemy), fixed());
        battle.startBattle();
        for (int i = 0; i < casts; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, jingliu, enemy, 0, 0));
        }
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == jingliu) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException("she is not in the queue");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
