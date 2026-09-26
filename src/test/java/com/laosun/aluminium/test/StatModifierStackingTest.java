package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The generic primitives the relic abilities needed (P10-3 follow-up): a <b>stackable</b> stat
 * modifier, an <b>unbounded</b> duration, and the two trigger-table arguments that expose them
 * ({@code max_stacks} / {@code permanent}).
 *
 * <h2>What has to be pinned, and why each one is easy to get quietly wrong</h2>
 * <ul>
 *   <li><b>Stacking is opt-in.</b> {@code StatModifierBuff.isSameKind} historically means "re-applying
 *       this replaces it", and two shipped tests depend on that. A stackable buff must accumulate
 *       <em>without</em> changing that predicate, so both behaviours are asserted here side by side —
 *       a change that makes the plain {@code MODIFY_ATTR} start stacking would be a silent buff to
 *       every character in the game.</li>
 *   <li><b>The cap is a cap, not a rolling window.</b> Applying a sixth copy of a 5-stack buff must
 *       change nothing at all; "evict the oldest" would mean the documented maximum is never
 *       reachable.</li>
 *   <li><b>Removal is exact and per-stack.</b> Each stack is an ordinary buff with its own id, so
 *       removing one must subtract exactly one stack's worth (not "recompute the attribute"), and
 *       expiry must return the attribute to base exactly — the property
 *       {@code BuffRuleTest.expiryRestoresTheOriginalValueExactly} already pins for one stack.</li>
 *   <li><b>"For the rest of the battle" is a flag, not a big number.</b> A permanent buff is never
 *       ticked, so it survives any number of turns; spelling it {@code Integer.MAX_VALUE} would still
 *       count down and would eventually expire.</li>
 *   <li><b>The arguments fail loudly at load.</b> A typo in {@code max_stacks} or a cap of 0 would
 *       otherwise produce a rule that loads, fires, and does nothing visible.</li>
 * </ul>
 */
public class StatModifierStackingTest {

    /** hp 1000 / def 200 / atk 300 / speed 100 — all different, so a mix-up shows. */
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

    // ==================================================================
    // 1. The primitive: a stackable stat buff
    // ==================================================================

    /** Copies accumulate: five applications of +5% ATK are +25%, not +5%. */
    @Test
    public void stackableModifiersAccumulate() {
        Character c = hero();
        for (int i = 0; i < 5; i++) {
            c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }

        Assertions.assertEquals(5, attackModifiersOf(c),
                "each application must install its own modifier, or nothing accumulates");
        Assertions.assertEquals(300 * 1.25, attackOf(c), 1e-9,
                "five stacks of +5% ATK on a base of 300 is 375");
        Assertions.assertEquals(5, c.getBuffManager().countBuffs(StatModifierBuff.class));
    }

    /** The cap is enforced: a sixth application changes nothing, and nothing is evicted. */
    @Test
    public void theStackCapIsEnforcedAndNothingIsEvicted() {
        Character c = hero();
        for (int i = 0; i < 8; i++) {
            c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }

        Assertions.assertEquals(5, attackModifiersOf(c),
                "a 5-stack buff must stop at five modifiers, not roll over");
        Assertions.assertEquals(300 * 1.25, attackOf(c), 1e-9,
                "the sixth, seventh and eighth applications are no-ops");
    }

    /** Removal is per stack and exact: two stacks off leaves three stacks' worth. */
    @Test
    public void stacksAreRemovedOneAtATimeAndExactly() {
        Character c = hero();
        for (int i = 0; i < 5; i++) {
            c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }

        Assertions.assertTrue(c.getBuffManager().removeOneBuff(StatModifierBuff.class));
        Assertions.assertTrue(c.getBuffManager().removeOneBuff(StatModifierBuff.class));

        Assertions.assertEquals(3, attackModifiersOf(c));
        Assertions.assertEquals(300 * 1.15, attackOf(c), 1e-9,
                "removal takes one modifier with it; it does not recompute the attribute");

        for (int i = 0; i < 3; i++) {
            Assertions.assertTrue(c.getBuffManager().removeOneBuff(StatModifierBuff.class));
        }
        Assertions.assertEquals(300, attackOf(c), 1e-9,
                "removing the last stack returns the attribute to base exactly");
        Assertions.assertFalse(c.getBuffManager().removeOneBuff(StatModifierBuff.class),
                "removing a stack that is not there reports false rather than throwing");
    }

    /**
     * "For the rest of the battle": a permanent stack survives any number of turn boundaries.
     *
     * <p>The assertion is deliberately "after many rounds the modifier is still there, with the same
     * duration" — the concrete meaning of unbounded here. A {@code Integer.MAX_VALUE} duration would
     * pass this too but would eventually expire; the flag cannot.
     */
    @Test
    public void permanentStacksSurviveEveryTurnBoundary() {
        Character c = hero();
        StatModifierBuff permanent = StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05,
                "buff", turnsPlaceholder(), false, true, 5);
        c.getBuffManager().addBuff(permanent);

        for (int turn = 0; turn < 20; turn++) {
            c.getBuffManager().beforeMove();
            c.getBuffManager().afterMove();
        }

        Assertions.assertEquals(1, attackModifiersOf(c),
                "a permanent buff is never ticked, so it cannot expire at a turn boundary");
        Assertions.assertEquals(300 * 1.05, attackOf(c), 1e-9);
        Assertions.assertTrue(permanent.isPermanent());
    }

    /** A stackable buff is still removable as a whole (dispel / death / {@code clearAll}). */
    @Test
    public void clearingRemovesEveryStack() {
        Character c = hero();
        for (int i = 0; i < 4; i++) {
            c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        }

        c.getBuffManager().clearAll();

        Assertions.assertEquals(300, attackOf(c), 1e-9);
        Assertions.assertEquals(0, attackModifiersOf(c));
    }

    // ==================================================================
    // 2. The default is untouched: replace, do not stack
    // ==================================================================

    /**
     * A modifier built <b>without</b> a cap replaces, exactly as before this change.
     *
     * <p>This is the other half of the opt-in rule, and the one that protects every existing
     * character: {@code BuffRuleTest.theSameBuffAgainRefreshesInsteadOfStacking} pins it for the
     * convenience factories, and this pins it for the data-driven one.
     */
    @Test
    public void withoutACapTheModifierStillReplaces() {
        Character c = hero();
        c.getBuffManager().addBuff(plainAttack(0.5, 2));
        c.getBuffManager().addBuff(plainAttack(0.2, 5));

        Assertions.assertEquals(1, attackModifiersOf(c), "same kind replaces, it does not stack");
        Assertions.assertEquals(300 * 1.2, attackOf(c), 1e-9, "the newest value wins");

        // And the same through the trigger table's plain MODIFY_ATTR, which is what content uses.
        Character triggered = hero();
        TriggerTable table = table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.5, 2)));
        triggered.setTriggerTable(table);
        Battle battle = battleWith(triggered);

        fire(battle, 3);
        Assertions.assertEquals(1, attackModifiersOf(triggered),
                "a plain MODIFY_ATTR must keep replacing no matter how often its rule fires");
        Assertions.assertEquals(300 * 1.5, attackOf(triggered), 1e-9);
    }

    /**
     * A stack of a different kind is a different stack group: +ATK% and +DEF% accumulate
     * independently (the same distinction {@code isSameKind} makes).
     */
    @Test
    public void stackGroupsDoNotMixAttributes() {
        Character c = hero();
        c.getBuffManager().addBuff(stackableAttack(0.05, 5));
        c.getBuffManager().addBuff(StatModifierBuff.of(AttributeType.DEFENCE, "add_percent", 0.1,
                "buff", turnsPlaceholder(), false, true, 5));

        Assertions.assertEquals(300 * 1.05, attackOf(c), 1e-9);
        Assertions.assertEquals(200 * 1.1, c.getAttribute(AttributeType.DEFENCE).get(), 1e-9);
        Assertions.assertEquals(2, c.getBuffManager().countBuffs(StatModifierBuff.class));
    }

    // ==================================================================
    // 3. The trigger-table view: max_stacks / permanent
    // ==================================================================

    /** {@code max_stacks} + {@code permanent} make one rule accumulate instead of refreshing. */
    @Test
    public void theTriggerTableCanAskForAStackingPermanentModifier() {
        Character c = hero();
        c.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 5, null, null))));
        Battle battle = battleWith(c);

        fire(battle, 3);

        Assertions.assertEquals(3, attackModifiersOf(c), "three firings, three stacks");
        Assertions.assertEquals(300 * 1.15, attackOf(c), 1e-9);
        for (DoubleValue.Modifier modifier : c.getAttribute(AttributeType.ATTACK)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF)) {
            Assertions.assertEquals(0.05, modifier.getValue(), 1e-9);
        }
    }

    /** The cap reaches through the table too: ten firings of a 3-stack rule stop at three. */
    @Test
    public void theTriggerTableHonoursTheCap() {
        Character c = hero();
        c.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 3, null, null))));
        Battle battle = battleWith(c);

        fire(battle, 10);

        Assertions.assertEquals(3, attackModifiersOf(c));
        Assertions.assertEquals(300 * 1.15, attackOf(c), 1e-9);
    }

    /** The alias spelling {@code stacks} means the same thing as {@code max_stacks}. */
    @Test
    public void theStacksAliasIsAccepted() {
        Character c = hero();
        c.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, null, 4, null))));
        Battle battle = battleWith(c);

        fire(battle, 6);

        Assertions.assertEquals(4, attackModifiersOf(c));
        Assertions.assertEquals(300 * 1.20, attackOf(c), 1e-9);
    }

    /**
     * A rule can stack a <b>timed</b> modifier too, not only a permanent one.
     *
     * <p>Not used by a relic rule yet (every shipped stack is "for the rest of the battle"), but it is
     * the shape several character talents have ("+8% CRIT Rate for 2 turns, up to 2 stacks"), and if it
     * did not work the primitive would be a relic-shaped special case rather than a generic one.
     *
     * <p>One "turn" here is a {@code beforeMove}/{@code afterMove} pair: a late buff is ticked by the
     * {@code afterMove} half, so a 2-turn stack is gone after the second pair — and both stacks were
     * created at the same moment, so both must go together, with the attribute back at base exactly.
     */
    @Test
    public void timedStacksWorkAsWellAndStillExpireExactly() {
        Character c = hero();
        c.setTriggerTable(table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                TriggerSpecs.modifyAttr("CRIT_CHANCE", 0.08, 2, null, 2, null, null))));
        Battle battle = battleWith(c);

        fire(battle, 2);
        Assertions.assertEquals(2, c.getBuffManager().countBuffs(StatModifierBuff.class));
        Assertions.assertEquals(0.16, c.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9);

        turnBoundary(c);
        Assertions.assertEquals(2, c.getBuffManager().countBuffs(StatModifierBuff.class),
                "a 2-turn stack survives its first turn boundary");
        Assertions.assertEquals(0.16, c.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9);

        turnBoundary(c);
        Assertions.assertEquals(0, c.getBuffManager().countBuffs(StatModifierBuff.class),
                "both stacks were applied at the same moment, so both expire together");
        Assertions.assertEquals(0, c.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "the attribute returns to base exactly (0 for a ratio attribute)");
    }

    /** One turn of the owner's own action bar: the early tick, then the late one. */
    private static void turnBoundary(Character c) {
        c.getBuffManager().beforeMove();
        c.getBuffManager().afterMove();
    }

    // ==================================================================
    // 4. Load-time validation, naming the phase
    // ==================================================================

    /** A cap that could never apply is rejected where the table is compiled, not mid-battle. */
    @Test
    public void nonPositiveStackCapsAreRejectedAtLoad() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 0, null, null))));
        Assertions.assertTrue(zero.getMessage().contains("non-positive stack cap"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, -3, null, null))));
    }

    /** Above the engine's limit is rejected too, so a typo cannot grow the buff list without bound. */
    @Test
    public void absurdStackCapsAreRejectedAtLoad() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true,
                                Constant.MAX_STACKS_LIMIT + 1, null, null))));
        Assertions.assertTrue(e.getMessage().contains(String.valueOf(Constant.MAX_STACKS_LIMIT)),
                e.getMessage());
    }

    /** Stating both spellings is ambiguous and is rejected rather than silently picking one. */
    @Test
    public void bothSpellingsOfTheCapAreRejectedAtLoad() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, null, true, 3, 3, null))));
        Assertions.assertTrue(e.getMessage().contains("alias"), e.getMessage());
    }

    /**
     * The duration is still mandatory, and {@code turns} vs {@code permanent} are mutually exclusive.
     *
     * <p>Both directions are asserted because both are silent failures otherwise: a missing duration
     * would have to default to something, and stating both would mean the engine picks one and the
     * rule's text disagrees with it.
     */
    @Test
    public void theDurationMustBeStatedExactlyOnce() {
        IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, null, null, null, null, null))));
        Assertions.assertTrue(missing.getMessage().contains("permanent"), missing.getMessage());

        IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, 2, true, null, null, null))));
        Assertions.assertTrue(both.getMessage().contains("both"), both.getMessage());
    }

    /** A non-positive turn count points the author at the argument that means "no turn limit". */
    @Test
    public void aNonPositiveTurnCountStillPointsAtPermanent() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(),
                        TriggerSpecs.modifyAttr("ATTACK", 0.05, 0, null, null, null, null))));
        Assertions.assertTrue(e.getMessage().contains("permanent"),
                "the message must name the argument that expresses 'for the rest of the battle': "
                        + e.getMessage());
    }

    /**
     * The new arguments belong to {@code MODIFY_ATTR} only.
     *
     * <p>Without this check a {@code max_stacks} written on, say, {@code GAIN_ENERGY} would be read by
     * Gson, ignored by the interpreter, and the rule would load perfectly while doing the wrong thing —
     * the same silent-typo class the closed op vocabulary exists to eliminate.
     */
    @Test
    public void theNewArgumentsAreRejectedOnOtherOps() {
        EffectSpec heal = TriggerSpecs.heal(10, null);
        TriggerSpecs.set(heal, "maxStacks", 3);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(), heal)));
        Assertions.assertTrue(e.getMessage().contains("MODIFY_ATTR"), e.getMessage());

        EffectSpec permanentHeal = TriggerSpecs.heal(10, null);
        TriggerSpecs.set(permanentHeal, "permanent", true);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("ALLY_ATTACK", List.of(), permanentHeal)));
    }

    /** The primitive itself refuses a nonsensical cap / duration, independent of the JSON layer. */
    @Test
    public void theBuffFactoryRejectsNonsense() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05, "buff", 1, false,
                        true, 0));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05, "buff", 0, false,
                        false, 3),
                "a stack that expires immediately would be a silent no-op");
        Assertions.assertDoesNotThrow(
                () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.05, "buff", 0, false,
                        true, 3),
                "…but a permanent stack needs no turn count");
    }

    /**
     * A stackable buff that cannot name its own stack group is refused rather than accumulated
     * without a bound.
     *
     * <p>The invariant is "stackable ⇒ a non-null group key" (only the count of siblings makes a cap
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

        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> c.getBuffManager().addBuff(broken));
        Assertions.assertTrue(e.getMessage().contains("stack group key"), e.getMessage());
        Assertions.assertEquals(0, c.getBuffManager().countBuffs(AbstractBuff.class));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The duration placeholder: never read for a permanent buff (see {@code TriggerInterpreter}). */
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

    /** A battle of one character against one enemy with far more HP than any test needs. */
    private static Battle battleWith(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
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
        Character actor = battle.characters.getFirst();
        for (int i = 0; i < times; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
        }
    }
}
