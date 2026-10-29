package com.laosun.aluminium.battle;

/**
 * The one-level guard around buff-path reactions (today: counter-attacks).
 *
 * <p><b>Why this is separate.</b> A counter is triggered by HP loss and its own damage causes HP loss,
 * so two wearers would hit each other forever. The trigger-table path has its own depth guard; this is
 * the buff-path one, and it is per battle - two battles in one test must not share it.
 */
public final class CounterNest {

    private int depth;

    /**
     * Runs the reaction unless one is already in progress.
     *
     * @param reaction the reaction
     * @return {@code true} when it ran, {@code false} when it was refused
     */
    public boolean run(Runnable reaction) {
        if (depth > 0) {
            return false;
        }
        depth++;
        try {
            reaction.run();
            return true;
        } finally {
            depth--;
        }
    }
}
