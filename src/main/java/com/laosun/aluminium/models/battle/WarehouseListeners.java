package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Units that are owned but not deployed, whose rules are asked without them ever acting.
 *
 * <p>Two documents start with "takes effect as soon as the character is obtained, no deployment
 * needed", and the data files them as global support skills. Their tables cannot ride in the battle's
 * character roster: that roster is what the action queue is built from, so a listener put there would
 * take turns of its own. They are asked through this separate map, and because they are in neither
 * camp nor the queue they are never targeted and never act.
 */
public final class WarehouseListeners {

    private final Map<Character, TriggerTable> listeners = new LinkedHashMap<>();

    /**
     * Registers a unit whose own table is asked although it is not on the field.
     *
     * @param listener the unit
     */
    public void register(Character listener) {
        if (listener != null) {
            listeners.put(listener, listener.getTriggerTable());
        }
    }

    /**
     * The same, naming the table to ask.
     *
     * <p>The caller sets the table: this is deliberately about WHOSE rules are heard, not about which
     * rules exist, so a listener can carry exactly the global support clauses instead of its whole
     * battle kit.
     *
     * @param listener  the unit
     * @param warehouse the table to ask
     */
    public void register(Character listener, TriggerTable warehouse) {
        if (listener != null && warehouse != null && !warehouse.isEmpty()) {
            listeners.put(listener, warehouse);
        }
    }

    /**
     * @return the registered listeners and their tables, in registration order
     */
    public Set<Map.Entry<Character, TriggerTable>> entries() {
        return listeners.entrySet();
    }
}
