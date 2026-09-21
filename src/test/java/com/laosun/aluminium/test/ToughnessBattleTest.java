package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
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

    /**
     * H-3：弹射技能的 {@code stance_list.single} 是**整个技能的总削韧**，要按段数均摊。
     *
     * <p>锚点（真实数据）：cid 1321 达丽娅 槽位 4 = Bounce Fire，{@code hits = 5}、{@code single = 9}
     * → 五段合计 9 点，而不是每段 9 点（合计 45）。
     * 修之前这个用例会看到 60 - 45 = 15；修之后是 60 - 9 = 51。
     */
    @Test
    public void bounceSplitsItsTotalStanceValueAcrossTheHits() {
        Character hero = character("dahlia");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);          // 弱火/雷、韧性 60
        Battle battle = newBattle(hero, iceEdge);

        battle.castImmediate(new DefaultSkill(1321, 4, 1), hero, List.of(iceEdge));

        Assertions.assertEquals(51, iceEdge.getStance(), EPS, "总值 9 均摊到 5 段：60 - 9 = 51");
        Assertions.assertFalse(iceEdge.isBroken(), "总共只削 9 点，远没打空");
    }

    /**
     * H-4：击破伤害按"这一段**实际**削掉的值"算，不是技能的标称削韧值。
     *
     * <p>冰锋韧性 60，普攻削 30 → 剩 30；第二发普攻标称 30、实际只削掉 30（刚好打空），
     * 所以这里先建立"剩 30 挨 30 点技能"的场景。为制造**过量**削韧，改用姬子战技
     * （Blast Fire {@code single=60}）打只剩 30 的韧性：实际削 30、标称 60。
     * 若用标称值 60 算击破伤害，结果会**翻倍**，本用例即失败。
     */
    @Test
    public void breakDamageUsesTheToughnessActuallyConsumed() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = newBattle(himeko, iceEdge);

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // 普攻削 30 → 剩 30
        Assertions.assertEquals(30, iceEdge.getStance(), EPS);

        double hpBefore = iceEdge.getCurrentHp();
        battle.castImmediate(new DefaultSkill(1003, 2, 1), himeko, List.of(iceEdge));   // 战技标称 60，实际只削 30
        Assertions.assertTrue(iceEdge.isBroken());

        double atk = himeko.getAttribute(AttributeType.ATTACK).get();
        double defenceZone = 1000.0 / (iceEdge.getAttribute(AttributeType.DEFENCE).get() + 1000.0);
        double resistZone = 1 - iceEdge.getDamageResist().getOrDefault(DamageElement.FIRE, 0.0);
        double skillDamage = atk * 1.0;                      // 姬子战技倍率 1.0（himeko ATK = 1000）
        double breakDamage = Constant.BREAKING_RATE.get(himeko.getLevel()) / 10.0 * 30;
        double expected = (skillDamage + breakDamage) * defenceZone * resistZone;

        Assertions.assertEquals(expected, hpBefore - iceEdge.getCurrentHp(), 1e-6,
                "击破伤害用实际削掉的 30，不是标称的 60");

        // 反向断言：若误用标称值 60，结算值会明显更大——确保本用例真的能失败
        double wrong = (skillDamage + breakDamage * 2) * defenceZone * resistZone;
        Assertions.assertNotEquals(wrong, hpBefore - iceEdge.getCurrentHp(), 1e-6,
                "标称值 60 的结果必须与本实现不同，否则这条测试没有区分力");
    }

    private static Character character(String name) {
        return Character.fromAttributes(name, 10_000, 100, 100, 100);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return new Battle(List.of(hero), List.of(enemy), new Random(0));
    }
}
