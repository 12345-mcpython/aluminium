package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A {@code MODIFY_ATTR} whose value is <b>derived</b>: {@code scale: "self_attr:<ATTRIBUTE>"} (P11-2, M-42).
 *
 * <p><b>Why a literal is not enough.</b> 「提高数值等同于大丽花 #1% 的击破特攻 + #3%」 / 「等同于星期日 #2% 暴击伤害 +
 * #4%」 state the granted value as a function of <b>the rule owner's own attribute</b>. `percent` can only be a
 * number, so writing one here would be wrong for every build in which the owner's attribute is not exactly the
 * value the author had in front of them — the classic "wrong number with nothing to see".
 *
 * <p><b>What each case is for.</b>
 * <ol>
 *   <li>the value really is {@code percent × (the owner's attribute) + amount};</li>
 *   <li>it reads the <b>owner's</b> attribute, not the receiving unit's (they are two different characters, and the
 *       rule's whole point is that the value travels);</li>
 *   <li>it stays <b>absolute</b> even on a base attribute — where a literal {@code percent} would mean "a share of
 *       the target's base". The two readings differ by a factor of the target's own stat, so one case separates
 *       them;</li>
 *   <li>it is <b>computed once</b>: changing the owner's attribute afterwards does not retro-rewrite the buff;</li>
 *   <li>every way of spelling it wrong is refused while the file is read — including the one that used to be
 *       silently dropped ({@code amount} without a {@code scale}).</li>
 * </ol>
 *
 * <p>The shipped user is 大丽花's trace 「又一场葬礼」 (checked end to end below).
 */
public class DerivedModifierTest {
    private static final double EPS = 1e-6;

    /** 姬子 — a plain character with no rule file of its own; the derived value is put on it. */
    private static final int OWNER = 1003;
    /** 停云 — "the other character" the party-wide grant reaches. */
    private static final int ALLY = 1202;
    /** 大丽花 — her trace is the first content that needs this. */
    private static final int DAHLIA = 1321;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private static final double OWNER_BREAK = 2.0;

    // ==================================================================
    // 1. The arithmetic
    // ==================================================================

    /** {@code percent × the owner's attribute + amount}, in the attribute's own units. */
    @Test
    public void theValueIsAShareOfTheOwnersAttributePlusAFlatPart() {
        Battle battle = battleWith(owner -> owner.setAttribute(AttributeType.BREAKING_EFFECT,
                        new DoubleValue(OWNER_BREAK)),
                rule("BATTLE_START", derived("BREAKING_EFFECT", "BREAKING_EFFECT", 0.5, 0.1, null)));

        Assertions.assertEquals(0.5 * OWNER_BREAK + 0.1,
                grantedTo(battle, 0, AttributeType.BREAKING_EFFECT, OWNER_BREAK), EPS,
                "0.5 x 2.0 + 0.1 = 1.1 -- the document's own arithmetic (「等同于 X% … + Y%」)");
    }

    /** The part that has to be stated: {@code amount} is optional, the percentage is not. */
    @Test
    public void theFlatPartIsOptional() {
        Battle battle = battleWith(owner -> owner.setAttribute(AttributeType.BREAKING_EFFECT,
                        new DoubleValue(OWNER_BREAK)),
                rule("BATTLE_START", derived("BREAKING_EFFECT", "BREAKING_EFFECT", 0.5, null, null)));

        Assertions.assertEquals(1.0, grantedTo(battle, 0, AttributeType.BREAKING_EFFECT, OWNER_BREAK), EPS,
                "0.5 x 2.0, with no addend");
    }

    // ==================================================================
    // 2. Whose attribute, and in what units
    // ==================================================================

    /**
     * The value comes from the <b>rule owner</b> and is granted to somebody else — the arrangement that makes the
     * two roles distinguishable.
     */
    @Test
    public void itReadsTheOwnersAttributeNotTheReceivers() {
        Battle battle = battleWith(owner -> owner.setAttribute(AttributeType.BREAKING_EFFECT,
                        new DoubleValue(OWNER_BREAK)),
                rule("BATTLE_START", derived("BREAKING_EFFECT", "BREAKING_EFFECT", 0.5, 0.0, "all_allies")));
        double allyBare = CharacterFactory.create(ALLY, LEVEL).getAttribute(AttributeType.BREAKING_EFFECT).get();
        Assertions.assertEquals(1.0, grantedTo(battle, 0, AttributeType.BREAKING_EFFECT, OWNER_BREAK), EPS,
                "the owner's own grant");
        Assertions.assertEquals(1.0, grantedTo(battle, 1, AttributeType.BREAKING_EFFECT, allyBare), EPS,
                "the ally receives the OWNER's number, whatever its own Break Effect happens to be");
    }

    /**
     * On a <b>base</b> attribute the derived value is an absolute amount.
     *
     * <p>⚠ This is the case that separates the two possible readings: a literal {@code percent} on ATTACK means "a
     * share of the target's base ATK", and reading a <i>derived</i> number the same way would multiply it by that
     * stat. The numbers are chosen so the two answers cannot be confused (50 vs 50 × the target's ATK).
     */
    @Test
    public void aDerivedValueStaysAbsoluteOnABaseAttribute() {
        Battle battle = battleWith(owner -> owner.setAttribute(AttributeType.SPEED, new DoubleValue(100)),
                rule("BATTLE_START", derived("ATTACK", "SPEED", 0.5, 0.0, null)));

        double bareAttack = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(50, grantedTo(battle, 0, AttributeType.ATTACK, bareAttack), EPS,
                "0.5 x 100 SPD = +50 ATK -- an absolute number, not 50 x the owner's base ATK");
    }

    /**
     * It is computed once: the modifier <b>holds</b> the number, so a later change to the owner cannot rewrite it.
     *
     * <p>Read off the buff itself rather than off the attribute, and deliberately: {@code setAttribute} replaces the
     * whole resolved chain, so moving the owner's Break Effect that way would also wipe the buff being measured.
     */
    @Test
    public void theValueIsFrozenWhenItIsGranted() {
        Battle battle = battleWith(owner -> owner.setAttribute(AttributeType.BREAKING_EFFECT,
                        new DoubleValue(OWNER_BREAK)),
                rule("BATTLE_START", derived("BREAKING_EFFECT", "BREAKING_EFFECT", 0.5, 0.1, null)));
        Character owner = battle.characters.getFirst();
        List<StatModifierBuff> buffs = owner.getBuffManager().allBuffsOf(StatModifierBuff.class);

        Assertions.assertEquals(1, buffs.size(), "precondition: one derived modifier");
        Assertions.assertEquals(1.1, buffs.getFirst().getValue(), EPS, "0.5 x 2.0 + 0.1, computed at fire time");

        owner.setAttribute(AttributeType.BREAKING_EFFECT, new DoubleValue(8.0));

        Assertions.assertEquals(1.1, buffs.getFirst().getValue(), EPS,
                "the modifier still says 1.1: the value was snapshot when it was granted, not bound to the owner");
    }

    /**
     * The owner-vs-receiver case where the two are genuinely different <b>objects with different values</b>.
     *
     * <p>⚠ It has to be an event that <b>carries a target</b>. The party-wide case above fires on {@code BATTLE_START},
     * where {@code ctx.target()} is null — so "reads the owner" and "reads the target" are the same code path there,
     * and a mutant that swapped them survived the suite until this case existed (2026-09-27).
     */
    @Test
    public void theOwnerIsNotSimplyTheEventSubject() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        owner.setAttribute(AttributeType.BREAKING_EFFECT, new DoubleValue(2.0));
        ally.setAttribute(AttributeType.BREAKING_EFFECT, new DoubleValue(9.0));
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                rule("SKILL_CAST", derived("BREAKING_EFFECT", "BREAKING_EFFECT", 0.5, 0.0, "target")))));
        Battle battle = new Battle(List.of(owner, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double allyBare = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, ally, 0, 0);

        Assertions.assertEquals(1.0, boostOf(ally, AttributeType.BREAKING_EFFECT) - allyBare, EPS,
                "0.5 x the OWNER's 2.0 = 1.0 -- not 0.5 x the receiver's 9.0, which is what the two readings "
                        + "disagree about");
    }

    // ==================================================================
    // 3. Refusals, while the file is read
    // ==================================================================

    /** A flat part with no scale has nothing to derive from, and used to be dropped on the floor. */
    @Test
    public void anAmountWithoutAScaleIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "BREAKING_EFFECT");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "amount", 0.1);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", null, effect))));

        Assertions.assertTrue(refused.getMessage().contains("scale"), refused.getMessage());
    }

    /** A scale this op cannot read (the heal/shield family) is refused rather than silently ignored. */
    @Test
    public void aHealScaleOnAnAttributeIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "BREAKING_EFFECT");
        TriggerSpecs.set(effect, "scale", "target_max_hp");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "turns", 1);

        assertRefused(effect, "target_max_hp");
    }

    /** A builder-only input key is not a runtime attribute. */
    @Test
    public void aBuilderOnlyAttributeIsRefused() {
        assertRefused(derived("BREAKING_EFFECT", "ATTACK_PERCENT", 0.5, null, null), "ATTACK_PERCENT");
    }

    /** A scale with no magnitude is not a number. */
    @Test
    public void aScaleWithoutAPercentIsRefused() {
        assertRefused(derived("BREAKING_EFFECT", "BREAKING_EFFECT", null, null, null), "percent");
    }

    /** A misspelled attribute is refused where the file is read, not when the rule fires. */
    @Test
    public void anUnknownAttributeInTheScaleIsRefused() {
        assertRefused(derived("BREAKING_EFFECT", "BREAK_EFFECT", 0.5, null, null), "BREAK_EFFECT");
    }

    // ==================================================================
    // 3b. A condition the event can never satisfy
    // ==================================================================

    /**
     * {@code actor == self} on {@code BATTLE_START} is refused: that event carries no actor, so the rule could
     * never fire.
     *
     * <p>⚠ Written by hand first, in the trace just below, and the only reason it was noticed is that nothing was
     * granted. The event is delivered to every character's own table, so "my own battle start" needs no condition —
     * which is what the message says.
     */
    @Test
    public void anIdentityConditionOnAnActorlessEventIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "BREAKING_EFFECT");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpec moved = TriggerSpecs.rule("BATTLE_START", List.of("actor == self"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(moved)));

        Assertions.assertTrue(refused.getMessage().contains("actor == self"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("BATTLE_START"), refused.getMessage());

        // The same condition on an event that does carry an actor is of course fine.
        Assertions.assertDoesNotThrow(() -> new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), effect))));
    }

    // ==================================================================
    // 4. The shipped content: 大丽花's trace
    // ==================================================================

    /**
     * 「进入战斗时，使其他角色的击破特攻提高，提高数值等同于 24% 大丽花的击破特攻 + 50%，持续 1 回合」.
     *
     * <p>Measured as a delta on each <b>other</b> character, with her own value read from the built character rather
     * than hardcoded — the trace's whole point is that the number follows her build.
     */
    @Test
    public void theAuthoredTraceGrantsThePartyHerOwnShare() {
        Character dahlia = CharacterFactory.create(DAHLIA, LEVEL);
        Character first = CharacterFactory.create(OWNER, LEVEL);
        Character second = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(dahlia, first, second), List.of(dummy()), new Random(0));
        double herBreak = dahlia.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double firstBare = first.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double secondBare = second.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double herBare = herBreak;

        battle.startBattle();

        double expected = 0.24 * herBreak + 0.5;
        Assertions.assertTrue(expected > 0.5, "precondition: the trace grants something (her Break Effect is "
                + herBreak + ")");
        Assertions.assertEquals(firstBare + expected, boostOf(first, AttributeType.BREAKING_EFFECT), EPS,
                "「使其他角色的击破特攻提高…」 -- 0.24 x " + herBreak + " + 0.5");
        Assertions.assertEquals(secondBare + expected, boostOf(second, AttributeType.BREAKING_EFFECT), EPS,
                "…for every other character");
        Assertions.assertEquals(herBare, boostOf(dahlia, AttributeType.BREAKING_EFFECT), EPS,
                "「**其他**角色」: her own Break Effect is untouched");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** A battle whose owner carries the given single rule, with the owner tweaked before it starts. */
    private static Battle battleWith(java.util.function.Consumer<Character> tweak, TriggerSpec rule) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
        tweak.accept(owner);
        Battle battle = new Battle(List.of(owner, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** A {@code MODIFY_ATTR} with a derived magnitude. */
    private static TriggerSpec rule(String event, EffectSpec effect) {
        return TriggerSpecs.rule(event, null, effect);
    }

    private static EffectSpec derived(String attribute, String scaleTarget, Double percent, Double amount,
                                      String target) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", attribute);
        // Spelled here on purpose: if the engine's prefix ever changes, this test must fail rather than follow.
        TriggerSpecs.set(effect, "scale", "self_attr:" + scaleTarget);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "amount", amount);
        TriggerSpecs.set(effect, "turns", 1);
        if (target != null) {
            TriggerSpecs.set(effect, "target", target);
        }
        return effect;
    }

    private static void assertRefused(EffectSpec effect, String expectedInMessage) {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(rule("BATTLE_START", effect))));
        Assertions.assertTrue(refused.getMessage().contains(expectedInMessage), refused.getMessage());
    }

    private static double boostOf(Character who, AttributeType attribute) {
        return who.getAttribute(attribute).get();
    }

    /**
     * What the rule really granted, as a delta against the value the case says that unit started with.
     *
     * <p>A delta and not an absolute reading: the fixture sets a starting Break Effect, and a modifier on the same
     * attribute is added on top of it ({@code getAttribute} resolves the whole chain), so "1.1 was granted" shows up
     * as {@code 3.1}. The bare value is passed in rather than reconstructed, because "what this unit started with"
     * is exactly the fact the case is asserting about.
     */
    private static double grantedTo(Battle battle, int index, AttributeType attribute, double bare) {
        return boostOf(battle.characters.get(index), attribute) - bare;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
