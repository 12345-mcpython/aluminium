package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.battle.BattleDriver;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A technique declared to the driver reaches a technique rule, end to end.
 *
 * <p><b>What was missing before this.</b> The engine could apply a {@code 秘技} state and the rules could
 * read it, but nothing ever declared that a character had used one: {@code Main} and {@code BattleDriver}
 * both built battles without it and never called {@code markTechniqueUsed}, so every technique rule in the
 * corpus was dead -- 53 rules across 37 characters, and not one test exercised the path. Declaring the
 * release to {@link BattleDriver#of} is what closes the loop.
 *
 * <h2>Why 花火 (Sparkle, 1306) is the subject</h2>
 * Her technique is 「进入战斗时为我方恢复3个战技点」, written as {@code GAIN_SKILL_POINT amount 3}. That gives
 * a number this test can name: skill points start at {@link Constant#SKILL_POINT_START} and are capped at
 * {@link Constant#SKILL_POINT_MAX}, so a declaration that works moves them to the cap and one that does not
 * leaves them at the start. Most technique rules in the corpus deal area damage, whose observable result
 * needs the whole damage model to interpret -- a bad foundation for a test about wiring.
 *
 * <p>The cases are contrasts for the reason the technique gate forces: a rule gated on
 * {@code self has_state 秘技} fires only when the state was applied, so "the points went up" alone would pass
 * for the wrong reason if some other part of the battle also granted them.
 */
public class BattleDriverTechniqueTest {

    private static final int SPARKLE = 1306;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * Declaring the release pays the technique, and declaring nothing does not.
     */
    @Test
    void aDeclaredTechniquePaysItsEffectAndAnUndeclaredOneDoesNot() {
        int without = skillPointsAfterSetup(null);
        int with = skillPointsAfterSetup(Battle.Techniques.released(SPARKLE));

        Assertions.assertEquals(Constant.SKILL_POINT_START, without,
                "with no technique declared the battle must open at the ordinary starting points");
        Assertions.assertEquals(Constant.SKILL_POINT_MAX, with,
                "花火's technique recovers 3 points, which from the starting 3 reaches the cap of 5");
        Assertions.assertTrue(with > without,
                "declaring the technique must make a difference, or the wiring does nothing");
    }

    /**
     * The declaration survives the whole drive, not just the opening.
     *
     * <p>Asserted after {@link BattleDriver#run()} rather than after {@code startBattle}, because the point
     * of the wiring is that a driven battle is the thing being configured. The skill points cannot simply be
     * read at the end -- a battle spends them -- so the case counts what the opening paid by comparing the
     * same battle driven with and without the declaration, at the first turn, which is where the technique's
     * effect is still the only difference.
     */
    @Test
    void theDeclarationIsVisibleInADrivenBattle() {
        int with = openingPointsOfDrivenBattle(Battle.Techniques.released(SPARKLE));
        int without = openingPointsOfDrivenBattle(null);

        Assertions.assertEquals(Constant.SKILL_POINT_MAX, with,
                "a driven battle must see the declared technique's points at its first turn");
        Assertions.assertEquals(Constant.SKILL_POINT_START, without);
    }

    /**
     * A declaration naming a character who is not in the party is ignored rather than refused.
     *
     * <p>The flags describe a team the caller assembled, and a battle may be built from a subset of it -- a
     * wave, or a fixture. Throwing here would make an ordinary situation a crash.
     */
    @Test
    void aDeclarationForAnAbsentCharacterIsIgnored() {
        int points = skillPointsAfterSetup(Battle.Techniques.released(999999));

        Assertions.assertEquals(Constant.SKILL_POINT_START, points,
                "a cid that matches nobody in the party must not grant anything");
    }

    /**
     * The battle opens with the declared technique applied.
     *
     * @param techniques the declaration, or {@code null} for none
     * @return the skill points after the battle has started
     */
    private static int skillPointsAfterSetup(Battle.Techniques techniques) {
        Battle battle = newBattle(techniques);
        battle.startBattle();
        return battle.getSkillPoints();
    }

    /**
     * The skill points at the first turn of a battle the driver plays.
     *
     * @param techniques the declaration, or {@code null} for none
     * @return the skill points observed just before the first action resolves
     */
    private static int openingPointsOfDrivenBattle(Battle.Techniques techniques) {
        Battle battle = newBattle(techniques);
        BattleDriver driver = new BattleDriver(battle, new com.laosun.aluminium.models.battle.AutoPolicy());
        int[] seen = {-1};
        driver.withListener(new com.laosun.aluminium.models.battle.BattleListener() {
            @Override
            public void onTurnStart(com.laosun.aluminium.models.CanHit actor, int turn) {
                if (turn == 1) {
                    seen[0] = battle.getSkillPoints();
                }
            }
        });
        driver.run();
        Assertions.assertTrue(seen[0] >= 0, "the first turn must have been reached");
        return seen[0];
    }

    /**
     * One character and one enemy, built with the given technique declaration.
     *
     * @param techniques the declaration, or {@code null} for none
     * @return the battle, not started
     */
    private static Battle newBattle(Battle.Techniques techniques) {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL, false, null, null, 0);
        return new Battle(List.of(sparkle), List.of(EnemyFactory.create(MONSTER, LEVEL, 1)),
                new java.util.Random(20260919L), techniques);
    }
}
