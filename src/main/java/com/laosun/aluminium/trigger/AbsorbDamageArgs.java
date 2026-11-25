package com.laosun.aluminium.trigger;

/**
 * What {@link AbsorbDamageOp} parsed out of its effect.
 *
 * <p>The grant rides on the unit the hit lands on, while the counter belongs to whoever <b>pays</b> - and
 * {@code payer} states that, defaulting to the rule's owner. All three numbers are validated at load time, so the
 * run-time half reads them rather than checking them.
 *
 * @param resource   the counter that pays for the blocked damage
 * @param unitAmount how much damage one point of that counter is worth
 * @param magnitude  the share of a hit that may be blocked, in (0, 1]
 * @param payer      the unit whose counter is spent, or {@code null} for the rule's owner
 * @param turns      the lifetime, or the unbounded placeholder when {@code permanent}
 * @param permanent  whether it lasts the battle
 */
record AbsorbDamageArgs(String resource, double unitAmount, Magnitude magnitude,
                        String payer, Integer turns, boolean permanent) implements OpArgs {
}
