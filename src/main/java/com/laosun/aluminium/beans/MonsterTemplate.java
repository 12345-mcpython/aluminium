package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.DamageElement;

/**
 * Base (unscaled) stats of a monster template, deserialized from
 * {@code monster_template_config.json} (tbgd {@code MonsterTemplateConfig}:
 * {@code HPBase} / {@code AttackBase} / {@code DefenceBase} / …).
 *
 * <p>These are the raw values only. The final panel is
 * {@code 基础值 × 等级组系数 × 实例自身调整 × Π精英组系数}（见 {@code EnemyScaler}）。
 *
 * <p>⚠ 命名：本数据里的 {@code health} 就是 tbgd 的 <b>HP</b>（血量，{@code HPBase}）；
 * 实例侧的对应字段叫 {@code health_modify_ratio}（见 {@link MonsterConfig}）。
 */
public record MonsterTemplate(Translate name,
                              double attack,
                              double defence,
                              double health,
                              double speed,
                              double stance,
                              @SerializedName("stance_count") int stanceCount,
                              @SerializedName("stance_type") DamageElement stanceType,
                              @SerializedName("effect_resistance") double effectResistance) {
}
