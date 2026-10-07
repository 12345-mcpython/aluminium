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
 * <p>⚠ <b>One firing, two enemies, and both are load-bearing.</b> The box settles one instance per enemy and the rider is
 * a share of their TOTAL, so with two enemies a rider that read only the instance on its own target would land at half
 * the size. And the enemy the rider did NOT hit is the control: its loss IS one box's hit per enemy, measured inside the
 * same firing. Reading the same thing from a second firing would need the box's chance to pass twice (it decays to 20%
 * after the first success), so the per-firing question is the engine judge's, where the fixture states no chance at all.
 *
 * <p>⚠ The random source is pinned rather than seeded: {@code nextInt} says which effect runs, and {@code nextDouble}
 * never passes a crit - measured on this very fixture, a crit moves the box's damage by 50% (146.36 → 219.54) and the
 * share would be asserted against the wrong total.
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
    /** The box's share of its own total, as the document states it (「等同于本次总伤害值 20%」). */
    private static final double SHARE = 0.2;

    /** 【超超超大剑】 is a fifth of what the box settled, over BOTH enemies, and it lands on ONE of them. */
    @Test
    public void theSwordIsAFifthOfWhatTheBoxSettled() {
        ScriptedRng rng = new ScriptedRng(PICK_SWORD);
        Battle battle = battle(rng);
        Reading sword = fire(battle, rng);

        Assertions.assertEquals(0, sword.skillPointsGained + sword.laughsGained,
                "neither of the other two effects ran alongside the sword");
        // The enemy the rider did not hit took the box's own damage and nothing else, so its loss is one instance.
        double perEnemy = Math.min(sword.hpLostEach[0], sword.hpLostEach[1]);
        double rider = Math.max(sword.hpLostEach[0], sword.hpLostEach[1]) - perEnemy;
        double total = 2 * perEnemy;
        System.out.println("[1506] the box settled " + perEnemy + " per enemy (" + total + " in all), the sword "
                + rider + " - a share of " + (rider / total));
        Assertions.assertTrue(perEnemy > 0, "the box's own 90% lands whichever effect is picked, on every enemy");
        Assertions.assertEquals(SHARE * total, rider, 1e-6,
                "the sword settles 20% of the TOTAL the box settled - reading the rider's own target's instance "
                        + "would be half of that, and no rider at all would leave the two losses equal");
    }

    /** 【爆爆爆炸蛋】 restores 2 skill points and settles nothing else: the other end of the same pick. */
    @Test
    public void thePointsEffectDoesNotTouchTheEnemySide() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);
        Reading points = fire(battle, rng);
        Assertions.assertEquals(2, points.skillPointsGained, "【爆爆爆炸蛋】 restores 2 skill points");
        Assertions.assertEquals(0, points.laughsGained, "and no laughs");
    }

    /** 【怪怪怪味豆】 grants 3 laughs and settles nothing else. */
    @Test
    public void theLaughsEffectDoesNotTouchTheEnemySide() {
        ScriptedRng rng = new ScriptedRng(PICK_LAUGHS);
        Battle battle = battle(rng);
        Reading laughs = fire(battle, rng);
        Assertions.assertEquals(3, laughs.laughsGained, "【怪怪怪味豆】 grants 3 laughs");
        Assertions.assertEquals(0, laughs.skillPointsGained, "and no skill points");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** What one firing of the box moved: HP (in total and per enemy), skill points and laughs. */
    private record Reading(double hpLost, double[] hpLostEach, int skillPointsGained, int laughsGained) {
    }

    /** Fires the one event the box listens to and reads everything either side of it. */
    private static Reading fire(Battle battle, ScriptedRng rng) {
        var enemies = battle.enemyUnits();
        double[] beforeHp = enemies.stream().mapToDouble(e -> e.getCurrentHp()).toArray();
        int pointsBefore = battle.getSkillPoints();
        int laughsBefore = battle.partyResourceValue(LAUGH);
        Character wolf = battle.characters.getFirst();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
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
     * A random source with the luck taken out: {@code nextInt} answers with the effect the caller scripts (which is what
     * {@code one_of} draws), and {@code nextDouble} is high enough that no crit roll ever passes.
     */
    private static final class ScriptedRng extends Random {
        private final int pick;

        private ScriptedRng(int pick) {
            this.pick = pick;
        }

        @Override
        public int nextInt(int bound) {
            return pick;
        }

        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }
}
