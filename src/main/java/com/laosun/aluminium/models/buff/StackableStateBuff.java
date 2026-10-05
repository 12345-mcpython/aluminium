package com.laosun.aluminium.models.buff;

/**
 * A named state that <b>stacks</b> -- the engine's spelling of "this state carries a COUNT" (2026-10-02).
 *
 * <p>It opts into the accumulate path that {@link StackBuff} already uses: {@code BuffManager.addBuff} sends every buff
 * whose {@link #isStackable()} is true to {@code addStackable}, grouped by {@link #stackGroupKey()} and capped by
 * {@link #maxStacks()}. The replace rule ({@code isSameKind}, still by name) is deliberately left alone -- it answers a
 * different question and two shipped tests pin it.
 */
public class StackableStateBuff extends StateBuff {

    private final int cap;

    public StackableStateBuff(String state, int turns, boolean permanent, int maxStacks) {
        super(state, turns, permanent);
        if (maxStacks < 1) {
            throw new IllegalArgumentException(
                    "StackableStateBuff '" + state + "' needs a cap of at least 1, got " + maxStacks);
        }
        this.cap = maxStacks;
    }

    @Override
    public boolean isStackable() {
        return true;
    }

    @Override
    public Object stackGroupKey() {
        return "state:" + getState();
    }

    @Override
    public int maxStacks() {
        return cap;
    }

    /** So that {@code stacksOf(name)} counts instances of this state, like any other named buff. */
    @Override
    public String getBuffName() {
        return getState();
    }
}
