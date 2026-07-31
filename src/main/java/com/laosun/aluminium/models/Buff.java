package com.laosun.aluminium.models;

// TODO
public interface Buff {
    CanHit getSource();

    // true can continue move
    // false can ignore the move
    boolean beforeMove();
}
