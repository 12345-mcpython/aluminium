package com.laosun.aluminium.buff;

import com.laosun.aluminium.entity.CanHit;

/**
 * 「被施加负面效果时可抵抗 1 次负面效果，该效果在 2 回合后可再次触发」 - a <b>charge</b> shield, not a
 * percentage resistance: the first debuff that would land is refused outright, then the shield is down
 * for a stated number of the carrier's turns before the charge comes back.
 *
 * <p><b>Why not {@link ClassResistBuff}.</b> That class answers "what fraction of the chance do I
 * resist", so {@code percent: 1.0} is <i>permanent</i> immunity. This sentence resists exactly
 * <b>one application</b> and then has a cooldown - the count and the cooldown are the mechanic, and
 * writing it as a percentage would change both.
 *
 * <p><b>How it stays alive.</b> The buff is not {@code permanent} (a permanent buff is never ticked),
 * so its duration is kept at 1 and every tick either runs the cooldown down or restores the charge
 * and resets the duration to 1. The manager therefore sees a live buff after every tick and never
 * removes it; {@link #consume()} is the only thing that spends it.
 */
public class DebuffShieldBuff extends AbstractBuff {

    private int charges;
    private final int cooldownTurns;
    private int cooldownRemaining;

    /**
     * @param charges       how many applications one charge refuses (normally 1)
     * @param cooldownTurns the carrier turns between spending a charge and it coming back
     */
    public DebuffShieldBuff(int charges, int cooldownTurns) {
        super(1, false, false);
        if (charges < 1) {
            throw new IllegalArgumentException("DebuffShieldBuff charges must be >= 1, got " + charges);
        }
        if (cooldownTurns < 0) {
            throw new IllegalArgumentException("DebuffShieldBuff cooldownTurns must be >= 0, got " + cooldownTurns);
        }
        this.charges = charges;
        this.cooldownTurns = cooldownTurns;
    }

    /**
     * Spend one charge, if the shield is up.
     *
     * @return {@code true} when the incoming debuff is refused
     */
    public boolean consume() {
        if (charges <= 0 || cooldownRemaining > 0) {
            return false;
        }
        charges--;
        cooldownRemaining = cooldownTurns;
        return true;
    }

    public int charges() {
        return charges;
    }

    public int cooldownRemaining() {
        return cooldownRemaining;
    }

    @Override
    public void applyEffect(CanHit target) {
        // Nothing to install: DebuffService asks this object before rolling.
    }

    @Override
    public void removeBuff(CanHit target) {
        // Nothing to take off.
    }

    @Override
    public void tickEffect(CanHit target) {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        } else {
            charges = 1;
        }
        // Never let the manager expire the object: 1 after the tick keeps it alive and ticking.
        remainingDuration = 1;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof DebuffShieldBuff shield && shield.source == source;
    }
}
