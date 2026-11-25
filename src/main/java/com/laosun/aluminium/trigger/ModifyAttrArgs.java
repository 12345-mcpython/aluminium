package com.laosun.aluminium.trigger;

/**
 * What {@link ModifyAttrOp} parsed out of its effect.
 *
 * <p><b>The attribute cluster.</b> One modifier on one attribute, plus the six gates that say <i>when</i> it applies
 * (a state, a counter threshold, the carrier's state, the carrier's HP, the owner's HP, a resource's value, a summon
 * count) and the two flags that say <i>how</i> it behaves beside other sources. The number itself stays in the
 * {@link Magnitude} handed beside this record.
 *
 * <p><b>Why {@code ratio} is here and not in the magnitude.</b> It is not a share of anything: it is the multiplier on
 * the <b>stated</b> amount, i.e. a scaling of the sentence, and the validator requires it to appear beside an
 * {@code amount} rather than a {@code percent}. Reading it here keeps that pair's contract in one place.
 *
 * @param attribute       the attribute being modified
 * @param buffName        a name so {@code REMOVE_STATE} can take the modifier off, or {@code null}
 * @param turns           the lifetime, or the unbounded placeholder
 * @param permanent       whether the modifier is never ticked
 * @param ratio           a multiplier on the stated amount, or {@code null}
 * @param instance        whether it applies to the CURRENT instance instead of a persistent modifier
 * @param damageType      a damage-type scope, or {@code null}
 * @param coexist         whether the modifier must survive another source's modifier
 * @param whileState      the owner state it lives as long as, or {@code null}
 * @param whileStacks     the owner counter whose threshold gates it, or {@code null}
 * @param whileStacksMin  that threshold
 * @param whileHolderState the carrier state it lives as long as, or {@code null}
 * @param whileHolderHpPercent the carrier HP share below which it applies, or {@code null}
 * @param whileHpPercent  the owner HP share below which it applies, or {@code null}
 * @param whileResource   the owner counter that must reach a value, or {@code null}
 * @param whileResourceMin that value
 * @param whileSummonCount the owner summon count it requires, or {@code null}
 */
record ModifyAttrArgs(String attribute,
                      String buffName,
                      Integer turns,
                      boolean permanent,
                      Double ratio,
                      boolean instance,
                      String damageType,
                      boolean coexist,
                      String whileState,
                      String whileStacks,
                      Integer whileStacksMin,
                      String whileHolderState,
                      Double whileHolderHpPercent,
                      Double whileHpPercent,
                      String whileResource,
                      Integer whileResourceMin,
                      Integer whileSummonCount) implements OpArgs {
}
