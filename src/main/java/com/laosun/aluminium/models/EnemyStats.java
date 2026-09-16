package com.laosun.aluminium.models;

/**
 * 一个敌人实例的最终数值面板（{@code EnemyScaler} 的输出）。
 *
 * <p>纯数值、不依赖 {@link Enemy}，便于单独对拍数据公式（见 {@code EnemyScalerTest}）。
 * 弱点与抗性不在这里——它们不参与数值缩放，由 {@code EnemyFactory} 直接从实例数据搬过去。
 *
 * @param hp               最终生命上限
 * @param attack           最终攻击力
 * @param defence          最终防御力
 * @param speed            最终速度
 * @param stance           韧性值（P4 削韧会消耗它）
 * @param effectHitRate    效果命中（加值口径，P6-1 用）
 * @param effectResistance 效果抵抗（**加值**口径：模板值 + 等级组值）
 */
public record EnemyStats(double hp, double attack, double defence, double speed, double stance,
                         double effectHitRate, double effectResistance) {
}
