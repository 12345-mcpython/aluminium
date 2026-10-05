package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 三月七's 星魂 3 / 星魂 5 and the level her own document is quoted at (M-32) - content, on top of the engine's
 * {@code RAISE_SKILL_LEVEL}.
 *
 * <p><b>What the documents say.</b> 星魂 3 = "<b>终结技</b>等级+2，最多不超过15级；<b>普攻</b>等级+1，最多不超过10级" (data row
 * {@code {'100103': 2, '100101': 1}}), 星魂 5 = "<b>战技</b>等级+2，最多不超过15级；<b>天赋</b>等级+2，最多不超过15级" (row
 * {@code {'100102': 2, '100104': 2}}). Her talent is quoted at Lv10 in the prose, which used to be pinned per-effect with
 * {@code damage_level: 10} and is now the <b>base level</b> the file states, so the 星魂 raises compose with it.
 *
 * <p>Note: The last case is why that composition matters: at 星魂 5 her counter reads a row that no fixed
 * {@code damage_level} could ever have expressed.
 */
public class March7thLevelTest {
    private static final double EPS = 1e-6;

    private static final int MARCH = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The document's level for her talent, stated by the file instead of by a per-effect pin. */
    @Test
    public void herTalentIsReadAtTheDocumentsLevel() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 0);
        new Battle(List.of(march), List.of(enemy()), fixed()).startBattle();

        Assertions.assertEquals(9, march.skillLevelBonus(SkillType.TALENT),
                "the base rule states 「the document quotes Lv10」 as +9 (level 1 -> 10)");
        Assertions.assertEquals(10, march.skillLevel(march.getSkills().get(SkillType.TALENT)),
                "…so every effect that reads her talent indexes row 10");
    }

    /** 星魂 3 raises the ultimate and the basic attack - and nothing else. */
    @Test
    public void starRailThreeRaisesTheUltimateAndTheBasicAttack() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 2);
        new Battle(List.of(march), List.of(enemy()), fixed()).startBattle();
        Assertions.assertEquals(0, march.skillLevelBonus(SkillType.ULTRA), "星魂 2 is not 星魂 3");

        Character atThree = CharacterFactory.create(MARCH, LEVEL, true, null, null, 3);
        new Battle(List.of(atThree), List.of(enemy()), fixed()).startBattle();
        Assertions.assertEquals(2, atThree.skillLevelBonus(SkillType.ULTRA), "「终结技等级+2」");
        Assertions.assertEquals(1, atThree.skillLevelBonus(SkillType.COMMON), "「普攻等级+1」");
        Assertions.assertEquals(0, atThree.skillLevelBonus(SkillType.SKILL), "…and 星魂 5 has not landed yet");
    }

    /** 星魂 5 raises the skill and the talent; the talent's raise sits <b>on top of</b> the base level. */
    @Test
    public void starRailFiveRaisesTheSkillAndTheTalent() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 5);
        new Battle(List.of(march), List.of(enemy()), fixed()).startBattle();

        Assertions.assertEquals(2, march.skillLevelBonus(SkillType.SKILL), "「战技等级+2」");
        Assertions.assertEquals(11, march.skillLevelBonus(SkillType.TALENT), "+9 base and +2 from this 星魂");
        Assertions.assertEquals(12, march.skillLevel(march.getSkills().get(SkillType.TALENT)),
                "so her talent is read at level 12 — its table has 15 rows, and this is the number the game means");
    }

    /**
     * Note: The composition lands on a row whose number is <b>different</b>: level 12 is not level 10.
     *
     * <p>Pinned at the data + resolver level on purpose. The behavioural case ("the counter hits harder at 星魂 5") was
     * written first and measured <b>1.13 at both ranks</b> - a fixture that was not reading the counter's row at all
     * (the rows are {@code [1.0, 2.0]} and {@code [1.1, 2.0]}, so a real read cannot be equal). Her own suite pins the
     * counter's Lv10 behaviour at 星魂 0 ({@code MarchthKitTest}), and this case pins the half that suite cannot see.
     */
    @Test
    public void theRaisedRowIsADifferentNumber() {
        Character atZero = CharacterFactory.create(MARCH, LEVEL, true, null, null, 0);
        new Battle(List.of(atZero), List.of(enemy()), fixed()).startBattle();
        Character atFive = CharacterFactory.create(MARCH, LEVEL, true, null, null, 5);
        new Battle(List.of(atFive), List.of(enemy()), fixed()).startBattle();

        var rows = atZero.getSkills().get(SkillType.TALENT).getData().getSkills();
        double baseRow = rows.get(atZero.skillLevel(atZero.getSkills().get(SkillType.TALENT)) - 1).getFirst();
        double raisedRow = rows.get(atFive.skillLevel(atFive.getSkills().get(SkillType.TALENT)) - 1).getFirst();

        Assertions.assertEquals(1.0, baseRow, EPS, "her talent's row 10 (「100%攻击力」)");
        Assertions.assertEquals(1.1, raisedRow, EPS, "…and row 12, which is what 星魂 5's 「天赋等级+2」 buys");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Enemy enemy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        return enemy;
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
