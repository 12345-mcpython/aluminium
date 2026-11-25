package com.laosun.aluminium.trigger;

/**
 * The arguments of an op that states exactly one number.
 *
 * <p><b>Why one record for several ops.</b> "State one magnitude" is the same argument in several places - a share for
 * {@code ADVANCE} and {@code DELAY}, an amount for {@code GAIN_SKILL_POINT} - and giving each its own one-field record
 * would say "these are different things" where the vocabulary says they are the same one. What differs is which
 * question the op asks of the number (a share, a flat amount), and that is the op's business, not the record's.
 *
 * <p><b>Why it holds a {@link Magnitude} and not a double.</b> A magnitude's value depends on the firing (the owner's
 * attributes, a stack count, the event's amount), so it cannot be resolved at load time. Holding the parsed
 * <b>spelling</b> - an interface the bean implements today - means the run-time half never names the bean, and the day
 * a parsed implementation replaces the adapter, these records do not change.
 *
 * @param magnitude the effect's own number, as a magnitude
 */
record MagnitudeArgs(Magnitude magnitude) implements OpArgs {
}
