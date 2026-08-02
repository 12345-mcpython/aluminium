package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

/**
 * Hard-level enemy scaling ratios including effect hit rate and resistance.
 *
 * <p>Deserialized from hard_level_group.json. Extends the basic scaling
 * ratios with additional effect-related multipliers.
 */
public record HardLevelGroup(@SerializedName("attack") double attackRatio,
                             @SerializedName("defence") double defenceRatio,
                             @SerializedName("health") double healthRatio,
                             @SerializedName("speed") double speedRatio,
                             @SerializedName("stance") double stanceRatio,
                             @SerializedName("effect_hit_rate") double effectHitRateRatio,
                             @SerializedName("effect_resistance") double effectResistanceRatio) {
}
