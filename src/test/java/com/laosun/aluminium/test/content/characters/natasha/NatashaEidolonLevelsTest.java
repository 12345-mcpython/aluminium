package com.laosun.aluminium.test.content.characters.natasha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Natasha's two skill-level Eidolons, which are easy to conflate:
 *
 * <ul>
 *   <li><b>星魂 3 对症下药</b>: 「<b>战技</b>等级+2，最多不超过15级；<b>普攻</b>等级+1，最多不超过10级。」</li>
 *   <li><b>星魂 5 医治未病</b>: 「<b>终结技</b>等级+2，最多不超过15级；<b>天赋</b>等级+2，最多不超过15级。」</li>
 * </ul>
 *
 * <p>Four different skills across the two, so neither rule may be the other with a wrong token.
 *
 * <p>Measured through {@code skillLevelBonus(slot)}, which reads the raise itself rather than the level a skill ends
 * up at: the resulting level depends on the data row's own base level, so asserting on it would pin the data row and
 * make the case fragile for reasons that have nothing to do with these Eidolons.
 *
 * <p>⚠ Deliberately NOT asserted: the 「最多不超过15级 / 10级」 caps. Those are the skill data's own level ceiling,
 * which the precedents (1103, 1109, 1215) also leave to the data rather than clamping in a rule.
 */
public class NatashaEidolonLevelsTest {

    private static final int NATASHA = 1105;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * At rank 0 neither Eidolon is unlocked, so no slot carries a raise.
     *
     * <p>This is the case that fails if either rule loses its {@code min_eidolon}.
     */
    @Test
    public void noEidolonMeansNoRaise() {
        for (SkillType slot : List.of(SkillType.SKILL, SkillType.COMMON, SkillType.ULTRA, SkillType.TALENT)) {
            Assertions.assertEquals(0, bonus(slot, 0),
                    "rank 0 has no skill-level Eidolon, so " + slot + " must carry no raise");
        }
    }

    /**
     * 星魂 3 raises the Skill by 2 and the basic attack by 1 -- and nothing else.
     */
    @Test
    public void eidolonThreeRaisesSkillByTwoAndBasicByOne() {
        Assertions.assertEquals(2, bonus(SkillType.SKILL, 3), "「战技等级+2」");
        Assertions.assertEquals(1, bonus(SkillType.COMMON, 3), "「普攻等级+1」");
        Assertions.assertEquals(0, bonus(SkillType.ULTRA, 3), "星魂 3 says nothing about the Ultimate");
        Assertions.assertEquals(0, bonus(SkillType.TALENT, 3), "星魂 3 says nothing about the Talent");
    }

    /**
     * 星魂 5 raises the Ultimate by 2 and the Talent by 2 -- and nothing else.
     */
    @Test
    public void eidolonFiveRaisesUltimateAndTalentByTwo() {
        Assertions.assertEquals(2, bonus(SkillType.ULTRA, 5), "「终结技等级+2」");
        Assertions.assertEquals(2, bonus(SkillType.TALENT, 5), "「天赋等级+2」");
        Assertions.assertEquals(0, bonus(SkillType.SKILL, 5) - bonus(SkillType.SKILL, 3),
                "rank 5 adds nothing to the Skill beyond 星魂 3's own +2");
    }

    /**
     * The two Eidolons are independent: at rank 3 the Ultimate is untouched, at rank 5 the Skill is not touched again.
     */
    @Test
    public void theTwoEidolonsDoNotOverlap() {
        // Rank 3 pays the Skill half; rank 5 pays the Ultimate half. Reading rank 5's Skill against rank 3's isolates
        // 星魂 5 from 星魂 3, because both ranks carry the latter.
        Assertions.assertEquals(0, bonus(SkillType.ULTRA, 3),
                "the Ultimate raise belongs to rank 5, not to rank 3");
        Assertions.assertEquals(2, bonus(SkillType.SKILL, 5),
                "rank 5 still carries 星魂 3's Skill raise, and 星魂 5 adds nothing on top of it");
    }

    /**
     * The raise a slot carries at the given rank.
     *
     * @param slot    which skill
     * @param eidolon her Eidolon rank
     * @return the levels this battle has raised that slot by
     */
    private static int bonus(SkillType slot, int eidolon) {
        // The file carries the corpus-wide level convention (+9 to every slot, so every number matches the Lv10 row the
        // document quotes), so this asks for the raise ABOVE rank 0 rather than the absolute bonus.
        return level(slot, eidolon) - level(slot, 0);
    }

    private static int level(SkillType slot, int eidolon) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(natasha),
                List.of(EnemyFactory.create(MONSTER, LEVEL, 1)), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();
        return battle.characters().getFirst().skillLevelBonus(slot);
    }
}
