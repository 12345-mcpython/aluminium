package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * acceptance: each damage instance reduces toughness by the skill's {@code stance_list}, only a
 * weakness hit reduces it, and emptying it triggers a weakness break.
 *
 * <p>Anchors: Ice Edge 1002011 @group 1 - Lv90 to toughness 60, weak to fire/lightning (neither ice nor
 * physical reduces it);
 * Himeko 1003's basic attack Fire has toughness reduction {@code single=30}, her skill Blast Fire has
 * {@code single=60 / spread=30}.
 *
 * <p><b>L3:</b> the single-clause cases that share one fixture are grouped into the six scenarios below,
 * one {@code assertAll} per scenario so a failing clause still names itself. The non-weakness hit, the
 * blast, the AoE and the bounce each rest on their own fixture (a different attacker, or a different
 * enemy layout), so each of those stays its own scenario; no rejection path is involved here.
 */
public class ToughnessBattleTest {
    private static final double EPS = 1e-6;
    private static final int ICE_EDGE = 1002011;

    /**
     * One Himeko facing one Ice Edge: the first basic attack only halves the bar, the second empties it
     * and breaks the enemy (paying the break energy exactly once), and a third attack on the already
     * broken enemy does not touch the empty bar again.
     */
    @Test
    public void weaknessHitsReduceToughnessAndEmptyingItBreaksTheEnemyOnce() {
        Character himeko = character("himeko");
        himeko.setMaxEnergy(120);
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        // first hit: basic attack Fire, toughness reduction 30
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        double stanceAfterFirstHit = iceEdge.getStance();
        boolean brokenAfterFirstHit = iceEdge.isBroken();

        // second hit empties the remaining 30 → weakness break
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        double stanceAfterBreak = iceEdge.getStance();
        boolean brokenAfterBreak = iceEdge.isBroken();
        DamageElement breakElement = iceEdge.getBrokenElement();
        double energyAfterBreak = himeko.getCurrentEnergy();

        // third hit lands on the already broken enemy
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));

        Assertions.assertAll("weakness hit → emptied bar → break, and only once",
                () -> Assertions.assertEquals(30, stanceAfterFirstHit, EPS,
                        "one weakness hit reduces toughness by the skill's stance_list single 30"),
                () -> Assertions.assertFalse(brokenAfterFirstHit, "only half is reduced, not broken yet"),
                () -> Assertions.assertTrue(brokenAfterBreak, "emptying the toughness breaks the enemy"),
                () -> Assertions.assertEquals(DamageElement.FIRE, breakElement,
                        "the break records the fire weakness element"),
                () -> Assertions.assertEquals(0, stanceAfterBreak, EPS, "the toughness bar is left at zero"),
                () -> Assertions.assertEquals(45, energyAfterBreak, EPS,
                        "two basic attacks 20+20 + weakness break energy gain 5"),
                () -> Assertions.assertTrue(iceEdge.isBroken(), "the broken state survives the following hit"),
                () -> Assertions.assertEquals(0, iceEdge.getStance(), EPS,
                        "a broken enemy takes no further toughness damage"),
                () -> Assertions.assertEquals(65, himeko.getCurrentEnergy(), EPS,
                        "weakness break energy gain is granted only once: 20×3 + 5"));
    }

    @Test
    public void nonWeaknessHitDoesNotReduceToughness() {
        Character mar7th = character("mar7th");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(mar7th, iceEdge);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(iceEdge));   // basic attack Ice, Ice Edge is not weak to ice

        Assertions.assertAll("only a weakness hit reduces toughness",
                () -> Assertions.assertEquals(60, iceEdge.getStance(), EPS,
                        "a non-weakness hit does not reduce toughness (HSR.md §3.2)"));
    }

    @Test
    public void blastReducesCentreBySingleAndNeighboursBySpread() {
        Character himeko = character("himeko");
        Enemy centre = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy left = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy right = EnemyFactory.create(ICE_EDGE, 90, 1);
        // Positional order = battle.enemies order; the main target must be in the middle to hit both neighbours
        Battle battle = new Battle(List.of(himeko), List.of(left, centre, right), BattleFixture.seeded());

        battle.castImmediate(new DefaultSkill(1003, 2, 1), himeko, List.of(centre));   // Blast Fire 60/30

        Assertions.assertAll("blast: centre gets single, neighbours get spread",
                () -> Assertions.assertEquals(0, centre.getStance(), EPS, "centre 60 - 60"),
                () -> Assertions.assertTrue(centre.isBroken(), "the centre is emptied → weakness break"),
                () -> Assertions.assertEquals(30, left.getStance(), EPS, "neighbour 60 - spread 30"),
                () -> Assertions.assertEquals(30, right.getStance(), EPS, "neighbour 60 - spread 30"));
    }

    @Test
    public void aoeReducesEveryEnemyByAllValue() {
        Character himeko = character("himeko");
        Enemy first = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy second = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(first, second), BattleFixture.seeded());

        battle.castImmediate(new DefaultSkill(1003, 3, 1), himeko, List.of(first));   // ultimate AoE Fire all=60

        Assertions.assertAll("AoE: every enemy takes the full all value",
                () -> Assertions.assertEquals(0, first.getStance(), EPS, "the main target 60 - all 60"),
                () -> Assertions.assertTrue(first.isBroken(), "the main target is emptied → weakness break"),
                () -> Assertions.assertEquals(0, second.getStance(), EPS, "the other enemy 60 - all 60"),
                () -> Assertions.assertTrue(second.isBroken(), "the other enemy is emptied → weakness break"));
    }

    /**
     * For a bouncing skill, {@code stance_list.single} is the total toughness reduction of the whole
     * skill and must be spread evenly across the hits.
     *
     * <p>Anchor (real data): cid 1321 Dahlia slot 4 = Bounce Fire, {@code hits = 5}, {@code single = 9}
     * to the five hits total 9 points, not 9 points per hit (45 in total).
     * Before the fix this case would see 60 - 45 = 15; after the fix it is 60 - 9 = 51.
     */
    @Test
    public void bounceSplitsItsTotalStanceValueAcrossTheHits() {
        Character hero = character("dahlia");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);          // weak to fire/lightning, toughness 60
        Battle battle = newBattle(hero, iceEdge);

        battle.castImmediate(new DefaultSkill(1321, 4, 1), hero, List.of(iceEdge));

        Assertions.assertAll("bounce: single is the total across the hits",
                () -> Assertions.assertEquals(51, iceEdge.getStance(), EPS,
                        "a total of 9 spread across 5 hits: 60 - 9 = 51"),
                () -> Assertions.assertFalse(iceEdge.isBroken(), "only 9 points in total, nowhere near emptied"));
    }

    /**
     * Weakness break damage is computed from "the value this instance actually reduced", not from
     * the skill's nominal toughness reduction value.
     *
     * <p>Ice Edge has toughness 60 and a basic attack reduces 30 to 30 left; the second basic attack is
     * nominally 30 and actually reduces exactly 30 (just emptying it), so here we first set up the scenario
     * of "30 left taking a 30-point skill". To create excess toughness reduction, Himeko's skill is used
     * instead (Blast Fire {@code single=60}) against the remaining 30 toughness: it actually reduces 30 while
     * being nominally 60.
     * If the nominal value 60 were used to compute the weakness break damage, the result would be doubled
     * and this case would fail.
     */
    @Test
    public void breakDamageUsesTheToughnessActuallyConsumed() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // basic attack reduces 30 to 30 left
        double stanceAfterBasicAttack = iceEdge.getStance();

        double hpBefore = iceEdge.getCurrentHp();
        battle.castImmediate(new DefaultSkill(1003, 2, 1), himeko, List.of(iceEdge));   // skill is nominally 60 but actually reduces only 30
        boolean brokenAfterSkill = iceEdge.isBroken();
        double damageDealt = hpBefore - iceEdge.getCurrentHp();

        double atk = himeko.getAttribute(AttributeType.ATTACK).get();
        double defenceZone = 1000.0 / (iceEdge.getAttribute(AttributeType.DEFENCE).get() + 1000.0);
        double resistZone = 1 - iceEdge.getDamageResist().getOrDefault(DamageElement.FIRE, 0.0);
        double skillDamage = atk * 1.0;                      // Himeko's skill multiplier 1.0 (himeko ATK = 1000)
        double breakDamage = Constant.BREAKING_RATE.get(himeko.getLevel()) / 10.0 * 30;
        // ⚠ BOTH instances take the toughness damage reduction: the skill lands while the bar still holds 30 points, and
        // the break damage 「一定会受该减伤影响」 (the author's ruling) even though the bar empties first - which is exactly
        // why `Battle.assemble` excepts the BREAK instance from its "is it broken" question.
        double expected = (skillDamage + breakDamage) * defenceZone * resistZone
                * (1 - com.laosun.aluminium.Constant.TOUGHNESS_DAMAGE_REDUCTION);

        // Reverse assertion: if the nominal value 60 were used by mistake the settled value would be clearly
        // larger - ensuring this case really can fail
        double wrong = (skillDamage + breakDamage * 2) * defenceZone * resistZone
                * (1 - com.laosun.aluminium.Constant.TOUGHNESS_DAMAGE_REDUCTION);

        Assertions.assertAll("break damage uses the toughness actually consumed",
                () -> Assertions.assertEquals(30, stanceAfterBasicAttack, EPS,
                        "the basic attack leaves 30 toughness"),
                () -> Assertions.assertTrue(brokenAfterSkill,
                        "the skill (nominally 60) empties the remaining 30 → weakness break"),
                () -> Assertions.assertEquals(expected, damageDealt, 1e-6,
                        "weakness break damage uses the 30 actually reduced, not the nominal 60"),
                () -> Assertions.assertNotEquals(wrong, damageDealt, 1e-6,
                        "the result of the nominal value 60 must differ from this implementation, otherwise this test has no discriminating power"));
    }

    private static Character character(String name) {
        return Character.fromAttributes(name, 10_000, 100, 100, 100);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return BattleFixture.battle(List.of(hero), List.of(enemy));
    }
}
