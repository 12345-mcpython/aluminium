package com.laosun.aluminium.battle;

import com.laosun.aluminium.enums.DebuffClass;

/**
 * The last per-firing facts the condition DSL reads from the battle: the state that just ended, the
 * resource a change is about, and the class of the debuff that just landed.
 *
 * <p><b>Why this is separate.</b> They are scoped to one firing by save/restore, not permanent battle
 * state. Keeping them next to the turn code made "the current firing's context" look like a fact about
 * the battle; this object owns only the last-firing slots.
 */
public final class TriggerFacts {

    private String stateEndedName;
    private String changedResource;
    private DebuffClass appliedDebuffClass;

    /**
     * @return the name of the state that just ended, or {@code null}
     */
    public String stateEndedName() {
        return stateEndedName;
    }

    /**
     * @param name the name of the state that just ended
     */
    public void stateEndedName(String name) {
        stateEndedName = name;
    }

    /**
     * @return the resource the current change is about, or {@code null}
     */
    public String changedResource() {
        return changedResource;
    }

    /**
     * @param resource the resource the current change is about
     */
    public void changedResource(String resource) {
        changedResource = resource;
    }

    /**
     * @return the class of the debuff that just landed, or {@code null}
     */
    public DebuffClass appliedDebuffClass() {
        return appliedDebuffClass;
    }

    /**
     * @param debuffClass the class of the debuff that just landed
     */
    public void appliedDebuffClass(DebuffClass debuffClass) {
        appliedDebuffClass = debuffClass;
    }
}
