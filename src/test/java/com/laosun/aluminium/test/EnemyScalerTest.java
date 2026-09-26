package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EliteGroup;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.models.enemy.EnemyScaler;
import com.laosun.aluminium.models.enemy.EnemyStats;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P2-3 acceptance: enemy attribute = template base value × level group multiplier × per-instance
 * adjustment × elite group multiplier.
 *
 * <p>Anchors: Ice Edge (冰锋) (1002011)'s stat sheet at group 1 · Lv90; and the Despair Starcrusher
 * Swarm King (绝境碎星王虫) cross-checked against in-game measurements in the session
 * (802501003 × group 3 · Lv120 × elite group 6.2 = 53,099,832).
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
        // 210 × 5.238095 ≈ 1100 = 200 + 10 × 90 (data precision makes it slightly less than 1100)
        Assertions.assertEquals(1099.99995, stats.defence(), 1e-4);
        // 100 × 1.32
        Assertions.assertEquals(132, stats.speed(), EPS);
        // 60 × 1 × 1
        Assertions.assertEquals(60, stats.stance(), EPS);
        Assertions.assertEquals(0.32, stats.effectHitRate(), EPS);
    }

    @Test
    public void effectResistanceIsAddedNotMultiplied() {
        // template 0.2 + level group 0.1 = 0.3 (30%, matches HSR.md §1.2); multiplying would give 0.02
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
        // 802501003 = Despair 「将杀王棋」 Starcrusher Swarm King (Simulated): template 8025010,
        // HP multiplier 1.979167, group 3 · Lv120 (HP multiplier 1938.7634), wave-group elite group
        // InfiniteEliteGroup 369's HPRatio 6.2.
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(8025010);
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(802501003);
        HardLevelGroup group = Constant.HARD_LEVEL_GROUPS.get(3).get(120);

        EnemyStats withoutElite = EnemyScaler.scale(template, config, group);
        // 2232 × 1938.7634 × 1.979167 ≈ 8,564,489 (back-derived in the session as 53,099,832 / 6.2)
        Assertions.assertEquals(8564489, withoutElite.hp(), 1.0);

        // the elite group is already exposed as a parameter → the measured value cross-checked in the session can be asserted right now
        EliteGroup infiniteElite369 = new EliteGroup(6.2, 1.1, 1, 1, 1);
        EnemyStats withElite = EnemyScaler.scale(template, config, group, infiniteElite369);
        Assertions.assertEquals(53099832, withElite.hp(), 5.0);
    }
}
