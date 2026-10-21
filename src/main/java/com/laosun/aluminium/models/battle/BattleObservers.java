package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;

import java.util.ArrayList;
import java.util.List;

/**
 * The battle's observer registration surface: observers for damage, rules, skipped turns, breaks,
 * controls, heals and shields.
 *
 * <p><b>Why this is separate.</b> Registration is rare and settlement is not, so every list is
 * copy-on-write; a removal replaces the list rather than mutating one an announcement may be iterating.
 * That rule used to be repeated around seven fields in {@code Battle}; here it is one generic list and
 * seven names.
 */
public final class BattleObservers {

    private final ObserverList<Battle.DamageObserver> damage = new ObserverList<>();
    private final ObserverList<Battle.RuleObserver> rule = new ObserverList<>();
    private final ObserverList<Battle.SkipObserver> skip = new ObserverList<>();
    private final ObserverList<Battle.BreakObserver> breakage = new ObserverList<>();
    private final ObserverList<Battle.ControlObserver> control = new ObserverList<>();
    private final ObserverList<Battle.HealObserver> heal = new ObserverList<>();
    private final ObserverList<Battle.ShieldObserver> shield = new ObserverList<>();

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addDamage(Battle.DamageObserver observer) {
        damage.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeDamage(Battle.DamageObserver observer) {
        damage.remove(observer);
    }

    /**
     * @return the damage observers, in registration order
     */
    public List<Battle.DamageObserver> damageObservers() {
        return damage.all();
    }

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addRule(Battle.RuleObserver observer) {
        rule.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeRule(Battle.RuleObserver observer) {
        rule.remove(observer);
    }

    /**
     * Announces that a rule effect was applied.
     *
     * @param owner the unit the rule belongs to
     * @param op    the operation performed
     */
    public void announceRuleFired(CanHit owner, String op) {
        for (Battle.RuleObserver observer : rule.all()) {
            observer.onRuleFired(owner, op);
        }
    }

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addSkip(Battle.SkipObserver observer) {
        skip.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeSkip(Battle.SkipObserver observer) {
        skip.remove(observer);
    }

    /**
     * Announces that a turn was skipped.
     *
     * @param unit   whose turn it was
     * @param broken whether the cause was a break
     */
    public void announceTurnSkipped(CanHit unit, boolean broken) {
        for (Battle.SkipObserver observer : skip.all()) {
            observer.onTurnSkipped(unit, broken);
        }
    }

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addBreak(Battle.BreakObserver observer) {
        breakage.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeBreak(Battle.BreakObserver observer) {
        breakage.remove(observer);
    }

    /**
     * Announces a weakness break.
     *
     * @param attacker who broke it
     * @param target   the unit that was broken
     * @param element  the element that broke it
     */
    public void announceBroken(CanHit attacker, CanHit target, DamageElement element) {
        for (Battle.BreakObserver observer : breakage.all()) {
            observer.onBroken(attacker, target, element);
        }
    }

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addControl(Battle.ControlObserver observer) {
        control.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeControl(Battle.ControlObserver observer) {
        control.remove(observer);
    }

    /**
     * Announces that a control landed.
     *
     * @param applier who applied it
     * @param target  the unit it landed on
     * @param name    the state's name
     */
    public void announceControl(CanHit applier, CanHit target, String name) {
        for (Battle.ControlObserver observer : control.all()) {
            observer.onControl(applier, target, name);
        }
    }

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addHeal(Battle.HealObserver observer) {
        heal.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeHeal(Battle.HealObserver observer) {
        heal.remove(observer);
    }

    /**
     * @return the heal observers, in registration order
     */
    public List<Battle.HealObserver> healObservers() {
        return heal.all();
    }

    /**
     * @param observer the observer, ignored when {@code null}
     */
    public void addShield(Battle.ShieldObserver observer) {
        shield.add(observer);
    }

    /**
     * @param observer the observer, ignored when {@code null} or not registered
     */
    public void removeShield(Battle.ShieldObserver observer) {
        shield.remove(observer);
    }

    /**
     * @return the shield observers, in registration order
     */
    public List<Battle.ShieldObserver> shieldObservers() {
        return shield.all();
    }

    /**
     * A copy-on-write list of observers.
     *
     * @param <T> the observer type
     */
    private static final class ObserverList<T> {
        private volatile List<T> observers = List.of();

        void add(T observer) {
            if (observer == null) {
                return;
            }
            List<T> next = new ArrayList<>(observers);
            next.add(observer);
            observers = List.copyOf(next);
        }

        void remove(T observer) {
            if (observer == null) {
                return;
            }
            List<T> next = new ArrayList<>(observers);
            next.remove(observer);
            observers = List.copyOf(next);
        }

        List<T> all() {
            return observers;
        }
    }
}
