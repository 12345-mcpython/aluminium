package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * The {@code REMOVE_STACK} op: "…stacking up to 3 time(s); at the start of the wearer's turn, removes 1
 * stack(s) of this effect".
 *
 * <p><b>Why the op had to exist.</b> {@code MODIFY_ATTR} with {@code max_stacks} already models the stacking
 * half, and {@code BuffManager.removeOneBuff} was already there — but a <b>rule</b> could only ever grow a
 * stack. So a text whose second half takes a stack back could only be modelled by dropping that half, which
 * is exactly what relic set 131 (「星如我见的领航员」) was registered as: unmodelled.
 *
 * <p>The observable is the attribute's value (the stacks are ordinary modifiers, summed), so these cases do
 * not need a damage pipeline: they apply stacks with real rules and take them back with real rules.
 */
public class TriggerRemoveStackTest {
    /** Himeko: no shipped rule file, so the table under test is the only one in the battle. */
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    private static final AttributeType BOOST = AttributeType.SKILL_DAMAGE_BOOST;

    // ==================================================================
    // 1. Taking a stack back
    // ==================================================================

    @Test
    public void removeStackTakesExactlyOneStackOff() {
        Battle battle = battleWith(stackRule(0.18, 3), removeRule(1));
        Character owner = battle.characters.getFirst();

        for (int i = 0; i < 3; i++) {
            fire(battle, owner);
        }
        Assertions.assertEquals(0.54, boostOf(owner), EPS, "precondition: three stacks of 18%");

        remove(battle, owner);
        Assertions.assertEquals(0.36, boostOf(owner), EPS, "one stack came off, and the rest are exact");
    }

    @Test
    public void removingMoreThanThereAreTakesWhatIsThereAndIsNotAnError() {
        Battle battle = battleWith(stackRule(0.18, 3), removeRule(5));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        fire(battle, owner);
        Assertions.assertEquals(0.36, boostOf(owner), EPS);

        remove(battle, owner);
        Assertions.assertEquals(0.0, boostOf(owner), EPS,
                "the common shape fires every turn, so an over-removal must not throw -- it takes what exists");
    }

    @Test
    public void removingWithNothingToRemoveIsANoOp() {
        Battle battle = battleWith(removeRule(1));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, remove(battle, owner),
                "the rule does fire -- there is simply nothing on the attribute to take");
        Assertions.assertEquals(0.0, boostOf(owner), EPS,
                "and removing nothing is not an error: 'at the start of the wearer's turn, removes 1 stack' "
                        + "fires on every turn, including the ones where the counter is at zero");
    }

    /**
     * The newest stack goes first — the same order {@code BuffManager.removeOneBuff} uses.
     *
     * <p>The two stacks carry <b>different</b> values on purpose: with equal ones the order would be
     * invisible, and "which stack came off" is exactly what a reader would otherwise have to assume. They are
     * also put on <b>different events</b>, so one firing adds exactly one stack — both rules on one event
     * would add two stacks per firing and hit the shared cap of 3 immediately (measured while writing this:
     * it read 0.7 instead of 0.6).
     */
    @Test
    public void theMostRecentlyAddedStackGoesFirst() {
        Battle battle = battleWith(stackRuleOn("ALLY_ATTACK", 0.1, 3),
                stackRuleOn("HP_LOST", 0.5, 3), removeRule(1));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);                                          // +10%
        battle.fireTriggers(TriggerEvent.HP_LOST, owner, owner, 0, 0); // +50% (same group -> a second stack)
        Assertions.assertEquals(0.6, boostOf(owner), EPS, "precondition: both stacks are on");

        remove(battle, owner);
        Assertions.assertEquals(0.1, boostOf(owner), EPS,
                "the 50% stack was added last, so it is the one that comes off");
    }

    /** The op honours the {@code target} selector like every other effect. */
    @Test
    public void stacksCanBeRemovedFromAnotherUnit() {
        Battle battle = withAlly(stackRule(0.18, 3), removeFromTargetRule(1));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        fire(battle, owner);
        Assertions.assertEquals(0.18, boostOf(owner), EPS);

        // the remove rule targets the event's subject, so fire with the owner as the subject
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, owner, 0, 0);
        Assertions.assertEquals(0.0, boostOf(owner), EPS, "the stack came off the event's target");
        Assertions.assertEquals(0.0, boostOf(ally), EPS, "and never off the rule's owner");
    }

    // ==================================================================
    // 2. Fail fast at load time
    // ==================================================================

    @Test
    public void aRemoveStackWithoutAnAttributeIsRejectedAtLoadTime() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REMOVE_STACK");
        TriggerSpecs.set(effect, "amount", 1.0);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
        Assertions.assertTrue(e.getMessage().contains("attribute"), e.getMessage());
    }

    @Test
    public void aNonPositiveAmountIsRejectedAtLoadTime() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(removeRule(0))));
        Assertions.assertTrue(zero.getMessage().contains("positive"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(removeRule(-2))));
    }

    @Test
    public void aRemoveStackCannotClaimAStackCap() {
        EffectSpec effect = removeOp(1);
        TriggerSpecs.set(effect, "maxStacks", 3);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
        Assertions.assertTrue(e.getMessage().contains("max_stacks"), e.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A rule that adds one stack of {@code percent} on the boost attribute (cap 3), on any ally attack. */
    private static TriggerSpec stackRule(double percent, int maxStacks) {
        return stackRuleOn("ALLY_ATTACK", percent, maxStacks);
    }

    /** The same, on a named event — the stack cases need one firing to add exactly one stack. */
    private static TriggerSpec stackRuleOn(String event, double percent, int maxStacks) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", BOOST.name());
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "maxStacks", maxStacks);
        return TriggerSpecs.rule(event, null, effect);
    }

    /** A rule on a skill cast that removes {@code amount} stacks of the boost attribute. */
    private static TriggerSpec removeRule(double amount) {
        return TriggerSpecs.rule("SKILL_CAST", null, removeOp(amount));
    }

    /** The same, but asking for the event's subject instead of the rule's owner. */
    private static TriggerSpec removeFromTargetRule(double amount) {
        EffectSpec effect = removeOp(amount);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("SKILL_CAST", null, effect);
    }

    private static EffectSpec removeOp(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REMOVE_STACK");
        TriggerSpecs.set(effect, "attribute", BOOST.name());
        TriggerSpecs.set(effect, "amount", amount);
        return effect;
    }

    private static Battle battleWith(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs)), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Battle withAlly(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs), CharacterFactory.create(ALLY, LEVEL)),
                List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    private static void fire(Battle battle, Character actor) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    private static int remove(Battle battle, Character actor) {
        return battle.fireTriggers(TriggerEvent.SKILL_CAST, actor, null, 0, 0);
    }

    /** The attribute's current value, i.e. what the stacks add up to. */
    private static double boostOf(Character who) {
        return who.getAttribute(BOOST).get();
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 100, 100, 100);
    }
}
