package com.laosun.aluminium.beans;

/**
 * 精英组别系数（tbgd {@code EliteGroup} / {@code InfiniteEliteGroup}）。
 *
 * <p>⚠ <b>有两张表</b>，而且都来自**波组**而不是怪自身：普通关卡用 {@code EliteGroup}，
 * 无限波次（{@code _StageInfiniteGroup}）关卡用 {@code InfiniteEliteGroup}——绝境王虫是
 * HPRatio 6.2 / AttackRatio 1.1，破晓之眼是 5.0；<b>用错表血量会差几倍</b>。
 *
 * <p>本数据目录暂无这两张表，所以 P2 只把系数作为参数暴露（见 {@code EnemyScaler}），
 * 「从哪张表取、怎么随关卡走」留给 P7-4 / P9。
 *
 * <p>⚠ 接表时先核对 JSON 键名：tbgd 里是 {@code HPRatio} / {@code AttackRatio} / {@code DefenceRatio} /
 * {@code SpeedRatio}，而本项目导出后的怪物表用的是 {@code health_modify_ratio} 这种风格——
 * 别重演 {@code HardLevelGroup} 那个「键名不匹配导致 Gson 静默读成 0」的坑。
 */
public record EliteGroup(double healthRatio, double attackRatio, double defenceRatio, double speedRatio,
                         double stanceRatio) {
}
