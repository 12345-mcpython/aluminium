package com.laosun.aluminium.test.content.characters.danheng;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1002 Dan Heng (丹恒): <b>where each of his numbers comes from</b> - the ones that move with a skill level read their own
 * row, and the ones that cannot are checked against the row they claim.
 *
 * <h2>The two numbers that used to be literals</h2>
 * <ul>
 *   <li><b>the talent's 36%</b> res-pen. His Talent's parameter row is {@code [0.18, 2]} at Lv1, {@code [0.36, 2]} at
 *       Lv10 and {@code [0.45, 2]} at Lv15 - a column that <b>moves with the level</b>. Writing {@code percent: 0.36}
 *       froze it, so 星魂 5 (Talent +2 = 12 levels) read 0.36 instead of <b>0.396</b>. The rule now states
 *       {@code percent_from_skill_param: "TALENT:0"}.</li>
 *   <li><b>the technique's 40%</b> ATK. Its table is a single row ({@code [0.4, 3]}), so the number is the same either
 *       way - but the source is now in the rule instead of being a literal that looks measured and is not.</li>
 * </ul>
 *
 * <h2>The numbers that stay literal on purpose</h2>
 * The Skill's slow is <b>-12%</b>: the row carries the magnitude {@code 0.12}, while the <b>sign</b> is the sentence
 * (「速度<u>降低</u>」) - there is no "negate the row" spelling, so a literal is the honest form. It is therefore checked
 * <b>against the row</b> here (the applied slow must equal {@code row[1]}), so the two cannot drift apart silently.
 */
public class DanHengLevelSourceTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * 天赋: the penetration is <b>the Talent row at its current level</b> - so 星魂 5 moves it, and at rank 0 it is
     * exactly the 36% the sentence states.
     */
    @Test
    public void theTalentPenetrationIsTheTalentsOwnRow() {
        double atRankZero = penetrationAfterTheAllyAims(0);
        double atRankFive = penetrationAfterTheAllyAims(5);

        var rows = SkillData.init(DANHENG, 4).getSkills();
        Assertions.assertEquals(rows.get(9).getFirst(), atRankZero, EPS,
                "rank 0 raises the Talent by 9 (the file's level convention) ⇒ row 10, whose first parameter is "
                        + rows.get(9).getFirst());
        Assertions.assertEquals(rows.get(11).getFirst(), atRankFive, EPS,
                "星魂 5 raises the Talent by 2 more ⇒ row 12, i.e. " + rows.get(11).getFirst()
                        + " - a literal 0.36 would have stayed at 0.36 here");
        Assertions.assertNotEquals(atRankZero, atRankFive, EPS,
                "…and the two differ, so the value really is read from the row rather than written down");
    }

    /**
     * 秘技: the ATK boost is the technique row's first parameter (a single-row table, so this pins the <b>source</b>) and
     * it is a <b>share of the base</b> (+40%), not a flat +0.4.
     *
     * <p>⚠ Measured as the <b>difference</b> between a battle where the technique was used and one where it was not,
     * because his stat traces already raise ATK by 18%: reading the ratio alone would report 0.58 and look wrong.
     */
    @Test
    public void theTechniqueAttackBonusIsTheTechniqueRow() {
        double withTechnique = attackRatioWithTechnique(true);
        double without = attackRatioWithTechnique(false);

        double row = SkillData.init(DANHENG, 7).getSkills().getFirst().getFirst();
        Assertions.assertEquals(row, withTechnique - without, EPS,
                "「攻击力提高 40%，持续 3 回合」 ⇒ the technique row's #1, applied as a share of the base");
        // The trap this case was written for: stated as `percent_from_skill_param` with no scale, the modifier was once
        // handed down as an ABSOLUTE number, so this came out as +0.4 of attack instead of +40%.
        Assertions.assertTrue(withTechnique - without > 0.39, "a flat +0.4 would be ~0.0007 of his attack");
    }

    /**
     * His ATK divided by its base, with or without the technique's opening buff.
     */
    private static double attackRatioWithTechnique(boolean used) {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        if (used) {
            battle.markTechniqueUsed(danHeng);
        }
        battle.startBattle();
        return danHeng.getAttribute(AttributeType.ATTACK).get()
                / danHeng.getAttribute(AttributeType.ATTACK).baseValue() - 1.0;
    }

    /**
     * The Skill's slow is a literal with a <b>sign the row does not carry</b>, so it is checked against the row: the
     * applied SPEED reduction must be the row's magnitude.
     *
     * <p>Note: This is the guard for "a literal that is the row plus the sentence's sign". The sentence says 降低, the
     * row says 0.12, so the modifier is -0.12; if the row were rebalanced to 0.15, this case would go red and the literal
     * would have to move with it.
     */
    @Test
    public void theSlowLiteralIsTheRowsMagnitudeWithTheSentencesSign() {
        var row = SkillData.init(DANHENG, 2).getSkills().get(9);     // Lv10, the file's level convention
        double statedSlow = row.get(1);
        double statedTurns = row.get(2);
        Assertions.assertEquals(2.0, statedTurns, EPS, "precondition: the duration is the row's third parameter");

        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));               // every crit roll passes, so the slow always lands
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.SPEED).get();
        battle.castImmediate(danHeng.getSkills().get(SkillType.SKILL), danHeng, List.of(enemy));

        double applied = 1.0 - enemy.getAttribute(AttributeType.SPEED).get() / before;
        Assertions.assertEquals(statedSlow, applied, EPS,
                "the applied reduction must be the row's magnitude, not a number that merely matches it today");
    }

    /**
     * His penetration after an ally aims a Skill at him, at the given eidolon rank.
     */
    private static double penetrationAfterTheAllyAims(int eidolon) {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(danHeng));
        return danHeng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }
}
