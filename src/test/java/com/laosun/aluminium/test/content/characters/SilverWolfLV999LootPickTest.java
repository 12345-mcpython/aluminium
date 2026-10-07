package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999): 【头号补给盲盒】 settles its own damage and then runs ONE of three effects, and
 * 【超超超大剑】 takes a fifth of the damage that box settled.
 *
 * <p>「…造成 90% 的虚数属性欢愉伤害，由敌方全体均分，并随机触发 1 种效果：【超超超大剑】：对生命值最高的敌方目标额外造成等同于本次总伤害值
 * 20% 的真实伤害；【爆爆爆炸蛋】：恢复 2 个战技点；【怪怪怪味豆】：获得 3 个笑点。」
 *
 * <p>⚠ The box is ONE rule: its own 90% carries {@code always} (「造成…伤害，<b>并</b>随机触发 1 种效果」 - it is not one of the
 * three), which is also what lets the box have a single fixed chance.
 *
 * <p>⚠ <b>Two enemies, and the reason is the sword's base.</b> The box settles one instance per enemy, and the sword's
 * share is taken of their TOTAL - so with two enemies a rider that read only the instance on its own target would land
 * at 1.1x the box's damage instead of 1.2x. With one enemy the two readings are the same number, and the judge could
 * not tell them apart.
 *
 * <p>⚠ The pick is read as a COUNT, not as "did X happen": the wrong implementations are "both ran" and "neither ran",
 * and a positive assertion is satisfied by the first of them. Here each effect has a marker only it produces (skill
 * points +2 / laughs +3 / the sword's own damage), so "exactly one" is a statement about all three at once.
 *
 * <p>⚠ The random source is pinned rather than seeded ({@link ScriptedRng}): which effect runs is scripted, and
 * {@code nextDouble} never passes a crit, so the numbers below are exact instead of "usually". That also makes the
 * sword's own reading a hard number: two lucky rolls per enemy would otherwise move the box's damage by 50%.
 */
public class SilverWolfLV999LootPickTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";
    /** What the scripted roll answers with: an index into the box's POOL, in the order the file states the three. */
    private static final int PICK_SWORD = 0;
    private static final int PICK_POINTS = 1;
    private static final int PICK_LAUGHS = 2;

    /**
     * The box's damage is settled once per enemy, and the sword is a fifth of that total - stated as a share so the
     * failure says which reading it got: 1.1x is "only my own target's instance", 1.0x is "no rider at all", and the
     * old stand-in (500% of her ATK) was 7.6x.
     */
    @Test
    public void theSwordIsAFifthOfWhatTheBoxSettled() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);

        // ① 【爆爆爆炸蛋】 settles no damage of its own, so this reading IS the box's total - over both enemies.
        Reading points = fire(battle, rng, PICK_POINTS);
        Assertions.assertEquals(2, points.skillPointsGained, "【爆爆爆炸蛋】 restores 2 skill points");
        Assertions.assertEquals(0, points.laughsGained, "and no laughs");
        double total = points.hpLost;
        System.out.println("[1506] the box's own total over " + points.hpLostEach.length + " enemies = " + total);
        Assertions.assertTrue(total > 0, "the box's own 90% lands whichever effect is picked");

        // ② 【超超超大剑】: that total, plus a fifth of it, as ONE true-damage instance on the highest-HP enemy.
        Reading sword = fire(battle, rng, PICK_SWORD);
        Assertions.assertEquals(0, sword.skillPointsGained + sword.laughsGained,
                "neither of the other two effects ran alongside the sword");
        Assertions.assertEquals(total + 0.2 * total, sword.hpLost, 1e-6,
                "the sword settles 20% of the TOTAL the box settled, not 20% of one enemy's instance");
        double spread = Math.max(sword.hpLostEach[0], sword.hpLostEach[1])
                - Math.min(sword.hpLostEach[0], sword.hpLostEach[1]);
        Assertions.assertEquals(0.2 * total, spread, 1e-6,
                "the rider lands on ONE enemy - the one with the highest HP - while both take the box's own damage");

        // ③ The total belongs to THIS firing. A second sword, after two firings that settled damage, must still be a
        //    fifth of one box - not a fifth of everything the battle has ever dealt.
        Reading again = fire(battle, rng, PICK_SWORD);
        Assertions.assertEquals(total + 0.2 * total, again.hpLost, 1e-6,
                "the running total is emptied at the start of each firing; otherwise this one reads three boxes");

        // ④ 【怪怪怪味豆】 adds laughs and no damage - and the laughs arrive AFTER the box's own damage, so the base is
        //    still the one measured in ①.
        Reading laughs = fire(battle, rng, PICK_LAUGHS);
        Assertions.assertEquals(3, laughs.laughsGained, "【怪怪怪味豆】 grants 3 laughs");
        Assertions.assertEquals(0, laughs.skillPointsGained, "and no skill points");
        Assertions.assertEquals(total, laughs.hpLost, 1e-6, "only the box's own damage - this effect has no rider");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** What one firing of the box moved: HP (in total and per enemy), skill points and laughs. */
    private record Reading(double hpLost, double[] hpLostEach, int skillPointsGained, int laughsGained) {
    }

    /** Fires the one event the box listens to and reads everything either side of it. */
    private static Reading fire(Battle battle, ScriptedRng rng, int pick) {
        rng.pick = pick;
        var enemies = battle.enemyUnits();
        double[] beforeHp = enemies.stream().mapToDouble(e -> e.getCurrentHp()).toArray();
        int pointsBefore = battle.getSkillPoints();
        int laughsBefore = battle.partyResourceValue(LAUGH);
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, battle.characters.getFirst(),
                battle.characters.getFirst(), 0, 1);
        battle.processRequests();
        double[] lost = new double[beforeHp.length];
        double totalLost = 0;
        for (int i = 0; i < beforeHp.length; i++) {
            lost[i] = beforeHp[i] - enemies.get(i).getCurrentHp();
            totalLost += lost[i];
        }
        return new Reading(totalLost, lost, battle.getSkillPoints() - pointsBefore,
                battle.partyResourceValue(LAUGH) - laughsBefore);
    }

    /** The wolf with her zone and 【无敌玩家】 up, and TWO identical enemies so "the total" is not one instance. */
    private static Battle battle(ScriptedRng rng) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1), EnemyFactory.create(MONSTER, 90, 1)), rng);
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters.getFirst();
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return battle;
    }

    /**
     * A random source with the luck taken out: {@code nextInt} answers with the effect the caller scripts (which is
     * what {@code one_of} draws), and {@code nextDouble} is high enough that no crit roll ever passes.
     */
    private static final class ScriptedRng extends Random {
        private int pick;
        private int intCalls;

        private ScriptedRng(int pick) {
            this.pick = pick;
        }

        @Override
        public int nextInt(int bound) {
            intCalls++;
            return pick;
        }

        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }
}
