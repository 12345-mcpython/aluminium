package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.BuffManager;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.buff.BoostDamageBuff;
import com.laosun.aluminium.models.buff.StunBuff;
import com.laosun.aluminium.models.event.DamageEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

public class BuffManagerTest {

    private static Character character() {
        return Character.fromAttributes("c", 1000, 100, 100, 100);
    }

    private static List<DoubleValue.Modifier> boostModifiers(Character c) {
        return c.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF);
    }

    @Test
    public void addBuffAppliesEffect() {
        Character c = character();
        c.getBuffManager().addBuff(new BoostDamageBuff(2, .5));

        Assertions.assertEquals(1, boostModifiers(c).size());
        Assertions.assertEquals(.5, boostModifiers(c).getFirst().getValue(), 1e-9);
    }

    @Test
    public void lateBuffTicksOnlyOnAfterMove() {
        Character c = character();
        c.getBuffManager().addBuff(new BoostDamageBuff(1, .5));

        c.getBuffManager().beforeMove();
        Assertions.assertEquals(1, boostModifiers(c).size(), "late buff must not tick on beforeMove");

        c.getBuffManager().afterMove();
        Assertions.assertTrue(boostModifiers(c).isEmpty(), "late buff should expire and remove effect");
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

    @Test
    public void removeBuffRemovesEffect() {
        Character c = character();
        BoostDamageBuff buff = new BoostDamageBuff(2, .5);
        c.getBuffManager().addBuff(buff);

        c.getBuffManager().removeBuff(buff);

        Assertions.assertTrue(boostModifiers(c).isEmpty());
    }

    @Test
    public void sameKindBuffRefreshesInsteadOfStacking() {
        Character c = character();
        c.getBuffManager().addBuff(new BoostDamageBuff(2, .5));

        c.getBuffManager().addBuff(new BoostDamageBuff(1, .7));

        List<DoubleValue.Modifier> buffMods = boostModifiers(c);
        Assertions.assertEquals(1, buffMods.size(), "same-kind buff should replace, not stack");
        Assertions.assertEquals(.7, buffMods.getFirst().getValue(), 1e-9);
    }

    @Test
    public void differentKindsCoexist() {
        Character c = character();
        c.getBuffManager().addBuff(new StunBuff(2));
        c.getBuffManager().addBuff(new BoostDamageBuff(3, .5));

        Assertions.assertFalse(c.getBuffManager().canAct(), "stun should block action");
        Assertions.assertEquals(1, boostModifiers(c).size());
    }

    @Test
    public void clearAllRemovesEverything() {
        Character c = character();
        c.getBuffManager().addBuff(new StunBuff(2));
        c.getBuffManager().addBuff(new BoostDamageBuff(3, .5));

        c.getBuffManager().clearAll();

        Assertions.assertTrue(c.getBuffManager().canAct());
        Assertions.assertTrue(boostModifiers(c).isEmpty());
    }

    // ==================================================================
    // H-7: the buff query point (the prerequisite for P4-6 super break / P8-7 triggers / P10-2 the control state machine)
    // ==================================================================

    @Test
    public void hasBuffFindsMountedBuffsAndSurvivesRemoval() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        BoostDamageBuff buff = new BoostDamageBuff(2, .5);

        Assertions.assertFalse(manager.hasBuff(BoostDamageBuff.class), "not attached yet → false");
        manager.addBuff(buff);
        Assertions.assertTrue(manager.hasBuff(BoostDamageBuff.class), "attached → true");
        Assertions.assertFalse(manager.hasBuff(StunBuff.class), "a category that is not attached → false");

        manager.removeBuff(buff);
        Assertions.assertFalse(manager.hasBuff(BoostDamageBuff.class), "removed → back to false");
    }

    @Test
    public void hasBuffTracksExpiryAndRejectsBadInput() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new BoostDamageBuff(1, .5));      // post-move buff, duration 1

        manager.beforeMove();
        Assertions.assertTrue(manager.hasBuff(BoostDamageBuff.class), "a post-move buff must not expire on beforeMove");

        manager.afterMove();
        Assertions.assertFalse(manager.hasBuff(BoostDamageBuff.class), "after expiry it should no longer be found");
        Assertions.assertFalse(manager.hasBuff(null), "null returns false without blowing up");
    }

    // ==================================================================
    // §12 M-5 / M-12: two stability holes in the manager itself
    // ==================================================================

    /**
     * M-5: {@code clearAll()} must clear the <b>blocked</b> flag as well as the list.
     *
     * <p>{@code blocked} is set when a control buff expires on the turn it was blocking (see
     * {@code stunExpiryStillBlocksTheCurrentTurn}). If clearing every buff leaves that flag standing, the
     * unit stays unable to act even though nothing is on it any more — a stuck unit that no amount of
     * dispelling can free. {@code clearAllRemovesEverything} did not catch it because its stun is never
     * ticked, so {@code blocked} is never set.
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
     * M-12: a buff that reacts to damage by attaching <b>another</b> buff must not blow up.
     *
     * <p>{@code onDamage} walked the live {@code buffs} list, and attaching a buff is exactly what a damage
     * reaction does (additional damage applying vulnerability, a counter attaching a marker, …). That is a
     * {@code ConcurrentModificationException} raised from inside damage settlement — the hardest place to
     * diagnose — or, where the list happens to tolerate it, a silently skipped buff.
     *
     * <p>The pattern is not hypothetical: {@code ExtraTrueDamageTest.DamageReactor} and
     * {@code DamageHookTest}'s anonymous subclasses are already written this way.
     */
    @Test
    public void aBuffMayAttachAnotherBuffWhileReactingToDamage() {
        Character hero = character();
        Enemy enemy = Enemy.fromAttributes("e", 100_000, 100, 100, 100);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));

        // The hero answers every hit by giving itself a damage boost.
        hero.getBuffManager().addBuff(new BoostsItselfWhenHit());
        double before = hero.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        Assertions.assertDoesNotThrow(
                () -> battle.applyDamage(hero, new Damage(enemy, hero, DamageElement.FIRE, DamageType.NORMAL, 100)),
                "attaching a buff from a damage reaction is ordinary content, not an error");

        Assertions.assertTrue(hero.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > before,
                "and the buff it attached must really be there, not skipped by the iteration");
    }

    /** A damage reaction that attaches a second buff — the shape M-12 is about. */
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
     * calls {@code tickEffect} — buff code again, so a buff that attaches another while ticking blew up the
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

    /** An early buff of duration 1 that attaches a boost as it ticks out. */
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
