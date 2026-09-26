package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A rule-level probability: 「有 X% 的固定概率…」 ({@code "chance": 0.35}).
 *
 * <p><b>Why it is a rule field and not an effect argument.</b> The texts that need it say "this effect has a
 * chance to happen" — one roll decides the whole rule — and it belongs next to {@code cooldown} /
 * {@code once_per_battle}, which are the same kind of statement ("how often may this fire").
 *
 * <p><b>How the tests are made deterministic.</b> The roll goes through the battle's <b>injected</b> generator,
 * so these cases hand in a generator that answers a fixed value: {@code 0.5} against {@code chance: 0.6} fires,
 * against {@code 0.4} does not. That is also the project's rule for randomness (never a fresh {@code Random}),
 * and it is what makes "a failed roll costs nothing" checkable rather than probabilistic.
 */
public class TriggerChanceTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;

    @Test
    public void aRollBelowTheChanceFires() {
        Battle battle = battleWith(fixed(0.5), chanceRule(0.6, null));
        drainSkillPoints(battle);

        Assertions.assertEquals(1, fire(battle, battle.characters.getFirst()), "0.5 < 0.6");
        Assertions.assertEquals(1, battle.getSkillPoints());
    }

    @Test
    public void aRollAboveTheChanceDoesNot() {
        Battle battle = battleWith(fixed(0.5), chanceRule(0.4, null));
        drainSkillPoints(battle);

        Assertions.assertEquals(0, fire(battle, battle.characters.getFirst()), "0.5 is not below 0.4");
        Assertions.assertEquals(0, battle.getSkillPoints(), "and nothing ran");
    }

    /**
     * A failed roll must cost nothing.
     *
     * <p>The point is concrete: with a cooldown too, a rule that started its cooldown on a failed roll would
     * throw the cooldown away on the (1 - chance) of attempts where it did not fire — a 35% trace would spend
     * 65% of its opportunities on nothing.
     *
     * <p>The generator answers 0.9 first and 0.1 afterwards, so the first attempt fails and the second succeeds
     * <b>in the same turn</b>. A fixed generator would fail both times and prove nothing about the cooldown —
     * which is exactly how the first version of this test read.
     */
    @Test
    public void aFailedRollDoesNotStartTheCooldown() {
        Battle battle = battleWith(sequence(0.9, 0.1), chanceRule(0.4, 1));
        Character owner = battle.characters.getFirst();
        drainSkillPoints(battle);

        Assertions.assertEquals(0, fire(battle, owner), "the first roll fails");
        Assertions.assertEquals(1, fire(battle, owner),
                "and the rule is still ready: a failed roll means 'nothing happened', not 'it fired and is on "
                        + "cooldown'");
    }

    @Test
    public void chanceOneAlwaysFiresWithoutConsumingADraw() {
        CountingRandom rng = new CountingRandom(0.99);
        Battle battle = new Battle(List.of(ownerWith(chanceRule(1.0, null))), List.of(dummy()), rng);
        battle.startBattle();
        drainSkillPoints(battle);
        int drawsBefore = rng.draws();

        Assertions.assertEquals(1, fire(battle, battle.characters.getFirst()));
        Assertions.assertEquals(drawsBefore, rng.draws(),
                "a certainty is not a coin flip, so it must not consume a draw from the battle's generator");
    }

    @Test
    public void aChanceOfZeroOrAboveOneIsRejectedAtLoadTime() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(chanceRule(0.0, null))));
        Assertions.assertTrue(zero.getMessage().contains("chance"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(chanceRule(1.5, null))),
                "a probability is a fraction of 1, not a percentage");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A generator that always answers the same value, so a coin flip becomes an assertion. */
    static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    /** A generator that answers the given values in order, then repeats the last one. */
    private static Random sequence(double first, double... rest) {
        return new Random() {
            private final double[] values = rest.length == 0 ? new double[]{first} : prepend(first, rest);
            private int index;

            @Override
            public double nextDouble() {
                double value = values[Math.min(index, values.length - 1)];
                index++;
                return value;
            }

            private static double[] prepend(double first, double[] rest) {
                double[] all = new double[rest.length + 1];
                all[0] = first;
                System.arraycopy(rest, 0, all, 1, rest.length);
                return all;
            }
        };
    }

    /** The same, counting how many draws were taken. */
    private static final class CountingRandom extends Random {
        private final double value;
        private int draws;

        CountingRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            draws++;
            return value;
        }

        int draws() {
            return draws;
        }
    }

    private static TriggerSpec chanceRule(double chance, Integer cooldown) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null, effect);
        TriggerSpecs.set(spec, "chance", chance);
        TriggerSpecs.set(spec, "cooldown", cooldown);
        return spec;
    }

    private static Battle battleWith(Random rng, TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs)), List.of(dummy()), rng);
        battle.startBattle();
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    private static int fire(Battle battle, Character actor) {
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    private static void drainSkillPoints(Battle battle) {
        while (battle.spendSkillPoint()) {
            // drain to zero
        }
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
