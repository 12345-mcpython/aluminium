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
        if (buff == null || !buffs.contains(buff)) return;
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

    public void tick() {
        for (AbstractBuff buff : buffs) {
            buff.tickEffect(instance);
        }
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
