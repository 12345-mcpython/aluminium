package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Countdown;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The <b>countdown</b> unit: a unit that exists on the action order in order to <b>have a turn</b>.
 *
 * <p><b>The sentence that needs it.</b> Robin (知更鸟)'s [协奏] lasts "until the turn the [协奏] countdown begins", and that countdown "has a fixed
 * 90 SPD" - the state's duration is a fact about the <b>action order</b>, not a number of anybody's turns. A
 * `turns: N` spelling would be a different duration in every fight (advances, delays and breaks all move the order),
 * which is why the engine had to grow a unit rather than a field.
 *
 * <p><b>What is pinned here.</b> That the countdown is scheduled by the same queue as everybody else, that its turn
 * announces itself ({@code COUNTDOWN_TURN}) so content can answer it with vocabulary that already exists, that it is
 * <b>not</b> a party member ("all of our side" must not see it) and that it cannot be killed (a stray AoE must not end a
 * duration early).
 */
public class CountdownTest {
    private static final int MARCH = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Its own turn is announced to our rules, which is the whole point of the unit. */
    @Test
    public void itsTurnIsAnnouncedToOurRules() {
        Fixture f = new Fixture(markerOnCountdownTurn());
        Countdown countdown = f.battle.startCountdown("协奏倒计时", 90);

        Signal signal = f.battle.queue.snapshot().stream()
                .filter(s -> s.getCanHit() == countdown)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the countdown has no signal on the action order"));
        double before = f.hero.getAttribute(AttributeType.ATTACK).get();

        f.battle.currentMove = signal;
        f.battle.beforeMove();

        Assertions.assertTrue(f.hero.getAttribute(AttributeType.ATTACK).get() > before,
                "the countdown's turn fired COUNTDOWN_TURN and the rule answered it with an ATTACK buff "
                        + "(before " + before + ", after " + f.hero.getAttribute(AttributeType.ATTACK).get() + ")");
    }

    /**
     * Note: `actor == countdown` means <b>my</b> countdown, not "a countdown": another unit's clock must not answer her
     * rule.
     */
    @Test
    public void somebodyElsesCountdownIsNotMine() {
        Fixture f = new Fixture(markerOnCountdownTurn("actor == countdown"));
        Character other = CharacterFactory.create(ALLY, LEVEL);
        CharacterFactory.create(MARCH, LEVEL);
        Countdown mine = f.battle.startCountdown(f.hero, "我的倒计时", 90);
        Countdown theirs = f.battle.startCountdown(other, "别人的倒计时", 90);

        Assertions.assertTrue(f.battle.countdownsOf(f.hero).contains(mine));
        Assertions.assertFalse(f.battle.countdownsOf(f.hero).contains(theirs),
                "the owner link is what tells them apart");

        double before = f.hero.getAttribute(AttributeType.ATTACK).get();
        f.battle.currentMove = f.signalFor(theirs);
        f.battle.beforeMove();
        Assertions.assertEquals(before, f.hero.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "somebody else's countdown does not fire her rule");

        f.battle.currentMove = f.signalFor(mine);
        f.battle.beforeMove();
        Assertions.assertTrue(f.hero.getAttribute(AttributeType.ATTACK).get() > before, "…and hers does");
    }

    /** Note: It is not a party member: "all of our side" must not see it, or every group sentence would include a clock. */
    @Test
    public void itIsNotInTheParty() {
        Fixture f = new Fixture(null);
        Countdown countdown = f.battle.startCountdown("协奏倒计时", 90);

        Assertions.assertEquals(2, f.battle.allies.size(),
                "the party is the two characters -- the countdown is scheduled but not one of them");
        Assertions.assertTrue(f.battle.allies.stream().noneMatch(unit -> unit instanceof Countdown));
        Assertions.assertTrue(f.battle.getOpponents(f.hero).stream().noneMatch(unit -> unit instanceof Countdown),
                "…and it is not an enemy either");
    }

    /** Note: A clock cannot be killed: a stray AoE must not end a duration early. */
    @Test
    public void itCannotBeKilledOrDamaged() {
        Fixture f = new Fixture(null);
        Countdown countdown = f.battle.startCountdown("协奏倒计时", 90);

        Assertions.assertFalse(countdown.takeDamage(99_999), "damage is a no-op on a countdown");
        Assertions.assertFalse(countdown.isDeath(), "and it is never dead");
    }

    /** The shape is refused at load: no speed, a non-positive speed, or a field it does not read. */
    @Test
    public void theShapeIsRefusedAtLoad() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(countdown(null, null)),
                "a countdown that never acts is a duration that never ends");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(countdown(0.0, null)), "speed 0");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(countdown(90.0, 3.0)),
                "a field it does not read (amount)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character hero;
        private final Battle battle;

        private Signal signalFor(CanHit unit) {
            return battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == unit)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for that unit"));
        }

        private Fixture(TriggerSpec rule) {
            hero = CharacterFactory.create(MARCH, LEVEL);
            hero.setTriggerTable(new TriggerTable(MARCH, rule == null ? List.of() : List.of(rule)));
            battle = new Battle(List.of(hero, CharacterFactory.create(ALLY, LEVEL)),
                    List.of(EnemyFactory.create(MONSTER, 90, 1)), fixed());
            battle.startBattle();
        }
    }

    /** A rule that fires on a countdown's turn and leaves a visible mark. */
    private static TriggerSpec markerOnCountdownTurn() {
        return markerOnCountdownTurn(null);
    }

    private static TriggerSpec markerOnCountdownTurn(String condition) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule(TriggerEvent.COUNTDOWN_TURN.value(),
                condition == null ? null : List.of(condition), effect);
    }

    private static TriggerSpec countdown(Double speed, Double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "START_COUNTDOWN");
        TriggerSpecs.set(effect, "speed", speed);
        TriggerSpecs.set(effect, "amount", amount);
        return TriggerSpecs.rule("ULT_CAST", null, effect);
    }

    private static TriggerTable table(TriggerSpec rule) {
        return new TriggerTable(MARCH, List.of(rule));
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
