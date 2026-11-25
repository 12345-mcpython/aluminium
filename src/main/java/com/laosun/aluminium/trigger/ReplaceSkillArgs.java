package com.laosun.aluminium.trigger;

/**
 * What {@link ReplaceSkillOp} parsed out of its effect.
 *
 * @param skill   the slot being swapped
 * @param remove  whether this effect puts the original row back
 * @param skillId the data slot of the replacement skill, or {@code null} when removing
 */
record ReplaceSkillArgs(String skill, boolean remove, Integer skillId) implements OpArgs {
}
