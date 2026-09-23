package com.laosun.aluminium.beans;

/**
 * Elite-group multipliers (tbgd {@code EliteGroup} / {@code InfiniteEliteGroup}).
 *
 * <p>⚠ <b>There are two tables</b>, and both come from the **wave group** rather than the
 * monster itself: normal stages use {@code EliteGroup}, infinite-wave
 * ({@code _StageInfiniteGroup}) stages use {@code InfiniteEliteGroup} — the King of Despair
 * Beetle is HPRatio 6.2 / AttackRatio 1.1 and the Eye of Daybreak is 5.0; <b>using the wrong
 * table puts HP off by several times</b>.
 *
 * <p>This data directory does not have those two tables yet, so P2 only exposes the
 * multipliers as parameters (see {@code EnemyScaler}), leaving "which table to read from and
 * how it follows the stage" to P7-4 / P9.
 *
 * <p>⚠ When wiring up the tables, first verify the JSON key names: in tbgd they are
 * {@code HPRatio} / {@code AttackRatio} / {@code DefenceRatio} / {@code SpeedRatio}, whereas
 * this project's exported monster tables use a style like {@code health_modify_ratio} — do
 * not repeat the {@code HardLevelGroup} pitfall where "a key-name mismatch made Gson silently
 * read 0".
 */
public record EliteGroup(double healthRatio, double attackRatio, double defenceRatio, double speedRatio,
                         double stanceRatio) {
}
