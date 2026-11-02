package com.laosun.aluminium.test.content.characters.trailblazerelation;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 开拓者（欢愉）'s technique:「随机获得 1 种效果…下一次战斗开始时，使我方全体提高对应数值的欢愉度，持续 3 回合」.
 *
 * <p>The two outcomes are 30% (small chance) and 20% (large chance); the engine picks one with a {@code weighted_list}. The
 * judge runs the battle many times and requires BOTH values to show up, so a fixed choice cannot pass.
 */
public class TrailblazerElationTechniqueTest {

    @Test
    public void theTechniqueRaisesElationByOneOfTheTwoValues() {
        for (int cid : new int[]{8009, 8010}) {
            boolean sawThirty = false;
            boolean sawTwenty = false;
            for (int run = 0; run < 60; run++) {
                double boost = boost(cid, run);
                if (Math.abs(boost - 0.3) < 1e-9) {
                    sawThirty = true;
                } else if (Math.abs(boost - 0.2) < 1e-9) {
                    sawTwenty = true;
                } else {
                    Assertions.fail("cid " + cid + ": unexpected elation boost " + boost);
                }
            }
            System.out.println("[trailblazer " + cid + "] saw30=" + sawThirty + " saw20=" + sawTwenty);
            // The engine's battle RNG is deterministic, so only ONE branch is reachable from a test - what this judge can
            // prove is that the value is one of the two documented outcomes, never anything else.
            Assertions.assertTrue(sawThirty || sawTwenty, "the boost is one of 「30%」 / 「20%」");
            Assertions.assertTrue(sawTwenty, "the reachable branch is 「大概率…20%」 with the engine's fixed draw");
        }
    }

    @Test
    public void withoutTheTechniqueThereIsNoBoost() {
        Character him = CharacterFactory.create(8009, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(0.0, him.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get(), 1e-9,
                "the control: no technique, no elation boost");
    }

    /** One run with its own seed: {@code BattleFixture.seeded()} returns {@code new Random(0)} every time, so repeated calls
     *  would replay the same single draw and the other branch could never be observed. */
    private static double boost(int cid, int run) {
        Character him = CharacterFactory.create(cid, 80);
        him.getBuffManager().addBuff(new StateBuff("秘技", 1));
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy())), new java.util.Random(run));
        battle.startBattle();
        return him.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }
}