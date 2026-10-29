package com.laosun.aluminium.battle;

/**
 * How many attacks have finished in this battle.
 *
 * <p><b>Why this is separate.</b> It is a boundary a rule-level {@code once_per_attack} compares
 * against, not a property of any one attack; keeping the counter beside the attack code made it look
 * like per-hit state.
 */
public final class AttackSequence {

    private int value;

    /**
     * Advances to the next attack.
     *
     * @return the new sequence number
     */
    public int next() {
        return ++value;
    }

    /**
     * @return the current sequence number
     */
    public int value() {
        return value;
    }
}
