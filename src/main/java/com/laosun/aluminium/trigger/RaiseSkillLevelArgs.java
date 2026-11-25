package com.laosun.aluminium.trigger;

import java.util.List;

/**
 * What {@link RaiseSkillLevelOp} parsed out of its effect.
 *
 * <p>Three mutually exclusive ways to name the slot, decided once here rather than re-inspected on every firing:
 * a character slot ({@code skill}), several character slots ({@code skills}), or a memosprite's data slot
 * ({@code memosprite} + {@code skillId}). The validator refuses any combination, so exactly one shape is reachable at
 * run time - but the parse still records which one, because "which of the three" is a fact of the sentence.
 *
 * @param delta      how many levels to raise
 * @param memosprite whether the slot is the owner's memosprite's data slot
 * @param skillId    the memosprite data slot, when {@code memosprite} is set
 * @param skills     several character slots at once, or {@code null}
 * @param skill      one character slot, or {@code null}
 */
record RaiseSkillLevelArgs(double delta, boolean memosprite, Integer skillId, List<String> skills, String skill)
        implements OpArgs {
}
