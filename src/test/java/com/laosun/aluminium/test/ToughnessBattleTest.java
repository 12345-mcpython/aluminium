package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P4-2 acceptance: each damage instance reduces toughness by the skill's {@code stance_list}, only a
 * weakness hit reduces it, and emptying it triggers a weakness break.
 *
 * <p>Anchors: Ice Edge 1002011 @group 1 · Lv90 → toughness 60, weak to fire/lightning (neither ice nor
 * physical reduces it);
 * Himeko 1003's basic attack Fire has toughness reduction {@code single=30}, her skill Blast Fire has
 * {@code single=60 / spread=30}.
 */
public class ToughnessBattleTest {
    private static final double EPS = 1e-6;
    private static final int ICE_EDGE = 1002011;

    @Test
    public void weaknessHitReducesToughnessBySkillStanceValue() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // basic attack Fire, toughness reduction 30

        Assertions.assertEquals(30, iceEdge.getStance(), EPS);
        Assertions.assertFalse(iceEdge.isBroken(), "only half is reduced, not broken yet");
    }

    @Test
    public void nonWeaknessHitDoesNotReduceToughness() {
        Character mar7th = character("mar7th");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(mar7th, iceEdge);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(iceEdge));   // basic attack Ice, Ice Edge is not weak to ice

        Assertions.assertEquals(60, iceEdge.getStance(), EPS, "a non-weakness hit does not reduce toughness (HSR.md §3.2)");
    }

    @Test
    public void emptyingToughnessBreaksTheEnemyAndGrantsBreakEnergy() {
        Character himeko = character("himeko");
        himeko.setMaxEnergy(120);
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(DamageElement.FIRE, iceEdge.getBrokenElement());
        Assertions.assertEquals(0, iceEdge.getStance(), EPS);
        Assertions.assertEquals(45, himeko.getCurrentEnergy(), EPS, "two basic attacks 20+20 + weakness break energy gain 5");
    }

    @Test
    public void brokenEnemyTakesNoFurtherToughnessDamage() {
        Character himeko = character("himeko");
        himeko.setMaxEnergy(120);
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        for (int i = 0; i < 3; i++) {
            battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        }

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(0, iceEdge.getStance(), EPS);
        Assertions.assertEquals(65, himeko.getCurrentEnergy(), EPS, "weakness break energy gain is granted only once: 20×3 + 5");
    }

    @Test
    public void blastReducesCentreBySingleAndNeighboursBySpread() {
        Character himeko = character("himeko");
        Enemy centre = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy left = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy right = EnemyFactory.create(ICE_EDGE, 90, 1);
        // Positional order = battle.enemies order; the main target must be in the middle to hit both neighbours
        Battle battle = new Battle(List.of(himeko), List.of(left, centre, right), new Random(0));

        battle.castImmediate(new DefaultSkill(1003, 2, 1), himeko, List.of(centre));   // Blast Fire 60/30

        Assertions.assertEquals(0, centre.getStance(), EPS, "centre 60 - 60");
        Assertions.assertTrue(centre.isBroken(), "the centre is emptied → weakness break");
        Assertions.assertEquals(30, left.getStance(), EPS, "neighbour 60 - spread 30");
        Assertions.assertEquals(30, right.getStance(), EPS);
    }

    @Test
    public void aoeReducesEveryEnemyByAllValue() {
        Character himeko = character("himeko");
        Enemy first = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy second = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(first, second), new Random(0));

        battle.castImmediate(new DefaultSkill(1003, 3, 1), himeko, List.of(first));   // ultimate AoE Fire all=60

        Assertions.assertEquals(0, first.getStance(), EPS);
        Assertions.assertTrue(first.isBroken());
        Assertions.assertEquals(0, second.getStance(), EPS);
        Assertions.assertTrue(second.isBroken());
    }

    /**
     * H-3: for a bouncing skill, {@code stance_list.single} is the **total toughness reduction of the whole
     * skill** and must be spread evenly across the hits.
     *
     * <p>Anchor (real data): cid 1321 Dahlia slot 4 = Bounce Fire, {@code hits = 5}, {@code single = 9}
     * → the five hits total 9 points, not 9 points per hit (45 in total).
     * Before the fix this case would see 60 - 45 = 15; after the fix it is 60 - 9 = 51.
     */
    @Test
    public void bounceSplitsItsTotalStanceValueAcrossTheHits() {
        Character hero = character("dahlia");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);          // weak to fire/lightning, toughness 60
        Battle battle = newBattle(hero, iceEdge);

        battle.castImmediate(new DefaultSkill(1321, 4, 1), hero, List.of(iceEdge));

        Assertions.assertEquals(51, iceEdge.getStance(), EPS, "a total of 9 spread across 5 hits: 60 - 9 = 51");
        Assertions.assertFalse(iceEdge.isBroken(), "only 9 points in total, nowhere near emptied");
    }

    /**
     * H-4: weakness break damage is computed from "the value this instance **actually** reduced", not from
     * the skill's nominal toughness reduction value.
     *
     * <p>Ice Edge has toughness 60 and a basic attack reduces 30 → 30 left; the second basic attack is
     * nominally 30 and actually reduces exactly 30 (just emptying it), so here we first set up the scenario
     * of "30 left taking a 30-point skill". To create **excess** toughness reduction, Himeko's skill is used
     * instead (Blast Fire {@code single=60}) against the remaining 30 toughness: it actually reduces 30 while
     * being nominally 60.
     * If the nominal value 60 were used to compute the weakness break damage, the result would be **doubled**
     * and this case would fail.
     */
    @Test
    public void breakDamageUsesTheToughnessActuallyConsumed() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // basic attack reduces 30 → 30 left
        Assertions.assertEquals(30, iceEdge.getStance(), EPS);

        double hpBefore = iceEdge.getCurrentHp();
        battle.castImmediate(new DefaultSkill(1003, 2, 1), himeko, List.of(iceEdge));   // skill is nominally 60 but actually reduces only 30
        Assertions.assertTrue(iceEdge.isBroken());

        double atk = himeko.getAttribute(AttributeType.ATTACK).get();
        double defenceZone = 1000.0 / (iceEdge.getAttribute(AttributeType.DEFENCE).get() + 1000.0);
        double resistZone = 1 - iceEdge.getDamageResist().getOrDefault(DamageElement.FIRE, 0.0);
        double skillDamage = atk * 1.0;                      // Himeko's skill multiplier 1.0 (himeko ATK = 1000)
        double breakDamage = Constant.BREAKING_RATE.get(himeko.getLevel()) / 10.0 * 30;
        double expected = (skillDamage + breakDamage) * defenceZone * resistZone;

        Assertions.assertEquals(expected, hpBefore - iceEdge.getCurrentHp(), 1e-6,
                "weakness break damage uses the 30 actually reduced, not the nominal 60");

        // Reverse assertion: if the nominal value 60 were used by mistake the settled value would be clearly
        // larger — ensuring this case really can fail
        double wrong = (skillDamage + breakDamage * 2) * defenceZone * resistZone;
        Assertions.assertNotEquals(wrong, hpBefore - iceEdge.getCurrentHp(), 1e-6,
                "the result of the nominal value 60 must differ from this implementation, otherwise this test has no discriminating power");
    }

    private static Character character(String name) {
        return Character.fromAttributes(name, 10_000, 100, 100, 100);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return new Battle(List.of(hero), List.of(enemy), new Random(0));
    }
}
