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
 * {@code {"STAT_CTRL_Frozen": 1}} = fully immune to Frozen). {@code summon_id} (P9-4) is the monster's
 * <b>summon roster</b> — see the note below.
 *
 * <p><b>The summon roster</b> ({@code summon_id} → {@link #summonIds()}): the ids of the monsters this one
 * may bring onto the field, in the order the data lists them. It is a <b>roster, not a trigger</b>: it says
 * "this monster's summons are 银鬃近卫 ×2" and deliberately says nothing about <em>when</em> they appear.
 * When stays a decision for the caller ({@code Battle.summon}), the same way {@link #hpRatio} and the phase
 * table keep "how strong" separate from "which skill". 692 of the 2649 monsters have a non-empty roster
 * (1450 references to 556 distinct monsters); 银鬃尉官 1003010 → 1002040 ×2 is a typical one.
 *
 * <p>⚠ <b>A {@code 0} entry means "no summon", and it is really in the data</b> — one monster (405301004)
 * carries exactly {@code [0]}. Feeding that id to the factory would look up monster 0 and fail loudly, which
 * is at least visible; adding a "just skip unknown ids" fallback would instead turn it into a silently
 * absent summon. {@code Constant.normalizeMonsterConfigs} therefore drops non-positive entries once, at
 * load time, and {@code SummonTest} pins the counting convention.
 *
 * <p>Never {@code null} once loaded (the normaliser also replaces a missing field with an empty list), so
 * consumers may iterate it directly.
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
                            @SerializedName("debuff_resistance") Map<String, Double> debuffResistance,
                            @SerializedName("summon_id") List<Integer> summonIds) {
}
