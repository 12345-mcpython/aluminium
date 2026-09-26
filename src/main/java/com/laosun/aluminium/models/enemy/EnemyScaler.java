package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.beans.EliteGroup;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;

/**
 * Enemy attribute value formula (P2-3): multiplies "template base value / stage level group /
 * per-instance adjustment / elite group" into the final stat sheet.
 *
 * <pre>
 * enemy attribute = template base value × level group multiplier × per-instance adjustment × elite group multiplier
 * </pre>
 *
 * <p>This chain has been cross-checked against in-game measurements (Despair Starcrusher Swarm King
 * phase 1 = 53,099,832, error &lt; 0.001%). Three rules that are easy to trip over:
 * <ul>
 *   <li><b>HP</b> uses {@code config.hpRatio()} (= tbgd's {@code HPModifyRatio}, called
 *   {@code health_modify_ratio} in this project's data), <b>NOT</b> that ghost field
 *   {@code hp_modify_ratio} which is always 1;</li>
 *   <li><b>Effect RES is additive</b>: template value + level group value (Ice Edge (冰锋) 0.2 +
 *   group 1 · Lv90's 0.1 = 0.3 = 30%); multiplying would give 0.02;</li>
 *   <li><b>The elite group multiplier comes from the wave group</b>, not from the monster itself;
 *   this dataset has no such table yet, so it is passed in by the caller as a parameter.</li>
 * </ul>
 */
public final class EnemyScaler {

    /**
     * No elite group bonus (all multipliers 1) — this data directory has no elite_group.json yet;
     * wiring the table up is left to P7-4 / P9.
     */
    public static final EliteGroup NO_ELITE_BONUS = new EliteGroup(1, 1, 1, 1, 1);

    private EnemyScaler() {
    }

    /**
     * Scales as if there were "no elite group bonus".
     */
    public static EnemyStats scale(MonsterTemplate template, MonsterConfig config, HardLevelGroup group) {
        return scale(template, config, group, NO_ELITE_BONUS);
    }

    /**
     * Scales the complete stat sheet of one enemy instance.
     *
     * @param template template base value ({@code monster_template_config.json})
     * @param config   per-instance adjustment multipliers ({@code monster_config.json}; missing fields
     *                 are already filled in on load)
     * @param group    stage level group multipliers ({@code hard_level_group.json}; both the group
     *                 number and the level come from the stage)
     * @param elite    elite group multipliers (from the wave group; pass {@link #NO_ELITE_BONUS} when
     *                 there is no bonus)
     * @return the final stat sheet
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
