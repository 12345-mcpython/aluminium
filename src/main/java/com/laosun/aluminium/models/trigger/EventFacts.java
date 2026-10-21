package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Damage;

/**
 * The event-scoped facts a trigger context carries: how many targets were hit, the event's magnitude,
 * the damage instance (only for {@code DEALING_DAMAGE}), and the cast category that produced it.
 *
 * <p><b>Why they are grouped.</b> They are facts about the firing, not about the rule being applied,
 * and every copy helper rebuilds the whole context. Grouping them keeps the event half one value and
 * makes the remaining context (owner / actor / target / rule / cast) easier to read.
 *
 * @param hitCount how many targets an attack connected with (0 for non-attack events)
 * @param amount   the event's magnitude where it has one
 * @param damage   the instance being settled, or {@code null}
 * @param fromCast the cast category that produced this event, or {@code null}
 */
public record EventFacts(int hitCount, double amount, Damage damage, SkillCategory fromCast) {

    /**
     * No event facts: the value a context with no magnitude carries.
     */
    public static final EventFacts EMPTY = new EventFacts(0, 0, null, null);
}
