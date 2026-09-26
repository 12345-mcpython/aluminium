package com.laosun.aluminium.models.enemy;

/**
 * The final stat sheet of one enemy instance (the output of {@code EnemyScaler}).
 *
 * <p>Pure numbers, with no dependency on {@link Enemy}, so the data formulas can be
 * checked against each other in isolation (see {@code EnemyScalerTest}). Weakness and
 * resistance are not here — they do not take part in stat scaling and are copied straight
 * over from the instance data by {@code EnemyFactory}.
 *
 * @param hp               final max HP
 * @param attack           final ATK
 * @param defence          final DEF
 * @param speed            final SPD
 * @param stance           toughness value (P4 toughness reduction consumes it)
 * @param effectHitRate    effect hit rate (additive-value convention, used by P6-1)
 * @param effectResistance effect resistance (**additive** convention: template value +
 *                         level-group value)
 */
public record EnemyStats(double hp, double attack, double defence, double speed, double stance,
                         double effectHitRate, double effectResistance) {
}
