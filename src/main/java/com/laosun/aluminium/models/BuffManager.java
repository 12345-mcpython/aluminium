package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.event.AttackEvent;
import com.laosun.aluminium.models.event.BreakEvent;
import com.laosun.aluminium.models.event.DamageEvent;
import com.laosun.aluminium.models.event.EnergyEvent;
import com.laosun.aluminium.models.event.HealEvent;
import com.laosun.aluminium.models.event.HpLossEvent;
import com.laosun.aluminium.models.event.KillEvent;
import com.laosun.aluminium.models.event.SkillCastEvent;
import com.laosun.aluminium.models.event.SkillPointGainedEvent;
import com.laosun.aluminium.models.event.SkillPointSpentEvent;

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
        buff.setOwner(instance);      // C-1: record the owner, so buffs that inject zones per side can tell which side they stand on
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
     * Lets every buff react to a finished attack（知更鸟【协奏】/缇宝结界的"after our side attacks"）.
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

    /**
     * Lets every buff react to a skill being cast（P8-6）—— **non-damaging skills also fire it**,
     * so "restore skill points after casting a skill" (Bronya / Sushang) effects of that kind can
     * also receive healing / shield skills.
     * Called by {@link CanHit#onSkillCast(Battle, CanHit, Skill, List, List)}.
     */
    public void onSkillCast(Battle battle, CanHit user, Skill skill,
                            List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof SkillCastEvent event) {
                event.onSkillCast(battle, user, skill, hitTargets, targets);
            }
        }
    }

    /**
     * Lets every buff react to an energy credit（P8-6）. {@code actuallyAdded} is the
     * value that really landed after the cap.
     * Called by {@link CanHit#onEnergyGain(Battle, CanHit, double)}.
     */
    public void onEnergyGain(Battle battle, CanHit target, double actuallyAdded) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof EnergyEvent event) {
                event.onEnergyGain(battle, target, actuallyAdded);
            }
        }
    }

    /**
     * Lets every buff react to real HP loss（P8-6）—— the part absorbed by a shield does not count,
     * so this event does not fire while the shield is unbroken.
     * Called by {@link CanHit#onHpLoss(Battle, CanHit, double, double, CanHit, double)}.
     */
    public void onHpLoss(Battle battle, CanHit target, double before, double after,
                         CanHit source, double amount) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof HpLossEvent event) {
                event.onHpLoss(battle, target, before, after, source, amount);
            }
        }
    }

    /**
     * Lets every buff react to real healing（P8-6）. Called by
     * {@link CanHit#onHeal(Battle, CanHit, CanHit, double)}.
     */
    public void onHeal(Battle battle, CanHit healer, CanHit target, double actuallyHealed) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof HealEvent event) {
                event.onHeal(battle, healer, target, actuallyHealed);
            }
        }
    }

    /**
     * Lets every buff react to a kill（P8-6）—— including additional damage / true damage finishing blows.
     * Called by {@link CanHit#onKill(Battle, CanHit, CanHit)}.
     */
    public void onKill(Battle battle, CanHit attacker, CanHit victim) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof KillEvent event) {
                event.onKill(battle, attacker, victim);
            }
        }
    }

    /**
     * Lets every buff react to a weakness break（P8-6）—— fires only once at the moment toughness hits zero.
     * Called by {@link CanHit#onBreak(Battle, CanHit, CanHit, DamageElement)}.
     */
    public void onBreak(Battle battle, CanHit attacker, CanHit target, DamageElement element) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof BreakEvent event) {
                event.onBreak(battle, attacker, target, element);
            }
        }
    }

    /**
     * Lets every buff react to a skill point being gained（P8-6）.
     * Called by {@link CanHit#onSkillPointGained(Battle, int)}.
     */
    public void onSkillPointGained(Battle battle, int amount) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof SkillPointGainedEvent event) {
                event.onSkillPointGained(battle, amount);
            }
        }
    }

    /**
     * Lets every buff react to a skill point being **really** spent（P8-6）——
     * this event does not fire when skill points are insufficient and the action does not go through.
     * Called by {@link CanHit#onSkillPointSpent(Battle, int)}.
     */
    public void onSkillPointSpent(Battle battle, int amount) {
        for (AbstractBuff buff : buffs) {
            if (buff instanceof SkillPointSpentEvent event) {
                event.onSkillPointSpent(battle, amount);
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
     * Whether a certain kind of buff is on us (needed by P4-6 / P8-7 / P10-2 alike).
     *
     * <p>Why "ask about a kind" instead of "hand the list out" (the P1-7 design decision):
     * iteration and matching stay inside the manager, so the outside cannot get a mutable list,
     * and therefore there is no opening for "a caller mutating the list causing
     * {@code ConcurrentModificationException}".
     *
     * <p>Matches exactly by {@code getClass()}, the same convention as
     * {@link AbstractBuff#isSameKind(AbstractBuff)} — a subclass does not count as its parent
     * (if you need "does it have some bloodline", pass the parent yourself and use a new method
     * with {@code isInstance} semantics; do not quietly loosen it here).
     *
     * @param kind the buff type to query
     * @return {@code true} = a buff of this kind is on us
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
     * Takes the first buff of that type on us, or {@code null} if there is none (needed since P5-2:
     * the target selector must obtain **the taunter itself**, merely knowing "whether there is one"
     * is not enough).
     *
     * <p>It is a strict superset of {@link #hasBuff(Class)} ({@code findBuff(X) != null} means "there is one").
     * It still **does not expose {@code getBuffs()}**: keeping iteration inside the manager is the P1-7
     * decision, and handing out the mutable list would add one more opening for
     * {@code ConcurrentModificationException}.
     *
     * @param kind the buff type to query
     * @param <T>  the buff type
     * @return the first buff of that type, or {@code null} if there is none
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
