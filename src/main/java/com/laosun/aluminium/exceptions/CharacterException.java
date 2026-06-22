package com.laosun.aluminium.exceptions;

/**
 * Thrown when an invalid or missing character configuration is encountered.
 *
 * <p>Typically raised during {@link com.laosun.aluminium.models.Character.Builder#build()}
 * when a character ID is not found in the data source or is zero/null.
 */
public class CharacterException extends RuntimeException {
    public CharacterException(String message) {
        super(message);
    }

    /**
     * Thrown specifically when a character ID is not found in the data source.
     */
    public static class CharacterNotFoundException extends CharacterException {
        public CharacterNotFoundException(String message) {
            super(message);
        }
    }
}
