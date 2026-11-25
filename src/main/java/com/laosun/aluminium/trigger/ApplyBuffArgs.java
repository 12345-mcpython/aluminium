package com.laosun.aluminium.trigger;

import java.util.List;

/**
 * What {@link ApplyBuffOp} parsed out of its effect.
 *
 * <p>Thirteen fields, and every one of them is a <b>property of the state being installed</b> rather than a number to
 * work out at fire time - which is why the whole shape is parsed once: the run-time half's job is to install exactly
 * what the sentence said, not to re-decide it.
 *
 * @param stateName      the state's name, or {@code null} when {@code randomOf} picks one
 * @param randomOf       several names to choose from ("implant one random 【缺陷】"), or {@code null}
 * @param stackable      whether re-applications accumulate instead of replacing
 * @param defersDeath    whether the carrier is kept alive while the state holds
 * @param suspendsTurns  whether the carrier's own turn is skipped
 * @param turns          the lifetime in turns, or the unbounded placeholder
 * @param permanent      whether the state is never ticked
 * @param maxStacks      the layer ceiling for a stackable state
 * @param blocks         what the state suppresses, comma-separated, or {@code null}
 * @param countsAs       other state names this one answers to, or {@code null}
 * @param countsAsRequires the state that must be present for those aliases to count, or {@code null}
 * @param magnitude      the base chance and the instance count, both read out of the NumberProviders
 */
record ApplyBuffArgs(String stateName,
                     List<String> randomOf,
                     boolean stackable,
                     boolean defersDeath,
                     boolean suspendsTurns,
                     Integer turns,
                     boolean permanent,
                     Integer maxStacks,
                     String blocks,
                     List<String> countsAs,
                     String countsAsRequires,
                     Magnitude magnitude) implements OpArgs {
}
