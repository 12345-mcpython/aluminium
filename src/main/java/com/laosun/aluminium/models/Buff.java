package com.laosun.aluminium.models;

// TODO
// Made BUFF change the ATTRIBUTE not BUFF MANAGER!
// BUFF MANAGER ONLY NEED to MANAGE IT!
public interface Buff {
    CanHit getSource();

    //
    // true can continue move
    // false can ignore the move
    boolean canAct();

    // apply attribute boost at the buff append
    boolean applyEffect(CanHit target);

    // remove attribute boost at the buff over
    void removeBuff(CanHit target);

    // tick it
    void tickEffect(CanHit target);

    int duration();
}
