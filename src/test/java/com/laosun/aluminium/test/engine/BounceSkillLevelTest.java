package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Skill.StanceList;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.DoubleValue;

/**
 * A Bounce skill's share and hit count come out of its row <b>at the level it is cast at</b>.
 *
 * <h2>Why this needed a guard</h2>
 * Those two numbers are read out of the <b>description's placeholders</b> (the rows disagree on layout, so a fixed index
 * is wrong), and both readers used to take {@code param_list[0]} unconditionally. That was invisible for as long as the
 * characters happened to cast at level 1. Once the content states its level convention (「the document quotes Lv10」),
 * a Bounce skill dealt its <b>row-1</b> share while its multiplier came from row 10: measured on 1004 Welt
 * (瓦尔特) - 0.36 per hit where row 10 says 0.72, and 1009 Asta (艾丝妲) - 0.25 where row 10 says 0.50.
 *
 * <h2>What each case is for</h2>
 * <ol>
 *   <li>the data readers answer per row (two rows, two numbers);</li>
 *   <li>the <b>behaviour</b> follows: the same skill cast at two levels deals <b>the ratio of the two shares</b> - the
 *       ratio rather than an absolute number, because one hit has to pass the defender's defence and resistance, and
 *       both casts share those (the same unit, the same enemy, the same seed);</li>
 *   <li>the hit count also comes from the row - with a fabricated table, because no shipped Bounce skill's count
 *       happens to vary by level (all of them are constant down the column, so the data alone cannot show it).</li>
 * </ol>
 */
public class BounceSkillLevelTest {
    private static final double EPS = 1e-9;

    /**
     * Asta (艾丝妲) - her Bounce states "additionally deals 4 instances" in prose, so it is one hit and its share is
     * {@code param_list[0]} of the row: 0.25 at row 1, 0.50 at row 10.
     */
    private static final int ASTA = 1009;
    /**
     * Welt (瓦尔特) - a Bounce whose chance column also moves (0.65 -> 0.75), used by the data cases.
     */
    private static final int WELT = 1004;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The data readers answer per row
    // ==================================================================

    /**
     * The share is read from the row the level names, not from the first row.
     */
    @Test
    public void theShareIsReadFromTheRowsTheLevelNames() {
        SkillData astaBounce = SkillData.init(ASTA, 2);
        Assertions.assertEquals(0.25, astaBounce.bounceDamageShare(1), EPS, "row 1");
        Assertions.assertEquals(0.5, astaBounce.bounceDamageShare(10), EPS, "row 10");
        Assertions.assertNotEquals(astaBounce.bounceDamageShare(1), astaBounce.bounceDamageShare(10), EPS,
                "a reader that ignored the level would answer the same number twice");

        SkillData weltBounce = SkillData.init(WELT, 2);
        Assertions.assertEquals(0.36, weltBounce.bounceDamageShare(1), EPS, "row 1");
        Assertions.assertEquals(0.72, weltBounce.bounceDamageShare(10), EPS, "row 10");
    }

    /**
     * The hit count is read from the same row - and the share of THAT row is what tells the count apart from it.
     */
    @Test
    public void theHitCountIsReadFromThatSameRow() {
        SkillData weltBounce = SkillData.init(WELT, 2);
        Assertions.assertEquals(2, weltBounce.bounceAdditionalHits(weltBounce.bounceDamageShare(1), 1),
                "row 1: the integer that is not the share");
        Assertions.assertEquals(2, weltBounce.bounceAdditionalHits(weltBounce.bounceDamageShare(10), 10),
                "row 10: same count, but it was read out of row 10's own members");

        // A fabricated table whose COUNT moves with the level: nothing shipped does, so the row really has to be read.
        SkillData twoRows = new SkillData(1, "Bounce", List.of(List.of(0.5, 2.0), List.of(1.0, 5.0)),
                new StanceList(0, 0, 0), DamageElement.ICE, SkillEffectType.BOUNCE, null, 30.0, null,
                "Deals DMG equal to #1[i]% of ATK and additionally deals #2[i] instances of DMG.");
        Assertions.assertEquals(2, twoRows.bounceAdditionalHits(twoRows.bounceDamageShare(1), 1), "row 1's count");
        Assertions.assertEquals(5, twoRows.bounceAdditionalHits(twoRows.bounceDamageShare(2), 2), "row 2's count");
        Assertions.assertNotEquals(twoRows.bounceAdditionalHits(twoRows.bounceDamageShare(1), 1),
                twoRows.bounceAdditionalHits(twoRows.bounceDamageShare(2), 2),
                "…and they differ, so a row-1 reader cannot pass");
    }

    /**
     * The count is read from the <b>sentence</b> when the row has none - and even when the row's integer contradicts it.
     *
     * <p>Note: Measured on the shipped data: 1009 Asta (艾丝妲) states 「额外造成4次伤害」 and her row is just {@code [0.25]},
     * so the row heuristic answered "none" and the skill hit <b>once</b>; 8005/8006 state the same sentence while their
     * row carries a {@code 2} that is <b>not</b> the count. Both cases are pinned here, because the row heuristic alone
     * cannot tell them apart.
     */
    @Test
    public void theCountComesFromTheSentenceWhenTheRowHasNone() {
        SkillData astaBounce = SkillData.init(ASTA, 2);
        Assertions.assertEquals(4, astaBounce.bounceAdditionalHits(astaBounce.bounceDamageShare(10), 10),
                "「并额外造成4次伤害」 → 4 extra hits (5 in total); her row has no integer at all");

        SkillData trailblazer = SkillData.init(8005, 2);
        Assertions.assertEquals(4, trailblazer.bounceAdditionalHits(trailblazer.bounceDamageShare(1), 1),
                "the same sentence, while the row's own 2 means something else");

        SkillData weltBounce = SkillData.init(WELT, 2);
        Assertions.assertEquals(2, weltBounce.bounceAdditionalHits(weltBounce.bounceDamageShare(1), 1),
                "1004 Welt (瓦尔特): sentence and row agree on 2 - the case the old heuristic got right");
    }

    // ==================================================================
    // 2. The behaviour follows the same row
    // ==================================================================

    /**
     * The same Bounce cast at level 1 and at level 10 deals the <b>ratio</b> of the two rows' shares.
     *
     * <p>Note: A ratio, not an absolute: the hit passes defence and resistance, and those cancel because both casts use
     * the same attacker, the same enemy and the same seed. 0.50 / 0.25 = 2.
     */
    @Test
    public void aRaisedBounceDealsTheRaisedShare() {
        double atOne = damageOfBounceAt(1);
        double atTen = damageOfBounceAt(10);

        double row1 = SkillData.init(ASTA, 2).bounceDamageShare(1);
        double row10 = SkillData.init(ASTA, 2).bounceDamageShare(10);

        System.out.println("[bounce] level 1 = " + atOne + " ; level 10 = " + atTen + " ; ratio = " + (atTen / atOne)
                + " ; the rows' ratio = " + (row10 / row1));
        Assertions.assertEquals(row10 / row1, atTen / atOne, 1e-6,
                "the settled damage ratio IS the two rows' share ratio - the multiplier path already followed the level, "
                        + "and now the share does too");
        Assertions.assertNotEquals(1.0, atTen / atOne, 1e-6,
                "a level-blind reader would deal exactly the same damage at both levels");
    }

    /**
     * Casts Asta's Bounce at the given level and returns the total damage dealt to one big enemy.
     */
    private static double damageOfBounceAt(int level) {
        // Note: built WITHOUT her rule table on purpose: this case is about the executor's row reading, and her own kit
        // fires on cast events, which would add its own terms and blur the ratio.
        Character asta = Character.builder().cid(ASTA).level(LEVEL).build();
        Enemy enemy = BattleFixture.monster();
        enemy.setAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH,
                new com.laosun.aluminium.DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        Battle battle = new Battle(List.of(asta), List.of(enemy), BattleFixture.alwaysRoll(1.0));

        Skill bounce = new DefaultSkill(ASTA, 2, level);
        double before = enemy.getCurrentHp();
        SkillExecutor.execute(battle, bounce, asta, List.of(enemy));
        return before - enemy.getCurrentHp();
    }
}
