package com.laosun.aluminium.exceptions;

public class RelicException extends RuntimeException {
    public RelicException(String message) {
        super(message);
    }

    public static class RelicWrongPartException extends RelicException {
        public RelicWrongPartException(String message) {
            super(message);
        }
    }
}
