package com.laosun.aluminium.trigger;

/**
 * What {@link ShieldOp} parsed out of its effect.
 *
 * <p>A shield differs from the other magnitude ops by its <b>ceiling</b>: a shield normally overwrites, so "the amount
 * may stack, up to 300% of the current Skill's" has to be stated, and it is stated as a share of an attribute (two
 * fields) or as a constant (one). Parsing them here keeps the run-time half from deciding, on every hit, which of those
 * three spellings the sentence used.
 *
 * @param magnitude   how much the shield grants
 * @param turns       its lifetime in the holder's turns, or {@code null} for "until used up"
 * @param addToShield whether a second grant stacks on the standing shield instead of replacing it
 * @param capScale    the attribute the ceiling is a share of, or {@code null}
 * @param capPercent  that attribute's share, or {@code null}
 * @param capAmount   a constant ceiling, or {@code null}
 */
record ShieldArgs(Magnitude magnitude, Integer turns, boolean addToShield,
                  String capScale, Double capPercent, Double capAmount) implements OpArgs {
}
