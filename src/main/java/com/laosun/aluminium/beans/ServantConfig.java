package com.laosun.aluminium.beans;

import java.util.List;

/**
 * A 忆灵 (memosprite) definition, deserialized from servant_config.json.
 *
 * <p>HP and SPD are defined by formulas referencing the summoner's talent
 * (行迹/天赋) parameters via "#N" placeholders:
 * {@code 忆灵面板 = 继承值 × 召唤者属性 + 基础值}. See
 * {@link com.laosun.aluminium.models.Summon#buildAttributes} for resolution.
 */
public record ServantConfig(Translate name,
                            @com.google.gson.annotations.SerializedName("hp_base") String hpBase,
                            @com.google.gson.annotations.SerializedName("hp_inherit") String hpInherit,
                            @com.google.gson.annotations.SerializedName("speed_base") String speedBase,
                            @com.google.gson.annotations.SerializedName("speed_inherit") String speedInherit,
                            @com.google.gson.annotations.SerializedName("hp_skill") Integer hpSkill,
                            @com.google.gson.annotations.SerializedName("speed_skill") Integer speedSkill,
                            double aggro, List<Integer> skills) {
}
