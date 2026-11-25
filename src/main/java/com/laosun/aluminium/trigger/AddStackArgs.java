package com.laosun.aluminium.trigger;

import java.util.List;

/**
 * What {@link AddStackOp} parsed out of its effect.
 *
 * <p><b>The stacking cluster.</b> A counter is a named stackable state, so this record carries everything that
 * describes <b>the counter being marked</b> - its name, how many marks one application gives, its ceiling, where the
 * overflow goes - and the magnitude ({@code value} / {@code amount_from_skill_param} / {@code scale: event_amount}) is
 * still resolved at fire time because it depends on the event.
 *
 * @param name        the counter's name, as written
 * @param unbounded   whether the counter keeps every layer ("the sentence states no ceiling" is not "one layer")
 * @param maxStacks   the stated ceiling, or {@code null}
 * @param turns       the lifetime of one mark, or the unbounded placeholder
 * @param permanent   whether the marks are never ticked
 * @param overflowTo  a second counter that takes the layers this one cannot, or {@code null}
 * @param overflowMax that counter's own ceiling, or {@code null}
 * @param countsAs    other state names a mark answers to, or {@code null}
 * @param countsAsRequires the state that must be present for those aliases to count, or {@code null}
 * @param magnitude   the number of marks (and, for {@code scale: event_amount}, the share of the event's amount)
 */
record AddStackArgs(String name,
                    boolean unbounded,
                    Integer maxStacks,
                    Integer turns,
                    boolean permanent,
                    String overflowTo,
                    Integer overflowMax,
                    List<String> countsAs,
                    String countsAsRequires,
                    Magnitude magnitude) implements OpArgs {
}
