package com.laosun.aluminium.exceptions;

public class CharacterException extends RuntimeException {
    public CharacterException(String message) {
        super(message);
    }

    public static class CharacterNotFoundException extends CharacterException {
        public CharacterNotFoundException(String message) {
            super(message);
        }
    }
}
