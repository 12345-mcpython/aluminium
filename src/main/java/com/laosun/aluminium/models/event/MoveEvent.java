package com.laosun.aluminium.models.event;

public interface MoveEvent {
    default void beforeMove() {
    }

    default void afterMove() {
    }
}
