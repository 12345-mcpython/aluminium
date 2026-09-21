package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.DamageElement;

/**
 * 单个敌人技能（{@code enemy_skills.json} 的值）—— P5-3 自建。
 *
 * <p><b>为什么自建</b>：本项目的游戏数据里没有敌人技能表。{@code skills.json} 只有角色技能；
 * tbgd 的下发数据里也没有怪物技能倍率。所以这张表的**倍率是猜的**（每条都带 {@link #guessed()} 标记），
 * 目的只是让敌人"会打人、数值量级合理"。找到源数据后（P9-1/P9-2）只替换数据文件与
 * {@code Constant} 的加载，引擎侧不用改。
 *
 * @param id         技能 id（唯一，留作 P9 接真实技能表时的键）
 * @param name       显示名
 * @param element    伤害元素；{@code null} = 用怪物自身的 {@code stance_type}（模板的韧性属性）
 * @param multiplier 倍率：{@code base = 敌人攻击力 × multiplier}
 * @param hits       段数（每段独立结算、独立判定暴击）
 * @param damageType 伤害类型字符串（见 {@code DamageType.fromString}）；{@code null} = normal
 * @param guessed    倍率是否为猜测值
 */
public record EnemySkillData(int id,
                             Translate name,
                             DamageElement element,
                             double multiplier,
                             int hits,
                             @SerializedName("damage_type") String damageType,
                             boolean guessed) {
}
