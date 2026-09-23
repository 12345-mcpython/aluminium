package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

/**
 * Level-based enemy scaling, deserialized from {@code hard_level_group.json}:
 * {@code {组号: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}}.
 *
 * <p>⚠ The first five entries are <b>zone multipliers</b> (multiplied onto the template's base
 * values); the last two are <b>additive values</b> — {@code effect_resistance} is <b>added</b> to the
 * template's {@code effect_resistance}: Ice Edge (冰锋) 0.2 + group 1·Lv90's 0.1 = 0.3 (30%), which
 * matches HSR.md §1.2 "30%~40% at level 90"; if you mistakenly treat it as a multiplier, you get
 * 0.02 (wrong).
 *
 * <p>⚠ Both the group number (组号) and the level (等级) come from the <b>stage</b> (StageConfig),
 * not from the monster's own {@code hard_level_group} (which is usually 1).
 *
 * <p>⚠ The field names MUST use {@code @SerializedName}: the JSON key is {@code attack} and not
 * {@code attackRatio}, otherwise Gson reads every multiplier as 0.
 */
public record HardLevelGroup(double attack,
                             double defence,
                             double health,
                             double speed,
                             double stance,
                             @SerializedName("effect_hit_rate") double effectHitRate,
                             @SerializedName("effect_resistance") double effectResistance) {
}
