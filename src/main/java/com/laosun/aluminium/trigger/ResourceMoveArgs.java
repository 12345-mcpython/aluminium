package com.laosun.aluminium.trigger;

/**
 * What the three resource-moving ops ({@code GAIN_RESOURCE}, {@code SPEND_RESOURCE}, {@code REMOVE_RESOURCE}) parsed
 * out of their effect.
 *
 * <p><b>Why one record for three ops.</b> They move the same counter in different directions, so they read the same
 * fields; the load-time rules that tell the directions apart already live in one place
 * ({@code TriggerValidator.requireResourceMovement}), and forking the record would fork that decision in two.
 *
 * <p><b>Why the amount is a bag rather than one number.</b> "How much moves" has more spellings here than anywhere
 * else: a literal, a share of an attribute, a share of what the previous effect moved, the event's own amount, another
 * counter's value, or "all of it". Each is a different question about the same sentence, and the run-time half picks one
 * by which field is set - so the record carries the flags, not a resolved number.
 *
 * @param resource      the counter this effect moves
 * @param magnitude     the literal/share magnitude ({@code amount}, {@code percent}, {@code scale}, {@code value})
 * @param spendAll      "consume all of it" - a spend with no stated size
 * @param overflowOnly  "consume only the overflow" - the tier above the counter's cap
 * @param toMax         "raise it to its ceiling", so the size is whatever is missing
 * @param maxPerRound   a per-round allowance the gain may not exceed
 * @param amountFromPrevious whether the size is what the previous effect of the rule moved
 * @param amountFromEvent    whether the size is what the triggering event reported
 * @param amountFromResource a counter whose value IS the size
 * @param amountFromAttr     an attribute whose share is the size
 * @param amountPercent      the share used beside {@code amountFromAttr} / {@code amountFromPrevious} / {@code amountFromEvent}
 * @param amountPercentFromResource the same share, carried in a counter (basis points)
 * @param amountCap          a ceiling on a single gain
 */
record ResourceMoveArgs(String resource,
                        Magnitude magnitude,
                        boolean spendAll,
                        boolean overflowOnly,
                        boolean toMax,
                        Double maxPerRound,
                        Boolean amountFromPrevious,
                        Boolean amountFromEvent,
                        String amountFromResource,
                        String amountFromAttr,
                        Double amountPercent,
                        String amountPercentFromResource,
                        Double amountCap) implements OpArgs {
}
