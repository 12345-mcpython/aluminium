package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

import java.util.ArrayList;
import java.util.List;

/**
 * Combatants admitted to the roster but not yet placed on the action bar.
 *
 * <p><b>Why this is separate.</b> Admission and scheduling are two moments: the roster and buff
 * manager change immediately, the action bar changes at the next settle. The buffer is what holds the
 * gap; it never contains a duplicate.
 */
public final class PendingCombatants {

    private final Battle battle;
    private final List<CanHit> pending = new ArrayList<>();

    /**
     * @param battle the battle whose queue receives the newcomers
     */
    public PendingCombatants(Battle battle) {
        this.battle = battle;
    }

    /**
     * @param unit the admitted combatant
     */
    public void add(CanHit unit) {
        if (!pending.contains(unit)) {
            pending.add(unit);
        }
    }

    /**
     * @return the admitted combatants, in admission order
     */
    public List<CanHit> view() {
        return List.copyOf(pending);
    }

    /**
     * Places everyone waiting on the action bar.
     */
    public void settle() {
        if (pending.isEmpty()) {
            return;
        }
        for (CanHit unit : pending) {
            battle.queue.addCombatant(unit);
        }
        pending.clear();
    }
}
