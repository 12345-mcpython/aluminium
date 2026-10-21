package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Countdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The countdown units a battle has placed on the action order.
 *
 * <p><b>Why this is separate.</b> A countdown is not an ally or an enemy: it belongs to our camp but
 * must not be targetable, and it exists only to have a turn. Keeping its list next to the camps made
 * that distinction easy to lose; this object owns the list and the two ownership queries.
 */
public final class Countdowns {

    private final Battle battle;
    private final List<Countdown> countdowns = new ArrayList<>();

    /**
     * @param battle the battle whose action queue receives the countdown
     */
    public Countdowns(Battle battle) {
        this.battle = battle;
    }

    /**
     * Places an unattributed countdown.
     *
     * @param name  its log name
     * @param speed its fixed speed
     * @return the countdown
     */
    public Countdown start(String name, double speed) {
        return start(null, name, speed);
    }

    /**
     * Places a countdown owned by {@code owner}.
     *
     * @param owner who placed it, or {@code null}
     * @param name  its log name
     * @param speed its fixed speed
     * @return the countdown
     */
    public Countdown start(CanHit owner, String name, double speed) {
        Countdown countdown = new Countdown(owner, name, speed);
        countdowns.add(countdown);
        battle.queue.addCombatant(countdown);
        return countdown;
    }

    /**
     * @return an unmodifiable view of every countdown, in placement order
     */
    public List<Countdown> all() {
        return Collections.unmodifiableList(countdowns);
    }

    /**
     * @param owner the summoner
     * @return the countdowns it placed, in placement order
     */
    public List<Countdown> ownedBy(CanHit owner) {
        List<Countdown> mine = new ArrayList<>();
        for (Countdown countdown : countdowns) {
            if (countdown.getOwner() == owner) {
                mine.add(countdown);
            }
        }
        return mine;
    }
}
