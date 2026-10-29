package com.laosun.aluminium.trigger;

/**
 * Supplies the trigger rules a light cone contributes.
 */
@FunctionalInterface
public interface WeaponTriggerTableProvider {

    /**
     * @param weaponId the light-cone id
     * @param rank     the superimposed rank
     * @return the rules, never {@code null}
     */
    TriggerTable of(int weaponId, int rank);
}
