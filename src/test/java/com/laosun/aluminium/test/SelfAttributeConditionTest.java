package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code self_attr:<ATTRIBUTE>} — a numeric condition on <b>one of my own attribute values</b>.
 *
 * <p><b>Why the vocabulary needed it.</b> The condition DSL could already ask about the event
 * ({@code hit_count}), about me ({@code hp_percent}) and about the event's subject
 * ({@code target_debuff_count}), but not about "how much of X do I have" — and that is the most common
 * conditional shape in the game's equipment: 13 of the planar-ornament 2-pieces read
 * 「当装备者的速度/暴击率/击破特攻/生命上限…大于等于 N 时」. The old names were a closed set of three; this one is
 * <b>parameterised</b> over {@link AttributeType}, which is already a validated closed set — so new content
 * needs a new attribute <em>name</em>, not a new engine change. That is the extensibility this exists for.
 *
 * <p><b>What is really being tested.</b> Three things that would each leave a rule which loads fine and
 * quietly does the wrong thing:
 * <ol>
 *   <li><b>The number is read at all</b> — a threshold just below and just at the wearer's value;</li>
 *   <li><b>it is read off the rule's OWNER</b>, not off whoever caused the event. Every trigger table
 *       evaluates the event with itself as {@code self}, so a condition that read the <em>actor</em> would
 *       let one fast character's speed decide another character's buff — and the battle would look normal;</li>
 *   <li><b>the units.</b> Flat attributes are absolute and ratio attributes are fractions, so
 *       {@code self_attr:SPEED >= 145} and {@code self_attr:CRIT_CHANCE >= 0.7} are the same shape on two
 *       different scales. Getting that wrong is a factor of 100 that fails <b>silently</b> — the trap
 *       ROADMAP L-9 already records once.</li>
 * </ol>
 *
 * <p>The shipped content that uses it is at the bottom, checked through the real loader and assembly point
 * rather than as JSON text: the three planar 2-pieces whose conditional half had no spelling before.
 */
public class SelfAttributeConditionTest {
    private static final double EPS = 1e-6;

    /** Himeko: no shipped rule file, so the table under test is the only one in play. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;

    // The planar 2-pieces authored with this vocabulary. None of them had a rule file before.
    /** 太空封印站: 攻击力 +12%; SPD >= 120 -> 攻击力额外 +12%. */
    private static final int SPACE_SEALING_STATION = 301;
    /** 盗贼公国塔利亚: 击破特攻 +16%; SPD >= 145 -> 击破特攻额外 +20%. */
    private static final int TALIA = 307;
    /** 繁星竞技场: 暴击率 +8%; 当前暴击率 >= 70% -> 普攻与战技伤害 +20%. */
    private static final int CELESTIAL_DIFFERENTIATOR = 309;

    // ==================================================================
    // 1. The value is read, and it is the owner's
    // ==================================================================

    /** The threshold decides: just short does not fire, at it and above does. */
    @Test
    public void theThresholdDecidesWhetherTheRuleFires() {
        Assertions.assertEquals(0, fireAtSpeed(144.9), "just short of 145");
        Assertions.assertEquals(1, fireAtSpeed(145), "exactly at it -- the text says 大于等于");
        Assertions.assertEquals(1, fireAtSpeed(200), "and above it");
    }

    /**
     * The attribute is read off the <b>owner</b>, not off whoever caused the event.
     *
     * <p>This is the case that would otherwise be invisible. The rule sits on the slow character; a fast
     * character acts. If the condition read the actor, the slow character's buff would arrive — some other
     * unit's speed deciding this unit's mechanics, with nothing in the log to say so.
     */
    @Test
    public void theAttributeIsReadFromTheRulesOwnerNotFromTheActor() {
        Character slow = ownerWithTrigger(speedThresholdRule(145), 100);
        Character fast = newOwner(200);
        fast.setTriggerTable(new TriggerTable(OWNER, List.of()));
        Battle battle = new Battle(List.of(slow, fast), List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, fast, null, 1, 0),
                "the actor is fast, but the rule belongs to the slow character, so it must not fire");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, slow, null, 1, 0),
                "and not even when the slow one acts: 100 speed is below the threshold either way");
    }

    /** The same rule fires for a fast owner, so the case above is not passing because nothing works. */
    @Test
    public void theSameRuleFiresForAFastOwner() {
        Character fast = ownerWithTrigger(speedThresholdRule(120), 130);
        Battle battle = new Battle(List.of(fast), List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, fast, null, 1, 0));
    }

    /**
     * A <b>ratio</b> attribute is compared as a fraction, not as a percentage.
     *
     * <p>{@code self_attr:CRIT_CHANCE >= 0.7} is "70% crit rate or better". An author who writes
     * {@code >= 70}, or an implementation that scaled the literal, fails here instead of in production —
     * where the only symptom would be "this set bonus never seems to do anything". The flat counterpart is
     * asserted in the same case so both scales are on one page: 145 speed is written {@code 145}, and
     * {@code 1.45} is nonsense for it.
     */
    @Test
    public void ratioAttributesCompareAsFractionsAndFlatOnesAsAbsoluteValues() {
        Assertions.assertEquals(0, fireAtCrit(0.699), "0.699 is 69.9% -- below 70%");
        Assertions.assertEquals(1, fireAtCrit(0.7), "0.7 is 70% -- at the threshold");

        Assertions.assertEquals(1, fireAtSpeed(145), "a flat attribute is absolute: SPEED >= 145");
        Assertions.assertEquals(0, fireAtSpeed(1.45), "the fraction spelling of 145 need not fire");
    }

    /** The literal may sit on either side, like every other numeric variable. */
    @Test
    public void theLiteralMayBeOnEitherSide() {
        // "100 <= self_attr:SPEED" -- true for a 130-speed owner.
        Assertions.assertEquals(1, fireWithCondition("100 <= self_attr:SPEED", 130),
                "the literal on the left reads the same comparison");
        // "140 >= self_attr:SPEED" would mean "speed at most 140", which 130 satisfies; 200 does not.
        Assertions.assertEquals(1, fireWithCondition("200 >= self_attr:SPEED", 130));
        Assertions.assertEquals(0, fireWithCondition("100 >= self_attr:SPEED", 130));
    }

    // ==================================================================
    // 2. Load-time rejections
    // ==================================================================

    /** A typo in the attribute name is a load error, not a rule that never fires. */
    @Test
    public void anUnknownAttributeNameIsRejectedAtLoadTime() {
        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(speedThresholdRule(145, "SPEEED"))));

        Assertions.assertTrue(unknown.getMessage().contains("AttributeType"), unknown.getMessage());
        // And it must be OUR message, quoting the condition as written: letting AttributeType's own
        // exception escape would report the lower-cased half ("Unknown AttributeType: speeed") without
        // saying which condition is at fault, which is much harder to act on in a 200-line rule file.
        Assertions.assertTrue(unknown.getMessage().contains("self_attr:SPEEED"),
                "the error must quote the condition as the author wrote it: " + unknown.getMessage());
    }

    /**
     * The four {@code *_PERCENT} builder keys are refused, and the message names the base attribute instead.
     *
     * <p>They look like the most natural thing in the world to write ({@code self_attr:HEALTH_PERCENT}), and
     * they are not runtime attributes at all: {@code AttributeBuilder} folds each into its base attribute and
     * stores the slot as {@code null}. Reading one would NPE <b>during a battle</b>, in the middle of a damage
     * calculation, so it is refused where the file is read.
     */
    @Test
    public void thePercentBuilderKeysAreRejectedWithTheBaseAttributeNamed() {
        IllegalArgumentException percent = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(speedThresholdRule(145, "HEALTH_PERCENT"))));

        Assertions.assertTrue(percent.getMessage().contains("HEALTH_PERCENT"), percent.getMessage());
        Assertions.assertTrue(percent.getMessage().contains("health"),
                "the message must say which attribute to read instead: " + percent.getMessage());
    }

    /**
     * The prefix with nothing after it gets its own message, not "unknown attribute ''".
     *
     * <p>⚠ This check is <b>redundant for correctness</b> — an empty name reaches
     * {@code AttributeType.fromString("")} and is rejected there too. What it buys is the diagnosis: the
     * generic path reports `reads unknown attribute ''`, which reads like a typo in a name that was never
     * written, and the author goes looking for a misspelling. So the assertion is on that message, not on
     * "an exception was thrown" — a mutant that deletes the check would otherwise stay green, which is
     * exactly what happened the first time this was run.
     */
    @Test
    public void anEmptyAttributeNameIsRejectedWithItsOwnMessage() {
        IllegalArgumentException empty = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(speedThresholdRule(145, ""))));

        Assertions.assertTrue(empty.getMessage().contains("no attribute"),
                "the message must say the attribute is missing, not that it is unknown: "
                        + empty.getMessage());
        Assertions.assertTrue(empty.getMessage().contains("self_attr:SPEED"), empty.getMessage());
    }

    /** The "unknown variable" error mentions the new form, so the vocabulary is discoverable from the error. */
    @Test
    public void theUnknownVariableMessagePointsAtTheNewSpelling() {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", List.of("self_attack >= 1"),
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(spec)));

        Assertions.assertTrue(unknown.getMessage().contains("self_attr:<ATTRIBUTE>"), unknown.getMessage());
        Assertions.assertTrue(unknown.getMessage().contains("self_attack"), unknown.getMessage());
    }

    // ==================================================================
    // 3. The shipped content: three planar 2-pieces that had no rule file before
    // ==================================================================

    /**
     * Each authored set states the attribute, the threshold and the numbers its own text states.
     *
     * <p>Read back through the loader, and compared against the condition's own printed form — so a wrong
     * number or a swapped attribute fails here rather than in a battle nobody is measuring. ⚠ The context
     * owner must <b>satisfy</b> the threshold: {@code matching} evaluates the conditions, so a default
     * character would return no rules and the assertions would be about nothing.
     */
    @Test
    public void theAuthoredPlanarTwoPiecesStateTheThresholdsFromTheirTexts() {
        assertSingleThreshold(SPACE_SEALING_STATION, "self_attr:speed >= 120.0", AttributeType.SPEED, 200);
        assertSingleThreshold(TALIA, "self_attr:speed >= 145.0", AttributeType.SPEED, 200);
        assertSingleThreshold(CELESTIAL_DIFFERENTIATOR, "self_attr:crit_chance >= 0.7",
                AttributeType.CRIT_CHANCE, 0.8);
    }

    /** Set 301's conditional extra is `+12% ATK, for the rest of the battle` — the file's own numbers. */
    @Test
    public void theAuthoredEffectsCarryTheNumbersFromTheText() {
        EffectSpec station = soleEffect(SPACE_SEALING_STATION, AttributeType.SPEED, 200);
        Assertions.assertEquals("MODIFY_ATTR", station.getOp());
        Assertions.assertEquals("ATTACK", station.getAttribute());
        Assertions.assertEquals(0.12, station.getPercent(), EPS, "param #3 is 0.12");
        Assertions.assertEquals(Boolean.TRUE, station.getPermanent(),
                "the text has no duration, so it is `permanent` -- not a long turn count");

        EffectSpec talia = soleEffect(TALIA, AttributeType.SPEED, 200);
        Assertions.assertEquals("BREAKING_EFFECT", talia.getAttribute(),
                "击破特攻 is AttributeType.BREAKING_EFFECT (its data property is BreakDamageAddedRatioBase)");
        Assertions.assertEquals(0.2, talia.getPercent(), EPS, "param #3 is 0.2");
    }

    /** Set 309's ability is a disjunction over two cast scopes, so it needs one modifier each. */
    @Test
    public void theBasicAttackAndSkillBoostIsTwoScopedModifiers() {
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(CELESTIAL_DIFFERENTIATOR)
                .at(2).matching(TriggerEvent.BATTLE_START,
                        context(satisfyingOwner(AttributeType.CRIT_CHANCE, 0.8)));

        Assertions.assertEquals(1, rules.size());
        List<String> attributes = rules.getFirst().effects().stream()
                .map(EffectSpec::getAttribute)
                .toList();
        Assertions.assertEquals(List.of("BASIC_ATTACK_DAMAGE_BOOST", "SKILL_DAMAGE_BOOST"), attributes,
                "one all-type boost would also buff ultimates and follow-ups -- an over-trigger, not a "
                        + "near miss");
        for (EffectSpec effect : rules.getFirst().effects()) {
            Assertions.assertEquals(0.2, effect.getPercent(), EPS, "param #3 is 0.2");
        }
    }

    /**
     * The unconditional half of each set is <b>not</b> in the rule file.
     *
     * <p>These sets' first sentence ("ATK +12%") is a plain stat that {@code RelicSuit} already applies from
     * the effect's {@code properties}. Writing it as a rule as well would grant it twice, and the symptom
     * would be "slightly more attack than the game gives" — a wrong number with nothing to see. So the file
     * carries exactly one rule, on one event: the conditional half.
     */
    @Test
    public void theUnconditionalHalfIsLeftToTheStatProperties() {
        for (int setId : List.of(SPACE_SEALING_STATION, TALIA, CELESTIAL_DIFFERENTIATOR)) {
            RelicSet.Effect effect = Constant.RELIC_SETS.get(setId).effects().getFirst();
            Assertions.assertFalse(effect.properties().isEmpty(),
                    "set " + setId + "'s unconditional half is a `properties` stat, applied by RelicSuit");
            Assertions.assertTrue(effect.hasAbility(),
                    "…and the conditional half is the named ability this rule file implements");

            Assertions.assertEquals(1, RelicTriggerTables.of(setId).at(2).ruleCount(TriggerEvent.BATTLE_START),
                    "set " + setId + " contributes exactly the conditional half, as one rule");
        }
    }

    /**
     * Wearing it for real: the conditional extra lands on top of the set's own stat, and only above the
     * threshold.
     *
     * <p>Measured as the difference between two wearers of the <b>identical</b> suit — one above the
     * threshold, one below — so the suit's main and sub affixes cancel out of the comparison and what is left
     * is exactly the rule's contribution. ⚠ Comparing a <em>ratio</em> instead does not work: the affixes add
     * a term of their own, so they do not cancel there (a first version assumed they did and measured 1.069
     * where it expected 1.107 — the affixes are not negligible).
     *
     * <p>⚠ <b>This asserts direction, not magnitude, and that is deliberate.</b> The contribution is 12% of
     * the character's <em>base</em> ATK, and base ATK is not readable from outside: a relic-less character's
     * resolved ATK (892.97 for 姬子 at 80) is already base × (traces' percentages), so the measured 90.81 is
     * 12% of 756.76, not of 892.97 — the "panel ≠ base × multiplier" trap recorded in {@code engine.md} §22.
     * The magnitude in the file is pinned exactly by {@link #theAuthoredEffectsCarryTheNumbersFromTheText}
     * instead, and the modifier arithmetic by the engine's own tests; what needs an end-to-end case is that
     * this condition lets the effect through <b>only</b> above its threshold.
     */
    @Test
    public void wearingItForRealTheConditionalExtraLandsOnlyAboveTheThreshold() {
        double above = resolvedAttackAfterBattleStart(SPACE_SEALING_STATION, 130);
        double below = resolvedAttackAfterBattleStart(SPACE_SEALING_STATION, 100);

        Assertions.assertTrue(above > below,
                "above the threshold the rule must land: " + above + " vs " + below);
        Assertions.assertTrue(above - below < 0.12 * above,
                "and by 12% of BASE attack, so strictly less than 12% of the resolved attack it sits on "
                        + "(base < resolved because traces already contribute percentages): extra "
                        + (above - below) + " of " + above);
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** Whenever an ally attacks, if my own SPEED is at least {@code threshold}, +12% ATK for a turn. */
    private static TriggerSpec speedThresholdRule(double threshold) {
        return speedThresholdRule(threshold, "SPEED");
    }

    private static TriggerSpec speedThresholdRule(double threshold, String attribute) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of("self_attr:" + attribute + " >= " + threshold));
        return spec;
    }

    /** The same shape on a ratio attribute. */
    private static TriggerSpec critThresholdRule(double threshold) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of("self_attr:CRIT_CHANCE >= " + threshold));
        return spec;
    }

    /**
     * A character whose speed is {@code speed} and whose crit rate is 5%, with a table holding {@code spec}.
     *
     * <p>The attributes are overwritten after construction through {@code setAttribute}, the public way to
     * state "this character's value is X"; every consumer reads the sheet, this condition included.
     */
    private static Character ownerWithTrigger(TriggerSpec spec, double speed) {
        Character owner = newOwner(speed);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(spec)));
        return owner;
    }

    private static Character newOwner(double speed) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setAttribute(AttributeType.SPEED, new DoubleValue(speed));
        owner.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0.05));
        return owner;
    }

    /** How many rules fire for an owner at the given speed. */
    private static int fireAtSpeed(double speed) {
        return fireWithCondition("self_attr:SPEED >= 145", speed);
    }

    /** How many rules fire for an owner at the given crit rate. */
    private static int fireAtCrit(double critChance) {
        Character owner = newOwner(100);
        owner.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(critChance));
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(critThresholdRule(0.7))));

        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0);
    }

    /** How many rules fire for a 130-speed owner whose table holds a rule carrying {@code condition}. */
    private static int fireWithCondition(String condition, double speed) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of(condition));

        Character owner = ownerWithTrigger(spec, speed);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0);
    }

    /** The wearer's resolved ATTACK after the battle opened, wearing {@code setId} at one fixed speed. */
    private static double resolvedAttackAfterBattleStart(int setId, double speed) {
        Character wearer = CharacterFactory.create(OWNER, LEVEL, true, null,
                RelicFactory.suit(setId, 5, 15));
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(speed));

        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        return wearer.getAttribute(AttributeType.ATTACK).get();
    }

    /** The set's one 2-piece rule states exactly one condition, and this is how it prints itself. */
    private static void assertSingleThreshold(int setId, String expected, AttributeType attribute,
                                              double value) {
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(setId).at(2)
                .matching(TriggerEvent.BATTLE_START, context(satisfyingOwner(attribute, value)));

        Assertions.assertEquals(1, rules.size(), "set " + setId + " must have exactly one 2-piece rule");
        Assertions.assertEquals(1, rules.getFirst().conditions().size(),
                "set " + setId + " has exactly one condition: the attribute threshold");
        Assertions.assertEquals(expected, rules.getFirst().conditions().getFirst().source(),
                "set " + setId + "'s threshold, as the condition prints itself back");
    }

    /** The single effect of the set's 2-piece rule, read with a context that satisfies its threshold. */
    private static EffectSpec soleEffect(int setId, AttributeType attribute, double value) {
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(setId).at(2)
                .matching(TriggerEvent.BATTLE_START, context(satisfyingOwner(attribute, value)));

        Assertions.assertEquals(1, rules.size(), "set " + setId + " must have one matching rule");
        Assertions.assertEquals(1, rules.getFirst().effects().size(),
                "set " + setId + "'s rule carries one effect");
        return rules.getFirst().effects().getFirst();
    }

    /** A character whose given attribute satisfies a threshold, for evaluating a condition that gates. */
    private static Character satisfyingOwner(AttributeType attribute, double value) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setAttribute(attribute, new DoubleValue(value));
        return owner;
    }

    private static TriggerTable.TriggerContext context(Character owner) {
        return new TriggerTable.TriggerContext(owner, owner, null, 0, 0);
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }
}
