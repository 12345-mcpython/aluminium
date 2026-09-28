package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;

/**
 * A <b>countdown</b>: a unit that exists on the action order and does nothing but <b>have a turn</b> (M-49).
 *
 * <p><b>Why it is a unit at all.</b> The documents describe things like 知更鸟's 【协奏】 as
 * 「行动序列上出现【协奏】倒计时，倒计时回合开始时知更鸟退出【协奏】状态并立即行动，倒计时固定拥有 <b>90</b> 点速度」 — the
 * duration of the state is not "N of somebody's turns", it is "until this thing's turn arrives", and its arrival time is
 * decided by a <b>speed</b>. Nothing in the engine could express that before: a timed buff counts somebody's turns, and
 * a `<b>turns: N</b>` spelling would be a different number in every fight (allied advances, breaks and extra turns all
 * move the order around).
 *
 * <p>So the countdown is an ordinary {@link CanHit} with a speed and no life: it is scheduled by the same
 * {@link Queue} as everybody else, which means every mechanic that already moves the action order (advance, delay,
 * break) moves it too — for free and by construction.
 *
 * <p>⚠ <b>What it deliberately is not.</b>
 * <ul>
 *   <li><b>Not in {@code Battle.allies}.</b> It belongs to our camp (so our rules can react to its turn) but it is not
 *       a party member: were it in the roster, 「我方全体」 would buff it, `lowest_hp_ally` could pick it, and it would
 *       be a legal target for every ally-directed effect. Keeping it out of the list is what keeps those sentences
 *       meaning what they say.</li>
 *   <li><b>Not damageable and not killable.</b> {@link #takeDamage} is a no-op and {@link #isDeath} is always false:
 *       a countdown is a clock, and a clock that a stray AoE could delete would silently change a duration.</li>
 *   <li><b>No health, no energy, no skills.</b> Its attribute sheet carries the speed and nothing else that matters
 *       (HEALTH is 1 so nothing divides by zero), so no percentage-of-HP sentence can accidentally read it.</li>
 * </ul>
 */
public class Countdown extends CanHit {

    /**
     * Builds a countdown that enters the order with the given speed.
     *
     * @param name  what it is called in logs (e.g. {@code 协奏倒计时})
     * @param speed the fixed speed that decides when its turn comes (90 for 知更鸟's 【协奏】)
     */
    public Countdown(String name, double speed) {
        this(null, name, speed);
    }

    /**
     * @param owner who placed it ({@code null} = a hand-built one) — the link {@code actor == countdown} reads, so a
     *              rule can tell <b>its own</b> countdown from somebody else's (2026-09-28)
     */
    public Countdown(CanHit owner, String name, double speed) {
        super(name, Camp.PLAYER, blankSheet(speed));
        this.owner = owner;
    }

    /**
     * Who placed this countdown, or {@code null} for a hand-built one.
     *
     * <p>⚠ The link exists for the same reason {@code Summon.master} does: 「倒计时回合开始时<b>知更鸟</b>退出【协奏】」
     * is about <i>her</i> countdown, and without the link a rule can only narrow by something else (the state, in her
     * file's first version) — which is the same answer only while one countdown exists in the fight.
     */
    @lombok.Getter
    private final CanHit owner;

    /** A sheet with just the speed (and a harmless HEALTH of 1) — see the class comment for why nothing else. */
    private static com.laosun.aluminium.models.DoubleValue[] blankSheet(double speed) {
        com.laosun.aluminium.models.DoubleValue[] attributes =
                new com.laosun.aluminium.models.DoubleValue[AttributeType.values().length];
        for (int i = 0; i < attributes.length; i++) {
            attributes[i] = new com.laosun.aluminium.models.DoubleValue(0);
        }
        attributes[AttributeType.HEALTH.ordinal()] = new com.laosun.aluminium.models.DoubleValue(1);
        attributes[AttributeType.SPEED.ordinal()] = new com.laosun.aluminium.models.DoubleValue(speed);
        return attributes;
    }

    /**
     * ⚠ A countdown cannot be killed: see the class comment. This is the whole reason the override exists — the base
     * implementation would let a stray AoE (or a DOT tick) end a duration early, with nothing to report.
     */
    @Override
    public boolean isDeath() {
        return false;
    }

    /** ⚠ And it cannot be damaged at all (which also means it never fires HP-loss style rules about itself). */
    @Override
    public boolean takeDamage(double damage) {
        // deliberately nothing: a clock has no health bar to take it out of
        return false;
    }
}
