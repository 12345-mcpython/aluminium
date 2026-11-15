package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The generic primitives the relic abilities needed (a follow-up to the relic work): a <b>stackable</b> stat
 * modifier, an <b>unbounded</b> duration, and the two trigger-table arguments that expose them
 * ({@code max_stacks} / {@code permanent}).
 *
 * <h2>What has to be pinned, and why each one is easy to get quietly wrong</h2>
 * <ul>
 *   <li><b>Stacking is opt-in.</b> {@code StatModifierBuff.isSameKind} historically means "re-applying
 *       this replaces it", and two shipped tests depend on that. A stackable buff must accumulate
 *       <em>without</em> changing that predicate, so both behaviours are asserted here side by side -
 *       a change that makes the plain {@code MODIFY_ATTR} start stacking would be a silent buff to
 *       every character in the game.</li>
 *   <li><b>The cap is a cap, not a rolling window.</b> Applying a sixth copy of a 5-stack buff must
 *       change nothing at all; "evict the oldest" would mean the documented maximum is never
 *       reachable.</li>
 *   <li><b>Removal is exact and per-stack.</b> Each stack is an ordinary buff with its own id, so
 *       removing one must subtract exactly one stack's worth (not "recompute the attribute"), and
 *       expiry must return the attribute to base exactly - the property
 *       {@code BuffRuleTest.expiryRestoresTheOriginalValueExactly} already pins for one stack.</li>
 *   <li><b>"For the rest of the battle" is a flag, not a big number.</b> A permanent buff is never
 *       ticked, so it survives any number of turns; spelling it {@code Integer.MAX_VALUE} would still
 *       count down and would eventually expire.</li>
 *   <li><b>The arguments fail loudly at load.</b> A typo in {@code max_stacks} or a cap of 0 would
 *       otherwise produce a rule that loads, fires, and does nothing visible.</li>
 * </ul>
 */
public class StatModifierStackingTest {

    /**
     * hp 1000 / def 200 / atk 300 / speed 100 - all different, so a mix-up shows.
     */
    private static Character hero() {
        return Character.fromAttributes("hero", 1000, 200, 300, 100);
    }

    private static double attackOf(Character c) {
        return c.getAttribute(AttributeType.ATTACK).get();
    }

    private static int attackModifiersOf(Character c) {
        return c.getAttribute(AttributeType.ATTACK)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF).size();
    }

    /**
     * The stack lifecycle on one character: applications accumulate, the cap is a hard ceiling, removal is
     * per stack and exact, and clearing removes every stack.
     */
    @Test
    public void stackableModifiersAccumulateCapAndAreRemovedExactly() {
        Character c = hero();
        for (int i = 0; i < 5; i++) {
            c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }

        Assertions.assertAll("five applications of a 5-stack +5% ATK buff",
                () -> Assertions.assertEquals(5, attackModifiersOf(c),
                        "each application must install its own modifier, or nothing accumulates"),
                () -> Assertions.assertEquals(300 * 1.25, attackOf(c), 1e-9,
                        "five stacks of +5% ATK on a base of 300 is 375"),
                () -> Assertions.assertEquals(5, c.getBuffManager().countBuffs(StatModifierBuff.class),
                        "five applications must leave five buffs to remove or expire"));

        // Three more applications reach eight in total, so the cap is exercised from a full stack.
        for (int i = 0; i < 3; i++) {
            c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }

        Assertions.assertAll("sixth, seventh and eighth applications at the cap",
                () -> Assertions.assertEquals(5, attackModifiersOf(c),
                        "a 5-stack buff must stop at five modifiers, not roll over"),
                () -> Assertions.assertEquals(300 * 1.25, attackOf(c), 1e-9,
                        "the sixth, seventh and eighth applications are no-ops"));

        Assertions.assertAll("removing two of the five stacks",
                () -> Assertions.assertTrue(c.getBuffManager().removeOneBuff(StatModifierBuff.class),
                        "the first stack is removable"),
                () -> Assertions.assertTrue(c.getBuffManager().removeOneBuff(StatModifierBuff.class),
                        "the second stack is removable"),
                () -> Assertions.assertEquals(3, attackModifiersOf(c),
                        "two removals must leave three stack modifiers"),
                () -> Assertions.assertEquals(300 * 1.15, attackOf(c), 1e-9,
                        "removal takes one modifier with it; it does not recompute the attribute"));

        Assertions.assertAll("removing the remaining three stacks",
                () -> {
                    for (int i = 0; i < 3; i++) {
                        Assertions.assertTrue(c.getBuffManager().removeOneBuff(StatModifierBuff.class),
                                "remaining stack " + (i + 1) + " is removable");
                    }
                },
                () -> Assertions.assertEquals(300, attackOf(c), 1e-9,
                        "removing the last stack returns the attribute to base exactly"),
                () -> Assertions.assertFalse(c.getBuffManager().removeOneBuff(StatModifierBuff.class),
                        "removing a stack that is not there reports false rather than throwing"));

        Character cleared = hero();
        for (int i = 0; i < 4; i++) {
            cleared.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }
        cleared.getBuffManager().clearAll();

        Assertions.assertAll("clearAll on a character holding four stacks",
                () -> Assertions.assertEquals(300, attackOf(cleared), 1e-9,
                        "clearAll removes every stack, returning ATK to base"),
                () -> Assertions.assertEquals(0, attackModifiersOf(cleared),
                        "no stack modifier survives clearAll"));
    }

    /**
     * "For the rest of the battle": a permanent stack survives any number of turn boundaries.
     *
     * <p>The assertion is deliberately "after many rounds the modifier is still there, with the same
     * duration" - the concrete meaning of unbounded here. A {@code Integer.MAX_VALUE} duration would
     * pass this too but would eventually expire; the flag cannot.
     */
    @Test
    public void permanentStacksSurviveEveryTurnBoundary() {
        Character c = hero();
        StatModifierBuff permanent = StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05,
                "buff", turnsPlaceholder(), false, true, 5);
        c.getBuffManager().addBuff(permanent);

        for (int turn = 0; turn < 20; turn++) {
            turnBoundary(c);
        }

        Assertions.assertAll("a permanent +5% ATK stack after twenty turn boundaries",
                () -> Assertions.assertEquals(1, attackModifiersOf(c),
                        "a permanent buff is never ticked, so it cannot expire at a turn boundary"),
                () -> Assertions.assertEquals(300 * 1.05, attackOf(c), 1e-9,
                        "the same single stack is still worth +5% after twenty rounds"),
                () -> Assertions.assertTrue(permanent.isPermanent(),
                        "the buff records itself as permanent, not as a large turn count"));
    }

    /**
     * The other half of the opt-in rule: a modifier built without a cap still replaces, and only a
     * different stack group accumulates alongside.
     *
     * <p>{@code BuffRuleTest.theSameBuffAgainRefreshesInsteadOfStacking} pins the replacement for the
     * convenience factories, and this pins it for the data-driven one.
     */
    @Test
    public void kindIdentityDecidesReplaceVersusAccumulate() {
        Character replaced = hero();
        replaced.getBuffManager().addBuff(plainAttack(0.5, 2));
        replaced.getBuffManager().addBuff(plainAttack(0.2, 5));

        Assertions.assertAll("a plain modifier built without a cap, applied twice",
                () -> Assertions.assertEquals(1, attackModifiersOf(replaced),
                        "same kind replaces, it does not stack"),
                () -> Assertions.assertEquals(300 * 1.2, attackOf(replaced), 1e-9, "the newest value wins"));

        Character triggered = hero();
        triggered.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battleWith(triggered);

        fire(battle, 3);

        Assertions.assertAll("a plain MODIFY_ATTR fired three times",
                () -> Assertions.assertEquals(1, attackModifiersOf(triggered),
                        "a plain MODIFY_ATTR must keep replacing no matter how often its rule fires"),
                () -> Assertions.assertEquals(300 * 1.5, attackOf(triggered), 1e-9,
                        "the newest +50% wins, not three copies of it"));

        Character mixedGroups = hero();
        mixedGroups.getBuffManager().addBuff(stackableAttack(0.05, 5));
        mixedGroups.getBuffManager().addBuff(StatModifierBuff.of(AttributeType.DEFENCE, "add_percent", 0.1,
                "buff", turnsPlaceholder(), false, true, 5));

        Assertions.assertAll("an ATK stack group and a DEF stack group on one character",
                () -> Assertions.assertEquals(300 * 1.05, attackOf(mixedGroups), 1e-9,
                        "the DEF stack does not touch ATK"),
                () -> Assertions.assertEquals(200 * 1.1,
                        mixedGroups.getAttribute(AttributeType.DEFENCE).get(), 1e-9,
                        "the ATK stack does not touch DEF"),
                () -> Assertions.assertEquals(2,
                        mixedGroups.getBuffManager().countBuffs(StatModifierBuff.class),
                        "different attributes are different stack groups, so both stacks live"));
    }

    /**
     * The trigger-table view of the cap: three firings of a 5-stack rule install three stacks, a 3-stack
     * cap holds at any number of firings, and the {@code stacks} alias means the same as {@code max_stacks}.
     *
     * <p>{@code max_stacks} + {@code permanent} make one rule accumulate instead of refreshing, and both
     * cap spellings fail loudly at load when they disagree.
     */
    @Test
    public void theTriggerTableBoundsStackingThroughMaxStacksAndItsAlias() {
        Character threeOfFive = hero();
        threeOfFive.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 5, null, null))));
        Battle firstBattle = battleWith(threeOfFive);

        fire(firstBattle, 3);

        Assertions.assertAll("three firings of a 5-stack permanent rule",
                () -> Assertions.assertEquals(3, attackModifiersOf(threeOfFive),
                        "three firings, three stacks"),
                () -> Assertions.assertEquals(300 * 1.15, attackOf(threeOfFive), 1e-9,
                        "three stacks of +5% ATK on a base of 300 is 345"),
                () -> {
                    for (DoubleValue.Modifier modifier : threeOfFive.getAttribute(AttributeType.ATTACK)
                            .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF)) {
                        Assertions.assertEquals(0.05, modifier.getValue(), 1e-9,
                                "each stack is +5%, not one merged value");
                    }
                });

        Character cappedAtThree = hero();
        cappedAtThree.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 3, null, null))));
        Battle secondBattle = battleWith(cappedAtThree);

        fire(secondBattle, 10);

        Assertions.assertAll("ten firings of a 3-stack permanent rule",
                () -> Assertions.assertEquals(3, attackModifiersOf(cappedAtThree),
                        "the cap stops the rule at three modifiers"),
                () -> Assertions.assertEquals(300 * 1.15, attackOf(cappedAtThree), 1e-9,
                        "the fourth through tenth firings are no-ops"));

        Character aliased = hero();
        aliased.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, null, 4, null))));
        Battle aliasBattle = battleWith(aliased);

        fire(aliasBattle, 6);

        Assertions.assertAll("the stacks alias spelling of the cap",
                () -> Assertions.assertEquals(4, attackModifiersOf(aliased),
                        "stacks is accepted as an alias of max_stacks, so four stacks are installed"),
                () -> Assertions.assertEquals(300 * 1.20, attackOf(aliased), 1e-9,
                        "four stacks of +5% ATK on a base of 300 is 360"));
    }

    /**
     * A rule can stack a <b>timed</b> modifier too, not only a permanent one.
     *
     * <p>Not used by a relic rule yet (every shipped stack is "for the rest of the battle"), but it is
     * the shape several character talents have ("+8% CRIT Rate for 2 turns, up to 2 stacks"), and if it
     * did not work the primitive would be a relic-shaped special case rather than a generic one.
     *
     * <p>One "turn" here is a {@code beforeMove}/{@code afterMove} pair: a late buff is ticked by the
     * {@code afterMove} half, so a 2-turn stack is gone after the second pair - and both stacks were
     * created at the same moment, so both must go together, with the attribute back at base exactly.
     */
    @Test
    public void timedStacksWorkAsWellAndStillExpireExactly() {
        Character c = hero();
        c.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("CRIT_CHANCE", 0.08, 2, null, 2, null, null))));
        Battle battle = battleWith(c);

        fire(battle, 2);

        Assertions.assertAll("two timed CRIT_CHANCE stacks",
                () -> Assertions.assertEquals(2, c.getBuffManager().countBuffs(StatModifierBuff.class),
                        "two firings install two timed stacks"),
                () -> Assertions.assertEquals(0.16, c.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                        "two stacks of +8% CRIT Rate make +16%"));

        turnBoundary(c);

        Assertions.assertAll("after the first turn boundary",
                () -> Assertions.assertEquals(2, c.getBuffManager().countBuffs(StatModifierBuff.class),
                        "a 2-turn stack survives its first turn boundary"),
                () -> Assertions.assertEquals(0.16, c.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                        "the stacks are still worth +16% after one turn"));

        turnBoundary(c);

        Assertions.assertAll("after the second turn boundary",
                () -> Assertions.assertEquals(0, c.getBuffManager().countBuffs(StatModifierBuff.class),
                        "both stacks were applied at the same moment, so both expire together"),
                () -> Assertions.assertEquals(0, c.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                        "the attribute returns to base exactly (0 for a ratio attribute)"));
    }

    /**
     * Load-time validation of the new {@code MODIFY_ATTR} arguments: a non-positive cap and a cap above the
     * engine limit are rejected, stating both cap spellings is rejected, and the duration must be stated
     * exactly once with no non-positive turn count.
     *
     * <p>All of these throw where the table is compiled rather than mid-battle, so a typo cannot produce a
     * rule that loads, fires, and does nothing visible.
     */
    @Test
    public void invalidCapAndDurationArgumentsAreRejectedAtLoad() {
        Assertions.assertAll("MODIFY_ATTR stack and duration arguments",
                () -> {
                    IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                    TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 0, null, null))),
                            "a zero stack cap is rejected at load");
                    Assertions.assertTrue(zero.getMessage().contains("non-positive stack cap"),
                            zero.getMessage());
                },
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, -3, null, null))),
                        "a negative stack cap is rejected at load"),
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                    TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true,
                                            Constant.MAX_STACKS_LIMIT + 1, null, null))),
                            "a cap above the engine limit is rejected at load");
                    Assertions.assertTrue(e.getMessage().contains(String.valueOf(Constant.MAX_STACKS_LIMIT)),
                            e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                    TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 3, 3, null))),
                            "stating both max_stacks and stacks is rejected at load");
                    Assertions.assertTrue(e.getMessage().contains("alias"), e.getMessage());
                },
                () -> {
                    IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                    TriggerSpecs.modifyAttr("ATTACK", 0.05, null, null, null, null, null))),
                            "a rule with neither turns nor permanent is rejected at load");
                    Assertions.assertTrue(missing.getMessage().contains("permanent"), missing.getMessage());
                },
                () -> {
                    IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                    TriggerSpecs.modifyAttr("ATTACK", 0.05, 2, true, null, null, null))),
                            "a rule stating both turns and permanent is rejected at load");
                    Assertions.assertTrue(both.getMessage().contains("both"), both.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                                    TriggerSpecs.modifyAttr("ATTACK", 0.05, 0, null, null, null, null))),
                            "a non-positive turn count is rejected at load");
                    Assertions.assertTrue(e.getMessage().contains("permanent"),
                            "the message must name the argument that expresses 'for the rest of the battle': "
                                    + e.getMessage());
                });
    }

    /**
     * The new arguments belong to {@code MODIFY_ATTR} only.
     *
     * <p>Without this check a {@code max_stacks} written on, say, {@code HEAL} would be read by Gson,
     * ignored by the interpreter, and the rule would load perfectly while doing the wrong thing - the same
     * silent-typo class the closed op vocabulary exists to eliminate.
     */
    @Test
    public void theNewArgumentsAreRejectedOnOtherOps() {
        EffectSpec heal = TriggerSpecs.heal(10, null);
        TriggerSpecs.set(heal, "maxStacks", 3);
        EffectSpec permanentHeal = TriggerSpecs.heal(10, null);
        TriggerSpecs.set(permanentHeal, "permanent", true);

        Assertions.assertAll("stack and duration arguments on a HEAL effect",
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(), heal)),
                            "max_stacks on a heal rule is rejected at load");
                    Assertions.assertTrue(e.getMessage().contains("MODIFY_ATTR"), e.getMessage());
                },
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(), permanentHeal)),
                        "permanent on a heal rule is rejected at load"));
    }

    /**
     * The primitive itself refuses a nonsensical cap / duration, independent of the JSON layer.
     */
    @Test
    public void theBuffFactoryRejectsNonsense() {
        Assertions.assertAll("StatModifierBuff.of argument validation",
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05, "buff", 1, false,
                                true, 0),
                        "a stackable buff with a zero cap is rejected"),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05, "buff", 0, false,
                                false, 3),
                        "a stack that expires immediately would be a silent no-op"),
                () -> Assertions.assertDoesNotThrow(
                        () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05, "buff", 0, false,
                                true, 3),
                        "…but a permanent stack needs no turn count"));
    }

    /**
     * A stackable buff that cannot name its own stack group is refused rather than accumulated
     * without a bound.
     *
     * <p>The invariant is "stackable so a non-null group key" (only the count of siblings makes a cap
     * enforceable). A future buff class that forgets the second half would otherwise grow the list for
     * every application, which is exactly what a cap exists to prevent.
     */
    @Test
    public void aStackableBuffWithoutAGroupKeyIsRefused() {
        Character c = hero();
        AbstractBuff broken = new AbstractBuff(1, false) {
            @Override
            public boolean isStackable() {
                return true;
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
            }
        };

        Assertions.assertAll("a stackable buff with no stack group key",
                () -> {
                    IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                            () -> c.getBuffManager().addBuff(broken),
                            "adding it is refused");
                    Assertions.assertTrue(e.getMessage().contains("stack group key"), e.getMessage());
                },
                () -> Assertions.assertEquals(0, c.getBuffManager().countBuffs(AbstractBuff.class),
                        "the refused buff is not installed"));
    }

    /**
     * The duration placeholder: never read for a permanent buff (see {@code TriggerInterpreter}).
     */
    private static int turnsPlaceholder() {
        return 1;
    }

    private static StatModifierBuff stackableAttack(double percent, int maxStacks) {
        return StatModifierBuff.of(AttributeType.ATTACK, "add_percent", percent, "buff", 2, false,
                true, maxStacks);
    }

    private static StatModifierBuff plainAttack(double percent, int turns) {
        return StatModifierBuff.percentBuff(AttributeType.ATTACK, percent, turns);
    }

    private static TriggerTable table(TriggerSpec... specs) {
        return new TriggerTable(0, List.of(specs));
    }

    /**
     * A battle of one character against one enemy with far more HP than any test needs.
     */
    private static Battle battleWith(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        Battle battle = BattleFixture.battle(List.of(hero), List.of(enemy));
        battle.startBattle();
        return battle;
    }

    /**
     * Fires {@code ALLY_ATTACK} {@code times} times with the character as the actor.
     *
     * <p>Deliberately the real {@code Battle.fireTriggers} entry point rather than the interpreter's
     * {@code apply}: the claim under test is "a relic rule can use this", and the path from the event
     * to the table is part of that.
     */
    private static void fire(Battle battle, int times) {
        Character actor = battle.characters().getFirst();
        for (int i = 0; i < times; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0));
        }
    }

    /**
     * One turn of the owner's own action bar: the early tick, then the late one.
     */
    private static void turnBoundary(Character c) {
        c.getBuffManager().beforeMove();
        c.getBuffManager().afterMove();
    }
}
