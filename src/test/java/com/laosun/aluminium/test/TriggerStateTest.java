package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Named states: the {@code APPLY_BUFF} op and the {@code has_state} condition.
 *
 * <p><b>Why this vocabulary exists.</b> The rule text of this game says 「处于【协奏】状态时」 /
 * 「【转魄】状态下」 / 「触电状态下的敌方目标」 constantly, and before this the trigger table could not ask
 * about a state at all: a mechanic that is otherwise pure data needed a Java class per character. A state is
 * not a new kind of thing in this engine — it is <b>an ordinary buff that carries a name</b> (the lesson the
 * DOT migration already taught), so it inherits duration, refresh, and {@code clearAll} removal for free.
 *
 * <p><b>What is pinned here, and why each case is worth its own test.</b>
 * <ul>
 *   <li>the op puts a state on a unit, and the condition reads it back;</li>
 *   <li>{@code target has_state X} reads the <b>event's subject</b>, not the owner — the classic
 *       actor/target confusion of this DSL, and the reason Kafka's "an enemy in 触电 state" is expressible;</li>
 *   <li>two <b>different</b> states coexist ({@code StateBuff.isSameKind} compares names, not classes — the
 *       class-based default would make 【协奏】 silently evict 【转魄】, the L-14 trap);</li>
 *   <li>the <b>same</b> state refreshes instead of stacking;</li>
 *   <li>a state expires after its turns, and a {@code permanent} one does not;</li>
 *   <li>{@code target has_skill <SLOT>} reads the named party's <b>kit</b> — the guard 8009/8010's ultimate needs
 *       before it may command that ally to cast (and the reason both its branches are expressible);</li>
 *   <li>every argument is validated at <b>load</b> time, and a missing party fails the condition rather than
 *       passing it.</li>
 * </ul>
 */
public class TriggerStateTest {
    /** Himeko: no shipped rule file, so the table under test is the only one in the battle. */
    private static final int OWNER = 1003;
    /** Tingyun: the "somebody else" whose state {@code target has_state} is asked about. */
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The op and the condition
    // ==================================================================

    @Test
    public void applyBuffPutsTheNamedStateOnTheOwner() {
        Battle battle = battleWith(stateOp("ALLY_ATTACK", "协奏", 2, null));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner), "the rule applies the state");
        Assertions.assertTrue(owner.getBuffManager().hasState("协奏"), "the owner is now in 【协奏】");
        Assertions.assertEquals(1, owner.getBuffManager().countBuffs(StateBuff.class));
        Assertions.assertFalse(owner.getBuffManager().hasState("转魄"), "a different name is a different state");
    }

    @Test
    public void theConditionReadsTheOwnersState() {
        Battle battle = battleWith(
                stateOp("ALLY_ATTACK", "协奏", 2, null),
                TriggerSpecs.rule("SKILL_CAST", List.of("self has_state 协奏"), gain(1)));
        Character owner = battle.characters.getFirst();
        drainSkillPoints(battle);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0),
                "before the state is applied the conditioned rule must not fire");
        Assertions.assertEquals(1, fire(battle, owner), "the state is applied");
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0),
                "now that the owner is in 【协奏】, the conditioned rule fires");
    }

    /**
     * {@code target has_state X} asks about the event's subject — not about the rule's owner.
     */
    @Test
    public void hasStateOnTheTargetReadsTheEventSubject() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_state 触电"), gain(1)));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        ally.getBuffManager().addBuff(new StateBuff("触电", 2));
        drainSkillPoints(battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, ally, 1, 0),
                "the subject of the event is in 触电");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0),
                "the owner is NOT in 触电, so the rule must not fire -- reading the owner here would make "
                        + "every 'the target is in state X' rule fire on the wrong unit");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0),
                "an event with no subject fails the condition instead of accidentally passing it");
    }

    // ==================================================================
    // 2. State identity: names, not classes
    // ==================================================================

    @Test
    public void twoDifferentStatesCoexist() {
        Battle battle = battleWith(
                stateOp("ALLY_ATTACK", "协奏", 2, null),
                stateOp("SKILL_CAST", "转魄", 2, null));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);

        Assertions.assertTrue(owner.getBuffManager().hasState("协奏"));
        Assertions.assertTrue(owner.getBuffManager().hasState("转魄"),
                "applying a second state must not evict the first: StateBuff.isSameKind compares names, "
                        + "not classes (the class-based default is the L-14 trap)");
        Assertions.assertEquals(2, owner.getBuffManager().countBuffs(StateBuff.class));
    }

    @Test
    public void theSameStateRefreshesInsteadOfStacking() {
        Battle battle = battleWith(stateOp("ALLY_ATTACK", "协奏", 2, null));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        takeTurn(battle, owner);
        Assertions.assertEquals(1, stateDuration(owner, "协奏"), "one turn of the duration was burned");

        fire(battle, owner);
        Assertions.assertEquals(1, owner.getBuffManager().countBuffs(StateBuff.class), "refreshed, not stacked");
        Assertions.assertEquals(2, stateDuration(owner, "协奏"), "and the duration is back to the full 2");
    }

    // ==================================================================
    // 3. Duration
    // ==================================================================

    @Test
    public void aStateExpiresAfterItsTurns() {
        Battle battle = battleWith(stateOp("ALLY_ATTACK", "协奏", 1, null));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        Assertions.assertTrue(owner.getBuffManager().hasState("协奏"));

        takeTurn(battle, owner);
        Assertions.assertFalse(owner.getBuffManager().hasState("协奏"), "「持续1回合」 is over after that turn");
    }

    @Test
    public void aPermanentStateNeverExpires() {
        Battle battle = battleWith(stateOp("ALLY_ATTACK", "协奏", null, true));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        for (int turn = 0; turn < 3; turn++) {
            takeTurn(battle, owner);
        }
        Assertions.assertTrue(owner.getBuffManager().hasState("协奏"),
                "permanent means until the battle ends, and nothing counts it down");
    }

    // ==================================================================
    // 4. Fail fast: every argument is validated at load time
    // ==================================================================

    @Test
    public void anApplyBuffWithoutAStateNameIsRejectedAtLoadTime() {
        EffectSpec noName = new EffectSpec();
        TriggerSpecs.set(noName, "op", "APPLY_BUFF");
        TriggerSpecs.set(noName, "turns", 2);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, noName))));
        Assertions.assertTrue(e.getMessage().contains("buff"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains(TriggerSpecs.TEST_SOURCE), e.getMessage());
    }

    @Test
    public void anApplyBuffNeedsExactlyOneDuration() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(stateOp("ALLY_ATTACK", "协奏", null, null))),
                "no duration at all");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(stateOp("ALLY_ATTACK", "协奏", 2, true))),
                "both durations at once");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(stateOp("ALLY_ATTACK", "协奏", 0, null))),
                "0 turns would be a state that expires the moment it is applied");
    }

    @Test
    public void anUnknownPartyInHasStateIsRejectedAtLoadTime() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("enemy has_state 协奏"), gain(1)))));
        Assertions.assertTrue(e.getMessage().contains("enemy"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("self"), "the message must list the known parties");
    }

    @Test
    public void hasStateWithoutANameIsRejectedAtLoadTime() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("self has_state"), gain(1)))));
        Assertions.assertTrue(e.getMessage().contains("has_state"), e.getMessage());
    }

    // ==================================================================
    // 3b. `REMOVE_STATE` — the other half of the state pair (M-42 ②)
    // ==================================================================

    /**
     * The state comes off, and the removal is the same vocabulary as the test: `APPLY_BUFF` writes the name,
     * `has_state` reads it, `REMOVE_STATE` takes it away.
     */
    @Test
    public void removeStateTakesTheNamedStateOff() {
        // Two different events so the two halves can be observed separately; on one event the table would apply and
        // remove the state within the same fire, which would prove nothing about either.
        Battle battle = battleWith(
                stateOp("ALLY_ATTACK", "蒙福者", 3, null),
                removeStateOn("SKILL_CAST", "蒙福者"));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner), "the state was applied");
        Assertions.assertTrue(owner.getBuffManager().hasState("蒙福者"), "precondition: it is on");
        Assertions.assertEquals(1, fire(battle, owner, TriggerEvent.SKILL_CAST), "and then removed");
        Assertions.assertFalse(owner.getBuffManager().hasState("蒙福者"), "「解除…状态」");
    }

    /**
     * Only the named state goes: other states on the same unit are untouched.
     *
     * <p>Pinned because the cheap implementation — "remove the first buff that is a StateBuff" — would pass the case
     * above and take somebody else's state off, which is a wrong state with nothing to report (the L-14 family).
     */
    @Test
    public void removeStateLeavesOtherStatesAlone() {
        Battle battle = battleWith(
                stateOp("ALLY_ATTACK", "协奏", 3, null),
                stateOp("ALLY_ATTACK", "转魄", 3, null),
                removeStateOn("SKILL_CAST", "协奏"));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        fire(battle, owner, TriggerEvent.SKILL_CAST);

        Assertions.assertFalse(owner.getBuffManager().hasState("协奏"), "the named one is gone");
        Assertions.assertTrue(owner.getBuffManager().hasState("转魄"), "the other one is not");
    }

    /**
     * The four DoT spellings resolve the same way here as they do in {@code has_state}.
     *
     * <p>「触电」 is not a {@code StateBuff} — the engine has represented the four damage-over-time states as an
     * ordinary {@code DotBuff(element)} since P10-0, and {@code BuffManager} is the one place that knows the two
     * spellings are the same fact. Removing is the side where forgetting that would be invisible: the state would
     * simply stay on, and the rule would look like it ran.
     */
    @Test
    public void removeStateUnderstandsTheDotSpelling() {
        Battle battle = battleWith(removeStateOn("ALLY_ATTACK", "触电"));
        Character owner = battle.characters.getFirst();
        owner.getBuffManager().addBuff(new DotBuff(owner, DamageElement.THUNDER, 100, 2));
        Assertions.assertTrue(owner.getBuffManager().hasState("触电"), "precondition: the thunder DoT is on");

        fire(battle, owner);

        Assertions.assertFalse(owner.getBuffManager().hasState("触电"),
                "「解除…状态」 works on the DoT names too -- one name, one meaning");
    }

    /** Removing a state that is not there is nothing to do, not a failure. */
    @Test
    public void removingAStateThatIsNotThereIsQuiet() {
        Battle battle = battleWith(removeStateOn("ALLY_ATTACK", "蒙福者"));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner), "the rule runs; there was simply nothing to take off");
        Assertions.assertFalse(owner.getBuffManager().hasState("蒙福者"));
    }

    /** The op takes the state off entirely, so a count is refused rather than silently ignored. */
    @Test
    public void removeStateRefusesAnAmount() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REMOVE_STATE");
        TriggerSpecs.set(effect, "buff", "蒙福者");
        TriggerSpecs.set(effect, "amount", 1.0);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));

        Assertions.assertTrue(refused.getMessage().contains("amount"), refused.getMessage());
    }

    /** …and the name is required, like every other op that names something. */
    @Test
    public void removeStateRequiresTheStateName() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REMOVE_STATE");

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));

        Assertions.assertTrue(refused.getMessage().contains("buff"), refused.getMessage());
    }

    // ==================================================================
    // 3c. `is_ally` — which SIDE the unit the event names is on
    // ==================================================================

    /**
     * {@code target is_ally} is true for one of ours and false for an enemy.
     *
     * <p>The condition exists for 「对<b>己方角色</b>施放终结技时」 (relic sets 114/118/121): a cast event carries the
     * unit it AIMED at, and a damaging cast aimed at an enemy carries one too — so without the side test the rule
     * would fire on every cast of that slot.
     */
    @Test
    public void isAllyReadsTheUnitsSide() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target is_ally"), gain(1)));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Enemy enemy = dummy();
        drainSkillPoints(battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, ally, 1, 0),
                "a teammate is on our side");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0),
                "…and an enemy target is not: the rule must not fire on a cast aimed at the other side");
    }

    /** {@code !target is_ally} is the opposite, and — like every negated party condition — fails with no target. */
    @Test
    public void isAllyNegatedIsTheOtherSide() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("!target is_ally"), gain(1)));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Enemy enemy = dummy();
        drainSkillPoints(battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0));
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, ally, 1, 0));
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0),
                "no target at all is not \"the other side\" either");
    }

    /** It takes no argument, so anything after the keyword is refused while the file is read. */
    @Test
    public void isAllyTakesNoArgument() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("target is_ally 我方"), gain(1)))));

        Assertions.assertTrue(refused.getMessage().contains("is_ally"), refused.getMessage());
    }

    // ==================================================================
    // 3d. `has_same_path_ally` — a question about the PARTY
    // ==================================================================

    /**
     * {@code self has_same_path_ally} is true when another <b>living</b> ally walks the wearer's Path.
     *
     * <p>Its first user is 遗器 314's 「若至少存在一名与装备者命途相同的队友」 — the rule cannot name a Path, because
     * the relic can be worn by anybody, so it has to compare the wearer against the rest of the side. That makes it the
     * first condition whose answer depends on the <b>party</b> rather than on the owner or the event.
     */
    @Test
    public void hasSamePathAllyReadsTheParty() {
        Battle same = withAllyOf(1013, pathRule(gain(1)));
        drainSkillPoints(same);
        Assertions.assertEquals(1, same.fireTriggers(TriggerEvent.ALLY_ATTACK, same.characters.getFirst(), null, 1, 0),
                "黑塔 is 智识, like 姬子");

        Battle other = withAllyOf(1210, pathRule(gain(1)));
        drainSkillPoints(other);
        Assertions.assertEquals(0,
                other.fireTriggers(TriggerEvent.ALLY_ATTACK, other.characters.getFirst(), null, 1, 0),
                "桂乃芬 is 虚无: 「命途相同」 does not hold");

        Battle alone = battleWith(pathRule(gain(1)));
        drainSkillPoints(alone);
        Assertions.assertEquals(0, alone.fireTriggers(TriggerEvent.ALLY_ATTACK, alone.characters.getFirst(), null, 1, 0),
                "nobody else on the field is not 「至少存在一名队友」");
    }

    /** A <b>dead</b> teammate is not somebody on the field — the same reading `summonsOf` uses. */
    @Test
    public void aDeadSamePathAllyDoesNotCount() {
        Battle battle = withAllyOf(1013, pathRule(gain(1)));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        drainSkillPoints(battle);
        ally.takeDamage(9_999_999);
        battle.processRequests();

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0),
                "a corpse is not 「与装备者命途相同的队友」");
    }

    /** {@code !self has_same_path_ally} is the opposite (and still needs a party to look at). */
    @Test
    public void hasSamePathAllyCanBeNegated() {
        Battle battle = withAllyOf(1210, pathRule(gain(1)));
        Character owner = battle.characters.getFirst();
        drainSkillPoints(battle);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0),
                "precondition: no teammate shares the Path, so the positive spelling is silent");

        Battle negated = withAllyOf(1210,
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!self has_same_path_ally"), gain(1)));
        drainSkillPoints(negated);
        Assertions.assertEquals(1,
                negated.fireTriggers(TriggerEvent.ALLY_ATTACK, negated.characters.getFirst(), null, 1, 0));
    }

    /** The question is about MY side, so no other subject (and no argument) is accepted. */
    @Test
    public void hasSamePathAllyTakesNoOtherSubject() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(
                        TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_same_path_ally"), gain(1)))));

        Assertions.assertTrue(refused.getMessage().contains("has_same_path_ally"), refused.getMessage());
    }

    // ==================================================================
    // 3e. `has_weakness` — a property only an ENEMY has
    // ==================================================================

    /**
     * {@code target has_weakness Fire} reads the enemy's weakness bar — the condition 遗器 316 needed.
     *
     * <p>⚠ A character has no weakness bar, so the condition is <b>false</b> for one rather than "not weak to Fire":
     * the family's rule is "cannot read it, therefore it fails".
     */
    @Test
    public void hasWeaknessReadsTheEnemysBar() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_weakness Fire"), gain(1)));
        Character owner = battle.characters.getFirst();
        Enemy weak = dummy();
        weak.setStanceWeak(Set.of(DamageElement.FIRE));
        Enemy resistant = dummy();
        drainSkillPoints(battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, weak, 1, 0),
                "a fire-weak enemy");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, resistant, 1, 0),
                "an enemy with no fire weakness");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0),
                "a character has no weakness bar at all");
    }

    /** A misspelled element is refused while the file is read, and the message lists the vocabulary. */
    @Test
    public void anUnknownWeaknessElementIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_weakness Fyre"), gain(1)))));

        Assertions.assertTrue(refused.getMessage().contains("Fyre"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("FIRE"), refused.getMessage());
    }

    // ==================================================================
    // 3f. `has_skill` — "that party carries this slot at all" (8009/8010)
    // ==================================================================

    /**
     * {@code target has_skill ELATION_SKILL} reads the <b>named party's kit</b>, not the owner's.
     *
     * <p>⭐ The discrimination is one slot asked about two units: 8010 carries the Elation skill (data slot 20) and
     * 姬子 does not, while <b>both</b> carry {@code COMMON}. So a wrong implementation that read the owner's kit, or
     * that answered "yes" for any slot, or "no" for every slot, fails at least one of the three counts below.
     *
     * <p>Why the condition exists at all: the sentence it comes from branches on it — 「若目标拥有欢愉技…**并使其立即
     * 施放1次**…欢愉技…若目标不拥有欢愉技，使其**行动提前50%**」 — so the two polarities are two different rules, and
     * this is also the <b>guard</b> {@code CAST_SKILL} needs (that op throws when the unit has no such slot).
     */
    @Test
    public void hasSkillAsksTheNamedPartysKit() {
        Battle battle = withAllyOf(8010,
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_skill ELATION_SKILL"), gain(1)),
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_skill COMMON"), gain(2)));
        Character owner = battle.characters.getFirst();
        Character elation = battle.characters.get(1);
        drainSkillPoints(battle);

        Assertions.assertTrue(elation.getSkills().get(SkillType.ELATION_SKILL).getData().isLoaded(),
                "precondition: 8010's slot 20 is a REAL row, which is what the condition asks about");
        Assertions.assertFalse(owner.getSkills().get(SkillType.ELATION_SKILL).getData().isLoaded(),
                "precondition: 姬子 CARRIES the key too (the builder fills every intrinsic slot) -- its row is the "
                        + "loader's not-found placeholder, so 'the slot is there' and 'she has the skill' differ, "
                        + "and only the second is the question");
        Assertions.assertTrue(owner.getSkills().get(SkillType.COMMON).getData().isLoaded(),
                "precondition: and her COMMON row is real, so the two units differ in exactly one slot");

        Assertions.assertEquals(2, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, elation, 1, 0),
                "8010 has both slots, so both rules run");
        Assertions.assertEquals(3, battle.getSkillPoints(), "and each granted its own amount (1 + 2)");

        drainSkillPoints(battle);
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0),
                "姬子 has COMMON but no Elation skill: only the second rule runs");
        Assertions.assertEquals(2, battle.getSkillPoints(),
                "and only ITS amount lands (2, measured from a drained bar so no cap can hide the difference)");
    }

    /**
     * The other polarity: {@code !target has_skill ELATION_SKILL} is the document's second branch.
     *
     * <p>Both directions in one case, because "it fires for the unit without the skill" and "it does not fire for the
     * unit with it" are two claims, and a wrong implementation satisfies either alone. The {@code !} prefix is
     * available because {@code HasSkill} reads a party — the same rule {@code has_path} follows.
     */
    @Test
    public void hasSkillNegationIsTheOtherBranch() {
        Battle battle = withAllyOf(8010,
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!target has_skill ELATION_SKILL"), gain(1)));
        Character owner = battle.characters.getFirst();
        Character elation = battle.characters.get(1);
        drainSkillPoints(battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0),
                "姬子 has no Elation skill, so the negated condition holds");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, elation, 1, 0),
                "8010 HAS one, so it must not");
    }

    /**
     * ⚠ A slot that is not a {@link SkillType} is refused where the file is read.
     *
     * <p>The wrong spelling in this case is not hypothetical: {@code ELATION} is a {@code DamageType} constant, and
     * writing it here reads perfectly — 「欢愉」 — while naming nothing the kit can carry. The message has to name
     * both the bad spelling and the right one, or the author is left with "unknown skill".
     */
    @Test
    public void anUnknownSkillSlotIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(
                        TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_skill ELATION"), gain(1)))));

        Assertions.assertTrue(refused.getMessage().contains("ELATION"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("ELATION_SKILL"), refused.getMessage());
    }

    // ==================================================================
    // 4. `has_path` and the `!` prefix (M-41)
    // ==================================================================

    /**
     * {@code has_path} reads the Path off the named party — 姬子 is 智识 (Erudition) and 停云 is 同谐 (Harmony).
     *
     * <p>Same shape as {@code has_state}: the left side names a party, so "the target is on this Path" and
     * "I am on this Path" are the same mechanism. The Path itself is engine knowledge already
     * ({@code Character.getPath()} — the aggro tier), so nothing about it is new data.
     */
    @Test
    public void hasPathReadsTheNamedPartysPath() {
        Battle battle = withAlly(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_path 同谐"), gain(1)),
                TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_path 智识"), gain(2)));
        Character owner = battle.characters.getFirst();
        Character harmony = battle.characters.get(1);
        drainSkillPoints(battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, harmony, 1, 0),
                "停云 is 同谐: only the first rule matches");
        Assertions.assertEquals(1, battle.getSkillPoints(), "and it is the one that granted a point");
    }

    /** A near miss is refused where the file is read, and the message lists the vocabulary. */
    @Test
    public void anUnknownPathNameIsRefused() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("target has_path 同谐谐"), gain(1)))));

        Assertions.assertTrue(rejected.getMessage().contains("同谐谐"), rejected.getMessage());
        Assertions.assertTrue(rejected.getMessage().contains("同谐"), rejected.getMessage());
    }

    /**
     * {@code !} inverts the condition — the exception 星期日's Skill is written as.
     *
     * <p>Both directions in one case, because "it fires for the other Path" and "it does not fire for 同谐" are
     * two different claims and a wrong implementation can satisfy either alone.
     */
    @Test
    public void negationInvertsTheCondition() {
        Battle battle = withAlly(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!target has_path 同谐"), gain(1)));
        Character owner = battle.characters.getFirst();
        Character harmony = battle.characters.get(1);
        drainSkillPoints(battle);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, harmony, 1, 0),
                "the target IS 同谐, so the negated condition fails");
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, owner, 1, 0),
                "姬子 is 智识, so it holds");
    }

    /**
     * ⚠ A negated condition still <b>fails</b> when its party does not exist.
     *
     * <p>This is the trap the prefix could have walked into: the positive spelling guarantees "a missing party is
     * never the accidental reason a rule matched" (a missing party reads as {@code false}), and a naive
     * {@code !inner.test(ctx)} would invert exactly that into {@code true}. {@code Negated} asks the inner
     * condition for its party first, and this case is what pins it.
     */
    @Test
    public void negatedConditionStillFailsWhenThePartyIsMissing() {
        Battle battle = battleWith(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("!target has_path 同谐"), gain(1)));
        Character owner = battle.characters.getFirst();
        drainSkillPoints(battle);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0),
                "there is no target for this event: the negation must not turn that into a match");
    }

    /**
     * ⚠ Only a party-reading condition may be negated, and the message says what to write instead.
     *
     * <p>For a number, "cannot read it" and "is zero" are different facts ({@code NaN} comparisons are all false,
     * so {@code !(NaN > 0)} is <b>true</b>), and the DSL already has the honest spelling for the second one.
     */
    @Test
    public void negatingANumberIsRefused() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", List.of("!self_summon_count >= 1"), gain(1)))));

        Assertions.assertTrue(rejected.getMessage().contains("self_summon_count == 0"), rejected.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A rule that puts {@code name} onto the owner for {@code turns} (or permanently) on {@code event}. */
    private static TriggerSpec stateOp(String event, String name, Integer turns, Boolean permanent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", name);
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "permanent", permanent);
        return TriggerSpecs.rule(event, null, effect);
    }

    /** A rule that takes the named state off the resolved targets (default: the owner). */
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

    /** The remaining duration of a named state, or {@code null} when it is not attached. */
    private static Integer stateDuration(Character who, String name) {
        for (StateBuff buff : who.getBuffManager().allBuffsOf(StateBuff.class)) {
            if (name.equals(buff.getState())) {
                return buff.duration();
            }
        }
        return null;
    }

    private static Battle battleWith(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs)), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** The same, but with a chosen ally — the Path comparison needs control over who is beside the owner. */
    private static Battle withAllyOf(int allyCid, TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs), CharacterFactory.create(allyCid, LEVEL)),
                List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** A rule that fires when the party contains somebody on the wearer's Path. */
    private static TriggerSpec pathRule(EffectSpec effect) {
        return TriggerSpecs.rule("ALLY_ATTACK", List.of("self has_same_path_ally"), effect);
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

    /** Fires the event with the owner as actor and returns how many rules really ran. */
    private static int fire(Battle battle, Character actor) {
        return fire(battle, actor, TriggerEvent.ALLY_ATTACK);
    }

    private static int fire(Battle battle, Character actor, TriggerEvent event) {
        return battle.fireTriggers(event, actor, null, 1, 0);
    }

    /**
     * Runs turns until {@code who} is the actor, settles that turn's start, and finishes the turn.
     *
     * <p>A turn has to be finished for the next one to begin ({@code afterMove} → {@code setTopZero}), and
     * it is the finish that counts a late buff's duration down — which is what these duration tests read.
     */
    private static void takeTurn(Battle battle, Character who) {
        for (int guard = 0; guard < 40; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue.getCurrentActor().getCanHit() == who;
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
