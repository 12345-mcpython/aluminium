package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.entity.CanHit;

import java.util.ArrayList;
import java.util.List;

/**
 * Summons placed since the last settle, waiting for {@code SUMMONED}.
 *
 * <p><b>Why this is separate.</b> A summon is placed in the roster and in the action bar before the
 * event fires; the buffer is what bridges those two moments. Draining is a copy-and-clear so a rule
 * that answers {@code SUMMONED} by summoning again is picked up by the next settle, not chased by the
 * current loop.
 */
public final class SummonedBuffer {

    private final List<CanHit> pending = new ArrayList<>();

    /**
     * @param summon the newly placed summon
     */
    public void add(CanHit summon) {
        if (summon != null) {
            pending.add(summon);
        }
    }

    /**
     * @return whether anything is waiting
     */
    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /**
     * Drains the buffer.
     *
     * @return the summons placed since the last drain, in placement order
     */
    public List<CanHit> drain() {
        List<CanHit> arrived = List.copyOf(pending);
        pending.clear();
        return arrived;
    }
}
