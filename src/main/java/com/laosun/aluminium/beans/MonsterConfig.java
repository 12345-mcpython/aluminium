package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.DamageElement;

import java.util.List;
import java.util.Map;

/**
 * Per-instance monster data, deserialized from {@code monster_config.json}
 * (tbgd {@code MonsterConfig}) — the "difficulty knob" layer on top of
 * {@link MonsterTemplate}: {@code 实例系数 × 等级组系数 × 模板基础值}.
 *
 * <p><b>⚠ 血量字段陷阱</b>（已对原始 tbgd 逐条核实）：tbgd 的血量修正叫 {@code HPModifyRatio}，
 * 在本文件里是 <b>{@code health_modify_ratio}</b>（即 {@link #hpRatio()}）；同文件里那个
 * {@code hp_modify_ratio} 是解析器遗留的<b>幽灵字段</b>（tbgd 没有 {@code HealthModifyRatio}），
 * 恒为默认值 1，<b>永远不要用它</b>。例：100201101 真实血量系数 0.266667，而幽灵字段是 1。
 *
 * <p><b>⚠ 缺失字段</b>：本数据没有攻击修正（tbgd 的 {@code AttackModifyRatio}，2649 个怪里 444 个 ≠ 1），
 * 由 {@link com.laosun.aluminium.Constant} 从补丁文件 {@code monster_attack_modify_ratio.json} 合并；
 * 各系数在 {@code Constant} 里统一补全，装载后的实例不会是 {@code null}。
 *
 * <p>机制字段：{@code debuff_resistance} P6-1 接（例：冰锋 {@code {"STAT_CTRL_Frozen": 1}} =
 * 完全免疫冻结）；{@code summon_id} 留给 P9-4。
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
