package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;

/**
 * A <b>timed</b> shield: 「提供能够抵消等同于三月七57%防御力+760伤害的护盾，<b>持续3回合</b>」.
 *
 * <p><b>Why this class had to exist.</b> {@code Battle.grantShield} writes a bare number on the combatant
 * ({@code CanHit.shield}) and nothing ever takes it off again — so before this class, a {@code SHIELD} effect's
 * {@code "turns"} was <b>accepted and silently ignored</b>: 三月七's shield said 「持续3回合」 and stayed for the
 * rest of the battle. That is the failure mode this project refuses (a wrong number with nothing to report), and
 * it is worse than it looks, because a shield that never comes off keeps every 「持有护盾的…」 clause true — the
 * 天赋 counter this class was written for would have counted attacks for the whole fight instead of for three
 * turns.
 *
 * <p><b>What it does.</b> It <b>installs</b> the shield when it is attached and <b>takes it off</b> when it
 * expires, so the number and its duration are one fact living in one place. The shield is still read through
 * {@code CanHit.getShield()} (that is what the damage path drains, and what a {@code has_shield} condition asks),
 * so nothing about the shield's <i>behaviour</i> changes — only its lifetime.
 *
 * <p><b>Whose turns count it down.</b> The carrier's (the shielded ally's), which is the engine's default anchor
 * and what 「持续3回合」 means in the texts: the shield is on <i>them</i>, and it lasts three of <i>their</i> turns.
 * 星期日's 【蒙福者】 is the one documented case where the clock is somebody else's, and it says so explicitly
 * ({@code ticks_on}); a shield does not.
 *
 * <p><b>⚠ Ownership, and why the value alone is not enough.</b> A new shield <b>overwrites</b> the old one
 * ({@code grantShield} is deliberately not additive), and the manager removes a same-kind buff before attaching
 * the new one — so the <i>old</i> buff's {@code removeBuff} runs at a moment when the new shield is not installed
 * yet, and it must take off <b>its own</b> shield, not "whatever is there". Comparing values would be a guess
 * (two shields of the same size are indistinguishable); the shield therefore remembers
 * <b>which buff installed it</b> ({@code CanHit.installShield} / {@code removeShieldFrom}), and a buff only ever
 * clears the shield it put up. The same field is what the registered 「这面盾<b>是不是我的</b>」 gap will read when
 * that content arrives — as {@link #getSource()}, the caster.
 */
public class ShieldBuff extends AbstractBuff {

    /**
     * The shield value this buff installed. Held so the buff can install it at attach time and so a test can
     * read what a shield was worth without going through the damage path.
     */
    private final double amount;

    /**
     * @param source the unit whose effect granted the shield ({@code null} = unknown), which is who
     *               「装备者提供的护盾」 would name — see {@link #getSource()}
     * @param amount the shield value (already scaled and resolved by the caller)
     * @param turns  how many of the <b>carrier's</b> turns it lasts; must be positive
     */
    public ShieldBuff(CanHit source, double amount, int turns) {
        // Not an early buff: the engine's turn boundary order for a plain timed buff -- the same slot every
        // StatModifierBuff uses, so "3 turns" means the same thing here as it does for 「攻击力提高，持续3回合」.
        super(turns, false);
        setSource(source);
        this.amount = amount;
    }

    /**
     * The shield value this buff installed.
     */
    public double getAmount() {
        return amount;
    }

    @Override
    public boolean canAct() {
        // A shield never blocks the carrier's action (canAct() answers "may this unit act"), so `true`.
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        target.installShield(amount, this);
    }

    @Override
    public void removeBuff(CanHit target) {
        target.removeShieldFrom(this);
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    @Override
    public String toString() {
        return "ShieldBuff[" + amount + ", " + remainingDuration + "t]";
    }
}
