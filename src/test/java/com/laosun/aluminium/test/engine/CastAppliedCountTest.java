package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "终结技<b>每冻结1个目标</b>，为三月七恢复6点能量" - a magnitude that is the <b>outcome</b> of the cast, not its aim.
 *
 * <p><b>Why the vocabulary needed it.</b> {@code per_target} multiplies by the number of targets an event
 * <i>aimed at</i>; this sentence multiplies by the number the 50%-per-target roll actually <b>let through</b>. Those
 * are different numbers (0...3 against three enemies), and the engine is the only party that knows the second: the
 * content cannot compute it, and a rule that guessed "3" would pay three times the text's figure with nothing to
 * report. So {@code APPLY_CONTROL} / {@code APPLY_DOT} record what they landed ({@code Battle.recordCastApplied}),
 * and a rule reads the count as a derived magnitude ({@code "scale": "cast_applied:冻结"}).
 *
 * <p><b>The four things this file pins.</b>
 * <ol>
 *   <li>the count is the <b>landed</b> one - a resisted application is not counted (the whole reason it exists);</li>
 *   <li>it belongs to <b>one cast</b>: the next cast starts from zero, and nothing later can read the old numbers;</li>
 *   <li>the loader refuses the spelling where the count cannot exist (a non-cast event) and where the state name is
 *       not one this engine rolls for - both are silent-zero mistakes otherwise;</li>
 *   <li>March 7th (三月七)'s own file pays 6 per landed freeze and nothing at Eidolon (星魂) 0.</li>
 * </ol>
 */
public class CastAppliedCountTest {
    private static final double EPS = 1e-6;

    /** March 7th (三月七) - the reader, and the character whose energy bar makes the count observable. */
    private static final int MARCH_7TH = 1001;
    private static final int LEVEL = 80;
    private static final int ULTIMATE_SLOT = 3;
    /** 1003010 states no {@code STAT_CTRL_*} resistances, unlike Ice Edge (冰锋) (which cannot be frozen at all). */
    private static final int MONSTER = 1003010;

    /**
     * Three enemies, all frozen, pay three times.
     *
     * <p>Stated as the <b>difference</b> between two identical casts that differ only in whether the freeze lands:
     * casting an ultimate also grants the skill's own energy, so an absolute number here would be measuring that
     * plus the counter. The contrast cancels everything except the counter.
     */
    @Test
    public void everyLandedApplicationPaysOnce() {
        double allFrozen = energyFromAnUltimate(3, 0);
        double noneFrozen = energyFromAnUltimate(3, 3);

        Assertions.assertEquals(18, allFrozen - noneFrozen, EPS,
                "6 energy × the 3 targets the freeze really landed on");
    }

    /** ...and a partial result pays partially: two of three frozen is 12, not 18 and not 0. */
    @Test
    public void aPartialResultPaysPartially() {
        Assertions.assertEquals(12, energyFromAnUltimate(3, 1) - energyFromAnUltimate(3, 3), EPS);
    }

    /**
     * Note: The case that separates this from {@code per_target: true}.
     *
     * <p>Three targets are aimed at and none can be frozen - the aim count is 3 and the landed count is 0. So the
     * counter's own contribution must be <b>exactly nothing</b> here: the comparison is against the same cast with
     * no counter rule at all, which cancels the skill's own energy and leaves only what the counter added. An
     * implementation that read the aim count would add 3  x  6 = 18 and go red.
     */
    @Test
    public void theAimedCountIsNotTheLandedCount() {
        double withCounter = energyFromAnUltimate(3, 3);
        double withoutCounter = energyFromAnUltimate(freezeOnlyHero(), 3, 3);

        Assertions.assertEquals(withoutCounter, withCounter, EPS,
                "a fully resisted ultimate pays nothing, however many enemies it hit");
    }

    /**
     * Note: A state applied <b>outside</b> a cast must not be paid for by the next one.
     *
     * <p>The counter can be written by an op that is not part of a cast at all - a {@code TAKING_HIT} rule that
     * freezes whoever hit her is the live example - so "one cast" has to be enforced at the <b>start</b> of a cast as
     * well as at the end of one. This is the case that pins the start: the same ultimate is cast twice, once in a
     * battle where an out-of-cast freeze already happened and once in a battle where it did not, and the counter's
     * contribution must be identical (nothing landed in either ultimate).
     */
    @Test
    public void anOutOfCastApplicationDoesNotPayTheNextCast() {
        // Freezable enemies, so the out-of-cast freeze really lands (a1) and the ultimate then lands its own (3).
        Battle contaminated = ultimateBattle(heroWithAnOutOfCastFreeze(), 3, 0);
        // The freeze happens on a hit she takes, long before the ultimate: `attacker` is the one who gets frozen.
        contaminated.fireTriggers(TriggerEvent.TAKING_HIT, contaminated.enemyUnits().getFirst(),
                contaminated.characters.getFirst(), 1, 0);

        double withStale = castUltimate(contaminated);
        double clean = energyFromAnUltimate(3, 0);

        Assertions.assertEquals(clean, withStale, EPS,
                "the ultimate froze 3 targets, so it pays 18 -- the earlier freeze belongs to no cast and must not "
                        + "make it 24");
    }

    /**
     * The count belongs to <b>one cast</b>, and the record is gone once that cast's events have been delivered.
     *
     * <p>Both halves matter: a second cast must not add to the first (that would make "每冻结1个目标" pay for
     * freezes from earlier turns), and a rule firing outside the window must not read a stale number at all.
     */
    @Test
    public void theCountIsPerCastAndIsForgottenAfterwards() {
        Battle battle = ultimateBattle(3, 1);

        double first = castUltimate(battle);
        Assertions.assertEquals(12, first, EPS, "two of three frozen on the first cast");
        Assertions.assertEquals(0, battle.castAppliedCount("冻结"),
                "once the cast's events are delivered, its record is cleared");

        double second = castUltimate(battle);
        Assertions.assertEquals(12, second, EPS, "the second cast starts from zero instead of adding to the first");
    }

    /** Note: A rule that reads the count on an event outside a cast is refused at load, not answered with 0. */
    @Test
    public void readingTheCountOutsideACastIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("TAKING_HIT", null, countedEnergy("冻结", 6))));

        Assertions.assertTrue(refused.getMessage().contains("TAKING_HIT"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("cast_applied:冻结"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("ULT_CAST"), "the message lists the cast events");
    }

    /** Note: A state name this engine does not roll for is refused: a typo would make the counter answer 0 forever. */
    @Test
    public void anUnknownStateNameIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("ULT_CAST", null, countedEnergy("冰冻", 6))));

        Assertions.assertTrue(refused.getMessage().contains("冰冻"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("冻结"), "the message lists the states it knows");
    }

    /** The DOT spelling counts too, through the same table that maps an element to its state name. */
    @Test
    public void theCountAlsoWorksForADotAppliedByTheCast() {
        Character hero = hero();
        EffectSpec burn = TriggerSpecs.dot("Fire", null, "self_attr:ATTACK", 0.5, 2, null);
        TriggerSpecs.set(burn, "target", "all_enemies");
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, List.of(
                TriggerSpecs.rule("ULT_CAST", null, burn),
                TriggerSpecs.rule("ULT_CAST", null, countedEnergy("灼烧", 4)))));
        Battle battle = ultimateBattle(hero, 3, 0);

        double gained = castUltimate(battle);

        Assertions.assertEquals(12, gained, EPS, "3 landed burns × 4 energy");
    }

    // ==================================================================
    // The shipped content: 1001 Eidolon (星魂) 1
    // ==================================================================

    /**
     * The shipped file pays 6 per landed freeze, and only from Eidolon (星魂) 1 up.
     *
     * <p>Both ranks are measured in the same battle shape, so the difference <b>is</b> the Eidolon: the Eidolon (星魂) 0 run
     * still freezes the same three enemies (that rule belongs to her base kit), and pays nothing for them.
     */
    @Test
    public void theShippedEidolonPaysPerLandedFreeze() {
        double withEidolon = shippedUltimateEnergy(1);
        double withoutEidolon = shippedUltimateEnergy(0);

        Assertions.assertEquals(18, withEidolon - withoutEidolon, EPS,
                "「终结技每冻结1个目标，为三月七恢复6点能量」 (for every target the Ultimate freezes, March 7th restores 6 Energy) × the 3 enemies her ultimate froze");
    }

    /** ...and her file really states the count, rather than the aim count that would pay the same 18 here. */
    @Test
    public void theShippedRuleReadsTheLandedCount() {
        // The context's actor is the character: her cast rules are all gated on `actor == self`, so a context
        // without one would match nothing and the "the rule is gone" failure below would be a lie.
        Character owner = CharacterFactory.create(MARCH_7TH, LEVEL, true, null, null, 1);
        EffectSpec effect = com.laosun.aluminium.data.TriggerTables.of(MARCH_7TH)
                .matching(TriggerEvent.ULT_CAST, new TriggerTable.TriggerContext(owner, owner, null, 0, 0))
                .stream()
                .filter(rule -> rule.minEidolon() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eidolon (星魂) 1's rule is gone from characters/1001.json"))
                .effects().getFirst();

        Assertions.assertEquals("GAIN_ENERGY", effect.getOp());
        Assertions.assertEquals("cast_applied:冻结", effect.getScale());
        Assertions.assertEquals(6, effect.getPercent(), EPS);
        Assertions.assertNull(effect.getPerTarget(), "per_target would count the enemies it aimed at, not the ones it froze");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The energy one ultimate of {@code hero}'s table grants, with {@code immune} of the {@code enemies} unable to be
     * frozen.
     *
     * <p>Note: The generator is pinned at 0 so a stated base chance of 1 is certain: every uncertainty is removed except
     * the one being measured (which targets the roll let through).
     */
    private static double energyFromAnUltimate(int enemies, int immune) {
        return energyFromAnUltimate(countingHero(), enemies, immune);
    }

    private static double energyFromAnUltimate(Character hero, int enemies, int immune) {
        Battle battle = ultimateBattle(hero, enemies, immune);
        return castUltimate(battle);
    }

    /** One cast of March 7th (三月七)'s ultimate data, measured as the energy it granted. */
    private static double castUltimate(Battle battle) {
        Character hero = battle.characters.getFirst();
        double before = hero.getCurrentEnergy();
        battle.castImmediate(new DefaultSkill(MARCH_7TH, ULTIMATE_SLOT, 1), hero,
                List.copyOf(battle.enemyUnits()));
        return hero.getCurrentEnergy() - before;
    }

    /** The shipped Eidolon (星魂) 1 measurement: her real file, her real ultimate, {@code rank} Eidolons active. */
    private static double shippedUltimateEnergy(int rank) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL, true, null, null, rank);
        Battle battle = ultimateBattle(hero, 3, 0);
        return castUltimate(battle);
    }

    /** A hero whose table is only the two rules under test: freeze everything, then pay per landed freeze. */
    private static Character countingHero() {
        Character hero = hero();
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, List.of(
                TriggerSpecs.rule("ULT_CAST", null,
                        TriggerSpecs.applyControl("冻结", 1, 1.0, "all_enemies")),
                TriggerSpecs.rule("ULT_CAST", null, countedEnergy("冻结", 6)))));
        return hero;
    }

    /** The same cast with the counter rule removed - the control for "what did the counter itself add". */
    private static Character freezeOnlyHero() {
        Character hero = hero();
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, List.of(
                TriggerSpecs.rule("ULT_CAST", null,
                        TriggerSpecs.applyControl("冻结", 1, 1.0, "all_enemies")))));
        return hero;
    }

    /**
     * The counter's own table plus a freeze that happens on a hit she takes - an application outside any cast.
     *
     * <p>It is the shape March 7th (三月七)'s own talent has (a {@code TAKING_HIT} rule), and the point is that its freeze must
     * not be inherited by the next ultimate.
     */
    private static Character heroWithAnOutOfCastFreeze() {
        Character hero = hero();
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, List.of(
                TriggerSpecs.rule("TAKING_HIT", null,
                        TriggerSpecs.applyControl("冻结", 1, 1.0, "attacker")),
                TriggerSpecs.rule("ULT_CAST", null,
                        TriggerSpecs.applyControl("冻结", 1, 1.0, "all_enemies")),
                TriggerSpecs.rule("ULT_CAST", null, countedEnergy("冻结", 6)))));
        return hero;
    }

    private static Character hero() {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL);
        // The energy bar is the observable, so it must not be a stack-resource character's (March 7th (三月七)'s is 120).
        Assertions.assertTrue(hero.getMaxEnergy() > 0, "precondition: the reader has an energy bar");
        return hero;
    }

    private static Battle ultimateBattle(int enemies, int immune) {
        return ultimateBattle(countingHero(), enemies, immune);
    }

    /** The battle shape: {@code enemies} ordinary monsters, {@code immune} of them unfreezable. */
    private static Battle ultimateBattle(Character hero, int enemies, int immune) {
        List<Enemy> monsters = new java.util.ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            Enemy monster = EnemyFactory.create(MONSTER, 90, 1);
            monster.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
            if (i < immune) {
                // Its own data-level immunity: the control cannot land on it however good the roll is.
                monster.setDebuffResist(Map.of("STAT_CTRL_Frozen", 1.0));
            }
            monsters.add(monster);
        }
        Battle battle = new Battle(List.of(hero), monsters, fixed(0.0));
        battle.startBattle();
        return battle;
    }

    /** A {@code GAIN_ENERGY} whose magnitude is "per landed application of this state". */
    private static EffectSpec countedEnergy(String state, double perApplication) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_ENERGY");
        TriggerSpecs.set(effect, "scale", "cast_applied:" + state);
        TriggerSpecs.set(effect, "percent", perApplication);
        return effect;
    }

    /** A generator that always answers the same value, so "a 100% base chance" really is certain. */
    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    /** Compiles one rule into a table, which is where load-time validation runs. */
    private static TriggerTable tableOf(TriggerSpec rule) {
        return new TriggerTable(MARCH_7TH, List.of(rule));
    }
}
