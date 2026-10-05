package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 光锥 23062 随心，第 4 句：`每消耗 1 点能量值，使本次造成的终结技伤害提高 #3%，最多 #6%`。
 *
 * <p>Note: 为什么是单元级：终结技只在满能量时可放（`Battle.castUltra` 第二行的 `isUltraReady`，实测 10 点能量
 * 直接 `return false`），所以"两种消耗"在端到端里造不出来。这里手工造 `Damage`、直接设实例上的
 * `castEnergySpent`，属性加成在比值里约掉，剩下的就是那条从句。
 *
 * <p>Note: 四个对照上的坑，全是实测踩出来的：
 * <ol>
 *   <li>"装 / 不装"不行 - - 那测到的是光锥的 ATK +18%（实测 1.088），拆掉引擎依然全绿；</li>
 *   <li>清空 `when` 不行 -  - 当时毫无变化，但那一次实验里同时有"写入从未发生"，两个变量一起失效，结论无效；</li>
 *   <li>"能量 0"不行 -  - `isUltraReady` 直接 `return false`，实验会在一个没被检查的 false 上跑完；</li>
 *   <li>Note: 手工 `Damage` 必须带 ULTRA 类别 -  - 否则 `from_skill ULTRA` 连匹配都不匹配，
 *       改与不改 `castEnergySpent` 得到同一个数（实测 550.099…）。</li>
 * </ol>
 */
public class Cone23062SpendTest {
    private static final int LEVEL = 80;
    private static final int RANK = 1;
    private static final int WEARER = 1003;
    private static final int MONSTER = 1002011;
    private static final double PER_POINT = 0.002;
    private static final double CAP = 0.72;
    private static final double CAP_POINTS = 360;

    /** 同一把光锥、同一件事，只改"本次消耗"；返回伤害。 */
    private static double damageWithSpend(double spend) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true,
                Weapon.build(23062, LEVEL, false, RANK));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        Damage hit = new Damage(enemy, wearer, DamageElement.FIRE, DamageType.NORMAL, 1000,
                SkillCategory.ULTRA);
        hit.withCastEnergySpent(spend);
        return battle.applyDamage(enemy, hit);
    }

    @Test
    public void theUltimateGainsPerPointOfEnergySpent() {
        double at50 = damageWithSpend(50);
        double at120 = damageWithSpend(120);
        Assertions.assertTrue(at50 > 0 && at120 > 0, "both hits must land");
        double ratio = at120 / at50;
        double expected = (1 + 120 * PER_POINT) / (1 + 50 * PER_POINT);
        System.out.println("[23062] at50=" + at50 + " at120=" + at120
                + " ratio=" + ratio + " expected=" + expected);
        Assertions.assertEquals(expected, ratio, 1e-4,
                "per " + PER_POINT + " per point: 120 vs 50 points");
    }

    @Test
    public void theBonusIsCappedAbsolutely() {
        double huge = damageWithSpend(1_000_000);
        double atCap = damageWithSpend(CAP_POINTS);
        System.out.println("[23062] huge=" + huge + " atCap=" + atCap
                + " (cap " + CAP + " needs " + CAP_POINTS + " points)");
        Assertions.assertEquals(atCap, huge, 1e-6,
                "beyond " + CAP_POINTS + " points the boost stops growing at " + CAP);
    }
}
