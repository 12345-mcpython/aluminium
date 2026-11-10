package com.laosun.aluminium.test.content.characters.blade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1205 刃's Eidolon 1: 「终结技对<b>指定敌方单体</b>造成的伤害额外提高，提高数值等同于刃 <b>150%</b> 本场战斗中累计的已损失生命值。」
 *
 * <p><b>Why a criterion.</b> Two facts decide the number, and each has a wrong reading that looks right:
 * <ol>
 *   <li><b>which victims</b> - the Ultimate is a Blast (skills.json 120503), so 「指定敌方单体」 is the centre and the
 *       neighbours are the other sentence. A rule that boosted every instance would raise the neighbours too;</li>
 *   <li><b>where the raise enters</b> - it is an absolute amount on the <b>settled</b> instance, not a percentage and not
 *       another crit-rolling hit. The measure below is therefore the exact delta: 1.5 x the tally, no zone factor.</li>
 * </ol>
 * The control is the same cast at E0 with the same tally.
 */
public class BladeEidolonOneTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;
    private static final String TALLY = "累计损失生命";

    @Test
    public void theDesignatedTargetTakesExactlyOneAndAHalfTimesTheTally() {
        int tally = 1000;
        Reading without = cast(0, tally);
        Reading with = cast(1, tally);
        System.out.println("[1205-E1] tally=" + tally
                + " centre " + without.centre + " -> " + with.centre
                + " ; neighbour " + without.neighbour + " -> " + with.neighbour
                + " ; base without tally " + cast(1, 0).centre);

        Assertions.assertEquals(1.5 * tally, with.centre - without.centre, 1e-6,
                "「提高数值等同于刃 150% 本场战斗中累计的已损失生命值」 - the raise is the settled amount's, not a factor");
        Assertions.assertEquals(0.0, with.neighbour - without.neighbour, 1e-9,
                "「指定敌方单体」 - the neighbours are the other sentence");
        Assertions.assertNotEquals(0.0, with.centre - without.centre,
                "if the deltas coincided, the criterion could not tell the two batches apart");
    }

    /**
     * Two-sided: with no accumulated loss the Eidolon pays nothing, and its own share is the <b>extra</b> 150% - the
     * Ultimate's row already carries 100% of the tally as one of its three base terms, which is the other sentence.
     */
    @Test
    public void noTallyMeansNoRaiseAndTheShareIsTheExtraHalf() {
        Assertions.assertEquals(0.0, cast(1, 0).centre - cast(0, 0).centre, 1e-9,
                "「累计已损失生命值」 = 0 gives a raise of 0");
        // Note: E0 with a tally is NOT the same as E0 without one: skills.json 120503's third base term is #5 = 100% of
        // the tally, so the row already reads it. What this Eidolon adds is the 150%, which is exactly the delta measured.
        Assertions.assertEquals(1.5 * 800, cast(1, 800).centre - cast(0, 800).centre, 1e-6,
                "the row's 100% and the Eidolon's 150% are two different terms of the same cast");
    }

    // ==================================================================

    private record Reading(double centre, double neighbour) {
    }

    /**
     * One cast of the Ultimate onto the middle of three dummies, returning what the centre and one neighbour lost.
     */
    private static Reading cast(int eidolon, int tally) {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(eidolon);
        Enemy left = BattleFixture.dummy();
        Enemy centre = BattleFixture.dummy();
        Enemy right = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(blade)),
                new ArrayList<>(List.of(left, centre, right)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (tally > 0) {
            blade.getResources().gain(TALLY, tally);
        }
        battle.processRequests();

        var ultimate = blade.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ultimate, "precondition: Blade has an Ultimate");
        double centreBefore = centre.getCurrentHp();
        double neighbourBefore = left.getCurrentHp();
        battle.castImmediate(ultimate, blade, List.of(centre));
        battle.processRequests();
        return new Reading(centreBefore - centre.getCurrentHp(), neighbourBefore - left.getCurrentHp());
    }
}
