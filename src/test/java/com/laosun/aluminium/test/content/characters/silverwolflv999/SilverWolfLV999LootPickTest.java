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
 * Character 1506 (Silver Wolf LV.999): 【头号补给盲盒】 settles its own damage and then runs ONE of three effects, and
 * 【超超超大剑】 takes a fifth of the damage that box settled.
 *
 * <p>「…造成 90% 的虚数属性欢愉伤害，由敌方全体均分，并随机触发 1 种效果：【超超超大剑】：对生命值最高的敌方目标额外造成等同于本次总伤害值
 * 20% 的真实伤害；【爆爆爆炸蛋】：恢复 2 个战技点；【怪怪怪味豆】：获得 3 个笑点。」
 *
 * <p>⚠ The box is ONE rule: its own 90% carries {@code always} (「造成…伤害，<b>并</b>随机触发 1 种效果」 - it is not one of the
 * three), which is also what lets the box have a single fixed chance.
 *
 * <p>⚠ <b>One firing, two enemies, and both are load-bearing.</b> The box's own 90% is a TOTAL split among the enemies
 * (「由敌方全体均分」), so it settles one instance per enemy and the rider is a share of their SUM - with two enemies a
 * rider that read only the instance on its own target would land at half the size. And the enemy the rider did NOT hit is
 * the control: its loss IS one box's hit per enemy, measured inside the same firing. Reading the same thing from a second
 * firing would need the box's chance to pass twice (it decays to 20% after the first success), so the per-firing question
 * is the engine judge's, where the fixture states no chance at all.
 *
 * <p>⚠ The random source is pinned rather than seeded: {@code nextInt} says which effect runs, and {@code nextDouble}
 * never passes a crit - measured on this very fixture, a crit moves the box's damage by 50% (146.36 → 219.54) and the
 * share would be asserted against the wrong total.
 */
public class SilverWolfLV999LootPickTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String LAUGH = "笑点";
    /**
     * 【好活当赏】 - the box's own gate since the audit (「「银狼LV.999」持有【好活当赏】时」): a RESOURCE, because that is what
     * the clause reads ({@code self_resource:好活当赏 >= 1}). Nothing in her file grants it - the Aha Moment does, and this
     * engine has no Aha stage - so the fixture states the premise.
     */
    private static final String GIFT = "好活当赏";
    /**
     * What the scripted roll answers with: a value inside the effect's WEIGHT bucket.
     *
     * <p>⚠ The pick is WEIGHTED - the game's own grades are 1, 1 and 10 while 隐藏分 is under 60 - so the roll is over
     * the weight total (12000 in this fixture's state) and these are the buckets' lower edges, not effect indices.
     */
    private static final int PICK_SWORD = 0;
    private static final int PICK_POINTS = 1000;
    private static final int PICK_LAUGHS = 2000;
    /**
     * The box's share of its own total, as the document states it (「等同于本次总伤害值 20%」).
     */
    private static final double SHARE = 0.2;

    /**
     * 【超超超大剑】 is a fifth of what the box settled, over BOTH enemies, and it lands on ONE of them.
     */
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

    /**
     * 「由敌方全体均分」: the box's own 90% is a TOTAL, so one enemy takes all of it and two take half each.
     *
     * <p>⚠ This is the reading the author confirmed, and it is the one the JSON cannot show by itself: the per-victim
     * reading ("each enemy takes 90%") would make the same sentence deal 2x here, with the enemy count as the hidden
     * factor. Read per enemy, the two fixtures differ by exactly the divisor.
     */
    @Test
    public void theBoxsOwnDamageIsSplitAmongTheEnemies() {
        double alone = boxsOwnDamagePerEnemy(1);
        double eachOfTwo = boxsOwnDamagePerEnemy(2);
        System.out.println("[1506] one enemy takes " + alone + ", two take " + eachOfTwo + " each");
        Assertions.assertEquals(alone, 2 * eachOfTwo, 1e-6,
                "the stated 90% is the total: with two enemies each takes half, so the TOTAL does not move with N");
    }

    /**
     * 【爆爆爆炸蛋】 restores 2 skill points and settles nothing else: the other end of the same pick.
     */
    @Test
    public void thePointsEffectDoesNotTouchTheEnemySide() {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng);
        Reading points = fire(battle, rng);
        Assertions.assertEquals(2, points.skillPointsGained, "【爆爆爆炸蛋】 restores 2 skill points");
        Assertions.assertEquals(0, points.laughsGained, "and no laughs");
    }

    /**
     * 【怪怪怪味豆】 grants 3 laughs and settles nothing else.
     */
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

    /**
     * One firing with {@code enemies} identical enemies, 【爆爆爆炸蛋】 picked, returning what each enemy lost.
     */
    private static double boxsOwnDamagePerEnemy(int enemies) {
        ScriptedRng rng = new ScriptedRng(PICK_POINTS);
        Battle battle = battle(rng, enemies);
        Reading reading = fire(battle, rng);
        for (double lost : reading.hpLostEach) {
            Assertions.assertEquals(reading.hpLostEach[0], lost, 1e-6,
                    "identical enemies must take the same share, or the comparison below is meaningless");
        }
        return reading.hpLostEach[0];
    }

    /**
     * What one firing of the box moved: HP (in total and per enemy), skill points and laughs.
     */
    private record Reading(double hpLost, double[] hpLostEach, int skillPointsGained, int laughsGained) {
    }

    /**
     * Fires the one event the box listens to and reads everything either side of it.
     */
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

    /**
     * The wolf with her zone and 【无敌玩家】 up, and TWO identical enemies so "the total" is not one instance.
     */
    private static Battle battle(ScriptedRng rng) {
        return battle(rng, 2);
    }

    /**
     * The same, with however many identical enemies the case needs.
     */
    private static Battle battle(ScriptedRng rng, int enemies) {
        List<com.laosun.aluminium.models.enemy.Enemy> roster = new java.util.ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            roster.add(BattleFixture.monster());
        }
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)), roster, rng);
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters.getFirst();
        wolf.getResources().gain(GIFT, 1);
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
            // ⚠ Clamped: other draws share this source (a bounce picks its target with `nextInt(alive.size())`), and a
            // weight bucket there would index out of bounds.
            return Math.min(pick, bound - 1);
        }

        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }
}
