package com.laosun.aluminium.exceptions;

/**
 * Thrown when an invalid relic configuration is encountered.
 *
 * <p>Typical causes include: invalid star level, missing main attribute for
 * a given relic type, sub-attribute levels out of valid range, etc.
 */
public class RelicException extends RuntimeException {
    public RelicException(String message) {
        super(message);
    }

    /**
     * Thrown when a relic is assigned to a part/slot it cannot occupy.
     */
    public static class RelicWrongPartException extends RelicException {
        public RelicWrongPartException(String message) {
            super(message);
        }
    }
}
