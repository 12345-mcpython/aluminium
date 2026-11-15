package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.ShieldBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.entity.CanHit;

/**
 * How long a <b>shield</b> lasts, and the {@code has_shield} condition that asks whether one is still up.
 *
 * <p><b>The hole this closes.</b> A shield used to be a bare number on the combatant
 * ({@code Battle.grantShield}) that <b>nothing ever took off</b>: March 7th's Skill said "持续3回合", the rule carried
 * {@code "turns": 3}, and the field was read, validated and then silently dropped - the shield stayed for the rest
 * of the battle. That is not a cosmetic difference, because "持有护盾的…" is a <b>condition</b>: March 7th's talent (天赋)
 * counter fires "当持有护盾的我方目标受到敌方目标攻击后", so a shield that never expires would have kept that
 * counter alive for the whole fight instead of for three turns. Both halves are pinned here - the lifetime, and
 * what the condition answers while it runs out.
 *
 * <p><b>Where the lifetime lives.</b> In a {@link ShieldBuff} attached to the <b>shielded unit</b>: it installs
 * the value when it lands and takes it off when it expires, so the number and its duration are one fact. The
 * carrier's own turns count it down (the engine's default clock), which is what "持续3回合" means - the shield is
 * on <i>them</i>, so it lasts three of <i>their</i> turns.
 *
 * <p>Note: The shield <b>value</b> is still read the ordinary way ({@code CanHit.getShield()}), which is what the
 * damage path drains: {@code HealShieldTest} keeps pinning that half.
 */
public class ShieldDurationTest {
    private static final double EPS = 1e-9;

    /**
     * The ally the shield lands on. Its table is emptied explicitly below, so no shipped content joins in.
     */
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    /**
     * The speeds the fixture states outright.
     *
     * <p>Note: Not decoration: "持续N回合" is counted in the <b>carrier's</b> turns, so a test that takes "the caster's
     * turn" has to be sure the ally's turn did not slip in first - and two real characters' speeds are a fact
     * about the data that changes whenever a panel is corrected (Tingyun (停云) is already faster than Himeko (姬子)). The fixture
     * therefore puts the caster far ahead and the ally far behind, which makes the order a property of the test.
     */
    private static final double CASTER_SPEED = 200;
    private static final double ALLY_SPEED = 50;

    // ==================================================================
    // 1. The lifetime: spent by the carrier's turns
    // ==================================================================

    /**
     * "持续N回合": the duration is spent in the carrier's turns - up while they remain, gone after the last one,
     * and a partly drained shield still expires on schedule.
     */
    @Test
    public void theDurationIsSpentByItsCarriersTurns() {
        // "持续2回合": up, survives one of the carrier's turns, gone after the second.
        Fixture twoTurns = new Fixture(shieldRule(500, 2, "target"));
        Assertions.assertAll("持续2回合: the timed shield's lifetime",
                () -> Assertions.assertEquals(1, twoTurns.fire(), "the rule fires"),
                () -> Assertions.assertEquals(500, twoTurns.ally.getShield(), EPS, "the shield is installed by the buff"),
                () -> {
                    TestTurns.take(twoTurns.battle, twoTurns.ally);
                    Assertions.assertEquals(500, twoTurns.ally.getShield(), EPS, "one of two turns spent: still up");
                },
                () -> {
                    TestTurns.take(twoTurns.battle, twoTurns.ally);
                    Assertions.assertEquals(0, twoTurns.ally.getShield(), EPS, "the second turn is the last one");
                });

        // Whose turns: the carrier's, not the caster's - "为指定我方单体提供…持续3回合的护盾".
        Fixture casterTurn = new Fixture(shieldRule(500, 1, "target"));
        casterTurn.fire();
        TestTurns.take(casterTurn.battle, casterTurn.caster);
        Assertions.assertAll("the caster's turns do not spend the shield",
                () -> Assertions.assertEquals(500, casterTurn.ally.getShield(), EPS,
                        "the shield is on the ally, so only the ally's turns count it down"));

        // A shield that has been partly drained still expires on schedule - its duration is not "until used up".
        Fixture partlyUsed = new Fixture(shieldRule(500, 1, "target"));
        partlyUsed.fire();
        partlyUsed.ally.takeDamage(200);
        Assertions.assertAll("a partly used shield still expires",
                () -> Assertions.assertEquals(300, partlyUsed.ally.getShield(), EPS, "precondition: partly drained"),
                () -> {
                    TestTurns.take(partlyUsed.battle, partlyUsed.ally);
                    Assertions.assertEquals(0, partlyUsed.ally.getShield(), EPS, "the rest goes with the duration");
                });
    }

    // ==================================================================
    // 2. The lifetime: whose grant owns the standing shield
    // ==================================================================

    /**
     * The duration belongs to the grant that installed the shield: a shield with no {@code turns} is a plain
     * grant, a raw grant is not taken off by a timed shield's expiry, and a second timed shield replaces the first.
     */
    @Test
    public void theDurationBelongsToTheGrantThatInstalledTheShield() {
        // No turns: the raw grant it always was - only being used up removes it.
        Fixture noTurns = new Fixture(shieldRule(500, null, "target"));
        noTurns.fire();
        Assertions.assertAll("no duration, no buff: the value is a plain grant",
                () -> Assertions.assertFalse(noTurns.ally.getBuffManager().hasBuff(ShieldBuff.class),
                        "no duration, no buff: the value is a plain grant"),
                () -> {
                    TestTurns.take(noTurns.battle, noTurns.ally);
                    TestTurns.take(noTurns.battle, noTurns.ally);
                    TestTurns.take(noTurns.battle, noTurns.ally);
                    Assertions.assertEquals(500, noTurns.ally.getShield(), EPS, "turns do not touch it");
                },
                () -> {
                    noTurns.ally.takeDamage(5000);
                    Assertions.assertEquals(0, noTurns.ally.getShield(), EPS, "being used up does");
                });

        // A raw grant after a timed one is not taken off by that one's expiry: the value alone cannot say whose
        // shield is standing there, and Battle.grantShield (a permanent grant) is a different promise.
        Fixture rawGrant = new Fixture(shieldRule(500, 1, "target"));
        rawGrant.fire();
        rawGrant.battle.grantShield(rawGrant.ally, 700);                 // a raw grant overwrites and owns the shield
        TestTurns.take(rawGrant.battle, rawGrant.ally);                  // the timed buff expires here
        Assertions.assertAll("a raw grant is not taken off by an expiring timed shield",
                () -> Assertions.assertEquals(700, rawGrant.ally.getShield(), EPS,
                        "the timed shield took off its own value, not the raw one that replaced it"));

        // A second timed shield replaces the first, exactly like the value itself does.
        Fixture replacement = new Fixture(shieldRule(500, 5, "target"), shieldRule(900, 1, "target"));
        replacement.fire();
        Assertions.assertAll("a second timed shield replaces the first",
                () -> Assertions.assertEquals(900, replacement.ally.getShield(), EPS, "the later shield wins"),
                () -> Assertions.assertEquals(1, replacement.ally.getBuffManager().countBuffs(ShieldBuff.class),
                        "and only one timed shield is attached: same kind replaces"),
                () -> {
                    TestTurns.take(replacement.battle, replacement.ally);
                    Assertions.assertEquals(0, replacement.ally.getShield(), EPS, "the lasting one is the second, with 1 turn");
                });
    }

    // ==================================================================
    // 3. The condition: "持有护盾的…"
    // ==================================================================

    /**
     * {@code target has_shield} asks whether the named subject still holds a shield; the negation is the same
     * question the other way, and {@code self} is the rule's owner, not the event's target.
     */
    @Test
    public void theConditionTracksWhetherTheNamedSubjectHoldsAShield() {
        // The shield is granted by hand rather than by a second rule in the same table: rules fire in order
        // within one event, so a shield rule would have made the first firing below carry a shield already --
        // and the test would have been measuring the ordering instead of the condition.
        Fixture condition = new Fixture(conditionRule("target has_shield"));
        Assertions.assertAll("target has_shield",
                () -> Assertions.assertEquals(0, fireCondition(condition),
                        "no shield yet: 「持有护盾的」 (one that holds a shield) is false"),
                () -> {
                    condition.battle.grantShield(condition.ally, 500);
                    Assertions.assertEquals(1, fireCondition(condition), "with a shield, the condition holds");
                },
                () -> {
                    condition.ally.takeDamage(5000);
                    Assertions.assertEquals(0, fireCondition(condition),
                            "a shield that has been used up is gone -- otherwise 「持有护盾的」 (one that holds a shield) would stay true forever");
                });

        // Its negation is the other half of the same question, and is read the same way.
        Fixture negated = new Fixture(conditionRule("!target has_shield"));
        Assertions.assertAll("!target has_shield",
                () -> Assertions.assertEquals(1, fireCondition(negated),
                        "no shield: 「没有护盾的」 (one that has no shield) holds"),
                () -> {
                    negated.battle.grantShield(negated.ally, 500);
                    Assertions.assertEquals(0, fireCondition(negated), "and stops holding once one is up");
                });

        // The subject decides whose shield is asked about: `self` is the rule's owner, not the event's target.
        Fixture subject = new Fixture(conditionRule("self has_shield"));
        subject.battle.grantShield(subject.ally, 500);
        Assertions.assertAll("self has_shield reads the rule owner",
                () -> Assertions.assertEquals(0, fireCondition(subject),
                        "the ALLY holds the shield, and `self` is the caster"),
                () -> {
                    subject.battle.grantShield(subject.caster, 500);
                    Assertions.assertEquals(1, fireCondition(subject), "now the caster holds one");
                });
    }

    // ==================================================================
    // 4. Fail fast
    // ==================================================================

    /**
     * Healing has no duration: HP comes back and stays back, so a {@code turns} there is a mistake.
     */
    @Test
    public void healingCannotClaimADuration() {
        EffectSpec heal = TriggerSpecs.heal(500, "target");
        TriggerSpecs.set(heal, "turns", 3);

        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(ALLY, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, heal))));
        Assertions.assertTrue(rejected.getMessage().contains("HEAL"), rejected.getMessage());
    }

    /**
     * A timed shield needs a positive duration; "0 turns" is a shield that expires before it can be used.
     */
    @Test
    public void aShieldWithANonPositiveDurationIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(ALLY, List.of(shieldRule(500, 0, "target"))));

        Assertions.assertTrue(rejected.getMessage().contains("turns"), rejected.getMessage());
    }

    /**
     * {@code has_shield} takes no argument, and the mistake is named rather than ignored.
     */
    @Test
    public void hasShieldRejectsAnArgument() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(ALLY, List.of(conditionRule("target has_shield 500"))));

        Assertions.assertTrue(rejected.getMessage().contains("has_shield"), rejected.getMessage());
    }

    /**
     * And it cannot be asked on {@code BATTLE_START}, which carries neither an actor nor a target.
     *
     * <p>The generic carried-party guard, the same one {@code has_state} / {@code is_ally} already obey: a rule
     * gated on "目标持有护盾" there could never be true, and silence is not an acceptable answer for that.
     */
    @Test
    public void hasShieldOnBattleStartIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(ALLY, List.of(TriggerSpecs.rule("BATTLE_START",
                        List.of("target has_shield"), TriggerSpecs.gainEnergy(1)))));

        Assertions.assertTrue(rejected.getMessage().contains("target"), rejected.getMessage());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * One battle: a caster with the rules under test, an ally to shield, and one enemy.
     */
    private static final class Fixture {
        private final Character caster;
        private final Character ally;
        private final Battle battle;

        private Fixture(TriggerSpec... specs) {
            this(1002, ALLY, specs);
        }

        private Fixture(int casterCid, int allyCid, TriggerSpec... specs) {
            this.caster = CharacterFactory.create(casterCid, LEVEL);
            this.ally = CharacterFactory.create(allyCid, LEVEL);
            // Stated speeds: the caster acts first, always (see CASTER_SPEED).
            caster.setAttribute(AttributeType.SPEED, new DoubleValue(CASTER_SPEED));
            ally.setAttribute(AttributeType.SPEED, new DoubleValue(ALLY_SPEED));
            // Both tables are stated explicitly, so no shipped content can join in: the numbers below are the
            // rules built here and nothing else.
            caster.setTriggerTable(new TriggerTable(casterCid, List.of(specs)));
            ally.setTriggerTable(new TriggerTable(allyCid, List.of()));
            this.battle = new Battle(List.of(caster, ally), List.of(dummy()), BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * Fires the attack event with the ally as its subject -- the shield's target.
         */
        private int fire() {
            return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, caster, ally, 1, 0));
        }
    }

    /**
     * Fires the same event and reports whether the condition-only rule matched.
     */
    private static int fireCondition(Fixture f) {
        return f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, f.caster, f.ally, 1, 0));
    }

    /**
     * A rule that shields the event's subject.
     */
    private static TriggerSpec shieldRule(double amount, Integer turns, String target) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, TriggerSpecs.shield(amount, turns, target));
    }

    /**
     * A rule gated on {@code condition}, whose effect is a fingerprint the caller counts (1 energy).
     */
    private static TriggerSpec conditionRule(String condition) {
        return TriggerSpecs.rule("ALLY_ATTACK", List.of(condition), TriggerSpecs.gainEnergy(1));
    }

    private static Enemy dummy() {
        return BattleFixture.monster();
    }
}
