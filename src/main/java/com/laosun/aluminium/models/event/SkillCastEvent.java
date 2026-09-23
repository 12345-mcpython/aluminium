package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Skill;

import java.util.List;

/**
 * Skill **cast** event (P8-6): fired once after a skill is cast, **also for non-damaging skills**.
 *
 * <p>Do not confuse how it divides the work with {@link AttackEvent}:
 * <ul>
 *   <li>The semantics of {@link AttackEvent} are "**an attack was dealt**", so it is only fired when a
 *       target was actually hit ({@code hitTargets} non-empty), and additional damage / true damage / DOT
 *       do not trigger it;</li>
 *   <li>The semantics of this event are "**a skill was cast**", so healing / shielding / pure buff /
 *       control / summon skills — those that "dealt no damage but really were cast" — **are fired as
 *       well**.</li>
 * </ul>
 *
 * <p>Why it is needed: the trigger source for cases like Misha/Sparkle's "after **casting** a skill…",
 * Qingque's "after casting an enhanced basic attack, recover 1 skill point", and Bronya's "when casting a
 * skill there is a 50% chance to recover 1 skill point" is the **act** of casting, not "dealing damage".
 * Those cannot be expressed with {@link AttackEvent} (healing skills deal no damage).
 *
 * <p>Emission point: {@code SkillExecutor.execute}, between the damage having been expanded and energy
 * not yet settled — that way a listener can get both "what was cast" and "who was actually hit".
 *
 * <p>⚠ Currently it is only emitted from {@code SkillExecutor}. {@code castImmediate} goes through it too,
 * so it is fired; but **enemy skills** ({@code EnemySkill}'s own {@code execute}) currently do not emit it —
 * to be aligned when P9-2 wires enemy skills into the unified executor.
 *
 * @param battle     the running battle
 * @param user       the caster
 * @param skill      the skill being cast
 * @param hitTargets the actual hit set (**empty for non-damaging skills**; includes those that died on the
 *                   spot, deduplicated in hit order)
 * @param targets    the targets chosen by the caller (passed through as-is, with no liveness filtering)
 */
public interface SkillCastEvent {
    default void onSkillCast(Battle battle, CanHit user, Skill skill,
                             List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
    }
}
