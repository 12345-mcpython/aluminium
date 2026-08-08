package com.laosun.aluminium.models;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class BuffManager {
    private CanHit instance;
    private final List<AbstractBuff> buffs = new ArrayList<>();

    public void addBuff(AbstractBuff buff) {
        if (buff == null) {
            return;
        }
        buff.setSource(instance);
        buffs.add(buff);
        buff.applyEffect(instance);
    }

    public void removeBuff(AbstractBuff buff) {
        if (buff == null || !buffs.remove(buff)) return;
        buff.removeBuff(instance);
    }

    public boolean canAct() {
        for (AbstractBuff buff : buffs) {
            if (!buff.canAct()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Settles early buffs before the owner's move (tick duration, remove expired).
     */
    public void beforeMove() {
        processBuffTick(true);
    }

    /**
     * Settles late buffs after the owner's move (tick duration, remove expired).
     */
    public void afterMove() {
        processBuffTick(false);
    }

    private void processBuffTick(boolean early) {
        buffs.removeIf(buff -> {
            if (buff.isEarlyBuff != early) {
                return false;
            }
            buff.tickEffect(instance);
            if (buff.duration() <= 0) {
                IO.println("remove: " + buff);
                buff.removeBuff(instance);
                buffs.remove(buff);
                return true;
            }
            return false;
        });
    }

    public void clearAll() {
        for (AbstractBuff buff : buffs) {
            buff.removeBuff(instance);
        }
        buffs.clear();
    }

    public boolean isEmpty() {
        return buffs.isEmpty();
    }
}
