package com.laosun.aluminium.exceptions;

public class RelicException extends RuntimeException {
    public RelicException(String message) {
        super(message);
    }

    public static class RelicNotFoundException extends RelicException {
        public RelicNotFoundException(String message) {
            super(message);
        }
    }
}
