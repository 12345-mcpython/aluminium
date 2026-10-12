package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): Eidolon 2 raises the extra turn's reward by 100% - 30/60 becomes 60/120.
 *
 * <p>「并使终结技提供的额外回合开始时获得的【好活当赏】和笑点提高 100%」 (rank 150302, Param [0.15, 1]). The grant is a
 * second additive clause gated on the rank, so the base reward stays what every rank pays.
 *
 * <p>What makes this worth its own case is the OTHER side. The removing clause cannot be paired the same way - two
 * removals would both fire at Eidolon 2 and the second would find the counter already drained, because the clamp that
 * makes a removal safe is exactly what makes a second one a no-op - so the shipped removal now asks for the boosted
 * amount (60/120) and relies on that clamp at the lower ranks. Both ranks therefore have to be read, on BOTH sides:
 * what was granted AND what came back.
 */
public class PearlEidolon2RewardTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;
    private static final String COUNTER = "好活当赏";
    private static final String PUNCHLINE = "笑点";

    /**
     * At Eidolon 2 the turn pays double - read off the counter that has NO cap.
     *
     * <p>Note: The 好活当赏 side cannot show "double" at full value, and that is not a bug in either the clause or the
     * cap: her talent states 「持有上限为 50 点」, so 30 + 30 lands on 50. Measured, and asserted as the cap rather than
     * quietly loosened - the doubling is what makes the cap BIND, and saying so is more useful than picking a starting
     * value that hides it. 笑点 is a battle-level pool with no cap (its declaration carries Integer.MAX_VALUE), so it
     * shows the doubling exactly.
     */
    @Test
    public void eidolonTwoDoublesTheReward() {
        Reading e0 = turn(0);
        Reading e2 = turn(2);
        System.out.println("[pearl_e2] E0 granted " + e0.granted + " and " + e0.punchlineGranted
                + " punchline ; E2 granted " + e2.granted + " and " + e2.punchlineGranted);
        Assertions.assertEquals(30, e0.granted, "below Eidolon 2 the base 30 is all there is");
        Assertions.assertEquals(60, e0.punchlineGranted, "and the base 60 of punchline");
        Assertions.assertEquals(50, e2.granted,
                "「提高 100%」would be 60, and her counter's stated cap of 50 is what it lands on");
        Assertions.assertEquals(120, e2.punchlineGranted,
                "the uncapped punchline pool shows the doubling exactly: 60 + 60");
    }

    /**
     * And the boosted amount comes back off at BOTH ranks, which is the half that needed a different treatment.
     *
     * <p>The removal states 60/120 at every rank; below Eidolon 2 that is more than was granted, and the clamp takes
     * only what is there.
     */
    @Test
    public void theBoostedAmountIsTakenBackAtBothRanks() {
        Reading e0 = turn(0);
        Reading e2 = turn(2);
        System.out.println("[pearl_e2] after the turn ended: E0 left " + e0.counterLeft + " of the counter and "
                + e0.punchlineLeft + " punchline ; E2 left " + e2.counterLeft + " / " + e2.punchlineLeft);
        Assertions.assertEquals(0, e0.counterLeft, "E0: the 30 that was granted is gone");
        Assertions.assertEquals(0, e0.punchlineLeft, "E0: and the 60");
        Assertions.assertEquals(0, e2.counterLeft, "E2: the 60 that was granted is gone");
        Assertions.assertEquals(0, e2.punchlineLeft, "E2: and the 120");
    }

    /**
     * Part of it spent before the turn ends: what is left is taken and no more, at both ranks.
     *
     * <p>The author's ruling for this clause (「花掉部分就撤回剩余的」), and the reason one removal serves both ranks: the
     * clamp, not the stated number, decides what comes off.
     */
    @Test
    public void aPartlySpentBoostedRewardIsTakenBackUpToWhatIsLeft() {
        Reading e0 = turnWithSpend(0, 12);
        Reading e2 = turnWithSpend(2, 12);
        System.out.println("[pearl_e2] granted-then-spent-12: E0 left " + e0.counterLeft + " ; E2 left "
                + e2.counterLeft);
        Assertions.assertEquals(0, e0.counterLeft, "E0: 30 granted, 12 spent, the remaining 18 comes off");
        Assertions.assertEquals(0, e2.counterLeft, "E2: 60 granted, 12 spent, the remaining 48 comes off");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * What the turn granted, what it took back, and what is left on each counter.
     */
    private record Reading(int granted, int punchlineGranted, int counterLeft, int punchlineLeft) {
    }

    private static Reading turn(int eidolonRank) {
        return run(eidolonRank, 0);
    }

    private static Reading turnWithSpend(int eidolonRank, int spend) {
        return run(eidolonRank, spend);
    }

    private static Reading run(int eidolonRank, int spendAfterGrant) {
        Battle battle = newBattle(eidolonRank);
        Character pearl = battle.characters.getFirst();
        Character archetype = battle.characters.get(1);

        int punchlineStart = battle.partyResource(PUNCHLINE).value();
        battle.fireTriggers(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0);
        battle.processRequests();
        int granted = pearl.getResources().value(COUNTER);
        int punchlineGranted = battle.partyResource(PUNCHLINE).value() - punchlineStart;
        if (spendAfterGrant > 0) {
            pearl.getResources().spend(COUNTER, spendAfterGrant);
        }

        battle.fireTriggers(TriggerEvent.EXTRA_TURN_ENDED, archetype, archetype, 0, 0);
        battle.processRequests();

        // What is left after the closing clause, measured against where the turn started on each counter.
        int counterLeft = pearl.getResources().value(COUNTER);
        int punchlineLeft = battle.partyResource(PUNCHLINE).value() - punchlineStart;
        return new Reading(granted, punchlineGranted, counterLeft, punchlineLeft);
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ELATION_ALLY, 80), CharacterFactory.create(1204, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.characters.get(1).getBuffManager()
                .addBuff(new com.laosun.aluminium.models.buff.StateBuff("美学底本", 3, false));
        return battle;
    }
}
