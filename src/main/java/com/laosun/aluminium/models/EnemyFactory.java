package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.utils.AttributeBuilder;

import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * 用真实怪物数据造一个面板正确的 {@link Enemy}（P2-4）。
 *
 * <pre>
 * EnemyFactory.create(1002011, 90, 1)
 *   → monster_config[1002011] × monster_template_config[1002011] × hard_level_group[1][90]
 *   → Enemy（属性 / 等级 / 抗性 / 弱点 / 韧性）
 * </pre>
 *
 * <p>本阶段只做**数值**：弱点与韧性只是把数据搬到 {@code Enemy} 上，削韧与击破机制在 P4；
 * 免控（{@code debuff_resistance}）、召唤、多阶段血量分别留给 P6-1 / P9-4 / P9-5。
 *
 * <p>等级与等级组都来自**关卡**（{@code StageConfig}），所以由调用方传入；关卡驱动的部分留 P7-4。
 */
public final class EnemyFactory {

    private EnemyFactory() {
    }

    /**
     * 按「实例 id + 等级 + 等级组」造敌人。
     *
     * @param monsterId      {@code monster_config.json} 的键，例如 1002011（冰锋）
     * @param level          关卡等级
     * @param hardLevelGroup 关卡等级组（怪的 {@code hard_level_group} 通常只是 1，真正决定难度的是关卡）
     * @return 面板正确的敌人（HP 取最终生命上限）
     * @throws IllegalArgumentException 实例 / 模板 / 等级组等级任一不存在
     */
    public static Enemy create(int monsterId, int level, int hardLevelGroup) {
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(monsterId);
        if (config == null) {
            throw new IllegalArgumentException("Unknown monster: " + monsterId);
        }
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(config.templateId());
        if (template == null) {
            throw new IllegalArgumentException("Unknown monster template: " + config.templateId()
                    + " (monster " + monsterId + ")");
        }
        HardLevelGroup group = Constant.HARD_LEVEL_GROUPS.getOrDefault(hardLevelGroup, Map.of()).get(level);
        if (group == null) {
            throw new IllegalArgumentException("No hard level group " + hardLevelGroup + " at level " + level);
        }

        EnemyStats stats = EnemyScaler.scale(template, config, group);
        AttributeBuilder attributes = new AttributeBuilder();
        attributes.setBase(HEALTH, stats.hp())
                .setBase(ATTACK, stats.attack())
                .setBase(DEFENCE, stats.defence())
                .setBase(SPEED, stats.speed())
                .setBase(EFFECT_RESISTANCE, stats.effectResistance());

        Enemy enemy = new Enemy(displayName(config, monsterId), attributes.build());
        enemy.setLevel(level);                                  // P1-4：等级进防御区
        enemy.setDamageResist(config.damageResistance());       // P1-6：抗性区直接生效
        enemy.setStanceWeak(Set.copyOf(config.stanceWeak()));   // P2-2：弱点（P4 削韧判定用）
        enemy.setStance(stats.stance());
        enemy.setMaxStance(stats.stance());
        enemy.setStanceCount(template.stanceCount());
        enemy.setStanceType(template.stanceType());
        return enemy;
    }

    private static String displayName(MonsterConfig config, int monsterId) {
        return config.name() == null ? "Monster#" + monsterId : config.name().chinese();
    }
}
