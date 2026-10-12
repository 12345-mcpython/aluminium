package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999): the 【头号补给盲盒】's three-way pick is <b>weighted</b> - 「【爆爆爆炸蛋】：恢复战技点（战技点
 * 较少时，触发概率提高，较多时降低）；【怪怪怪味豆】：获得笑点（【隐藏分】较少时，触发概率提高…）」.
 *
 * <p>The numbers are the game's own, decoded out of its ability script's stack VM
 * ({@code SilverWolf999_00_RollItemIndex}): the script rolls {@code RandomConfig} over
 * {@code [Item1Chance, Item2Chance, Item3Chance]}, where item 1 is never graded (1), item 2 is multiplied by 10 / 0.1 /
 * 0.001 as 战技点 sits at 4 / at its cap of 5, and item 3 by 10 / 0.1 / 0.001 as 隐藏分 sits below 60 / at 120 / at 300.
 *
 * <p>⚠ The judge reads the ROLL ITSELF: the scripted source records the bound it was asked for, and the bound IS the
 * weight total - 12000 at the start of a battle, 11100 with 4 skill points, 1101 with the cap and a full hidden score.
 * That is what makes the weights visible without measuring a distribution, and the bucket a value falls in then names the
 * effect, which the resources and the enemies' HP confirm.
 */
public class SilverWolfLV999LootBoxWeightsTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String LAUGH = "笑点";
    private static final String HIDDEN = "隐藏分";

    /**
     * 1 : 1 : 10 at the start of a battle - the hidden score is under 60, so 获得笑点 is ten times as likely.
     */
    @Test
    public void theWeightsAreTheGamesOwnGrades() {
        Assertions.assertEquals(12000, boundOf(3, 0), "a battle opens with 3 skill points: 1 + 1 + 10, in thousandths");
        Assertions.assertEquals(12000, boundOf(0, 0), "and the grade only moves at 4");
        Assertions.assertEquals(11100, boundOf(4, 0), "4 skill points: 爆爆爆炸蛋 drops to 0.1");
        Assertions.assertEquals(11001, boundOf(5, 0), "and at the cap of 5 its 0.001 step lands on 0.1 - 0.099");
        Assertions.assertEquals(3000, boundOf(0, 60), "a hidden score of 60 drops 怪怪怪味豆 to 1");
        Assertions.assertEquals(2100, boundOf(0, 120), "120 drops it to 0.1");
        Assertions.assertEquals(2001, boundOf(0, 300), "and 300 (60 + its 240 overflow) to 0.001");
        Assertions.assertEquals(1101, boundOf(5, 130), "the two grades are independent: 1 + 0.001 + 0.1");
        Assertions.assertEquals(1200, boundOf(4, 130), "both dropped one tier: 1 + 0.1 + 0.1");
    }

    /**
     * Which effect a given roll lands on, so the buckets are pinned and not just their total.
     *
     * <p>⚠ Every state here keeps the skill points BELOW their cap: at the cap, 「恢复 2 个战技点」 is a no-op and the
     * branch would be unobservable (measured - that case is why the mapping uses 3 and 4).
     */
    @Test
    public void eachBucketLandsOnItsOwnEffect() {
        Assertions.assertEquals(0, whichEffect(3, 0, 0), "the first thousandth is 超超超大剑");
        Assertions.assertEquals(1, whichEffect(3, 0, 1000), "the second is 爆爆爆炸蛋");
        Assertions.assertEquals(2, whichEffect(3, 0, 2000), "and everything above 2000 is 怪怪怪味豆");
        Assertions.assertEquals(2, whichEffect(3, 0, 11999), "right up to the total");
        Assertions.assertEquals(1, whichEffect(4, 0, 1000), "with 4 skill points 爆爆爆炸蛋 is only 100 wide");
        Assertions.assertEquals(2, whichEffect(4, 0, 1100), "so 怪怪怪味豆 starts earlier");
        Assertions.assertEquals(0, whichEffect(4, 130, 0), "1 + 0.1 + 0.1 with a hidden score of 130: the sword is first");
        Assertions.assertEquals(1, whichEffect(4, 130, 1000), "then 爆爆爆炸蛋, 100 wide");
        Assertions.assertEquals(2, whichEffect(4, 130, 1100), "and 怪怪怪味豆, also 100 wide");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The weight total the box's roll asks for, at the given skill points and hidden score.
     */
    private static int boundOf(int skillPoints, int hiddenScore) {
        Fixture f = fixture(skillPoints, hiddenScore, 0);
        return f.roll();
    }

    /**
     * Which of the three effects a roll at {@code roll} lands on.
     */
    private static int whichEffect(int skillPoints, int hiddenScore, int roll) {
        Fixture f = fixture(skillPoints, hiddenScore, roll);
        f.roll();
        return f.landedOn();
    }

    private record Fixture(Battle battle, Character wolf, ScriptedRng rng, int pointsBefore, double[] enemyHp) {
        int roll() {
            rng.lastBound = -1;
            battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
            battle.processRequests();
            Assertions.assertTrue(rng.lastBound > 0, "the pick must roll against the weight total");
            return rng.lastBound;
        }

        /**
         * 0 = the sword, 1 = the points, 2 = the laughs.
         */
        int landedOn() {
            if (battle.getSkillPoints() > pointsBefore) {
                return 1;
            }
            if (battle.partyResourceValue(LAUGH) > 0) {
                return 2;
            }
            // The rider lands on ONE enemy, so it shows up as an UNEVEN loss: the box's own damage is split evenly, so
            // the other two branches leave the two losses identical. (Measured: the rider is only ~29 beside the box's
            // ~73 per enemy - an absolute threshold read "nothing landed".)
            List<com.laosun.aluminium.models.enemy.Enemy> enemies = battle.enemyUnits();
            double first = enemyHp[0] - enemies.get(0).getCurrentHp();
            double second = enemyHp[1] - enemies.get(1).getCurrentHp();
            Assertions.assertTrue(Math.abs(first - second) > 1e-6,
                    "one of the three must have landed: no points, no laughs, no uneven damage");
            return 0;
        }
    }

    private static Fixture fixture(int skillPoints, int hiddenScore, int roll) {
        ScriptedRng rng = new ScriptedRng(roll);
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster(), BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        // ⚠ The skill points are brought to the requested value BEFORE the box is armed: a battle opens with 3 of them, so
        // a lower value means SPENDING - and spending fires SKILL_POINT_SPENT. Nothing triggers here because the zone and
        // 【无敌玩家】 are not up yet; doing it after the ultimate would fire the box during the setup.
        setSkillPoints(battle, skillPoints);
        Character wolf = battle.characters.getFirst();
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        if (hiddenScore > 0) {
            wolf.getResources().gain(HIDDEN, hiddenScore);
        }
        double[] hp = battle.enemyUnits().stream().mapToDouble(e -> e.getCurrentHp()).toArray();
        int points = battle.getSkillPoints();
        return new Fixture(battle, wolf, rng, points, hp);
    }

    /**
     * Brings the battle's skill points to {@code wanted}, by gaining or spending one at a time.
     */
    private static void setSkillPoints(Battle battle, int wanted) {
        while (battle.getSkillPoints() > wanted) {
            battle.skillPointPolicy.spend();
        }
        if (battle.getSkillPoints() < wanted) {
            battle.skillPointPolicy.gain(wanted - battle.getSkillPoints());
        }
        Assertions.assertEquals(wanted, battle.getSkillPoints(), "the fixture must start where the case says");
    }

    /**
     * {@code nextInt} answers with the scripted roll (clamped, because other draws share this source) and records it.
     */
    private static final class ScriptedRng extends Random {
        private final int roll;
        private int lastBound = -1;

        private ScriptedRng(int roll) {
            this.roll = roll;
        }

        @Override
        public int nextInt(int bound) {
            lastBound = bound;
            return Math.min(roll, bound - 1);
        }

        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }
}
