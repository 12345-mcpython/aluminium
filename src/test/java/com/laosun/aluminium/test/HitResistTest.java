package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.buffs.StunBuff;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P6-1 验收：效果命中与抵抗。
 *
 * <pre>
 * 生效概率 = 基础概率 × (1 + 施加方命中) × (1 - 受击方抵抗) × (1 - 特定负面效果抵抗)，clamp [0,1]
 * </pre>
 *
 * <p>三个因子**都是乘算**（不是"命中减抵抗"）。锚点（ROADMAP）：
 * base 1.0 + 命中 0 + 抵抗 0.3 → 0.7；命中 0.5 时 → 1.0（clamp）；
 * base 0.8 + 命中 0.25 + 抵抗 0.2 → 0.8。
 */
public class HitResistTest {
    private static final double EPS = 1e-9;

    @Test
    public void chanceIsBaseTimesHitTimesResist() {
        Character caster = caster(0, 0);                 // 命中 0
        Enemy target = enemyWithResist(0.3, null);       // 抵抗 0.3

        Assertions.assertEquals(0.7, newBattle(caster, target).hitChance(caster, target, 1.0, null), EPS,
                "1.0 × (1+0) × (1-0.3) = 0.7");
    }

    @Test
    public void chanceIsClampedToOne() {
        Character caster = caster(0.5, 0);
        Enemy target = enemyWithResist(0.3, null);

        Assertions.assertEquals(1.0, newBattle(caster, target).hitChance(caster, target, 1.0, null), EPS,
                "1.0 × 1.5 × 0.7 = 1.05 → clamp 到 1.0");
    }

    @Test
    public void baseBelowOneMultipliesBothFactors() {
        Character caster = caster(0.25, 0);
        Enemy target = enemyWithResist(0.2, null);

        Assertions.assertEquals(0.8, newBattle(caster, target).hitChance(caster, target, 0.8, null), EPS,
                "0.8 × 1.25 × 0.8 = 0.8");
    }

    /**
     * 特定负面效果抵抗：冰锋的 {@code debuff_resistance = {"STAT_CTRL_Frozen": 1}}
     * → 冻结完全免疫（概率 0），其余效果不受影响。
     */
    @Test
    public void specificResistanceCanNullifyTheChance() {
        Character caster = caster(0, 0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);

        Assertions.assertEquals(1.0, iceEdge.getDebuffResist().get("STAT_CTRL_Frozen"), EPS,
                "冰锋数据里就是 100% 抵抗冻结");
        Assertions.assertEquals(0.0,
                newBattle(caster, iceEdge).hitChance(caster, iceEdge, 1.0, "STAT_CTRL_Frozen"), EPS,
                "特定抵抗 1.0 → 关键因子 (1-1) = 0 → 完全免疫");

        // 注意冰锋自身还有 30% 效果抵抗（模板 0.2 + 等级组 0.1），所以"不受特定抵抗影响"
        // ≠ 概率 1.0，而是 1.0 × (1 - 0.3) = 0.7
        Assertions.assertEquals(0.7,
                newBattle(caster, iceEdge).hitChance(caster, iceEdge, 1.0, "STAT_DOT_Burn"), EPS,
                "没配的键按 0 算 → 只剩通用的 30% 效果抵抗");
        Assertions.assertEquals(0.7,
                newBattle(caster, iceEdge).hitChance(caster, iceEdge, 1.0, null), EPS,
                "不指定键就不查特定抵抗 → 同上");
    }

    /**
     * 角色的效果抵抗走面板（{@code EFFECT_RESISTANCE}），且**没有**特定抵抗表。
     */
    @Test
    public void characterUsesPanelResistanceOnly() {
        Character caster = caster(0, 0);
        Character victim = Character.fromAttributes("victim", 10_000, 100, 100, 100);
        victim.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0.4));

        Assertions.assertEquals(0.6,
                newBattle(caster, victim).hitChance(caster, victim, 1.0, "STAT_CTRL_Frozen"), EPS,
                "角色没有特定抵抗表，只有面板抵抗 0.4");
    }

    /**
     * 敌人的效果命中**必须落进面板**：{@code EnemyScaler} 算出了 0.32（组1·Lv90），
     * 但早期 {@code EnemyFactory} 漏了往面板写，敌人命中恒为 0（审查报告 M-5）。
     */
    @Test
    public void enemyEffectHitRateReachesThePanel() {
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);

        Assertions.assertEquals(0.32, iceEdge.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                "组1·Lv90 的效果命中 = 0.32");
        Assertions.assertEquals(0.3, iceEdge.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "效果抵抗是加值：模板 0.2 + 等级组 0.1（P2-3）");
        // 敌人当施加者时，0.32 的命中真的会放大概率
        Character victim = Character.fromAttributes("victim", 10_000, 100, 100, 100);
        Assertions.assertEquals(1.0, newBattle(iceEdge, victim).hitChance(iceEdge, victim, 1.0, null), EPS);
        Assertions.assertEquals(0.66, newBattle(iceEdge, victim).hitChance(iceEdge, victim, 0.5, null), 1e-9,
                "0.5 × 1.32 = 0.66");
    }

    /**
     * {@code tryApplyDebuff}：命中才挂上；被完全免疫时不会挂上。
     */
    @Test
    public void tryApplyDebuffGatesOnTheRoll() {
        // 必定命中：命中 0 抵抗 0 基础 1.0 → 概率 1.0（rng.nextDouble() < 1 恒真）
        Character caster = caster(0, 0);
        Enemy target = enemyWithResist(0, null);
        Battle battle = newBattle(caster, target);

        Assertions.assertTrue(battle.tryApplyDebuff(caster, target, new StunBuff(2), 1.0, null),
                "概率 1.0 → 必定挂上");
        Assertions.assertTrue(target.getBuffManager().hasBuff(StunBuff.class));

        // 必定失败：冰锋免疫冻结 → 概率 0.0
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Battle battle2 = newBattle(caster, iceEdge);
        Assertions.assertFalse(battle2.tryApplyDebuff(caster, iceEdge, new StunBuff(2), 1.0, "STAT_CTRL_Frozen"),
                "概率 0.0 → 挂不上");
        Assertions.assertFalse(iceEdge.getBuffManager().hasBuff(StunBuff.class));
    }

    @Test
    public void rollIsReproducibleWithASeed() {
        Character caster = caster(0, 0);
        Enemy target = enemyWithResist(0.5, null);

        // 同一 seed → 同一串结果
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        Battle first = new Battle(List.of(caster), List.of(target), new Random(7));
        Battle second = new Battle(List.of(caster), List.of(target), new Random(7));
        for (int i = 0; i < 32; i++) {
            a.append(first.rollDebuff(caster, target, 0.5, null) ? '1' : '0');
            b.append(second.rollDebuff(caster, target, 0.5, null) ? '1' : '0');
        }
        Assertions.assertEquals(a.toString(), b.toString(), "注入同种子的 Random → 可复现");
        Assertions.assertTrue(a.toString().contains("1") && a.toString().contains("0"),
                "0.5 概率下 32 次里两种结果都该出现");
    }

    // ==================================================================

    private static Character caster(double hitRate, double resist) {
        Character c = Character.fromAttributes("caster", 10_000, 100, 100, 100);
        c.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(hitRate));
        c.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(resist));
        return c;
    }

    /** 一个自造敌人：只设效果抵抗与（可选的）特定抵抗。 */
    private static Enemy enemyWithResist(double resist, String specificKey) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, 10_000)
                .setBase(AttributeType.DEFENCE, 100)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, 100)
                .setBase(AttributeType.EFFECT_RESISTANCE, resist);
        Enemy enemy = new Enemy("dummy", builder.build());
        if (specificKey != null) {
            enemy.setDebuffResist(java.util.Map.of(specificKey, 1.0));
        }
        return enemy;
    }

    private static Battle newBattle(CanHit... combatants) {
        List<Character> characters = new java.util.ArrayList<>();
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (CanHit c : combatants) {
            if (c.getCamp() == Camp.PLAYER) {
                characters.add((Character) c);
            } else {
                enemies.add((Enemy) c);
            }
        }
        if (characters.isEmpty()) {
            characters.add(Character.fromAttributes("filler", 1000, 100, 100, 100));
        }
        if (enemies.isEmpty()) {
            enemies.add(enemyWithResist(0, null));
        }
        return new Battle(characters, enemies, new Random(20260919));
    }
}
