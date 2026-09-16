package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P4-2 acceptance: 每段伤害按技能 {@code stance_list} 削韧，命中弱点才削，削空触发击破。
 *
 * <p>锚点：冰锋 1002011 @组1·Lv90 → 韧性 60、弱火/雷（冰/物理都不削）；
 * 姬子 1003 普攻 Fire 削韧 {@code single=30}、战技 Blast Fire {@code single=60 / spread=30}。
 */
public class ToughnessBattleTest {
    private static final double EPS = 1e-6;
    private static final int ICE_EDGE = 1002011;

    @Test
    public void weaknessHitReducesToughnessBySkillStanceValue() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // 普攻 Fire，削韧 30

        Assertions.assertEquals(30, iceEdge.getStance(), EPS);
        Assertions.assertFalse(iceEdge.isBroken(), "只削一半，还没破");
    }

    @Test
    public void nonWeaknessHitDoesNotReduceToughness() {
        Character mar7th = character("mar7th");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(mar7th, iceEdge);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(iceEdge));   // 普攻 Ice，冰锋不弱冰

        Assertions.assertEquals(60, iceEdge.getStance(), EPS, "非弱点不削韧（HSR.md §3.2）");
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
        Assertions.assertEquals(45, himeko.getCurrentEnergy(), EPS, "两次普攻 20+20 + 击破回能 5");
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
        Assertions.assertEquals(65, himeko.getCurrentEnergy(), EPS, "击破回能只给一次：20×3 + 5");
    }

    @Test
    public void blastReducesCentreBySingleAndNeighboursBySpread() {
        Character himeko = character("himeko");
        Enemy centre = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy left = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy right = EnemyFactory.create(ICE_EDGE, 90, 1);
        // 站位顺序 = battle.enemies 顺序，主目标必须在中间才会同时打到左右相邻
        Battle battle = new Battle(List.of(himeko), List.of(left, centre, right), new Random(0));

        battle.castImmediate(new DefaultSkill(1003, 2, 1), himeko, List.of(centre));   // Blast Fire 60/30

        Assertions.assertEquals(0, centre.getStance(), EPS, "中心 60 - 60");
        Assertions.assertTrue(centre.isBroken(), "中心被削空 → 击破");
        Assertions.assertEquals(30, left.getStance(), EPS, "相邻 60 - spread 30");
        Assertions.assertEquals(30, right.getStance(), EPS);
    }

    @Test
    public void aoeReducesEveryEnemyByAllValue() {
        Character himeko = character("himeko");
        Enemy first = EnemyFactory.create(ICE_EDGE, 90, 1);
        Enemy second = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(first, second), new Random(0));

        battle.castImmediate(new DefaultSkill(1003, 3, 1), himeko, List.of(first));   // 终结技 AoE Fire all=60

        Assertions.assertEquals(0, first.getStance(), EPS);
        Assertions.assertTrue(first.isBroken());
        Assertions.assertEquals(0, second.getStance(), EPS);
        Assertions.assertTrue(second.isBroken());
    }

    private static Character character(String name) {
        return Character.fromAttributes(name, 10_000, 100, 100, 100);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return new Battle(List.of(hero), List.of(enemy), new Random(0));
    }
}
