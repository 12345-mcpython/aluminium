package com.laosun.aluminium.test.content.characters.mar7th;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * March 7th's Eidolon 3 / Eidolon 5 and the level her own document is quoted at - content, on top of the engine's
 * {@code RAISE_SKILL_LEVEL}.
 *
 * <p><b>What the documents say.</b> Eidolon 3 = "<b>终结技</b>等级+2，最多不超过15级；<b>普攻</b>等级+1，最多不超过10级" (data row
 * {@code {'100103': 2, '100101': 1}}), Eidolon 5 = "<b>战技</b>等级+2，最多不超过15级；<b>天赋</b>等级+2，最多不超过15级" (row
 * {@code {'100102': 2, '100104': 2}}). Every one of her four slots is quoted at Lv10 in the prose (her shield's #1 and #4,
 * her ultimate's #1 and #4, her talent's #1), which the file states once as a `level_convention` rule (+9), so the
 * Eidolon raises compose with it instead of being pinned per effect.
 *
 * <p>Note: The last case is why that composition matters: at Eidolon 5 her counter reads a row that no fixed
 * {@code damage_level} could ever have expressed.
 */
public class March7thLevelTest {
    private static final double EPS = 1e-6;

    private static final int MARCH = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * The document's level for her talent, stated by the file instead of by a per-effect pin.
     */
    @Test
    public void herTalentIsReadAtTheDocumentsLevel() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 0);
        new Battle(List.of(march), List.of(enemy()), fixed()).startBattle();

        Assertions.assertEquals(9, march.skillLevelBonus(SkillType.TALENT),
                "the base rule states 「the document quotes Lv10」 as +9 (level 1 -> 10)");
        Assertions.assertEquals(10, march.skillLevel(march.getSkills().get(SkillType.TALENT)),
                "…so every effect that reads her talent indexes row 10");
    }

    /**
     * Eidolon 3 raises the ultimate and the basic attack - and nothing else.
     *
     * <p>Measured as a <b>delta against 星魂 0</b>, not as an absolute bonus: the file's own `level_convention` rule
     * already puts all four slots at +9 (「the document quotes Lv10」), so an absolute assertion would be reading the
     * convention's number and calling it the Eidolon's. The question this case asks is what the Eidolon <b>adds</b>.
     */
    @Test
    public void starRailThreeRaisesTheUltimateAndTheBasicAttack() {
        Character atZero = startedAt(0);
        Character atTwo = startedAt(2);
        Assertions.assertEquals(0, bonus(atTwo, SkillType.ULTRA) - bonus(atZero, SkillType.ULTRA),
                "Eidolon (星魂) 2 is not Eidolon 3");

        Character atThree = startedAt(3);
        Assertions.assertEquals(2, bonus(atThree, SkillType.ULTRA) - bonus(atZero, SkillType.ULTRA),
                "\"Ultimate Lv. +2\" (「终结技等级+2」)");
        Assertions.assertEquals(1, bonus(atThree, SkillType.COMMON) - bonus(atZero, SkillType.COMMON),
                "\"Basic ATK Lv. +1\" (「普攻等级+1」)");
        Assertions.assertEquals(0, bonus(atThree, SkillType.SKILL) - bonus(atZero, SkillType.SKILL),
                "…and Eidolon (星魂) 5 has not landed yet");
    }

    /**
     * Eidolon 5 raises the skill and the talent; the talent's raise sits <b>on top of</b> the base level.
     */
    @Test
    public void starRailFiveRaisesTheSkillAndTheTalent() {
        Character atZero = startedAt(0);
        Character atFive = startedAt(5);

        Assertions.assertEquals(2, bonus(atFive, SkillType.SKILL) - bonus(atZero, SkillType.SKILL),
                "\"Skill Lv. +2\" (「战技等级+2」)");
        Assertions.assertEquals(9, bonus(atZero, SkillType.TALENT),
                "the file's own `level_convention` states 「the document quotes Lv10」 as +9");
        Assertions.assertEquals(11, bonus(atFive, SkillType.TALENT), "+9 base and +2 from this Eidolon (星魂)");
        Assertions.assertEquals(12, atFive.skillLevel(atFive.getSkills().get(SkillType.TALENT)),
                "so her talent is read at level 12 — its table has 15 rows, and this is the number the game means");
    }

    /**
     * Note: The composition lands on a row whose number is <b>different</b>: level 12 is not level 10.
     *
     * <p>Pinned at the data + resolver level on purpose. The behavioural case ("the counter hits harder at 星魂 5") was
     * written first and measured <b>1.13 at both ranks</b> - a fixture that was not reading the counter's row at all
     * (the rows are {@code [1.0, 2.0]} and {@code [1.1, 2.0]}, so a real read cannot be equal). Her own suite pins the
     * counter's Lv10 behaviour at Eidolon 0 ({@code MarchthKitTest}), and this case pins the half that suite cannot see.
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
        Assertions.assertEquals(1.1, raisedRow, EPS, "…and row 12, which is what Eidolon (星魂) 5's \"Talent Lv. +2\" (「天赋等级+2」) buys");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Her, at the given 星魂 count, with the battle started so the file's own rules have run.
     */
    private static Character startedAt(int eidolon) {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, eidolon);
        new Battle(List.of(march), List.of(enemy()), fixed()).startBattle();
        return march;
    }

    /**
     * How far above its base level that slot reads, i.e. the file's convention plus whatever the 星魂 adds.
     */
    private static int bonus(Character who, SkillType slot) {
        return who.skillLevelBonus(slot);
    }

    private static Enemy enemy() {
        Enemy enemy = BattleFixture.monster();
        return enemy;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
