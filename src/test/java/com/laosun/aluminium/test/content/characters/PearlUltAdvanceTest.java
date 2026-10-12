package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): the three advance tiers her Ultimate lands on 【美学底本】, the extra turn at four Elation
 * allies, and the Technique's opening counter.
 *
 * <p>These are the rules her file shipped without a judge. The project's own history says that is exactly where a
 * dead condition hides - "enhanced_basic_heals_on_slot_8/9" were gated on a skill ID that the event never carries, and
 * nothing but a judge that FIRES the rule could see it.
 *
 * <p>The three tiers are nested thresholds (>= 1 / >= 2 / >= 3 Elation-path allies), so a team that qualifies for a
 * higher tier fires the lower ones too: the advance COMPOUNDS, and the expected value is
 * {@code (1-p1)(1-p2)(1-p3)} of the untouched wait. That is asserted here rather than "more allies means further
 * ahead", because the composition rule is the thing a wrong implementation gets wrong.
 */
public class PearlUltAdvanceTest {
    private static final int PEARL = 1503;      // Elation path herself
    private static final int ELATION_A = 1502;  // Yao Guang (爻光), Elation
    private static final int ELATION_B = 1505;  // Evanescia (绯英), Elation
    private static final int ELATION_C = 1506;  // Silver Wolf LV.999 (银狼LV.999), Elation
    private static final int HUNTER = 1204;     // The Hunt (巡猎): NOT Elation
    private static final double EPS = 1e-9;

    /**
     * Each tier moves the archetype by its own percentage, and the tiers compound because the thresholds nest.
     *
     * <p>Measured against the SAME character with her rules removed, so "how much did the pull do" is a difference
     * rather than a guess about the action bar's own arithmetic.
     */
    @Test
    public void theThreeTiersCompoundOnTheSameTarget() {
        double one = advanceFraction(1);
        double two = advanceFraction(2);
        double three = advanceFraction(3);
        System.out.println("[pearl_advance] the target kept this fraction of its wait: 1 Elation ally -> " + one
                + " (0.90 expected) | 2 -> " + two + " (0.90x0.85 = 0.765) | 3 -> " + three
                + " (0.90x0.85x0.70 = 0.5355)");

        Assertions.assertEquals(0.90, one, 1e-6, "one Elation ally (Pearl herself) advances the archetype 10%");
        Assertions.assertEquals(0.90 * 0.85, two, 1e-6,
                "two Elation allies qualify for BOTH tiers, and the file runs both: 10% then 15%");
        Assertions.assertEquals(0.90 * 0.85 * 0.70, three, 1e-6,
                "three qualify for all three, so all three run: 10%, 15% then 30%");
    }

    /**
     * Four Elation allies hand the archetype an EXTRA TURN - and three do not.
     *
     * <p>The control is the point: {@code EXTRA_TURN} and {@code ADVANCE} are different mechanics, and a fixture that
     * only checked the positive side would pass for an implementation that granted the extra turn at every tier.
     */
    @Test
    public void theExtraTurnArrivesAtFourElationAlliesAndNotAtThree() {
        Character atFour = extraTurnActorWith(4);
        Character atThree = extraTurnActorWith(3);
        System.out.println("[pearl_advance] the extra turn went to "
                + (atFour == null ? "nobody at four" : atFour.getCid()) + " at four Elation allies, and to "
                + (atThree == null ? "nobody" : atThree.getCid()) + " at three");

        Assertions.assertNotNull(atFour, "four Elation allies owe the archetype an extra turn");
        Assertions.assertEquals(HUNTER, atFour.getCid(),
                "and that extra turn belongs to 【美学底本】 - the ally the Ultimate aimed at, not to Pearl");
        Assertions.assertNull(atThree, "three Elation allies are one short of the extra turn");
    }

    /**
     * The Technique's opening counter: 20 with the marker, none without it.
     */
    @Test
    public void theTechniqueGrantsTheOpeningCounter() {
        double without = counterAfterOpening(false);
        double with = counterAfterOpening(true);
        System.out.println("[pearl_advance] the opening counter: without the technique " + without
                + " ; with it " + with);

        Assertions.assertEquals(0, without, EPS,
                "no technique was used, so the rule's own gate (the 秘技 marker) is not satisfied");
        Assertions.assertEquals(20, with, EPS,
                "「使用秘技后…下一次战斗开始时，真珠获得 20 点【好活当赏】」");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The fraction of its wait the archetype KEEPS after Pearl's Ultimate, for a team with that many Elation-path
     * characters (Pearl counts). Measured against the same team with Pearl's rules removed.
     */
    private static double advanceFraction(int elationAllies) {
        double untouched = timeRemaining(team(elationAllies), false);
        double advanced = timeRemaining(team(elationAllies), true);
        Assertions.assertTrue(untouched > 0, "precondition: the archetype had a wait to shorten");
        return advanced / untouched;
    }

    /**
     * Who was handed the extra turn, or {@code null} when the Ultimate granted none.
     */
    private static Character extraTurnActorWith(int elationAllies) {
        Battle battle = new Battle(team(elationAllies), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character archetype = battle.characters.get(battle.characters.size() - 1);
        battle.fireTriggers(TriggerEvent.ULT_CAST, battle.characters.get(0), archetype, 0, 0);
        battle.processRequests();
        CanHit actor = battle.getExtraTurnActor();
        return actor instanceof Character character ? character : null;
    }

    /**
     * The counter at the opening of a battle, with or without the Technique marker.
     */
    private static double counterAfterOpening(boolean techniqueUsed) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (techniqueUsed) {
            battle.markTechniqueUsed(pearl);
        }
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst().getResources().value("好活当赏");
    }

    /**
     * A team of exactly {@code elationAllies} Elation-path characters plus Pearl (who is one herself), with the Hunt
     * ally LAST: the Ultimate aims at the last member, so the archetype is a character who is NOT on the Elation path,
     * which keeps "who was aimed at" and "who qualifies" independent.
     */
    private static List<Character> team(int elationAllies) {
        List<Integer> elationIds = List.of(PEARL, ELATION_A, ELATION_B, ELATION_C);
        List<Character> team = new ArrayList<>();
        for (int i = 0; i < elationAllies; i++) {
            team.add(CharacterFactory.create(elationIds.get(i), 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        return team;
    }

    /**
     * How much action value the aiming target still has, fired through Pearl's own Ultimate or with no rules.
     */
    private static double timeRemaining(List<Character> team, boolean fireTheUltimate) {
        List<Character> fresh = new ArrayList<>();
        for (Character c : team) {
            fresh.add(CharacterFactory.create(c.getCid(), 80));
        }
        if (!fireTheUltimate) {
            fresh.getFirst().setTriggerTable(TriggerTable.EMPTY);
        }
        Battle battle = new Battle(fresh, List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character archetype = battle.characters.get(battle.characters.size() - 1);
        if (fireTheUltimate) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, battle.characters.get(0), archetype, 0, 0);
            battle.processRequests();
        }
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == archetype) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }
}
