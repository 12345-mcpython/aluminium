package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EliteGroup;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.models.EnemyScaler;
import com.laosun.aluminium.models.EnemyStats;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P2-3 acceptance: 敌人属性 = 模板基础值 × 等级组系数 × 实例自身调整 × 精英组系数。
 *
 * <p>锚点：冰锋（1002011）在组1·Lv90 的面板；以及会话里与游戏实测对拍过的绝境碎星王虫
 * （802501003 × 组3·Lv120 × 精英组 6.2 = 53,099,832）。
 */
public class EnemyScalerTest {
    private static final double EPS = 1e-9;

    private static EnemyStats iceEdgeLv90() {
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(1002011);
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(1002011);
        HardLevelGroup group = Constant.HARD_LEVEL_GROUPS.get(1).get(90);
        return EnemyScaler.scale(template, config, group);
    }

    @Test
    public void iceEdgeAtLevel90() {
        EnemyStats stats = iceEdgeLv90();

        // 69.75 × 236.53471
        Assertions.assertEquals(16498.296, stats.hp(), 1e-3);
        // 18 × 36.821384
        Assertions.assertEquals(662.784912, stats.attack(), 1e-6);
        // 210 × 5.238095 ≈ 1100 = 200 + 10 × 90（数据精度使得它略小于 1100）
        Assertions.assertEquals(1099.99995, stats.defence(), 1e-4);
        // 100 × 1.32
        Assertions.assertEquals(132, stats.speed(), EPS);
        // 60 × 1 × 1
        Assertions.assertEquals(60, stats.stance(), EPS);
        Assertions.assertEquals(0.32, stats.effectHitRate(), EPS);
    }

    @Test
    public void effectResistanceIsAddedNotMultiplied() {
        // 模板 0.2 + 等级组 0.1 = 0.3（30%，对得上 HSR.md §1.2）；相乘会得到 0.02
        Assertions.assertEquals(0.3, iceEdgeLv90().effectResistance(), 1e-9);
    }

    @Test
    public void defaultOverloadEqualsNoEliteBonus() {
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(1002011);
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(1002011);
        HardLevelGroup group = Constant.HARD_LEVEL_GROUPS.get(1).get(90);

        Assertions.assertEquals(
                EnemyScaler.scale(template, config, group, EnemyScaler.NO_ELITE_BONUS),
                EnemyScaler.scale(template, config, group));
    }

    @Test
    public void peakBossHpMatchesTheMeasuredValue() {
        // 802501003 = 绝境「将杀王棋」碎星王虫（拟造）：模板 8025010、血量系数 1.979167、
        // 组3·Lv120（血量系数 1938.7634）、波组精英组 InfiniteEliteGroup 369 的 HPRatio 6.2。
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(8025010);
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(802501003);
        HardLevelGroup group = Constant.HARD_LEVEL_GROUPS.get(3).get(120);

        EnemyStats withoutElite = EnemyScaler.scale(template, config, group);
        // 2232 × 1938.7634 × 1.979167 ≈ 8,564,489（会话里 53,099,832 / 6.2 反推值）
        Assertions.assertEquals(8564489, withoutElite.hp(), 1.0);

        // 精英组已作为参数暴露 → 现在就能断到会话对拍过的实测值
        EliteGroup infiniteElite369 = new EliteGroup(6.2, 1.1, 1, 1, 1);
        EnemyStats withElite = EnemyScaler.scale(template, config, group, infiniteElite369);
        Assertions.assertEquals(53099832, withElite.hp(), 5.0);
    }
}
