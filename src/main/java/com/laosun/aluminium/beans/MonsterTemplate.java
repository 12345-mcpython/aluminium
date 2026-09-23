package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.DamageElement;

/**
 * Base (unscaled) stats of a monster template, deserialized from
 * {@code monster_template_config.json} (tbgd {@code MonsterTemplateConfig}:
 * {@code HPBase} / {@code AttackBase} / {@code DefenceBase} / …).
 *
 * <p>These are the raw values only. The final panel is
 * {@code base value × level-group coefficient × instance's own adjustment × Π elite-group coefficients} (see {@code EnemyScaler}).
 *
 * <p>⚠ Naming: {@code health} in this data is tbgd's <b>HP</b> (health, {@code HPBase});
 * the corresponding field on the instance side is called {@code health_modify_ratio}
 * (see {@link MonsterConfig}).
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
