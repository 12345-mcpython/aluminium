package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.EliteGroup;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;

/**
 * 敌人属性数值公式（P2-3）：把「模板基础值 / 关卡等级组 / 实例自身调整 / 精英组」乘成最终面板。
 *
 * <pre>
 * 敌人属性 = 模板基础值 × 等级组系数 × 实例自身调整 × 精英组系数
 * </pre>
 *
 * <p>这条链与游戏实测对拍过（绝境碎星王虫阶段 1 = 53,099,832，误差 &lt; 0.001%）。
 * 三条容易踩的规则：
 * <ul>
 *   <li><b>血量</b>用 {@code config.hpRatio()}（= tbgd 的 {@code HPModifyRatio}，在本项目数据里叫
 *   {@code health_modify_ratio}），<b>不是</b>那个恒为 1 的幽灵字段 {@code hp_modify_ratio}；</li>
 *   <li><b>效果抵抗是加值</b>：模板值 + 等级组值（冰锋 0.2 + 组1·Lv90 的 0.1 = 0.3 = 30%），相乘会得到 0.02；</li>
 *   <li><b>精英组系数来自波组</b>，不是怪自身；本数据暂无该表，所以由调用方作为参数传入。</li>
 * </ul>
 */
public final class EnemyScaler {

    /**
     * 无精英组加成（系数全 1）——本数据目录还没有 elite_group.json，接表留 P7-4 / P9。
     */
    public static final EliteGroup NO_ELITE_BONUS = new EliteGroup(1, 1, 1, 1, 1);

    private EnemyScaler() {
    }

    /**
     * 按"无精英组加成"缩放。
     */
    public static EnemyStats scale(MonsterTemplate template, MonsterConfig config, HardLevelGroup group) {
        return scale(template, config, group, NO_ELITE_BONUS);
    }

    /**
     * 缩放一个敌人实例的完整面板。
     *
     * @param template 模板基础值（{@code monster_template_config.json}）
     * @param config   实例自身调整系数（{@code monster_config.json}，装载时已补全缺失字段）
     * @param group    关卡等级组系数（{@code hard_level_group.json}，组号与等级都来自关卡）
     * @param elite    精英组别系数（来自波组；无加成传 {@link #NO_ELITE_BONUS}）
     * @return 最终数值面板
     */
    public static EnemyStats scale(MonsterTemplate template, MonsterConfig config, HardLevelGroup group,
                                   EliteGroup elite) {
        return new EnemyStats(
                template.health() * group.health() * config.hpRatio() * elite.healthRatio(),
                template.attack() * group.attack() * config.attackRatio() * elite.attackRatio(),
                template.defence() * group.defence() * config.defenceRatio() * elite.defenceRatio(),
                template.speed() * group.speed() * config.speedRatio() * elite.speedRatio(),
                template.stance() * group.stance() * config.stanceRatio() * elite.stanceRatio(),
                group.effectHitRate(),
                template.effectResistance() + group.effectResistance());
    }
}
