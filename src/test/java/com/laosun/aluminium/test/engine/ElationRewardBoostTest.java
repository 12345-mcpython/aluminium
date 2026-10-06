package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "触发行迹…的获得好活当赏效果时，额外获得等同于本次的 50%" - 1505 Eidolon (星魂).
 *
 * <p>FILE-DRIVEN and TWO-WAY on the EIDOLON RANK itself: at rank 1 the trace's capped credit gains its 50% on top; at
 * rank 0 the same gain credits exactly the capped amount, which is what the older guards already assert.
 */
public class ElationRewardBoostTest {
    private static final int OWNER = 1505;
    private static final int MONSTER = 1002011;
    private static final String REWARD = "好活当赏";

    /** With the Eidolon: 150 energy -> 20 + 100 (capped) + 50. */
    @Test
    public void theEidolonAddsHalfOfTheCappedCredit() {
        Assertions.assertEquals(170, rewardAfterGain(1, 150),
                "rank 1: 20 (technique) + 100 (capped) + 50 (the eidolon's half)");
    }

    /** Note: Without it: the base trace only, exactly as the older guards expect. */
    @Test
    public void theBaseIsUntouchedWithoutTheEidolon() {
        Assertions.assertEquals(120, rewardAfterGain(0, 150),
                "rank 0: 20 (technique) + 100 (capped), no eidolon share");
    }

    /** Note: The technique is not a trace, so its own 20 must not be boosted at any rank. */
    @Test
    public void theTechniqueGainIsNotBoosted() {
        Assertions.assertEquals(20, rewardAfterGain(1, 0),
                "the technique's flat 20 stays 20 even with the Eidolon");
    }

    // ==================================================================

    private static int rewardAfterGain(int eidolonRank, int energy) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, eidolonRank);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (energy > 0) {
            owner.setCurrentEnergy(0);
            battle.applyEnergyGain(owner, new EnergyGain(energy, false));
            battle.processRequests();
        }
        return owner.getResources().has(REWARD) ? owner.getResources().value(REWARD) : 0;
    }
}
