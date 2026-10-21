package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.models.CanHit;

/**
 * Who a trigger firing is about: the table's owner ("self"), who caused the event, and the event's
 * subject.
 *
 * <p>Grouping them keeps the "who" half of a context separate from the "what happened" half
 * ({@link EventFacts}) and from the rule/cast half. Every copy helper rebuilds the context, so one
 * value also makes it harder for a copy to reset only one of the three.
 *
 * @param owner  the character this table belongs to ("self")
 * @param actor  who caused the event (may be {@code null})
 * @param target the event's subject (may be {@code null})
 */
public record TriggerSubject(CanHit owner, CanHit actor, CanHit target) {
}
