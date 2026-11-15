package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code target: "other_allies"} - "the teammates other than yourself", and the group {@code ADVANCE} it exists for.
 *
 * <p><b>Why a new word was needed.</b> Robin (知更鸟)'s ultimate says "makes <b>the teammates other than yourself</b> act immediately". The op and the
 * fraction were already there (`ADVANCE percent: 1.0` = skip all of a unit's remaining wait = act immediately), but the
 * <em>group</em> could not be named:
 *
 * <ul>
 *   <li>{@code all_allies} / {@code party} is "our whole side" <b>including</b> the rule's owner - that is pinned
 *       by 302 Fleet of the Ageless (不老者的仙舟)'s "raises the ATK of all of us", which must buff the wearer - so it cannot mean "everyone but
 *       me";</li>
 *   <li>a condition cannot say it either: conditions filter <b>rules</b> (is this event mine?), not the units an
 *       effect reaches (all of them but one). Those are different questions, and the second one is the effect's
 *       target selector.</li>
 * </ul>
 *
 * <p><b>What is really being tested.</b> That the two group selectors differ in exactly one unit, that
 * {@code ADVANCE} reaches every unit in the group (resolving a single target would silently
 * advance only one of them), that the group is the <b>camp</b> (a player-side summon is one of us), that our
 * enemies are untouched, and that Robin (知更鸟)'s authored rule does what her sentence says.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the six tests below, one {@code assertAll} per positive
 * scenario so a failing clause still names itself: the camp group with its owner exclusion and its summon, the
 * {@code all_allies} contrast, Robin's authored rule both on the field and as filed, and the {@code target_and_summon}
 * pair with and without a living summon. The two rejection paths stay their own tests: an {@code assertThrows} is not a
 * positive clause and must not hide behind one.
 */
public class OtherAlliesTargetTest {
    private static final double EPS = 1e-6;

    /**
     * The pair selector this class also covers; spelled once so a rename cannot half-apply.
     */
    private static final String TARGET_AND_SUMMON = "target_and_summon";

    /**
     * Himeko (姬子) - no shipped rule file, so the table under test is the only one in play.
     */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /**
     * Robin (知更鸟) - her ultimate's first sentence is the first user of this selector.
     */
    private static final int ROBIN = 1309;

    // ==================================================================
    // 1. The two group selectors differ in one unit
    // ==================================================================

    /**
     * "除自身以外": the teammates act now, the owner does not, and the group is the <b>camp</b> - so our own summon is
     * advanced with them.
     *
     * <p>One scenario because both clauses are the same selector on the same fixture ({@code party()}: the owner with
     * the rule under test, two plain teammates, one dummy enemy) and read the same observable, what is left of each
     * unit's action value. The summon clause needs a summon on the field <i>before</i> the rule fires, so it drives its
     * own instance: a summon appearing must not be able to change what the owner-exclusion clauses see.
     */
    @Test
    public void otherAlliesIsTheTeamAndTheCampButNotTheOwner() {
        Battle battle = new Battle(party(), List.of(dummy()), BattleFixture.seeded());
        Character owner = battle.characters().getFirst();
        battle.startBattle();
        double ownerBefore = timeRemaining(battle, owner);

        int fired = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, null, 0, 0));

        // The camp reading: a player-side summon is one of us and is advanced with the rest.
        Battle campBattle = new Battle(party(), List.of(dummy()), BattleFixture.seeded());
        Character summoner = campBattle.characters().getFirst();
        campBattle.startBattle();
        Summon minion = campBattle.summon(summoner, MONSTER, 1);
        campBattle.processRequests();

        campBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, summoner, null, 0, 0));

        Assertions.assertAll("other_allies: the team acts, the owner and the enemies do not, and the camp is the group",
                () -> Assertions.assertEquals(1, fired, "the rule fires once"),
                () -> Assertions.assertEquals(0, timeRemaining(battle, battle.characters().get(1)), EPS, "teammate 1 acts now"),
                () -> Assertions.assertEquals(0, timeRemaining(battle, battle.characters().get(2)), EPS, "teammate 2 acts now"),
                () -> Assertions.assertEquals(ownerBefore, timeRemaining(battle, owner), EPS,
                        "…and the owner keeps their place: that is what 「除自身以外」 (other than herself) means"),
                () -> Assertions.assertTrue(timeRemaining(battle, battle.enemyUnits().getFirst()) > 0,
                        "the enemies are not ours to advance"),
                () -> Assertions.assertEquals(0, timeRemaining(campBattle, minion), EPS,
                        "「我方」 (our side) is the side, not the character list -- the same reading all_allies has"));
    }

    /**
     * The contrast case: {@code all_allies} is the whole side, owner included.
     *
     * <p>Its own scenario rather than an entry of the camp one: the two spellings are one word apart, but the selector
     * is the clause under test, so the two do not share a fixture - and only side by side do they show that they are
     * not the same thing.
     */
    @Test
    public void allAlliesIncludesTheOwner() {
        Battle battle = new Battle(party("all_allies"), List.of(dummy()), BattleFixture.seeded());
        Character owner = battle.characters().getFirst();
        battle.startBattle();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, null, 0, 0));

        Assertions.assertAll("all_allies: 「我方全体」 is the whole side",
                () -> Assertions.assertEquals(0, timeRemaining(battle, owner), EPS, "「我方全体」 (all allies) includes the wearer"),
                () -> Assertions.assertEquals(0, timeRemaining(battle, battle.characters().get(1)), EPS, "teammate 1 acts now"),
                () -> Assertions.assertEquals(0, timeRemaining(battle, battle.characters().get(2)), EPS, "teammate 2 acts now"));
    }

    // ==================================================================
    // 2. The spelling is closed
    // ==================================================================

    /**
     * A near miss is refused where the file is read, and the message names the vocabulary.
     *
     * <p>Its own test, not an {@code assertAll} entry: a rejection is not a positive clause.
     */
    @Test
    public void aMisspelledSelectorIsRefused() {
        TriggerSpec spec = TriggerSpecs.rule("ULT_CAST", null, advance("other_ally"));
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(spec)));

        Assertions.assertTrue(rejected.getMessage().contains("other_ally"), rejected.getMessage());
        Assertions.assertTrue(rejected.getMessage().contains("all_allies"), rejected.getMessage());
    }

    // ==================================================================
    // 3. The shipped content: Robin's ultimate
    // ==================================================================

    /**
     * Her ultimate's first sentence, end to end - the two teammates act, Robin does not - and the rule is filed as that
     * sentence, with the fraction that means act immediately.
     *
     * <p>One scenario because both clauses are about the same authored rule ({@code TriggerTables.of(ROBIN)} on
     * {@code ULT_CAST}) and about one question, what her sentence is: on the field, and as filed. The filing clause
     * reads the compiled rules through a {@code TriggerContext}, which needs a battle of its own, so it drives its own
     * instance rather than borrowing the field one.
     */
    @Test
    public void theAuthoredRobinRuleActsAndIsFiledAsHerFirstSentence() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Character first = CharacterFactory.create(OWNER, LEVEL);
        Character second = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(robin, first, second), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        double robinBefore = timeRemaining(battle, robin);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, robin, null, 0, 0));

        // As filed: her own table is the only one in play here, so this side has nothing but Robin in it.
        Character onlyRobin = CharacterFactory.create(ROBIN, LEVEL);
        Battle filed = new Battle(List.of(onlyRobin), List.of(dummy()), BattleFixture.seeded());
        filed.startBattle();

        List<TriggerTable.CompiledRule> rules = TriggerTables.of(ROBIN).matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(onlyRobin, onlyRobin, null, 0, 0, null, filed));
        List<EffectSpec> effects = rules.getFirst().effects();

        Assertions.assertAll("Robin's authored ULT_CAST rule: what it does, and how it is filed",
                () -> Assertions.assertEquals(0, timeRemaining(battle, first), EPS, "\"makes allies other than herself act immediately\" (「使除自身以外的队友立即行动」)"),
                () -> Assertions.assertEquals(0, timeRemaining(battle, second), EPS, "her second teammate acts too"),
                () -> Assertions.assertEquals(robinBefore, timeRemaining(battle, robin), EPS,
                        "her own turn is untouched by this clause (【协奏】 (Concerto) is what does something else to her)"),
                // Three since 2026-11-02: this ADVANCE, the [协奏] state plus its countdown, and the state's own ATK boost plus
                // control immunity. A fourth rule (a 20% defence ignore under 「结界」) was removed that day: 知更鸟's document has no
                // such clause at all (0 mentions of 结界 - her state is 【协奏】), and the sentence it quoted belongs to 1303 阮•梅's
                // Eidolon 1, which already implements it.
                // Six since 2026-11-11: the three Eidolon auras (1: party penetration, 2: party speed, 4: party effect RES) are
                // granted on this same cast - the one that ENTERS 【协奏】 - and are grouped by that name for removal.
                () -> Assertions.assertEquals(6, rules.size(),
                        "six rules on this cast: the ADVANCE, the 【协奏】 state with its countdown, its ATK boost, and the three Eidolon auras"),
                () -> Assertions.assertEquals(1, effects.size(), "the first sentence is one effect"),
                () -> Assertions.assertEquals("ADVANCE", effects.getFirst().getOp(), "「立即行动」 is the ADVANCE op"),
                () -> Assertions.assertEquals(1.0, effects.getFirst().getPercent(), EPS, "all of the remaining wait"),
                () -> Assertions.assertEquals("other_allies", effects.getFirst().getTarget(),
                        "「除自身以外的队友」 (the teammates other than herself) is the other_allies selector"));
    }

    // ==================================================================
    // 4. The pair: "指定我方单体及其召唤物"
    // ==================================================================

    /**
     * {@code target_and_summon} is the chosen unit <b>and its</b> summon - not the rule owner's - and only the summons
     * that are <b>out</b> count.
     *
     * <p>One scenario because every clause is the same selector on the same fixture ({@code party(TARGET_AND_SUMMON)})
     * and reads the same observable, what is left of the chosen unit's action value; the variable under test is which
     * summons exist. This is what separates the word from every neighbour: {@code target} alone would leave the summon
     * standing, and {@code summon} is the <b>rule owner's</b> - so the full-pair clause gives <b>both</b> units a summon
     * and asserts that only one of the two moves. Each variation drives its own battle, so no clause inherits another's
     * state: a living pair, a chosen unit with no summon at all, and a chosen unit whose summon has died.
     */
    @Test
    public void thePairIsTheChosenUnitAndItsOwnLivingSummon() {
        Battle battle = new Battle(party(TARGET_AND_SUMMON), List.of(dummy()), BattleFixture.seeded());
        Character owner = battle.characters().getFirst();
        Character chosen = battle.characters().get(1);
        battle.startBattle();
        Summon ownersSummon = battle.summon(owner, MONSTER, 1);
        Summon chosenSummon = battle.summon(chosen, MONSTER, 1);
        battle.processRequests();
        double ownerBefore = timeRemaining(battle, owner);

        // The ULT_CAST event carries the unit the cast was AIMED AT, which is what "指定" means.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, chosen, 0, 0));

        // A chosen unit with no summon is just that unit - no error, nothing invented.
        Battle plain = new Battle(party(TARGET_AND_SUMMON), List.of(dummy()), BattleFixture.seeded());
        Character plainOwner = plain.characters().getFirst();
        Character plainChosen = plain.characters().get(1);
        plain.startBattle();

        int fired = plain.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, plainOwner, plainChosen, 0, 0));

        // Only the summon that is out counts: a dead one is not part of the pair.
        Battle bereft = new Battle(party(TARGET_AND_SUMMON), List.of(dummy()), BattleFixture.seeded());
        Character bereftOwner = bereft.characters().getFirst();
        Character bereftChosen = bereft.characters().get(1);
        bereft.startBattle();
        Summon doomed = bereft.summon(bereftChosen, MONSTER, 1);
        bereft.processRequests();
        doomed.takeDamage(9_999_999);
        bereft.processRequests();

        bereft.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, bereftOwner, bereftChosen, 0, 0));

        Assertions.assertAll("target_and_summon: the chosen unit and its own summon that is out",
                () -> Assertions.assertEquals(0, timeRemaining(battle, chosen), EPS,
                        "「指定我方单体」 (a designated single ally)acts now"),
                () -> Assertions.assertEquals(0, timeRemaining(battle, chosenSummon), EPS,
                        "「及其召唤物」 (and its summons) acts with it"),
                () -> Assertions.assertTrue(timeRemaining(battle, ownersSummon) > 0,
                        "…but the RULE OWNER's summon is untouched: that would be the `summon` selector, a different unit"),
                () -> Assertions.assertEquals(ownerBefore, timeRemaining(battle, owner), EPS,
                        "and the owner itself is not part of the pair either"),
                () -> Assertions.assertEquals(1, fired, "the rule fires; having no summon is not a failure"),
                () -> Assertions.assertEquals(0, timeRemaining(plain, plainChosen), EPS, "the chosen unit acts now"),
                () -> Assertions.assertEquals(0, timeRemaining(bereft, bereftChosen), EPS,
                        "「及其召唤物」 (and its summons) means the one that is on the field -- a corpse is not advanced"));
    }

    /**
     * The pair is a <b>list</b>, so an op that resolves exactly one target refuses it loudly.
     *
     * <p>Pinned because the tempting implementation - let the singular resolver return the chosen unit and ignore
     * the summon - would silently drop half of "及其召唤物". Its own test, not an {@code assertAll} entry: a rejection
     * is not a positive clause.
     */
    @Test
    public void aSingleTargetOpRefusesThePair() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "EXTRA_TURN");
        TriggerSpecs.set(effect, "target", TARGET_AND_SUMMON);
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ULT_CAST", null, effect))));
        Battle battle = new Battle(List.of(owner, CharacterFactory.create(1210, LEVEL)), List.of(dummy()),
                BattleFixture.seeded());
        battle.startBattle();

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, owner, 0, 0)));

        Assertions.assertTrue(refused.getMessage().contains(TARGET_AND_SUMMON), refused.getMessage());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * Three characters; the first carries the rule under test, the others are plain.
     */
    private static List<Character> party() {
        return party("other_allies");
    }

    private static List<Character> party(String selector) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("ULT_CAST", null, advance(selector)))));
        return List.of(owner, CharacterFactory.create(1210, LEVEL), CharacterFactory.create(1202, LEVEL));
    }

    /**
     * "立即行动": skip all of the target's remaining wait.
     */
    private static EffectSpec advance(String selector) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADVANCE");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", selector);
        return effect;
    }

    /**
     * How much action value the unit still has - zero means "acts now".
     */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
