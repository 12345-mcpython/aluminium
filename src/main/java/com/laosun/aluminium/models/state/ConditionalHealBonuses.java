package com.laosun.aluminium.models.state;

import com.laosun.aluminium.models.trigger.TriggerTable;

import java.util.ArrayList;
import java.util.List;
import com.laosun.aluminium.models.entity.CanHit;

/**
 * Heal bonuses that depend on <b>who is being healed</b>, held by the healer.
 *
 * <p><b>Why this is separate.</b> A stat cannot express "raises healing only when the target is at
 * 30% HP or lower", and no rule can raise it before the heal settles (there is no pre-heal event). The
 * conditions are parsed at load time and asked by the heal calculation; keeping the list with the
 * other per-battle rule bookkeeping gives it one owner.
 */
public final class ConditionalHealBonuses {

    private final List<CanHit.ConditionalHealBonus> bonuses = new ArrayList<>();

    /**
     * @param condition the parsed condition
     * @param percent   the multiplier bonus
     */
    public void add(TriggerTable.Condition condition, double percent) {
        bonuses.add(new CanHit.ConditionalHealBonus(condition, percent));
    }

    /**
     * @return the registered bonuses
     */
    public List<CanHit.ConditionalHealBonus> all() {
        return bonuses;
    }
}
