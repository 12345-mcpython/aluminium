package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.trigger.TriggerTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules whose trigger happened with nobody to deliver them to, held until a wave brings enemies in.
 *
 * <p>The context is kept, not just the rule: "self" and the event's own actor are facts about the
 * firing, and the delivery must not re-derive them from the wave (a wave event carries no actor at
 * all). The conditions are deliberately not re-checked - the trigger already happened.
 */
public final class DeferredTriggers {

    private final List<Held> held = new ArrayList<>();

    /**
     * Holds a rule's effects until a wave arrives.
     *
     * @param rule    the rule that triggered
     * @param context the context it triggered with
     */
    public void hold(TriggerTable.CompiledRule rule, TriggerTable.TriggerContext context) {
        held.add(new Held(rule, context));
    }

    /**
     * @return whether any rule is waiting
     */
    public boolean isEmpty() {
        return held.isEmpty();
    }

    /**
     * Releases every held rule and clears the buffer.
     *
     * @return the held rules, in firing order
     */
    public List<Held> release() {
        List<Held> copy = List.copyOf(held);
        held.clear();
        return copy;
    }

    /**
     * One rule held for a later wave.
     *
     * @param rule    the rule
     * @param context the firing context
     */
    public record Held(TriggerTable.CompiledRule rule, TriggerTable.TriggerContext context) {
    }
}
