package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * {@code one_of}: a rule executes ONE of its own effects, chosen at random.
 *
 * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - 「随机触发 1 种效果」 - and light cone 21032's 「随机生效 1 个效果」.
 *
 * <p>⚠ The reading that matters is a COUNT, not "did X happen": the two wrong implementations are "both ran" and "neither
 * ran", and {@code assertTrue(X appeared)} is satisfied by the first of those. So the fixture gives each effect its own
 * visible marker and asserts that EXACTLY one appears per firing, over several firings with different seeds.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the five scenarios below, one {@code assertAll} per
 * fixture/clause group so a failing clause still names itself. A positive clause and a rejection never share an
 * {@code assertAll}: the refusals form their own scenarios, beside the positive ones.
 */
public class OneOfEffectTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String A = "标记A";
    private static final String B = "标记B";
    private static final String C = "标记C";

    // ==================================================================
    // 1. The count: exactly one effect per firing, and the field is what makes it one
    // ==================================================================

    /**
     * Exactly one of the three markers is applied per firing, and which one moves with the seed; without the field EVERY
     * effect runs - which is what makes the field meaningful.
     *
     * <p>One fixture (a three-mark TURN_START rule fired once by {@code marksAfterOneFiring}) and one observable (how
     * many of the three markers the unit carries after the firing), so the clause and its control are one scenario.
     */
    @Test
    public void oneOfRunsExactlyOneEffectAndWithoutItEveryEffectRuns() {
        List<Character> firings = new ArrayList<>();
        boolean sawA = false;
        boolean sawB = false;
        for (int seed = 0; seed < 24; seed++) {
            Character wolf = marksAfterOneFiring(seed);
            firings.add(wolf);
            sawA |= wolf.getBuffManager().hasState(A);
            sawB |= wolf.getBuffManager().hasState(B);
        }
        System.out.println("[one_of] over 24 seeds: saw A=" + sawA + " saw B=" + sawB);
        boolean variedWithTheRoll = sawA && sawB;

        Character withoutField = marksAfterOneFiring(0, false);
        int withoutFieldMarkers = markerCount(withoutField);
        System.out.println("[one_of] with no field: " + withoutFieldMarkers + " of 3 markers applied");

        Assertions.assertAll("one_of: exactly one effect per firing, and without the field all of them",
                () -> {
                    for (int seed = 0; seed < firings.size(); seed++) {
                        Assertions.assertEquals(1, markerCount(firings.get(seed)),
                                "seed " + seed + ": exactly one effect must run - 0 means none was picked, 2+ means "
                                        + "the roll happened per effect instead of once");
                    }
                },
                () -> Assertions.assertTrue(variedWithTheRoll,
                        "and the pick varies with the roll - always the same effect would be a constant, not a choice"),
                () -> Assertions.assertEquals(3, withoutFieldMarkers,
                        "a rule without `one_of` runs all of its effects, as it always did"));
    }

    // ==================================================================
    // 2. `always` is not part of the pick
    // ==================================================================

    /**
     * An effect marked {@code always} runs EVERY firing while exactly one of the rest runs.
     *
     * <p>Reader: 1506's 【头号补给盲盒】, whose own 90% is 「造成…伤害，<b>并</b>随机触发 1 种效果」 - the box's damage is not one of
     * the three that can be picked. It is the same rule and not a second one because the box's fixed chance is one roll.
     *
     * <p>One fixture (three marks where C is {@code always}: A/B are the pool) and the two observables it states - C
     * every firing, and exactly one of A/B - so both per-seed sets are entries of one {@code assertAll}.
     */
    @Test
    public void anAlwaysEffectStaysOutOfThePick() {
        List<Character> firings = new ArrayList<>();
        int countA = 0;
        int countB = 0;
        for (int seed = 0; seed < 24; seed++) {
            Character wolf = marksWithAnAlwaysEffect(seed);
            firings.add(wolf);
            boolean constant = wolf.getBuffManager().hasState(C);
            boolean a = wolf.getBuffManager().hasState(A);
            boolean b = wolf.getBuffManager().hasState(B);
            System.out.println("[one_of] always seed " + seed + ": constant=" + constant + " A=" + a + " B=" + b);
            countA += a ? 1 : 0;
            countB += b ? 1 : 0;
        }
        System.out.println("[one_of] always over 24 seeds: A=" + countA + " B=" + countB);
        boolean sawA = countA > 0;
        boolean sawB = countB > 0;

        Assertions.assertAll("the `always` effect runs every firing, and exactly one pool effect is picked",
                () -> {
                    for (int seed = 0; seed < firings.size(); seed++) {
                        Assertions.assertTrue(firings.get(seed).getBuffManager().hasState(C),
                                "seed " + seed + ": the `always` effect must run every firing, picked or not");
                    }
                },
                () -> {
                    for (int seed = 0; seed < firings.size(); seed++) {
                        Character wolf = firings.get(seed);
                        int picked = (wolf.getBuffManager().hasState(A) ? 1 : 0)
                                + (wolf.getBuffManager().hasState(B) ? 1 : 0);
                        Assertions.assertEquals(1, picked,
                                "seed " + seed + ": exactly one of the two POOL effects runs - 0 means the "
                                        + "always-effect swallowed the pick, 2 means the roll went per effect");
                    }
                },
                () -> Assertions.assertTrue(sawA && sawB,
                        "and the pool is what the roll chooses from - both of its effects appear"));
    }

    // ==================================================================
    // 3. A field only `one_of` reads is refused without it
    // ==================================================================

    /**
     * {@code always} and {@code weight} are shares of a pick: on a rule that states no {@code one_of} they read like a
     * forgotten field, so each is refused with a message naming the field it needs.
     *
     * <p>One fixture (a two-mark TURN_START rule with no {@code one_of}, one effect carrying the stray field) and one
     * observable (the load-time refusal and the field its message names); it stays apart from every positive scenario
     * above, because an {@code assertThrows} is not a positive clause.
     */
    @Test
    public void aFieldWithoutOneOfIsRefused() {
        Assertions.assertAll("a field only `one_of` reads is refused when the rule does not state it",
                () -> {
                    EffectSpec constant = mark(A);
                    TriggerSpecs.set(constant, "always", true);
                    IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("TURN_START", List.of(), constant, mark(B))),
                            "`always` without `one_of` is refused");
                    System.out.println("[one_of] `always` without the field refused: " + error.getMessage());
                    Assertions.assertTrue(error.getMessage().contains("one_of"),
                            "the message names the field it needs");
                },
                () -> {
                    EffectSpec weighted = mark(A);
                    TriggerSpecs.set(weighted, "weight", 10.0);
                    IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("TURN_START", List.of(), weighted, mark(B))),
                            "a `weight` without `one_of` is refused");
                    System.out.println("[one_of] a weight without the field refused: " + error.getMessage());
                    Assertions.assertTrue(error.getMessage().contains("one_of"),
                            "the message names the field it needs");
                });
    }

    // ==================================================================
    // 4. "Pick one of one" states nothing
    // ==================================================================

    /**
     * The POOL is what "at least two" counts: one constant plus one pick, or a single effect with the field, both state
     * "pick one of one" - which states nothing and reads exactly like a rule that forgot the field, so both are refused.
     *
     * <p>The same fixture shape (a rule the trigger table validates) and the same observable (the refusal and the field
     * its message names); the single-effect spelling and the one-beside-{@code always} spelling are one clause.
     */
    @Test
    public void aPoolOfOneIsRefused() {
        Assertions.assertAll("`one_of` over a pool of one is refused, in either spelling",
                () -> {
                    EffectSpec constant = mark(C);
                    TriggerSpecs.set(constant, "always", true);
                    EffectSpec onlyPick = mark(A);
                    TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of(), onlyPick, constant);
                    TriggerSpecs.set(spec, "oneOf", true);
                    IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(spec), "one pickable effect beside an `always` effect is refused");
                    System.out.println("[one_of] a pool of one refused: " + error.getMessage());
                    Assertions.assertTrue(error.getMessage().contains("one_of"), "the message names the field");
                },
                () -> {
                    IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(spec(true, 1)), "a single effect with `one_of` is refused");
                    System.out.println("[one_of] one effect refused: " + error.getMessage());
                    Assertions.assertTrue(error.getMessage().contains("one_of"), "the message names the field");
                });
    }

    // ==================================================================
    // 5. A per-effect Eidolon gate composes two ways with the field
    // ==================================================================

    /**
     * Combined with a per-effect `min_eidolon` the two filters compose two ways, so the pair is refused.
     *
     * <p>A fixture of its own (the gate sits on one effect) and an observable of its own (the message names the
     * conflict rather than {@code one_of}), so it keeps its own rejection scenario.
     */
    @Test
    public void theFieldBesideAPerEffectEidolonGateIsRefused() {
        EffectSpec first = mark(A);
        EffectSpec gated = mark(B);
        TriggerSpecs.set(gated, "minEidolon", 2);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of("actor == self"), first, gated);
        TriggerSpecs.set(spec, "oneOf", true);
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class, () -> table(spec));
        System.out.println("[one_of] with a per-effect Eidolon gate refused: " + error.getMessage());
        Assertions.assertAll("`one_of` beside a per-effect `min_eidolon` is refused",
                () -> Assertions.assertTrue(error.getMessage().contains("min_eidolon"),
                        "the message names the conflict"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * How many of the three markers the unit carries: the observable the count scenarios above are stated in.
     */
    private static int markerCount(Character unit) {
        return (unit.getBuffManager().hasState(A) ? 1 : 0)
                + (unit.getBuffManager().hasState(B) ? 1 : 0)
                + (unit.getBuffManager().hasState(C) ? 1 : 0);
    }

    private static Character marksAfterOneFiring(int seed) {
        return marksAfterOneFiring(seed, true);
    }

    /**
     * The same firing, with a rule of three marks where the THIRD is {@code always}: A/B are the pool, C is constant.
     */
    private static Character marksWithAnAlwaysEffect(int seed) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), new Random(seed));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        EffectSpec constant = mark(C);
        TriggerSpecs.set(constant, "always", true);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of("actor == self"), mark(A), mark(B), constant);
        TriggerSpecs.set(spec, "oneOf", true);
        wolf.setTriggerTable(table(spec));
        // ⚠ Burn four draws first, and the reason is measured rather than tidy: `nextInt(2)` takes the
        // POWER-OF-TWO branch of `java.util.Random`, which reads the TOP bit of `next(31)` - and the FIRST draw of
        // `new Random(seed)` has nearly the same top bits for small seeds. Measured: two effects and 24 seeds 0..23
        // answered 1 every single time, so the pool looked like a constant. Three effects hid it, because `nextInt(3)`
        // takes the other branch and reads the LOW bits. Nothing here is wrong with the engine - it is how this fixture
        // must hand it a source, and a battle that draws once before its first pick would never see it.
        for (int draw = 0; draw < 4; draw++) {
            battle.getRng().nextInt();
        }
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wolf, wolf, 0, 0));
        battle.processRequests();
        return wolf;
    }

    /**
     * Applies one firing of a three-effect rule and returns the unit, so the caller can read the markers.
     */
    private static Character marksAfterOneFiring(int seed, boolean oneOf) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), new Random(seed));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        wolf.setTriggerTable(table(spec(oneOf, 3)));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wolf, wolf, 0, 0));
        battle.processRequests();
        return wolf;
    }

    private static TriggerTable table(TriggerSpec spec) {
        return new TriggerTable(WOLF, List.of(spec));
    }

    /**
     * A rule whose N effects each mark the unit, so a count says how many ran.
     */
    private static TriggerSpec spec(boolean oneOf, int effects) {
        List<EffectSpec> marks = List.of(mark(A), mark(B), mark(C)).subList(0, effects);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                marks.toArray(new EffectSpec[0]));
        if (oneOf) {
            TriggerSpecs.set(spec, "oneOf", true);
        }
        return spec;
    }

    private static EffectSpec mark(String state) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", state);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }
}
