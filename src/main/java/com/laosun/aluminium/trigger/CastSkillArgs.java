package com.laosun.aluminium.trigger;

/**
 * What {@link CastSkillOp} parsed out of its effect.
 *
 * <p>Four names, and the 120 lines behind them: a commanded cast has to find the <b>skill</b> (by slot, or by data slot
 * for a memosprite), the unit it was <b>aimed</b> at, and the one number the settlement cannot fold on its own (a fixed
 * Elation laugh count).
 *
 * @param skill          the slot to cast, or {@code null} when only a data slot is named
 * @param skillId        the data slot, which is how a memosprite keeps its skills, or {@code null}
 * @param castTarget     the selector naming the main target, or {@code null}
 * @param countedLaughs  a fixed laugh count the commanded Elation cast must fold in, or {@code null}
 */
record CastSkillArgs(String skill, Integer skillId, String castTarget, Integer countedLaughs) implements OpArgs {
}
