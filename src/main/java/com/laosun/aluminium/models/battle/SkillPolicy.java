package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.skill.Skill;

import java.util.List;

/**
 * Decides what one unit does on its turn.
 *
 * <p><b>Why this exists.</b> The engine knows how to execute an action but deliberately has no opinion
 * about which action to take: {@code Battle} schedules turns and resolves effects, while "use the
 * ultimate now" or "hit the weakest enemy" is a policy. Before this interface that policy lived inside
 * the demo's main class, so a battle could only be driven by that one hard-coded script and the
 * engine could not be played headlessly at all.
 *
 * <p>An implementation is asked once per turn and returns a {@link Decision}, or {@code null} when the
 * unit declines to act (no usable skill, or no legal target). Declining is not an error: a turn that
 * accomplishes nothing still has to end, and {@link BattleDriver} ends it.
 *
 * <p><b>The policy does not get to break the turn.</b> It may choose what to cast, never when the
 * turn starts or ends or whether the queue advances; those belong to {@link Battle} and to
 * {@link BattleDriver}. That split is the reason a policy can be swapped for a human at the keyboard
 * without touching the loop.
 *
 * @see AutoPolicy
 */
@FunctionalInterface
public interface SkillPolicy {

    /**
     * What a unit decided to do.
     *
     * @param skill   the skill to cast, never {@code null} in a returned decision
     * @param targets the targets in the order the skill expects them; not empty
     * @param note    a short human-readable reason, for logging. May be empty.
     */
    record Decision(Skill skill, List<CanHit> targets, String note) {

        /**
         * A decision that casts {@code skill} at {@code targets}.
         *
         * @param skill   the skill to cast
         * @param targets its targets
         * @return the decision, with an empty note
         */
        public static Decision of(Skill skill, List<CanHit> targets) {
            return new Decision(skill, List.copyOf(targets), "");
        }

        /**
         * A decision that casts {@code skill} at {@code targets}, with a reason for the log.
         *
         * @param skill   the skill to cast
         * @param targets its targets
         * @param note    why this action was chosen
         * @return the decision
         */
        public static Decision of(Skill skill, List<CanHit> targets, String note) {
            return new Decision(skill, List.copyOf(targets), note);
        }
    }

    /**
     * Choose this turn's action for {@code actor}.
     *
     * <p>Called after the turn has started ({@code stepForward}, {@code beforeMove}) and before it
     * ends ({@code afterMove}), so damage over time has already settled and the actor may have died in
     * the meantime -- the driver checks that before asking, but an implementation must not assume the
     * actor is still alive if it caches anything across turns.
     *
     * @param battle the battle being driven, for reading state (skill points, energy, camps)
     * @param actor  the unit whose turn it is
     * @return the action to take, or {@code null} to pass this turn
     */
    Decision choose(Battle battle, CanHit actor);
}
