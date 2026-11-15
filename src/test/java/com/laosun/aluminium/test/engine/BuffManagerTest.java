package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.BoostDamageBuff;
import com.laosun.aluminium.buff.BuffManager;
import com.laosun.aluminium.buff.StunBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.event.DamageEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BuffManagerTest {

    private static Character character() {
        return Character.fromAttributes("c", 1000, 100, 100, 100);
    }

    private static List<DoubleValue.Modifier> boostModifiers(Character c) {
        return c.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF);
    }

    /**
     * The boost buff's whole life on one unit: attach, query, remove, and a same-kind re-application that
     * refreshes instead of stacking. Every clause uses the same fixture (one character, one BUFF-sourced
     * modifier on {@code ALL_DAMAGE_TYPE_BOOST}) and observes the same effect.
     */
    @Test
    public void boostBuffLifecycleThroughEffectAndQuery() {
        Character c = character();
        BuffManager manager = c.getBuffManager();

        Assertions.assertFalse(manager.hasBuff(BoostDamageBuff.class), "not attached yet → false");

        BoostDamageBuff buff = new BoostDamageBuff(2, .5);
        manager.addBuff(buff);

        Assertions.assertAll("boost buff attaches one effect and becomes visible to hasBuff",
                () -> Assertions.assertEquals(1, boostModifiers(c).size(), "attach: one BUFF-sourced boost modifier"),
                () -> Assertions.assertEquals(.5, boostModifiers(c).getFirst().getValue(), 1e-9, "attach: boost value"),
                () -> Assertions.assertTrue(manager.hasBuff(BoostDamageBuff.class), "attached → true"),
                () -> Assertions.assertFalse(manager.hasBuff(StunBuff.class),
                        "a category that is not attached → false"));

        manager.removeBuff(buff);

        Assertions.assertAll("removeBuff drops the effect and the query result",
                () -> Assertions.assertTrue(boostModifiers(c).isEmpty(), "remove: boost modifier gone"),
                () -> Assertions.assertFalse(manager.hasBuff(BoostDamageBuff.class), "removed → back to false"));

        manager.addBuff(new BoostDamageBuff(2, .5));
        manager.addBuff(new BoostDamageBuff(1, .7));

        Assertions.assertAll("a same-kind buff refreshes instead of stacking",
                () -> Assertions.assertEquals(1, boostModifiers(c).size(), "same-kind buff should replace, not stack"),
                () -> Assertions.assertEquals(.7, boostModifiers(c).getFirst().getValue(), 1e-9,
                        "refresh: replaced boost value"));
    }

    /**
     * A late (post-move) buff must survive {@code beforeMove} and expire on {@code afterMove}. The effect
     * and the {@code hasBuff} query are two probes of the same fixture, so they share one scenario.
     */
    @Test
    public void postMoveBuffExpiresOnlyAfterTheMove() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new BoostDamageBuff(1, .5));      // post-move buff, duration 1

        manager.beforeMove();

        Assertions.assertAll("beforeMove: a late buff has not ticked yet",
                () -> Assertions.assertEquals(1, boostModifiers(c).size(), "late buff must not tick on beforeMove"),
                () -> Assertions.assertTrue(manager.hasBuff(BoostDamageBuff.class),
                        "a post-move buff must not expire on beforeMove"));

        manager.afterMove();

        Assertions.assertAll("afterMove: the late buff expires and disappears",
                () -> Assertions.assertTrue(boostModifiers(c).isEmpty(), "late buff should expire and remove effect"),
                () -> Assertions.assertFalse(manager.hasBuff(BoostDamageBuff.class),
                        "after expiry it should no longer be found"),
                () -> Assertions.assertFalse(manager.hasBuff(null), "null returns false without blowing up"));
    }

    /**
     * A control buff and a boost buff share one manager: the control blocks acting while the boost's
     * modifier is present, and {@code clearAll} removes both effects at once.
     */
    @Test
    public void controlAndBoostBuffsCoexistUntilClearAll() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new StunBuff(2));
        manager.addBuff(new BoostDamageBuff(3, .5));

        Assertions.assertAll("different kinds of buff coexist",
                () -> Assertions.assertFalse(manager.canAct(), "stun should block action"),
                () -> Assertions.assertEquals(1, boostModifiers(c).size(), "coexist: one BUFF-sourced boost modifier"));

        manager.clearAll();

        Assertions.assertAll("clearAll removes every effect",
                () -> Assertions.assertTrue(manager.canAct(), "clearAll: action is no longer blocked"),
                () -> Assertions.assertTrue(boostModifiers(c).isEmpty(), "clearAll: boost modifier is gone"));
    }

    @Test
    public void stunExpiryStillBlocksTheCurrentTurn() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new StunBuff(1));

        manager.beforeMove();
        Assertions.assertFalse(manager.canAct(), "the turn a stun expires should still be blocked");
        manager.afterMove();

        manager.beforeMove();
        Assertions.assertTrue(manager.canAct(), "stun should not block after full expiry");
    }

    // ==================================================================
    // a stability hole in the manager itself
    // ==================================================================

    /**
     * {@code clearAll()} must clear the <b>blocked</b> flag as well as the list.
     *
     * <p>{@code blocked} is set when a control buff expires on the turn it was blocking (see
     * {@code stunExpiryStillBlocksTheCurrentTurn}). If clearing every buff leaves that flag standing, the
     * unit stays unable to act even though nothing is on it any more - a stuck unit that no amount of
     * dispelling can free. {@code controlAndBoostBuffsCoexistUntilClearAll} did not catch it because its
     * stun is never ticked, so {@code blocked} is never set.
     */
    @Test
    public void clearAllAlsoClearsTheBlockedFlag() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new StunBuff(1));

        manager.beforeMove();
        Assertions.assertFalse(manager.canAct(), "precondition: the stun expired on its blocking turn");

        manager.clearAll();

        Assertions.assertTrue(manager.canAct(),
                "every buff is gone, so 'blocked' must be gone too -- otherwise the unit can never act again");
    }

    /**
     * a buff that reacts to damage by attaching <b>another</b> buff must not blow up.
     *
     * <p>{@code onDamage} walked the live {@code buffs} list, and attaching a buff is exactly what a damage
     * reaction does (additional damage applying vulnerability, a counter attaching a marker, …). That is a
     * {@code ConcurrentModificationException} raised from inside damage settlement - the hardest place to
     * diagnose - or, where the list happens to tolerate it, a silently skipped buff.
     *
     * <p>The pattern is not hypothetical: {@code ExtraTrueDamageTest.DamageReactor} and
     * {@code DamageHookTest}'s anonymous subclasses are already written this way.
     */
    @Test
    public void aBuffMayAttachAnotherBuffWhileReactingToDamage() {
        Character hero = character();
        Enemy enemy = Enemy.fromAttributes("e", 100_000, 100, 100, 100);
        Battle battle = new Battle(List.of(hero), List.of(enemy), BattleFixture.seeded());

        // The hero answers every hit by giving itself a damage boost.
        hero.getBuffManager().addBuff(new BoostsItselfWhenHit());
        double before = hero.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        Assertions.assertDoesNotThrow(
                () -> battle.applyDamage(hero, new Damage(enemy, hero, DamageElement.FIRE, DamageType.NORMAL, 100)),
                "attaching a buff from a damage reaction is ordinary content, not an error");

        Assertions.assertTrue(hero.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > before,
                "and the buff it attached must really be there, not skipped by the iteration");
    }

    /**
     * A damage reaction that attaches a second buff - the shape described above.
     */
    private static final class BoostsItselfWhenHit extends AbstractBuff implements DamageEvent {
        private BoostsItselfWhenHit() {
            super(5, false);
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            decreaseDuration();
        }

        @Override
        public void onDamage(Battle battle, Damage damage) {
            damage.getDefender().getBuffManager().addBuff(new BoostDamageBuff(3, 0.5));
        }
    }

    /**
     * The same hole on the <b>tick</b> path: {@code processBuffTick} used {@code removeIf}, whose predicate
     * calls {@code tickEffect} - buff code again, so a buff that attaches another while ticking blew up the
     * same way. Both paths are fixed by the same snapshot rule, and both need their own test, because a
     * regression in one would not show up in the other.
     */
    @Test
    public void aBuffMayAttachAnotherBuffWhileTicking() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new AddsABuffWhileTicking());

        Assertions.assertDoesNotThrow(manager::beforeMove,
                "an early buff that attaches another buff while it ticks must not blow up the turn boundary");

        Assertions.assertTrue(manager.hasBuff(BoostDamageBuff.class),
                "the buff it attached must really be there");
        Assertions.assertFalse(manager.hasBuff(AddsABuffWhileTicking.class),
                "and the ticking buff itself expired, as its duration said");
    }

    /**
     * An early buff of duration 1 that attaches a boost as it ticks out.
     */
    private static final class AddsABuffWhileTicking extends AbstractBuff {
        private AddsABuffWhileTicking() {
            super(1, true);
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            target.getBuffManager().addBuff(new BoostDamageBuff(3, 0.5));
            decreaseDuration();
        }
    }
}
