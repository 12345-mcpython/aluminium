package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Named states: the {@code APPLY_BUFF} op and the {@code has_state} condition.
 *
 * <p><b>Why this vocabulary exists.</b> The rule text of this game says "while in the [协奏] state" /
 * "while in the [转魄] state" / "an enemy target in the shocked state" constantly, and before this the trigger table could not ask
 * about a state at all: a mechanic that is otherwise pure data needed a Java class per character. A state is
 * not a new kind of thing in this engine - it is <b>an ordinary buff that carries a name</b> (the lesson the
 * DOT migration already taught), so it inherits duration, refresh, and {@code clearAll} removal for free.
 *
 * <p><b>What is pinned here, and why each case is worth pinning.</b>
 * <ul>
 *   <li>the op puts a state on a unit, and the condition reads it back;</li>
 *   <li>{@code target has_state X} reads the <b>event's subject</b>, not the owner - the classic
 *       actor/target confusion of this DSL, and the reason Kafka's "an enemy in the shocked state" is expressible;</li>
 *   <li>two <b>different</b> states coexist ({@code StateBuff.isSameKind} compares names, not classes - the
 *       class-based default would make [协奏] silently evict [转魄], a collision that must not happen under name matching);</li>
 *   <li>the <b>same</b> state refreshes instead of stacking;</li>
 *   <li>a state expires after its turns, and a {@code permanent} one does not;</li>
 *   <li>{@code target has_skill <SLOT>} reads the named party's <b>kit</b> - the guard 8009/8010's ultimate needs
 *       before it may command that ally to cast (and the reason both its branches are expressible);</li>
 *   <li>every argument is validated at <b>load</b> time, and a missing party fails the condition rather than
 *       passing it.</li>
 * </ul>
 *
 * <p>Cases that exercise one clause against one observed object share a scenario method and are asserted together
 * with {@link Assertions#assertAll}; rejection paths stay in their own validation scenarios.
 */
public class TriggerStateTest {
    /**
     * Himeko: no shipped rule file, so the table under test is the only one in the battle.
     */
    private static final int OWNER = 1003;
    /**
     * Tingyun: the "somebody else" whose state {@code target has_state} is asked about.
     */
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The op and the condition
    // ==================================================================

    /**
     * The op puts the named state on the owner, and applying the same name again refreshes it instead of stacking.
     */
    @Test
    public void applyBuffPutsTheNamedStateOnTheOwner() {
        Battle battle = battleWith(stateOp("ALLY_ATTACK", "协奏", 2, null));
        Character owner = battle.characters().getFirst();

        int applied = fire(battle, owner);
        boolean inState = owner.getBuffManager().hasState("协奏");
        int stateCount = owner.getBuffManager().countBuffs(StateBuff.class);
        boolean otherName = owner.getBuffManager().hasState("char:1212/soul_shift");

        Assertions.assertAll("APPLY_BUFF applies and refreshes the named state",
                () -> Assertions.assertEquals(1, applied, "the rule applies the state"),
                () -> Assertions.assertTrue(inState, "the owner is now in 【协奏】"),
                () -> Assertions.assertEquals(1, stateCount, "exactly one state buff is attached"),
                () -> Assertions.assertFalse(otherName, "a different name is a different state"));

        takeTurn(battle, owner);
        Integer afterOneTurn = stateDuration(owner, "协奏");
        fire(battle, owner);
        int refreshedCount = owner.getBuffManager().countBuffs(StateBuff.class);
        Integer refreshed = stateDuration(owner, "协奏");
        Assertions.assertAll("the same state refreshes instead of stacking",
                () -> Assertions.assertEquals(1, afterOneTurn, "one turn of the duration was burned"),
                () -> Assertions.assertEquals(1, refreshedCount, "refreshed, not stacked"),
                () -> Assertions.assertEquals(2, refreshed, "and the duration is back to the full 2"));
    }

    /**
     * {@code self has_state X} gates a rule on the owner's own state: silent before it is applied, firing after.
     */
    @Test
    public void theConditionReadsTheOwnersState() {
        Battle battle = battleWith(
                stateOp("ALLY_ATTACK", "协奏", 2, null),
                TriggerSpecs.rule("SKILL_CAST", List.of("self has_state 协奏"), gain(1)));
        Character owner = battle.characters().getFirst();
        drainSkillPoints(battle);

        int conditionedBefore = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, owner, null, 0, 0));
        int applied = fire(battle, owner);
        int conditionedAfter = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, owner, null, 0, 0));

        Assertions.assertAll("self has_state reads the owner's state",
                () -> Assertions.assertEquals(0, conditionedBefore,
                        "before the state is applied the conditioned rule must not fire"),
                () -> Assertions.assertEquals(1, applied, "the state is applied"),
                () -> Assertions.assertEquals(1, conditionedAfter,
                        "now that the owner is in 【协奏】, the conditioned rule fires"));
    }

    /**
     * {@code target has_state X} asks about the event's subject - not about the rule's owner.
     */
    @Test
    public void hasStateOnTheTargetReadsTheEventSubject() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_state dot:shock"), gain(1)));
        Character owner = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        ally.getBuffManager().addBuff(new StateBuff("dot:shock", 2));
        drainSkillPoints(battle);

        int onSubject = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, ally, 1, 0));
        int onOwner = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0));
        int onNothing = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0));

        Assertions.assertAll("target has_state reads the event subject",
                () -> Assertions.assertEquals(1, onSubject,
                        "the subject of the event is in the shocked state"),
                () -> Assertions.assertEquals(0, onOwner,
                        "the owner is NOT in the shocked state, so the rule must not fire -- reading the owner here would make "
                                + "every 'the target is in state X' rule fire on the wrong unit"),
                () -> Assertions.assertEquals(0, onNothing,
                        "an event with no subject fails the condition instead of accidentally passing it"));
    }

    // ==================================================================
    // 2. State identity and duration: names, not classes
    // ==================================================================

    /**
     * A state is identified by its name, not by its class: two different names coexist on one unit, while a timed
     * state expires after its turns and a {@code permanent} one never does.
     */
    @Test
    public void statesAreNamedAndTheirDurationIsHonoured() {
        Battle coexisting = battleWith(
                stateOp("ALLY_ATTACK", "协奏", 2, null),
                stateOp("SKILL_CAST", "char:1212/soul_shift", 2, null));
        Character coexistOwner = coexisting.characters().getFirst();
        fire(coexisting, coexistOwner);
        coexisting.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, coexistOwner, null, 0, 0));
        boolean firstState = coexistOwner.getBuffManager().hasState("协奏");
        boolean secondState = coexistOwner.getBuffManager().hasState("char:1212/soul_shift");
        int coexistCount = coexistOwner.getBuffManager().countBuffs(StateBuff.class);

        Assertions.assertAll("two different states coexist",
                () -> Assertions.assertTrue(firstState, "the first state is on"),
                () -> Assertions.assertTrue(secondState,
                        "applying a second state must not evict the first: StateBuff.isSameKind compares names, "
                                + "not classes (matching by class would silently evict the first state)"),
                () -> Assertions.assertEquals(2, coexistCount, "both state buffs are attached"));

        Battle timed = battleWith(stateOp("ALLY_ATTACK", "协奏", 1, null));
        Character timedOwner = timed.characters().getFirst();
        fire(timed, timedOwner);
        boolean applied = timedOwner.getBuffManager().hasState("协奏");
        takeTurn(timed, timedOwner);
        boolean expired = timedOwner.getBuffManager().hasState("协奏");

        Assertions.assertAll("a state expires after its turns",
                () -> Assertions.assertTrue(applied, "the state is on right after it is applied"),
                () -> Assertions.assertFalse(expired, "「lasts 1 turn」 is over after that turn"));

        Battle permanent = battleWith(stateOp("ALLY_ATTACK", "协奏", null, true));
        Character permanentOwner = permanent.characters().getFirst();
        fire(permanent, permanentOwner);
        for (int turn = 0; turn < 3; turn++) {
            takeTurn(permanent, permanentOwner);
        }
        boolean stillThere = permanentOwner.getBuffManager().hasState("协奏");

        Assertions.assertAll("a permanent state never expires",
                () -> Assertions.assertTrue(stillThere,
                        "permanent means until the battle ends, and nothing counts it down"));
    }

    // ==================================================================
    // 3. `REMOVE_STATE` - the other half of the state pair
    // ==================================================================

    /**
     * The state comes off, and the removal is the same vocabulary as the test: {@code APPLY_BUFF} writes the name,
     * {@code has_state} reads it, {@code REMOVE_STATE} takes it away. Only the named state goes - the DoT spelling
     * is understood, other states are untouched, and removing one that is not there is quiet rather than a failure.
     */
    @Test
    public void removeStateOnlyTakesTheNamedStateOff() {
        // Two different events so the two halves can be observed separately; on one event the table would apply and
        // remove the state within the same fire, which would prove nothing about either.
        Battle oneState = battleWith(
                stateOp("ALLY_ATTACK", "char:1313/blessed_one", 3, null),
                removeStateOn("SKILL_CAST", "char:1313/blessed_one"));
        Character oneStateOwner = oneState.characters().getFirst();
        int applyCount = fire(oneState, oneStateOwner);
        boolean onAfterApply = oneStateOwner.getBuffManager().hasState("char:1313/blessed_one");
        int removeCount = fire(oneState, oneStateOwner, TriggerEvent.SKILL_CAST);
        boolean offAfterRemove = oneStateOwner.getBuffManager().hasState("char:1313/blessed_one");

        Assertions.assertAll("REMOVE_STATE takes the named state off",
                () -> Assertions.assertEquals(1, applyCount, "the state was applied"),
                () -> Assertions.assertTrue(onAfterApply, "precondition: it is on"),
                () -> Assertions.assertEquals(1, removeCount, "and then removed"),
                () -> Assertions.assertFalse(offAfterRemove, "「removes ... the state」"));

        Battle twoStates = battleWith(
                stateOp("ALLY_ATTACK", "协奏", 3, null),
                stateOp("ALLY_ATTACK", "char:1212/soul_shift", 3, null),
                removeStateOn("SKILL_CAST", "协奏"));
        Character twoStatesOwner = twoStates.characters().getFirst();
        fire(twoStates, twoStatesOwner);
        fire(twoStates, twoStatesOwner, TriggerEvent.SKILL_CAST);
        boolean namedGone = twoStatesOwner.getBuffManager().hasState("协奏");
        boolean otherStateStays = twoStatesOwner.getBuffManager().hasState("char:1212/soul_shift");

        Assertions.assertAll("REMOVE_STATE leaves other states alone",
                () -> Assertions.assertFalse(namedGone, "the named one is gone"),
                () -> Assertions.assertTrue(otherStateStays, "the other one is not"));

        Battle dotBattle = battleWith(removeStateOn("ALLY_ATTACK", "dot:shock"));
        Character dotOwner = dotBattle.characters().getFirst();
        dotOwner.getBuffManager().addBuff(new DotBuff(dotOwner, DamageElement.THUNDER, 100, 2));
        boolean dotOn = dotOwner.getBuffManager().hasState("dot:shock");
        fire(dotBattle, dotOwner);
        boolean dotOff = dotOwner.getBuffManager().hasState("dot:shock");

        Assertions.assertAll("REMOVE_STATE understands the DoT spelling",
                () -> Assertions.assertTrue(dotOn, "precondition: the thunder DoT is on"),
                () -> Assertions.assertFalse(dotOff,
                        "「removes ... the state」 works on the DoT names too -- one name, one meaning"));

        Battle quietBattle = battleWith(removeStateOn("ALLY_ATTACK", "char:1313/blessed_one"));
        Character quietOwner = quietBattle.characters().getFirst();
        int quietCount = fire(quietBattle, quietOwner);
        boolean neverThere = quietOwner.getBuffManager().hasState("char:1313/blessed_one");

        Assertions.assertAll("removing a state that is not there is quiet",
                () -> Assertions.assertEquals(1, quietCount, "the rule runs; there was simply nothing to take off"),
                () -> Assertions.assertFalse(neverThere, "nothing was there to remove"));
    }

    // ==================================================================
    // 4. `is_ally` - which SIDE the unit the event names is on
    // ==================================================================

    /**
     * {@code target is_ally} is true for one of ours and false for an enemy; {@code !target is_ally} is the opposite
     * and - like every negated party condition - fails with no target.
     *
     * <p>The condition exists for "when casting the ultimate on <b>one of our own characters</b>" (relic sets 114/118/121): a cast event carries the
     * unit it AIMED at, and a damaging cast aimed at an enemy carries one too - so without the side test the rule
     * would fire on every cast of that slot.
     */
    @Test
    public void isAllyReadsTheUnitsSide() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target is_ally"), gain(1)));
        Character owner = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        Enemy enemy = dummy();
        drainSkillPoints(battle);
        int onAlly = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, ally, 1, 0));
        int onEnemy = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0));

        Assertions.assertAll("target is_ally reads the unit's side",
                () -> Assertions.assertEquals(1, onAlly, "a teammate is on our side"),
                () -> Assertions.assertEquals(0, onEnemy,
                        "…and an enemy target is not: the rule must not fire on a cast aimed at the other side"));

        Battle negation = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("!target is_ally"), gain(1)));
        Character negationOwner = negation.characters().getFirst();
        Character negationAlly = negation.characters().get(1);
        Enemy negationEnemy = dummy();
        drainSkillPoints(negation);
        int onEnemyNegated = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, negationEnemy, 1, 0));
        int onAllyNegated = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, negationAlly, 1, 0));
        int onNothingNegated = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, null, 1, 0));

        Assertions.assertAll("!target is_ally is the other side",
                () -> Assertions.assertEquals(1, onEnemyNegated, "an enemy target is the other side"),
                () -> Assertions.assertEquals(0, onAllyNegated, "a teammate is not the other side"),
                () -> Assertions.assertEquals(0, onNothingNegated, "no target at all is not \"the other side\" either"));
    }

    // ==================================================================
    // 5. `has_same_path_ally` - a question about the PARTY
    // ==================================================================

    /**
     * {@code self has_same_path_ally} is true when another <b>living</b> ally walks the wearer's Path;
     * {@code !self has_same_path_ally} is the opposite (and still needs a party to look at).
     *
     * <p>Its first user is relic 314's "if there is at least one teammate on the same Path as the wearer" - the rule cannot name a Path, because
     * the relic can be worn by anybody, so it has to compare the wearer against the rest of the side. That makes it the
     * first condition whose answer depends on the <b>party</b> rather than on the owner or the event.
     */
    @Test
    public void hasSamePathAllyReadsTheParty() {
        Battle samePath = withAllyOf(1013, pathRule(gain(1)));
        drainSkillPoints(samePath);
        int withSamePath = samePath.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, samePath.characters().getFirst(), null, 1, 0));

        Battle otherPath = withAllyOf(1210, pathRule(gain(1)));
        drainSkillPoints(otherPath);
        int withOtherPath = otherPath.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, otherPath.characters().getFirst(), null, 1, 0));

        Battle alone = battleWith(pathRule(gain(1)));
        drainSkillPoints(alone);
        int noParty = alone.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, alone.characters().getFirst(), null, 1, 0));

        Assertions.assertAll("self has_same_path_ally reads the party",
                () -> Assertions.assertEquals(1, withSamePath,
                        "Herta (黑塔) is Erudition, like Himeko (姬子)"),
                () -> Assertions.assertEquals(0, withOtherPath,
                        "Guinaifen (桂乃芬) is Nihility: 「same Path」 does not hold"),
                () -> Assertions.assertEquals(0, noParty,
                        "nobody else on the field is not 「at least one teammate exists」"));

        Battle deadAlly = withAllyOf(1013, pathRule(gain(1)));
        Character deadAllyOwner = deadAlly.characters().getFirst();
        Character dead = deadAlly.characters().get(1);
        drainSkillPoints(deadAlly);
        dead.takeDamage(9_999_999);
        deadAlly.processRequests();
        int withCorpse = deadAlly.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, deadAllyOwner, null, 1, 0));

        Assertions.assertAll("a dead same-Path ally does not count",
                () -> Assertions.assertEquals(0, withCorpse,
                        "a corpse is not 「a teammate on the same Path as the wearer」"));

        Battle positivePrecondition = withAllyOf(1210, pathRule(gain(1)));
        Character positiveOwner = positivePrecondition.characters().getFirst();
        drainSkillPoints(positivePrecondition);
        int positiveSilent = positivePrecondition.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, positiveOwner, null, 1, 0));

        Battle negated = withAllyOf(1210,
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!self has_same_path_ally"), gain(1)));
        drainSkillPoints(negated);
        int negatedHolds = negated.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negated.characters().getFirst(), null, 1, 0));

        Assertions.assertAll("!self has_same_path_ally is the opposite",
                () -> Assertions.assertEquals(0, positiveSilent,
                        "precondition: no teammate shares the Path, so the positive spelling is silent"),
                () -> Assertions.assertEquals(1, negatedHolds,
                        "the negated spelling holds when nobody shares the Path"));
    }

    // ==================================================================
    // 6. `has_weakness` - a property only an ENEMY has
    // ==================================================================

    /**
     * {@code target has_weakness Fire} reads the enemy's weakness bar - the condition relic 316 needed.
     *
     * <p>Note: A character has no weakness bar, so the condition is <b>false</b> for one rather than "not weak to Fire":
     * the family's rule is "cannot read it, therefore it fails".
     */
    @Test
    public void hasWeaknessReadsTheEnemysBar() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_weakness Fire"), gain(1)));
        Character owner = battle.characters().getFirst();
        Enemy weak = dummy();
        weak.setStanceWeak(Set.of(DamageElement.FIRE));
        Enemy resistant = dummy();
        drainSkillPoints(battle);

        int onWeak = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, weak, 1, 0));
        int onResistant = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, resistant, 1, 0));
        int onCharacter = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0));

        Assertions.assertAll("target has_weakness reads the enemy's bar",
                () -> Assertions.assertEquals(1, onWeak, "a fire-weak enemy"),
                () -> Assertions.assertEquals(0, onResistant, "an enemy with no fire weakness"),
                () -> Assertions.assertEquals(0, onCharacter, "a character has no weakness bar at all"));
    }

    // ==================================================================
    // 7. `has_skill` - "that party carries this slot at all" (8009/8010)
    // ==================================================================

    /**
     * {@code target has_skill ELATION_SKILL} reads the <b>named party's kit</b>, not the owner's;
     * {@code !target has_skill ELATION_SKILL} is the document's second branch.
     *
     * <p>The discrimination is one slot asked about two units: 8010 carries the Elation skill (data slot 20) and
     * Himeko (姬子) does not, while <b>both</b> carry {@code COMMON}. So a wrong implementation that read the owner's kit, or
     * that answered "yes" for any slot, or "no" for every slot, fails at least one of the three counts below.
     *
     * <p>Why the condition exists at all: the sentence it comes from branches on it - "if the target has an elation skill ... and make it immediately
     * cast 1 ... elation skill ... if the target does not have an elation skill, advance its action by 50%" - so the two polarities are two different rules, and
     * this is also the <b>guard</b> {@code CAST_SKILL} needs (that op throws when the unit has no such slot).
     */
    @Test
    public void hasSkillAsksTheNamedPartysKit() {
        Battle battle = withAllyOf(8010,
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_skill ELATION_SKILL"), gain(1)),
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_skill COMMON"), gain(2)));
        Character owner = battle.characters().getFirst();
        Character elation = battle.characters().get(1);
        drainSkillPoints(battle);

        boolean elationLoaded = elation.getSkills().get(SkillType.ELATION_SKILL).getData().isLoaded();
        boolean ownerElationLoaded = owner.getSkills().get(SkillType.ELATION_SKILL).getData().isLoaded();
        boolean ownerCommonLoaded = owner.getSkills().get(SkillType.COMMON).getData().isLoaded();
        int onElation = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, elation, 1, 0));
        int pointsAfterElation = battle.getSkillPoints();
        drainSkillPoints(battle);
        int onOwner = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0));
        int pointsAfterOwner = battle.getSkillPoints();

        Assertions.assertAll("target has_skill asks the named party's kit",
                () -> Assertions.assertTrue(elationLoaded,
                        "precondition: 8010's slot 20 is a REAL row, which is what the condition asks about"),
                () -> Assertions.assertFalse(ownerElationLoaded,
                        "precondition: Himeko (姬子) CARRIES the key too (the builder fills every intrinsic slot) -- its row is the "
                                + "loader's not-found placeholder, so 'the slot is there' and 'she has the skill' differ, "
                                + "and only the second is the question"),
                () -> Assertions.assertTrue(ownerCommonLoaded,
                        "precondition: and her COMMON row is real, so the two units differ in exactly one slot"),
                () -> Assertions.assertEquals(2, onElation,
                        "8010 has both slots, so both rules run"),
                () -> Assertions.assertEquals(3, pointsAfterElation, "and each granted its own amount (1 + 2)"),
                () -> Assertions.assertEquals(1, onOwner,
                        "Himeko (姬子) has COMMON but no Elation skill: only the second rule runs"),
                () -> Assertions.assertEquals(2, pointsAfterOwner,
                        "and only ITS amount lands (2, measured from a drained bar so no cap can hide the difference)"));

        Battle negation = withAllyOf(8010,
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!target has_skill ELATION_SKILL"), gain(1)));
        Character negationOwner = negation.characters().getFirst();
        Character negationElation = negation.characters().get(1);
        drainSkillPoints(negation);
        int negatedOnOwner = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, negationOwner, 1, 0));
        int negatedOnElation = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, negationElation, 1, 0));

        Assertions.assertAll("!target has_skill is the other branch",
                () -> Assertions.assertEquals(1, negatedOnOwner,
                        "Himeko (姬子) has no Elation skill, so the negated condition holds"),
                () -> Assertions.assertEquals(0, negatedOnElation,
                        "8010 HAS one, so it must not"));
    }

    // ==================================================================
    // 8. `has_path` and the `!` prefix
    // ==================================================================

    /**
     * {@code has_path} reads the Path off the named party - Himeko (姬子) is Erudition and Tingyun (停云) is Harmony.
     * {@code !} inverts the condition, and a negated condition still <b>fails</b> when its party does not exist.
     *
     * <p>Same shape as {@code has_state}: the left side names a party, so "the target is on this Path" and
     * "I am on this Path" are the same mechanism. The Path itself is engine knowledge already
     * ({@code Character.getPath()} - the aggro tier), so nothing about it is new data.
     */
    @Test
    public void hasPathReadsTheNamedPartysPath() {
        Battle battle = withAlly(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_path 同谐"), gain(1)),
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_path 智识"), gain(2)));
        Character owner = battle.characters().getFirst();
        Character harmony = battle.characters().get(1);
        drainSkillPoints(battle);
        int matched = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, harmony, 1, 0));
        int points = battle.getSkillPoints();

        Assertions.assertAll("target has_path reads the named party's Path",
                () -> Assertions.assertEquals(1, matched,
                        "Tingyun (停云) is Harmony: only the first rule matches"),
                () -> Assertions.assertEquals(1, points, "and it is the one that granted a point"));

        Battle negation = withAlly(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!target has_path 同谐"), gain(1)));
        Character negationOwner = negation.characters().getFirst();
        Character negationHarmony = negation.characters().get(1);
        drainSkillPoints(negation);
        int negatedOnHarmony = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, negationHarmony, 1, 0));
        int negatedOnOwner = negation.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, negationOwner, negationOwner, 1, 0));

        Assertions.assertAll("!target has_path inverts the condition",
                () -> Assertions.assertEquals(0, negatedOnHarmony,
                        "the target IS Harmony, so the negated condition fails"),
                () -> Assertions.assertEquals(1, negatedOnOwner,
                        "Himeko (姬子) is Erudition, so it holds"));

        Battle missing = battleWith(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!target has_path 同谐"), gain(1)));
        Character missingOwner = missing.characters().getFirst();
        drainSkillPoints(missing);
        int withoutTarget = missing.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, missingOwner, null, 1, 0));

        Assertions.assertAll("a negated condition still fails when the party is missing",
                () -> Assertions.assertEquals(0, withoutTarget,
                        "there is no target for this event: the negation must not turn that into a match"));
    }

    // ==================================================================
    // 9. Fail fast: the effect arguments are validated at load time
    // ==================================================================

    /**
     * {@code APPLY_BUFF} needs one state name and exactly one duration; {@code REMOVE_STATE} needs a name and refuses
     * an amount. All of it is rejected while the table is constructed, before a battle can run.
     */
    @Test
    public void effectArgumentsAreValidatedAtLoadTime() {
        Assertions.assertAll("APPLY_BUFF and REMOVE_STATE validate their arguments at load time",
                () -> {
                    EffectSpec noName = new EffectSpec();
                    TriggerSpecs.set(noName, "op", "APPLY_BUFF");
                    TriggerSpecs.set(noName, "turns", 2);
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, noName))),
                            "an APPLY_BUFF without a state name is rejected at load time");
                    Assertions.assertTrue(e.getMessage().contains("buff"), e.getMessage());
                    Assertions.assertTrue(e.getMessage().contains(TriggerSpecs.TEST_SOURCE), e.getMessage());
                },
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new TriggerTable(OWNER, List.of(stateOp("ALLY_ATTACK", "协奏", null, null))),
                        "no duration at all"),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new TriggerTable(OWNER, List.of(stateOp("ALLY_ATTACK", "协奏", 2, true))),
                        "both durations at once"),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new TriggerTable(OWNER, List.of(stateOp("ALLY_ATTACK", "协奏", 0, null))),
                        "0 turns would be a state that expires the moment it is applied"),
                () -> {
                    EffectSpec withAmount = new EffectSpec();
                    TriggerSpecs.set(withAmount, "op", "REMOVE_STATE");
                    TriggerSpecs.set(withAmount, "buff", "char:1313/blessed_one");
                    TriggerSpecs.set(withAmount, "amount", 1.0);
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, withAmount))),
                            "REMOVE_STATE refuses an amount");
                    Assertions.assertTrue(refused.getMessage().contains("amount"), refused.getMessage());
                },
                () -> {
                    EffectSpec noBuff = new EffectSpec();
                    TriggerSpecs.set(noBuff, "op", "REMOVE_STATE");
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, noBuff))),
                            "REMOVE_STATE requires the state name");
                    Assertions.assertTrue(refused.getMessage().contains("buff"), refused.getMessage());
                });
    }

    // ==================================================================
    // 10. Fail fast: the `has_state` arguments are validated at load time
    // ==================================================================

    /**
     * {@code has_state} validates its party and its name at load time: an unknown party is refused with the known
     * parties listed, and a missing name is refused with the condition named.
     */
    @Test
    public void hasStateArgumentsAreValidatedAtLoadTime() {
        Assertions.assertAll("has_state validates its party and its name at load time",
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER,
                                    List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("enemy has_state 协奏"), gain(1)))),
                            "an unknown party in has_state is rejected at load time");
                    Assertions.assertTrue(e.getMessage().contains("enemy"), e.getMessage());
                    Assertions.assertTrue(e.getMessage().contains("self"), "the message must list the known parties");
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER,
                                    List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("self has_state"), gain(1)))),
                            "has_state without a name is rejected at load time");
                    Assertions.assertTrue(e.getMessage().contains("has_state"), e.getMessage());
                });
    }

    // ==================================================================
    // 11. Fail fast: the remaining condition vocabulary is validated
    // ==================================================================

    /**
     * Every other condition argument is validated where the file is read, and each message names the bad spelling and
     * the right one: {@code is_ally} takes no argument, {@code has_same_path_ally} takes no other subject,
     * {@code has_weakness} and {@code has_skill} and {@code has_path} list their vocabulary, and a number cannot be
     * negated (the honest spelling is the comparison).
     */
    @Test
    public void conditionVocabularyIsValidatedAtLoadTime() {
        Assertions.assertAll("every condition argument is validated while the file is read",
                () -> {
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER,
                                    List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("target is_ally 我方"), gain(1)))),
                            "is_ally takes no argument");
                    Assertions.assertTrue(refused.getMessage().contains("is_ally"), refused.getMessage());
                },
                () -> {
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER, List.of(
                                    TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_same_path_ally"), gain(1)))),
                            "has_same_path_ally takes no other subject");
                    Assertions.assertTrue(refused.getMessage().contains("has_same_path_ally"), refused.getMessage());
                },
                () -> {
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER,
                                    List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_weakness Fyre"), gain(1)))),
                            "an unknown weakness element is refused");
                    Assertions.assertTrue(refused.getMessage().contains("Fyre"), refused.getMessage());
                    Assertions.assertTrue(refused.getMessage().contains("FIRE"), refused.getMessage());
                },
                () -> {
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER, List.of(
                                    TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_skill ELATION"), gain(1)))),
                            "an unknown skill slot is refused");
                    Assertions.assertTrue(refused.getMessage().contains("ELATION"), refused.getMessage());
                    Assertions.assertTrue(refused.getMessage().contains("ELATION_SKILL"), refused.getMessage());
                },
                () -> {
                    IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER,
                                    List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_path 同谐谐"), gain(1)))),
                            "an unknown Path name is refused");
                    Assertions.assertTrue(rejected.getMessage().contains("同谐谐"), rejected.getMessage());
                    Assertions.assertTrue(rejected.getMessage().contains("同谐"), rejected.getMessage());
                },
                () -> {
                    IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(OWNER, List.of(
                                    TriggerSpecs.rule("ALLY_ATTACK", List.of("!self_summon_count >= 1"), gain(1)))),
                            "negating a number is refused");
                    Assertions.assertTrue(rejected.getMessage().contains("self_summon_count == 0"), rejected.getMessage());
                });
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A rule that puts {@code name} onto the owner for {@code turns} (or permanently) on {@code event}.
     */
    private static TriggerSpec stateOp(String event, String name, Integer turns, Boolean permanent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", name);
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "permanent", permanent);
        return TriggerSpecs.rule(event, null, effect);
    }

    /**
     * A rule that takes the named state off the resolved targets (default: the owner).
     */
    private static TriggerSpec removeStateOn(String event, String name) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REMOVE_STATE");
        TriggerSpecs.set(effect, "buff", name);
        return TriggerSpecs.rule(event, null, effect);
    }

    private static EffectSpec gain(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(effect, "amount", amount);
        return effect;
    }

    /**
     * The remaining duration of a named state, or {@code null} when it is not attached.
     */
    private static Integer stateDuration(Character who, String name) {
        for (StateBuff buff : who.getBuffManager().allBuffsOf(StateBuff.class)) {
            if (name.equals(buff.getState())) {
                return buff.duration();
            }
        }
        return null;
    }

    private static Battle battleWith(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs)), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * The same, but with a chosen ally - the Path comparison needs control over who is beside the owner.
     */
    private static Battle withAllyOf(int allyCid, TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs), CharacterFactory.create(allyCid, LEVEL)),
                List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * A rule that fires when the party contains somebody on the wearer's Path.
     */
    private static TriggerSpec pathRule(EffectSpec effect) {
        return TriggerSpecs.rule("ALLY_ATTACK", List.of("self has_same_path_ally"), effect);
    }

    private static Battle withAlly(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs), CharacterFactory.create(ALLY, LEVEL)),
                List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    /**
     * Fires the event with the owner as actor and returns how many rules really ran.
     */
    private static int fire(Battle battle, Character actor) {
        return fire(battle, actor, TriggerEvent.ALLY_ATTACK);
    }

    private static int fire(Battle battle, Character actor, TriggerEvent event) {
        return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(event, actor, null, 1, 0));
    }

    /**
     * Runs turns until {@code who} is the actor, settles that turn's start, and finishes the turn.
     *
     * <p>A turn has to be finished for the next one to begin ({@code afterMove} to {@code setTopZero}), and
     * it is the finish that counts a late buff's duration down - which is what these duration tests read.
     */
    private static void takeTurn(Battle battle, Character who) {
        for (int guard = 0; guard < 40; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue().getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
            }
            battle.afterMove();
            if (mine) {
                return;
            }
        }
        throw new AssertionError("no turn for the requested unit within 40 steps");
    }

    private static void drainSkillPoints(Battle battle) {
        while (battle.spendSkillPoint()) {
            // drain to zero
        }
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 100_000, 100, 100, 100);
    }
}
