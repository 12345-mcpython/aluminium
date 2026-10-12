package com.laosun.aluminium.test.content.characters.c1506;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999): 「若触发前目标被消灭则对新入场的敌方目标触发」 - the box waits for the wave.
 *
 * <p>The author's reading of the sentence, and what the judge pins: the field being empty does not spend the box, it
 * <b>holds</b> it, and a wave arriving delivers it. And the firing that was held is the firing that already happened -
 * its conditions and its <b>roll</b> are spent, and it already decayed the chance - while the effects, including which of
 * the three runs, are decided at delivery.
 *
 * <p>⚠ The observable is the PICK, not the damage: with 【爆爆爆炸蛋】 written as the scripted pick, a delivered box shows
 * up as +2 skill points, and "held" therefore reads as "the counter did not move" - a reading that a dead enemy could not
 * have given (its HP cannot move either way).
 *
 * <p>Note: {@code WAVE_START} is fired by hand here, the way most of the cone judges do; that entering a wave for real
 * fires it is pinned separately by {@code SheAlreadyShutHerEyesTest.aRealWaveBoundaryFiresTheHeal}.
 */
public class SilverWolfLV999LootBoxWaveTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    /**
     * 【爆爆爆炸蛋】's WEIGHT bucket: 2 skill points, and no damage of its own.
     *
     * <p>⚠ The pick is WEIGHTED (the game's grades: 1 / 1 / 10 while 隐藏分 is under 60), so the roll is over the weight
     * total and this is the bucket's lower edge, not a pool index.
     */
    private static final int PICK_POINTS = 1000;

    /**
     * The box triggered with nobody on the field is held, and the wave delivers it to the enemy that just entered.
     */
    @Test
    public void aBoxWithNobodyToHitWaitsForTheWave() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);
        Character wolf = battle.characters.getFirst();
        for (Enemy enemy : battle.enemyUnits()) {
            enemy.perish();                       // 「触发前目标被消灭」: the field is empty when the point is spent
        }
        int pointsBefore = battle.getSkillPoints();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
        battle.processRequests();
        Assertions.assertEquals(pointsBefore, battle.getSkillPoints(),
                "with nobody to hit, the box is HELD - a delivered one would have granted 2 skill points here");

        Enemy fresh = BattleFixture.monster();      // the next wave walks in
        battle.enemies.add(fresh);
        double freshBefore = fresh.getCurrentHp();
        battle.fireTriggers(TriggerEvent.WAVE_START);
        battle.processRequests();
        System.out.println("[1506] after the wave: points +" + (battle.getSkillPoints() - pointsBefore)
                + ", the new enemy lost " + (freshBefore - fresh.getCurrentHp()));
        Assertions.assertEquals(pointsBefore + 2, battle.getSkillPoints(),
                "and the wave delivers it: the held pick runs now");
        Assertions.assertTrue(fresh.getCurrentHp() < freshBefore,
                "the box's own 90% lands on the enemy that just entered - that is the half the sentence is about");
    }

    /**
     * The control: with an enemy still standing, the very same firing is delivered at once.
     */
    @Test
    public void aBoxWithSomeoneToHitIsNotHeld() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);
        Character wolf = battle.characters.getFirst();
        int pointsBefore = battle.getSkillPoints();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
        battle.processRequests();
        Assertions.assertEquals(pointsBefore + 2, battle.getSkillPoints(),
                "an enemy was standing, so nothing is held - the pick lands in the same firing");
    }

    /**
     * The held firing already rolled, and already decayed: delivery does not roll again.
     *
     * <p>Steps: ① with nobody to hit and the initial chance of 100%, the box triggers and is held; ② the wave delivers it
     * with a 0.9 draw in place - if delivery re-rolled, 0.9 would fail the 20% the firing left behind, and the pick would
     * not land; ③ the NEXT firing, now with an enemy standing, draws 0.9 again and must deliver nothing, because the held
     * one already decayed the chance to 20%.
     */
    @Test
    public void theHeldFiringAlreadyRolledAndDecayed() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);
        Character wolf = battle.characters.getFirst();
        for (Enemy enemy : battle.enemyUnits()) {
            enemy.perish();
        }
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
        battle.processRequests();
        int pointsBefore = battle.getSkillPoints();

        Enemy fresh = BattleFixture.monster();
        battle.enemies.add(fresh);
        rng.nextDoubleValue = 0.9;                 // would FAIL a 20% roll
        battle.fireTriggers(TriggerEvent.WAVE_START);
        battle.processRequests();
        Assertions.assertEquals(pointsBefore + 2, battle.getSkillPoints(),
                "delivery does NOT re-roll: the held firing's own success is what buys these effects");

        rng.nextDoubleValue = 0.9;
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
        battle.processRequests();
        Assertions.assertEquals(pointsBefore + 2, battle.getSkillPoints(),
                "and the held firing already decayed the chance to 20%: this 0.9 draw fires nothing");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The wolf with her zone and 【无敌玩家】 up, and one enemy, so the box is armed.
     */
    private static Battle battle(ScriptedRng rng) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster(), BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters.getFirst();
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return battle;
    }

    /**
     * {@code nextInt} answers with the scripted pick; {@code nextDouble} is whatever the case set.
     */
    private static final class ScriptedRng extends Random {
        private final int pick;
        private double nextDoubleValue = 0.9999;

        private ScriptedRng(int pick) {
            this.pick = pick;
        }

        @Override
        public int nextInt(int bound) {
            // ⚠ Clamped: other draws share this source (a bounce picks its target with `nextInt(alive.size())`), and a
            // weight bucket there would index out of bounds.
            return Math.min(pick, bound - 1);
        }

        @Override
        public double nextDouble() {
            return nextDoubleValue;
        }
    }
}
