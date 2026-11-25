package com.laosun.aluminium.trigger;

/**
 * What {@link ModifyRuleOp} parsed out of its effect.
 *
 * <p><b>One amendment, stated by which field carries it.</b> The validator refuses two at once, so exactly one of the
 * eight numbers below is non-null; the record still names all eight because "which spelling did the sentence use" is
 * the whole meaning of this op, and the run-time half should not be the place that asks again. {@code percent} is the
 * fallback spelling (a base-chance raise), so it is the one that is <b>not</b> an optional.
 *
 * @param rule                     the id of the rule being amended
 * @param amount                   a count of firings, for {@code per_turn}
 * @param effectTurns              a duration delta, in the owner's turns
 * @param effectMaxStacks          a stack-cap delta
 * @param effectPercent            a delta on the rule's effect value
 * @param effectPercentScale       a multiplier on the rule's effect values (1.15 = "raised by 15%")
 * @param effectAmount             a delta on the layers one application grants
 * @param effectPercentFromResource the same as {@code effectPercent}, sized by a resource in basis points
 * @param cooldownDelta            a delta on the rule's cooldown, in whole turns
 * @param percent                  the fallback: a base-chance delta, as a fraction of 1
 */
record ModifyRuleArgs(String rule,
                      Double amount,
                      Integer effectTurns,
                      Integer effectMaxStacks,
                      Double effectPercent,
                      Double effectPercentScale,
                      Integer effectAmount,
                      String effectPercentFromResource,
                      Integer cooldownDelta,
                      Double percent) implements OpArgs {
}
