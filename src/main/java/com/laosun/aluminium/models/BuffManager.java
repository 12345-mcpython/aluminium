package com.laosun.aluminium.models;

import java.util.ArrayList;
import java.util.List;

public class BuffManager {
    private CanHit instance;
    private final List<AbstractBuff> buffs = new ArrayList<>();
    private boolean blocked = false;

    public BuffManager(CanHit instance) {
        this.instance = instance;
    }

    public void addBuff(AbstractBuff buff) {
        if (buff == null) {
            return;
        }
        for (int i = buffs.size() - 1; i >= 0; i--) {
            AbstractBuff existed = buffs.get(i);
            if (existed.isSameKind(buff)) {
                buffs.remove(i);
                existed.removeBuff(instance);
            }
        }
        buffs.add(buff);
        buff.applyEffect(instance);
    }

    public void removeBuff(AbstractBuff buff) {
        if (buff == null || !buffs.remove(buff)) return;
        buff.removeBuff(instance);
    }

    public boolean canAct() {
        if (blocked) {
            return false;
        }
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
        blocked = false;
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
            boolean couldAct = buff.canAct();
            buff.tickEffect(instance);
            if (buff.duration() <= 0) {
                buff.removeBuff(instance);
                if (!couldAct) {
                    blocked = true;
                }
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
