package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): Eidolon 2 widens the Ultimate's action advance from the archetype to every Elation ally.
 *
 * <p>「施放终结技时，使除真珠和【美学底本】外的其他我方「欢愉」命途角色也触发行动提前效果」 - and the author's ruling on what
 * 「也」 means is that this moves the TARGET rather than adding a second advance: at Eidolon 0-1 each tier advances the
 * archetype, from Eidolon 2 the same tier advances every ally on the Elation path. The shipped three therefore carry
 * `max_eidolon: 1`, which is why the engine needed a CEILING as well as a floor - both sets firing would advance the
 * archetype twice, and `ADVANCE` is multiplicative (twice 10% is 19%, not 20%).
 *
 * <p>⚠ What the clause says about the ARCHETYPE, measured and worth stating because it is surprising: 「除…【美学底本】外」
 * excludes it. So when the archetype is NOT on the Elation path it stops being advanced at all from Eidolon 2 - it is
 * not an "other Elation ally". The first team below makes that visible; the second, whose archetype is NOT an Elation
 * character, is the one that separates the two ranks for the OTHER allies.
 */
public class PearlEidolon2AdvanceTest {
    private static final int PEARL = 1503;      // Elation
    private static final int ELATION_A = 1502;  // Elation
    private static final int ELATION_B = 1505;  // Elation
    private static final int HUNTER = 1204;     // NOT Elation - the archetype in these teams

    /**
     * From Eidolon 2 the OTHER Elation allies are advanced where they were not before, and the non-Elation archetype
     * no longer is.
     *
     * <p>The archetype is the Hunt ally, i.e. not on the Elation path - which is what makes the two effects separable:
     * the tier's only Elation targets are the two allies beside Pearl, and the archetype is excluded by name.
     */
    @Test
    public void eidolonTwoAdvancesTheOtherElationAlliesInsteadOfTheArchetype() {
        Fraction e0 = fractions(0);
        Fraction e2 = fractions(2);
        System.out.println("[pearl_e2_advance] E0 -> archetype keeps " + e0.archetype
                + ", elation ally A " + e0.elationA + ", elation ally B " + e0.elationB
                + " || E2 -> archetype " + e2.archetype + ", A " + e2.elationA + ", B " + e2.elationB);

        Assertions.assertEquals(1.0, e0.elationA, 1e-6,
                "at E0 the tiers advance the archetype only: an Elation ally beside Pearl is untouched");
        Assertions.assertEquals(1.0, e0.elationB, 1e-6, "and so is the other one");
        Assertions.assertTrue(e0.archetype < 0.9,
                "while the archetype IS advanced at E0 (" + e0.archetype + ")");

        Assertions.assertTrue(e2.elationA < 0.9,
                "at E2 the same tier reaches the Elation allies (" + e2.elationA + ")");
        Assertions.assertTrue(e2.elationB < 0.9, "both of them (" + e2.elationB + ")");
        Assertions.assertEquals(1.0, e2.archetype, 1e-6,
                "and the archetype, which is NOT on the Elation path, is excluded by 「除…【美学底本】外」");
    }

    /**
     * The percentages are the tier's own, and the tiers still compound - all three of them here.
     *
     * <p>Note: Pearl is on the Elation path HERSELF, so this team has three Elation characters (her plus the two
     * allies), which satisfies every tier: each ally is advanced by 10%, then 15%, then 30%, keeping
     * {@code 0.9 x 0.85 x 0.70}. The first version of this case expected only two tiers and was corrected by the
     * measurement - the count includes her.
     */
    @Test
    public void theWidenedAdvanceKeepsTheTiersOwnPercentages() {
        Fraction e2 = fractions(2);
        System.out.println("[pearl_e2_advance] at E2 an Elation ally keeps " + e2.elationA
                + " (expected 0.9 x 0.85 x 0.70 = 0.5355)");
        Assertions.assertEquals(0.9 * 0.85 * 0.70, e2.elationA, 1e-6,
                "the tiers are unchanged: 10%, 15% then 30% on the same ally");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The fraction of its wait each unit KEEPS after the Ultimate.
     */
    private record Fraction(double archetype, double elationA, double elationB) {
    }

    private static Fraction fractions(int eidolonRank) {
        Battle battle = new Battle(team(eidolonRank), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character elationA = battle.characters.get(1);
        Character elationB = battle.characters.get(2);
        Character archetype = battle.characters.get(3);

        double[] before = waits(battle, pearl, elationA, elationB, archetype);
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, archetype, 0, 0);
        battle.processRequests();
        double[] after = waits(battle, pearl, elationA, elationB, archetype);
        return new Fraction(after[3] / before[3], after[1] / before[1], after[2] / before[2]);
    }

    /**
     * Remaining action value for Pearl, the two Elation allies and the archetype, in that order.
     */
    private static double[] waits(Battle battle, Character pearl, Character elationA, Character elationB,
                                  Character archetype) {
        double[] out = new double[4];
        Character[] wanted = {pearl, elationA, elationB, archetype};
        for (Signal signal : battle.queue.snapshot()) {
            for (int i = 0; i < wanted.length; i++) {
                if (signal.getCanHit() == wanted[i]) {
                    out[i] = battle.queue.getTimeRemaining(signal);
                }
            }
        }
        return out;
    }

    /**
     * Pearl, two other Elation allies, and a HUNT ally last - the Ultimate aims at the last member, so the archetype is
     * not on the Elation path and "who was aimed at" stays independent of "who qualifies".
     */
    private static List<Character> team(int eidolonRank) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank));
        team.add(CharacterFactory.create(ELATION_A, 80));
        team.add(CharacterFactory.create(ELATION_B, 80));
        team.add(CharacterFactory.create(HUNTER, 80));
        return team;
    }
}
