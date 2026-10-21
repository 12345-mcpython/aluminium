package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.models.CanHit;

import java.util.List;

/**
 * The cast-scoped facts a trigger context carries: which skill row produced the event, how many hit
 * targets shared its weakness, and which targets the attack hit.
 *
 * <p><b>Why they are grouped.</b> These three only exist for cast events and every copy helper has to
 * rebuild the whole record; grouping them keeps "the cast facts" one value rather than three fields
 * that a future copy can silently reset one of.
 *
 * @param skillId          the skill row id (0 = not a cast event)
 * @param weakHitCount     how many hit targets shared the attack's weakness
 * @param attackHitTargets the targets this attack hit
 */
public record CastFacts(int skillId, int weakHitCount, List<CanHit> attackHitTargets) {

    /**
     * No cast facts: the value a non-cast event carries.
     */
    public static final CastFacts EMPTY = new CastFacts(0, 0, List.of());

    /**
     * Copies the target list so a caller cannot mutate a context after the fact.
     */
    public CastFacts {
        attackHitTargets = attackHitTargets == null ? List.of() : List.copyOf(attackHitTargets);
    }

    /**
     * @param id the skill row id
     * @return the same facts with that row id
     */
    public CastFacts withSkillId(int id) {
        return new CastFacts(id, weakHitCount, attackHitTargets);
    }

    /**
     * @param count how many hit targets shared the weakness
     * @return the same facts with that count
     */
    public CastFacts withWeakHitCount(int count) {
        return new CastFacts(skillId, count, attackHitTargets);
    }

    /**
     * @param targets the targets this attack hit
     * @return the same facts with those targets
     */
    public CastFacts withAttackHitTargets(List<CanHit> targets) {
        return new CastFacts(skillId, weakHitCount, targets);
    }
}
