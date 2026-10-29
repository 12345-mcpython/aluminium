package com.laosun.aluminium.trigger;

/**
 * Supplies a character's trigger table.
 *
 * <p>The production implementation is the classpath loader; a test or a different content pack can
 * supply its own without touching the static cache in {@code TriggerTables}.
 */
@FunctionalInterface
public interface TriggerTableProvider {

    /**
     * @param cid the character id
     * @return the table, never {@code null}
     */
    TriggerTable of(int cid);
}
