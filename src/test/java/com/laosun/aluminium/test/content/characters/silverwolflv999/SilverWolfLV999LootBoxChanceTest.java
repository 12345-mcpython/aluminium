package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999): the 【头号补给盲盒】 has a fixed chance that <b>decays</b>, and 【崩坏级伤害演示】
 * resets it.
 *
 * <p>「触发该效果的初始<u>固定概率</u>为 100%，成功触发后下一次触发的<u>固定概率</u>降低至当前概率的 <b>20%</b>。」
 * (150603) and 「造成 6 次伤害…将触发【头号补给盲盒】的固定概率重置为初始值。」(150621).
 *
 * <p>⚠ <b>The fixture reads the chance through the roll, not through a getter.</b> The engine keeps the decayed number on
 * the combatant, and the honest way to ask what it is, is to hand the rule a draw and see whether it fires: 0.1 passes a
 * 20% chance and fails a 4% one, 0.9 fails anything below 90%. So the sequence below pins 100% → 20% → 4% → (reset) → 100%
 * without ever reading a private field, and a failed roll doubling as the proof that a failure does NOT decay.
 *
 * <p>⚠ A fixed random source, as everywhere: {@code nextInt} says which of the three effects is picked (【爆爆爆炸蛋】,
 * which settles no damage of its own, so every HP movement here is the box's own 90%), and {@code nextDouble} is set per
 * firing - high enough that no crit roll ever passes (measured on the sibling judge: a crit would move the box's damage by
 * 50%).
 */
public class SilverWolfLV999LootBoxChanceTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    /**
     * 【爆爆爆炸蛋】's WEIGHT bucket: it restores skill points and settles no damage.
     *
     * <p>⚠ The pick is WEIGHTED (the game's grades: 1 / 1 / 10 while 隐藏分 is under 60), so the roll is over the weight
     * total and this is the bucket's lower edge, not a pool index.
     */
    private static final int PICK_POINTS = 1000;

    /**
     * 100% → 20% → 4% → 【崩坏级伤害演示】 → 100%, each step read through the roll.
     */
    @Test
    public void theBoxsChanceDecaysAndTheDemoResetsIt() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);

        // ① 100%: a 0.9 draw cannot stop it - and at a chance of 1 no draw is even taken.
        Assertions.assertTrue(fireTheBox(battle, rng, 0.9) > 0,
                "the box's initial chance is 100%, so the first trigger is certain");

        // ② 20% after one success: 0.9 fails, and a failure must not decay anything - 0.1 still passes.
        Assertions.assertEquals(0, fireTheBox(battle, rng, 0.9),
                "after one successful trigger the chance is 20%, so a 0.9 draw fires nothing");
        Assertions.assertTrue(fireTheBox(battle, rng, 0.1) > 0,
                "and the failed roll left the chance alone: a 0.1 draw still passes 20%");

        // ③ 4% after two successes: the SAME 0.1 draw that passed at 20% now fails, which is what pins the factor.
        Assertions.assertEquals(0, fireTheBox(battle, rng, 0.1),
                "after two successful triggers the chance is 4%, so the 0.1 draw that passed at 20% now fires nothing");

        // ④ 【崩坏级伤害演示】 puts it back to the initial value: 0.9 cannot stop it again.
        castHonkaiDemo(battle);
        Assertions.assertTrue(fireTheBox(battle, rng, 0.9) > 0,
                "the enhanced Elation skill resets the fixed chance to its initial value");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Fires the one event the box listens to, with the given draw, and returns the HP the enemy side lost to it.
     */
    private static double fireTheBox(Battle battle, ScriptedRng rng, double draw) {
        rng.nextDoubleValue = draw;
        var enemies = battle.enemyUnits();
        double before = enemies.stream().mapToDouble(e -> e.getCurrentHp()).sum();
        Character wolf = battle.characters.getFirst();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
        battle.processRequests();
        return before - enemies.stream().mapToDouble(e -> e.getCurrentHp()).sum();
    }

    /**
     * Casts her enhanced Elation skill (槽 21, 崩坏级伤害演示) through the engine's own cast entry, which is what announces
     * {@code CAST_SETUP} with that slot.
     */
    private static void castHonkaiDemo(Battle battle) {
        Character wolf = battle.characters.getFirst();
        var skill = wolf.getSkills().get(SkillType.ELATION_EXTRA);
        Assertions.assertNotNull(skill, "the fixture needs her 欢愉技·强化 (ELATION_EXTRA, slot 21) to cast at all");
        battle.castImmediate(skill, wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    /**
     * The wolf with her zone and 【无敌玩家】 up, and TWO enemies so a box's own damage is unmistakable.
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
     * {@code nextInt} answers with the scripted pick; {@code nextDouble} is whatever the case set for this firing.
     */
    private static final class ScriptedRng extends Random {
        private final int pick;
        private double nextDoubleValue = 0.9999;

        private ScriptedRng(int pick) {
            this.pick = pick;
        }

        @Override
        public int nextInt(int bound) {
            // ⚠ Clamped: other draws share this source - her enhanced Elation skill's bounce picks a target with
            // `nextInt(alive.size())`, and a weight bucket there would index out of bounds.
            return Math.min(pick, bound - 1);
        }

        @Override
        public double nextDouble() {
            return nextDoubleValue;
        }
    }
}
