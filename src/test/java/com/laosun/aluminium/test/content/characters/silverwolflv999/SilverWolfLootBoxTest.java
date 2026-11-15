package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999): the 【头号补给盲盒】's decaying fixed chance, its reset by 【崩坏级伤害演示】, and
 * the wave deferral (「若触发前目标被消灭则对新入场的敌方目标触发」).
 *
 * <p>These were two small files in the same package; one class keeps the box's chance/wave facts together without
 * changing any assertion.
 *
 * <h2>The pick's weights</h2>
 *
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
 *
 * <h2>One firing settles its own damage, then runs one of the three</h2>
 *
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
 *
 * <p>⚠ Moved: the weights and the pick used to live in their own classes ({@code SilverWolfLV999LootBoxWeightsTest},
 * {@code SilverWolfLV999LootPickTest}); one file now carries the whole box. No assertion changed in the move.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one {@code assertAll} per fixture so a failing
 * clause still names itself: the held firing (held, delivered, and neither re-rolled nor re-decayed), the two resource
 * effects read at the same end of the same pick, and the box's own 90% read as the number it settles AND as the total the
 * enemies split. The chance decay, the weights, the buckets, the sword's share and the spelling of the share keep their own
 * tests: their fixtures differ on the very dimension they measure.
 *
 */
public class SilverWolfLootBoxTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String LAUGH = "笑点";
    private static final String HIDDEN = "隐藏分";
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
    /**
     * 【爆爆爆炸蛋】's WEIGHT bucket: it restores skill points and settles no damage.
     *
     * <p>⚠ The pick is WEIGHTED (the game's grades: 1 / 1 / 10 while 隐藏分 is under 60), so the roll is over the weight
     * total and this is the bucket's lower edge, not a pool index.
     */
    private static final int PICK_POINTS = 1000;
    private static final int PICK_LAUGHS = 2000;
    /**
     * The box's share of its own total, as the document states it (「等同于本次总伤害值 20%」).
     */
    private static final double SHARE = 0.2;

    /**
     * 100% → 20% → 4% → 【崩坏级伤害演示】 → 100%, each step read through the roll.
     */
    @Test
    public void theBoxsChanceDecaysAndTheDemoResetsIt() {
        ChanceRng rng = new ChanceRng(PICK_POINTS);
        Battle battle = boxBattle(rng);

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

    /**
     * The box triggered with nobody on the field is held, the wave delivers it to the enemy that just entered - and the
     * held firing neither re-rolls nor re-decays.
     *
     * <p>One fixture and one firing, read at three moments: before the wave (HELD), when it lands (delivered, with a 0.9
     * draw that would fail a 20% roll), and on the next spent point (nothing, because the held firing already decayed the
     * chance to 20%).
     */
    @Test
    public void aBoxWithNobodyToHitIsHeldUntilTheWave() {
        ChanceRng rng = new ChanceRng(PICK_POINTS);
        Battle battle = boxBattle(rng);
        Character wolf = battle.characters().getFirst();
        for (Enemy enemy : battle.enemyUnits()) {
            enemy.perish();                       // 「触发前目标被消灭」: the field is empty when the point is spent
        }
        int pointsBefore = battle.getSkillPoints();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        int pointsWhileHeld = battle.getSkillPoints();

        Enemy fresh = BattleFixture.monster();      // the next wave walks in
        battle.addCombatant(fresh);
        double freshBefore = fresh.getCurrentHp();
        rng.nextDoubleValue = 0.9;                 // would FAIL a 20% roll
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.WAVE_START, null, null, 0, 0));
        battle.processRequests();
        int pointsAfterWave = battle.getSkillPoints();
        double freshLost = freshBefore - fresh.getCurrentHp();
        System.out.println("[1506] after the wave: points +" + (pointsAfterWave - pointsBefore)
                + ", the new enemy lost " + freshLost);

        rng.nextDoubleValue = 0.9;
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        int pointsAfterNextSpend = battle.getSkillPoints();
        Assertions.assertAll(
                () -> Assertions.assertEquals(pointsBefore, pointsWhileHeld,
                        "with nobody to hit, the box is HELD - a delivered one would have granted 2 skill points here"),
                () -> Assertions.assertEquals(pointsBefore + 2, pointsAfterWave,
                        "and the wave delivers it: the held pick runs now"),
                () -> Assertions.assertTrue(freshLost > 0,
                        "the box's own 90% lands on the enemy that just entered - that is the half the sentence is about"),
                () -> Assertions.assertEquals(pointsBefore + 2, pointsAfterWave,
                        "delivery does NOT re-roll: the held firing's own success is what buys these effects"),
                () -> Assertions.assertEquals(pointsBefore + 2, pointsAfterNextSpend,
                        "and the held firing already decayed the chance to 20%: this 0.9 draw fires nothing"));
    }

    /**
     * The control: with an enemy still standing, the very same firing is delivered at once.
     */
    @Test
    public void aBoxWithSomeoneToHitIsNotHeld() {
        ChanceRng rng = new ChanceRng(PICK_POINTS);
        Battle battle = boxBattle(rng);
        Character wolf = battle.characters().getFirst();
        int pointsBefore = battle.getSkillPoints();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        Assertions.assertEquals(pointsBefore + 2, battle.getSkillPoints(),
                "an enemy was standing, so nothing is held - the pick lands in the same firing");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Fires the one event the box listens to, with the given draw, and returns the HP the enemy side lost to it.
     */
    private static double fireTheBox(Battle battle, ChanceRng rng, double draw) {
        rng.nextDoubleValue = draw;
        var enemies = battle.enemyUnits();
        double before = enemies.stream().mapToDouble(e -> e.getCurrentHp()).sum();
        Character wolf = battle.characters().getFirst();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        return before - enemies.stream().mapToDouble(e -> e.getCurrentHp()).sum();
    }

    /**
     * Casts her enhanced Elation skill (槽 21, 崩坏级伤害演示) through the engine's own cast entry, which is what announces
     * {@code CAST_SETUP} with that slot.
     */
    private static void castHonkaiDemo(Battle battle) {
        Character wolf = battle.characters().getFirst();
        var skill = wolf.getSkills().get(SkillType.ELATION_EXTRA);
        Assertions.assertNotNull(skill, "the fixture needs her 欢愉技·强化 (ELATION_EXTRA, slot 21) to cast at all");
        battle.castImmediate(skill, wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    /**
     * The wolf with her zone and 【无敌玩家】 up, and TWO enemies so a box's own damage is unmistakable.
     */
    private static Battle boxBattle(ChanceRng rng) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster(), BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        wolf.getResources().gain(GIFT, 1);
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return battle;
    }

    /**
     * {@code nextInt} answers with the scripted pick; {@code nextDouble} is whatever the case set for this firing.
     */
    private static final class ChanceRng extends Random {
        private final int pick;
        private double nextDoubleValue = 0.9999;

        private ChanceRng(int pick) {
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

    // ==================================================================
    // Weights - the cases that were SilverWolfLV999LootBoxWeightsTest
    // ==================================================================

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
    // Weight helpers
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

    private record Fixture(Battle battle, Character wolf, WeightRng rng, int pointsBefore, double[] enemyHp) {
        int roll() {
            rng.lastBound = -1;
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
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
            List<com.laosun.aluminium.enemy.Enemy> enemies = battle.enemyUnits();
            double first = enemyHp[0] - enemies.get(0).getCurrentHp();
            double second = enemyHp[1] - enemies.get(1).getCurrentHp();
            Assertions.assertTrue(Math.abs(first - second) > 1e-6,
                    "one of the three must have landed: no points, no laughs, no uneven damage");
            return 0;
        }
    }

    private static Fixture fixture(int skillPoints, int hiddenScore, int roll) {
        WeightRng rng = new WeightRng(roll);
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster(), BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        // ⚠ The skill points are brought to the requested value BEFORE the box is armed: a battle opens with 3 of them, so
        // a lower value means SPENDING - and spending fires SKILL_POINT_SPENT. Nothing triggers here because the zone and
        // 【无敌玩家】 are not up yet; doing it after the ultimate would fire the box during the setup.
        setSkillPoints(battle, skillPoints);
        Character wolf = battle.characters().getFirst();
        wolf.getResources().gain(GIFT, 1);
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
            battle.getSkillPointPolicy().spend();
        }
        if (battle.getSkillPoints() < wanted) {
            battle.getSkillPointPolicy().gain(wanted - battle.getSkillPoints());
        }
        Assertions.assertEquals(wanted, battle.getSkillPoints(), "the fixture must start where the case says");
    }

    /**
     * {@code nextInt} answers with the scripted roll (clamped, because other draws share this source) and records it.
     */
    private static final class WeightRng extends Random {
        private final int roll;
        private int lastBound = -1;

        private WeightRng(int roll) {
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

    // ==================================================================
    // The pick - the cases that were SilverWolfLV999LootPickTest
    // ==================================================================

    /**
     * 【超超超大剑】 is a fifth of what the box settled, over BOTH enemies, and it lands on ONE of them.
     */
    @Test
    public void theSwordIsAFifthOfWhatTheBoxSettled() {
        PickRng rng = new PickRng(PICK_SWORD);
        Battle battle = pickBattle(rng);
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
     * 「造成 90% 的虚数属性欢愉伤害，由敌方全体均分」: the box's own share is her ULTIMATE row's #3 <b>read at her level</b>
     * - so 1.125, not the 0.9 the page prints - and what it settles is a TOTAL, so one enemy takes all of it and two take
     * half each.
     *
     * <p>⚠ The number's model, so it is not a magic constant: {@code elation_base(Lv80) × 1.125 × (1 + 欢愉度)} =
     * 7535.107 × 1.125 × 1.10 = 9324.69 before zones, and this fixture's monster scales that by 0.34287 - measured on this very
     * reading. Her page prints 90% because the generator substitutes the Lv10 display row (0.9); with that literal the same
     * reading is 2557.63, i.e. 0.8 of this.
     *
     * <p>⚠ And the divisor is the reading the author confirmed, the one the JSON cannot show by itself: the per-victim
     * reading ("each enemy takes 90%") would make the same sentence deal 2x here, with the enemy count as the hidden factor.
     * Read per enemy, the two fixtures differ by exactly the divisor.
     */
    @Test
    public void theBoxsOwnDamageIsATotalSplitAmongTheEnemies() {
        PickRng rng = new PickRng(PICK_POINTS);
        Battle battle = pickBattle(rng, 1);
        Reading settled = fire(battle, rng);
        double alone = boxsOwnDamagePerEnemy(1);
        double eachOfTwo = boxsOwnDamagePerEnemy(2);
        System.out.println("[1506] the box settled " + settled.hpLost() + " with one enemy (the Lv10 literal 0.9 would "
                + "read 2557.63) ; one enemy takes " + alone + ", two take " + eachOfTwo + " each");
        Assertions.assertAll(
                () -> Assertions.assertEquals(3197.0383318342465, settled.hpLost(), 1e-6,
                        "the box settles elation_base x the row's 1.125 x her 欢愉度, through this fixture's zones"),
                () -> Assertions.assertEquals(alone, 2 * eachOfTwo, 1e-6,
                        "the stated 90% is the total: with two enemies each takes half, so the TOTAL does not move with N"));
    }

    /**
     * The other two effects, read at the same end of the same pick: 【爆爆爆炸蛋】 restores 2 skill points and settles
     * nothing else, 【怪怪怪味豆】 grants 3 laughs and settles nothing else.
     */
    @Test
    public void theTwoResourceEffectsLandAlone() {
        PickRng pointsRng = new PickRng(PICK_POINTS);
        Reading points = fire(pickBattle(pointsRng), pointsRng);
        PickRng laughsRng = new PickRng(PICK_LAUGHS);
        Reading laughs = fire(pickBattle(laughsRng), laughsRng);
        System.out.println("[1506] 【爆爆爆炸蛋】: +" + points.skillPointsGained + " skill points, "
                + points.laughsGained + " laughs ; 【怪怪怪味豆】: +" + laughs.laughsGained + " laughs, "
                + laughs.skillPointsGained + " skill points");
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, points.skillPointsGained, "【爆爆爆炸蛋】 restores 2 skill points"),
                () -> Assertions.assertEquals(0, points.laughsGained, "and no laughs"),
                () -> Assertions.assertEquals(3, laughs.laughsGained, "【怪怪怪味豆】 grants 3 laughs"),
                () -> Assertions.assertEquals(0, laughs.skillPointsGained, "and no skill points"));
    }

    /**
     * The box's share is read from her Ultimate's row (#3), where the sentence states it - not from a literal.
     *
     * <p>⚠ This half is about the SPELLING, and it is separate from the number below on purpose: a revert to the literal would
     * redden both, and keeping them apart is what lets the numeric case be read as a number rather than as a spelling check.
     */
    @Test
    public void theBoxsShareIsReadFromHerUltimateRow() {
        var box = compileBoxEffect();
        var share = box.getPercentFrom();
        System.out.println("[1506] the box's share: provider=" + share
                + " literal=" + box.getPercent());
        Assertions.assertNotNull(share, "「造成 90% 的虚数属性欢愉伤害」 states a share of the Ultimate's own row");
        Assertions.assertEquals("skill_param", share.getType(), "the share is a row lookup, not a literal");
        Assertions.assertEquals("ULTRA", share.getSkill(), "of her ULTIMATE");
        Assertions.assertEquals(2, share.getIndex(), "item #3 of that row (zero-based, the data's own spelling)");
        Assertions.assertNull(box.getPercent(), "and not a literal, which is what the page's Lv10 display row would give");
    }

    // ==================================================================
    // Pick helpers
    // ==================================================================

    /**
     * The compiled box rule's own damage effect.
     */
    private static com.laosun.aluminium.beans.EffectSpec compileBoxEffect() {
        Character wolf = CharacterFactory.create(WOLF, 80);
        return wolf.getTriggerTable().rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(rule -> rule.id().equals("p1506_zone_loot_box_on_a_spent_point"))
                .findFirst().orElseThrow(() -> new AssertionError("her box rule is not on her table"))
                .effects().stream().filter(e -> "DAMAGE".equals(e.getOp())).findFirst()
                .orElseThrow(() -> new AssertionError("the box's own damage is not on that rule"));
    }

    /**
     * One firing with {@code enemies} identical enemies, 【爆爆爆炸蛋】 picked, returning what each enemy lost.
     */
    private static double boxsOwnDamagePerEnemy(int enemies) {
        PickRng rng = new PickRng(PICK_POINTS);
        Battle battle = pickBattle(rng, enemies);
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
    private static Reading fire(Battle battle, PickRng rng) {
        var enemies = battle.enemyUnits();
        double[] beforeHp = enemies.stream().mapToDouble(e -> e.getCurrentHp()).toArray();
        int pointsBefore = battle.getSkillPoints();
        int laughsBefore = battle.partyResourceValue(LAUGH);
        Character wolf = battle.characters().getFirst();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
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
    private static Battle pickBattle(PickRng rng) {
        return pickBattle(rng, 2);
    }

    /**
     * The same, with however many identical enemies the case needs.
     */
    private static Battle pickBattle(PickRng rng, int enemies) {
        List<com.laosun.aluminium.enemy.Enemy> roster = new java.util.ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            roster.add(BattleFixture.monster());
        }
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)), roster, rng);
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
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
    private static final class PickRng extends Random {
        private final int pick;

        private PickRng(int pick) {
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
