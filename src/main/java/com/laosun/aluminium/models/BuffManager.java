package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.event.AttackEvent;
import com.laosun.aluminium.models.event.DamageEvent;

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
        buff.setOwner(instance);      // C-1：记录持有者，供按侧注入乘区的 buff 判断自己站在哪一边
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

    /**
     * Lets every buff that reacts to damage touch the zones of the hit being settled.
     * Called by {@link CanHit#onDamage(Battle, Damage)} before {@code Damage.toValue()}.
     */
    public void onDamage(Battle battle, Damage damage) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof DamageEvent event) {
                event.onDamage(battle, damage);
            }
        }
    }

    /**
     * Lets every buff react to a finished attack（知更鸟【协奏】/缇宝结界的"我方攻击后"）.
     * Called by {@link CanHit#afterAttack(Battle, CanHit, CanHit, List, double)}.
     */
    public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                            List<? extends CanHit> hitTargets, double totalDamage) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof AttackEvent event) {
                event.afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage);
            }
        }
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

    /**
     * 身上是否挂着某一类的 buff（P4-6 / P8-7 / P10-2 都要用）。
     *
     * <p>为什么是"问一类"而不是"把列表交出去"（P1-7 的设计决定）：遍历与判定留在 manager 内部，
     * 外部拿不到可变列表，也就不存在"调用方改列表导致 {@code ConcurrentModificationException}"的口子。
     *
     * <p>按 {@code getClass()} 精确匹配，与 {@link AbstractBuff#isSameKind(AbstractBuff)} 同一口径
     * ——子类不算父类（要判"有没有某条血统"请自己传父类并改用 {@code isInstance} 语义的新方法，
     * 不要在这里偷偷放宽）。
     *
     * @param kind 要查询的 buff 类型
     * @return {@code true} = 身上有这一类的 buff
     */
    public boolean hasBuff(Class<? extends AbstractBuff> kind) {
        if (kind == null) {
            return false;
        }
        for (AbstractBuff buff : buffs) {
            if (buff.getClass() == kind) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取身上第一个该类型的 buff，没有则 {@code null}（P5-2 起需要：目标选择器要拿到
     * **嘲讽者本人**，光知道"有没有"不够）。
     *
     * <p>它是 {@link #hasBuff(Class)} 的严格超集（{@code findBuff(X) != null} 即"有"）。
     * 仍然**不暴露 {@code getBuffs()}**：遍历留在 manager 内部是 P1-7 的决定，
     * 把可变列表交出去会多一个 {@code ConcurrentModificationException} 的口子。
     *
     * @param kind 要查询的 buff 类型
     * @param <T>  buff 类型
     * @return 该类型的第一个 buff，没有则 {@code null}
     */
    public <T extends AbstractBuff> T findBuff(Class<T> kind) {
        if (kind == null) {
            return null;
        }
        for (AbstractBuff buff : buffs) {
            if (buff.getClass() == kind) {
                return kind.cast(buff);
            }
        }
        return null;
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
