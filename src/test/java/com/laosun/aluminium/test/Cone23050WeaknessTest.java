package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 光锥 23050 随心：`装备者为敌方目标添加弱点时，恢复 1 个战技点，该效果最多触发 1 次，施放终结技时重置可触发次数`。
 *
 * <p>⚠ 这个判据**不搭夹具**：让**两个真内容互相作用** —— 角色 `1315` 的本体规则在终结技时给目标加物理弱点
 * （本段第 12 件 `ADD_ELEMENTAL_WEAKNESS` 的读者），而 `23050` 那条规则对"加了弱点"这件事作出反应。
 * ⚠ 早先的写法用 `setTriggerTable` 塞了一条探针，⚠ **那一行把光锥自己的表顶掉了**，于是判据其实在测
 * "装了 23050 但没有 23050 的规则" —— 实测 `before=2 after=2`，而弱点确实加上了。
 *
 * <p>⚠ 两条前置都必须断言，否则判据会绿着而什么都没测：① 满能量（`castUltra` 在不满时**直接返回 false**）；
 * ② 战技点在上限以下（策略是 start 3 / max 5，满了 `gainSkillPoint(1)` 会被**静默截断**）。
 */
public class Cone23050WeaknessTest {
    private static final int WEARER = 1315;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    /** 装 23050（阶 1）的 1315，面对一个敌人；返回 {battle, wearer, enemy}。 */
    private static Object[] scene() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23050, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Object[]{battle, wearer, enemy};
    }

    /** 放一次终结技（先灌满能量），返回 {战技点前, 战技点后}。 */
    private static int[] castOnce(Battle battle, Character wearer, Enemy enemy) {
        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        battle.spendSkillPoint();                     // ⚠ 腾出空间：满了就什么都测不到
        int before = battle.getSkillPoints();
        Assertions.assertTrue(before < battle.getSkillPointMax(),
                "the judge needs room below the cap: " + before + "/" + battle.getSkillPointMax());
        boolean cast = battle.castUltra(wearer, List.of(enemy));
        Assertions.assertTrue(cast, "the ultimate must be cast at full energy");
        return new int[]{before, battle.getSkillPoints()};
    }

    @Test
    public void theUltimateInsertsAWeaknessAndTheConePaysOnePoint() {
        Object[] s = scene();
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.PHYSICAL),
                "the enemy must not already have Physical, or 1315 inserts nothing");

        int[] p = castOnce(battle, wearer, enemy);
        System.out.println("[23050] first ult: points " + p[0] + " -> " + p[1]
                + " ; physical=" + enemy.isWeakTo(DamageElement.PHYSICAL));
        Assertions.assertTrue(enemy.isWeakTo(DamageElement.PHYSICAL), "1315's own rule must have inserted it");
        Assertions.assertEquals(1, p[1] - p[0], "a real insertion grants exactly one skill point");
    }

    @Test
    public void aSecondUltimateOnTheSameWeaknessPaysNothing() {
        Object[] s = scene();
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];

        int[] first = castOnce(battle, wearer, enemy);
        int[] second = castOnce(battle, wearer, enemy);       // ⚠ 同一属性，敌人已经有了
        System.out.println("[23050] second ult: first=" + (first[1] - first[0])
                + " second=" + (second[1] - second[0]) + " ; physical=" + enemy.isWeakTo(DamageElement.PHYSICAL));
        Assertions.assertEquals(1, first[1] - first[0], "the first insertion pays");
        Assertions.assertEquals(0, second[1] - second[0],
                "WEAKNESS_ADDED fires only when an insertion actually inserts, so the second pays nothing");
    }
}
