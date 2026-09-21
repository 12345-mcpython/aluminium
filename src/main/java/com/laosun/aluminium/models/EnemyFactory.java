package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EnemySkillData;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
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
                .setBase(EFFECT_RESISTANCE, stats.effectResistance())
                // P6-1：效果命中必须落进面板，否则敌人的命中恒为 0（EnemyStats 里算了却没人用）
                .setBase(EFFECT_HIT_RATE, stats.effectHitRate());

        Enemy enemy = new Enemy(displayName(config, monsterId), attributes.build());
        enemy.setLevel(level);                                  // P1-4：等级进防御区
        enemy.setDamageResist(config.damageResistance());       // P1-6：抗性区直接生效
        enemy.setDebuffResist(config.debuffResistance());        // P6-1：特定负面效果抵抗
        enemy.setStanceWeak(Set.copyOf(config.stanceWeak()));   // P2-2：弱点（P4 削韧判定用）
        enemy.setStance(stats.stance());
        enemy.setMaxStance(stats.stance());
        enemy.setStanceCount(template.stanceCount());
        enemy.setStanceType(template.stanceType());
        installEnemySkill(enemy, monsterId, template);
        return enemy;
    }

    /**
     * 给敌人装上它的普攻（P5-3）。
     *
     * <p>技能来自 {@code enemy_skills.json}（{@link Constant#ENEMY_SKILLS}）。
     * 该表**只覆盖了演示用的几只怪**，其余怪物没有条目 —— 这时退回一个兜底普攻：
     * 倍率 1.0、单段、元素取自身的 {@code stance_type}。这样"任何怪都能打人"，
     * 不会因为没数据就站着不动。
     *
     * <p>元素解析顺序：表里的 {@code element} → 怪物自身的 {@code stance_type} → 物理
     * （{@code stance_type} 也可能是 null，数据里确有这种条目）。
     *
     * <p>⚠ 倍率是猜的，见 {@link com.laosun.aluminium.beans.EnemySkillData}。
     */
    private static void installEnemySkill(Enemy enemy, int monsterId, MonsterTemplate template) {
        EnemySkillData data = Constant.ENEMY_SKILLS.get(monsterId);
        DamageElement element = data == null ? null : data.element();
        if (element == null) {
            element = template.stanceType() == null ? null : template.stanceType();
        }
        if (element == null) {
            element = DamageElement.PHYSICAL;
        }
        DamageType type = data == null || data.damageType() == null
                ? DamageType.NORMAL
                : DamageType.fromString(data.damageType());
        double multiplier = data == null ? 1.0 : data.multiplier();
        int hits = data == null ? 1 : data.hits();
        enemy.setSkill(SkillType.COMMON, new EnemySkill(element, multiplier, hits, type));
    }

    private static String displayName(MonsterConfig config, int monsterId) {
        return config.name() == null ? "Monster#" + monsterId : config.name().chinese();
    }
}
