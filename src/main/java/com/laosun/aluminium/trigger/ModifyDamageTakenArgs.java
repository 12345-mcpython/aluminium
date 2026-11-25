package com.laosun.aluminium.trigger;

/**
 * What {@link ModifyDamageTakenOp} parsed out of its effect.
 *
 * <p><b>The zone cluster.</b> A "damage taken" modifier is the largest single-effect shape in the vocabulary: a share, a
 * lifetime, two optional scopes, and five different ways of saying <i>when it applies</i> (a gate on the owner's state,
 * on a counter's threshold, on the carrier's state, on a live per-layer count, or "beside another source's zone"). All of
 * them are properties of the sentence, so all of them are parsed here; only the share is worked out at settlement.
 *
 * @param magnitude       the share (negative means a reduction)
 * @param instance        whether it applies to the CURRENT instance instead of a zone
 * @param turns           the zone's lifetime, or the unbounded placeholder
 * @param permanent       whether the zone is never ticked
 * @param damageType      a damage-type scope, or {@code null}
 * @param scopeElement    an element scope, or {@code null}
 * @param fromSelf        whether the zone only applies to damage from the owner
 * @param maxStacks       the zone's layer ceiling
 * @param perStack        the counter its share is multiplied by, or {@code null}
 * @param perStackCap     that counter's ceiling, or {@code null}
 * @param buffName        a name so {@code REMOVE_STATE} can take it off, or {@code null}
 * @param perStackLive    whether the share is re-read at every settlement instead of frozen
 * @param whileState      the owner state the zone lives as long as, or {@code null}
 * @param whileStacks     the owner counter whose threshold gates it, or {@code null}
 * @param whileStacksMin  that threshold
 * @param whileHolderState the carrier state the zone lives as long as, or {@code null}
 * @param coexist         whether the zone must survive another source's zone
 */
record ModifyDamageTakenArgs(Magnitude magnitude,
                             boolean instance,
                             Integer turns,
                             boolean permanent,
                             String damageType,
                             String scopeElement,
                             boolean fromSelf,
                             Integer maxStacks,
                             String perStack,
                             Integer perStackCap,
                             String buffName,
                             boolean perStackLive,
                             String whileState,
                             String whileStacks,
                             Integer whileStacksMin,
                             String whileHolderState,
                             boolean coexist) implements OpArgs {
}
