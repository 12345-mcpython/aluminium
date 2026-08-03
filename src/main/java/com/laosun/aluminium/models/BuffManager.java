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
     * For decrease <code>Buff</code> duration
     * In the future may have more usage
     */
    public void tick() {
        buffs.removeIf(buff -> {
            buff.tickEffect(instance);
            if (buff.duration() <= 0) {
                buff.removeBuff(instance);
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
