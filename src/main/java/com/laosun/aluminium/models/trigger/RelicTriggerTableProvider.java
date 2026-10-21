package com.laosun.aluminium.models.trigger;

/**
 * Supplies the trigger rules a worn relic set contributes at a given piece count.
 */
@FunctionalInterface
public interface RelicTriggerTableProvider {

    /**
     * @param setId       the relic set id
     * @param wornPieces  how many pieces of that set are worn
     * @return the rules, never {@code null}
     */
    TriggerTable of(int setId, int wornPieces);
}
