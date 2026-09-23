package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.DamageElement;

import java.util.List;
import java.util.Map;

/**
 * Per-instance monster data, deserialized from {@code monster_config.json}
 * (tbgd {@code MonsterConfig}) — the "difficulty knob" layer on top of
 * {@link MonsterTemplate}: {@code instance multiplier × level-group multiplier × template base value}.
 *
 * <p><b>⚠ The HP-field trap</b> (verified entry by entry against the original tbgd): tbgd calls its HP
 * modifier {@code HPModifyRatio}, which in this file is <b>{@code health_modify_ratio}</b> (i.e.
 * {@link #hpRatio()}); the {@code hp_modify_ratio} in the same file is a <b>ghost field</b> left over
 * from the parser (tbgd has no {@code HealthModifyRatio}), it is always the default 1, and you must
 * <b>never use it</b>. Example: 100201101 has a real HP multiplier of 0.266667, while the ghost field is 1.
 *
 * <p><b>⚠ Missing field</b>: this data has no attack modifier (tbgd's {@code AttackModifyRatio}, which
 * is ≠ 1 for 444 of the 2649 monsters); {@link com.laosun.aluminium.Constant} merges it in from the
 * patch file {@code monster_attack_modify_ratio.json}; all the multipliers are filled in uniformly in
 * {@code Constant}, so a loaded instance is never {@code null}.
 *
 * <p>Mechanic fields: {@code debuff_resistance} is wired up in P6-1 (example: Ice Edge (冰锋)
 * {@code {"STAT_CTRL_Frozen": 1}} = fully immune to Frozen); {@code summon_id} is left for P9-4.
 */
public record MonsterConfig(Translate name,
                            @SerializedName("template_id") int templateId,
                            @SerializedName("elite_group") int eliteGroup,
                            @SerializedName("hard_level_group") int hardLevelGroup,
                            @SerializedName("stance_weak") List<DamageElement> stanceWeak,
                            @SerializedName("health_modify_ratio") Double hpRatio,
                            @SerializedName("attack_modify_ratio") Double attackRatio,
                            @SerializedName("defence_modify_ratio") Double defenceRatio,
                            @SerializedName("speed_modify_ratio") Double speedRatio,
                            @SerializedName("stance_modify_ratio") Double stanceRatio,
                            @SerializedName("damage_resistance") Map<DamageElement, Double> damageResistance,
                            @SerializedName("debuff_resistance") Map<String, Double> debuffResistance) {
}
