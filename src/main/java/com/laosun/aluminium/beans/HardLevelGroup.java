package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

/**
 * Level-based enemy scaling, deserialized from {@code hard_level_group.json}:
 * {@code {组号: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}}.
 *
 * <p>⚠ 前五项是<b>乘区系数</b>（乘在模板基础值上）；后两项是<b>加值</b>——{@code effect_resistance}
 * 与模板的 {@code effect_resistance} <b>相加</b>：冰锋 0.2 + 组1·Lv90 的 0.1 = 0.3（30%），
 * 对上 HSR.md §1.2「90 级 30%~40%」；若当成系数相乘会得到 0.02（错）。
 *
 * <p>⚠ 组号与等级都来自<b>关卡</b>（StageConfig），不是怪自身的 {@code hard_level_group}（那通常是 1）。
 *
 * <p>⚠ 字段名必须用 {@code @SerializedName}：JSON 键是 {@code attack} 而不是 {@code attackRatio}，
 * 否则 Gson 会把所有系数读成 0。
 */
public record HardLevelGroup(double attack,
                             double defence,
                             double health,
                             double speed,
                             double stance,
                             @SerializedName("effect_hit_rate") double effectHitRate,
                             @SerializedName("effect_resistance") double effectResistance) {
}
