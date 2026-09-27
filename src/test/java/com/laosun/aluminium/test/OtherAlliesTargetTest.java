package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code target: "other_allies"} — 「除自身以外的队友」, and the group {@code ADVANCE} it exists for.
 *
 * <p><b>Why a new word was needed.</b> 知更鸟's ultimate says 「使<b>除自身以外的队友</b>立即行动」. The op and the
 * fraction were already there (`ADVANCE percent: 1.0` = skip all of a unit's remaining wait = 立即行动), but the
 * <em>group</em> could not be named:
 *
 * <ul>
 *   <li>{@code all_allies} / {@code party} is "our whole side" <b>including</b> the rule's owner — that is pinned
 *       by 302 不老者的仙舟's 「我方全体攻击力提高」, which must buff the wearer — so it cannot mean "everyone but
 *       me";</li>
 *   <li>a condition cannot say it either: conditions filter <b>rules</b> (is this event mine?), not the units an
 *       effect reaches (all of them but one). Those are different questions, and the second one is the effect's
 *       target selector.</li>
 * </ul>
 *
 * <p><b>What is really being tested.</b> That the two group selectors differ in exactly one unit, that
 * {@code ADVANCE} reaches every unit in the group (it used to resolve a single target, so "our side" silently
 * advanced only one of them), that the group is the <b>camp</b> (a player-side summon is one of us), that our
 * enemies are untouched, and that 知更鸟's authored rule does what her sentence says.
 */
public class OtherAlliesTargetTest {
    private static final double EPS = 1e-6;

    /** The pair selector this class also covers; spelled once so a rename cannot half-apply. */
    private static final String TARGET_AND_SUMMON = "target_and_summon";

    /** 姬子 — no shipped rule file, so the table under test is the only one in play. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** 知更鸟 — her ultimate's first sentence is the first user of this selector. */
    private static final int ROBIN = 1309;

    // ==================================================================
    // 1. The two group selectors differ in one unit
    // ==================================================================

    /** 「除自身以外」: the teammates act now, the owner does not. */
    @Test
    public void otherAlliesLeavesTheOwnerOut() {
        Battle battle = new Battle(party(), List.of(dummy()), new Random(0));
        Character owner = battle.characters.getFirst();
        battle.startBattle();
        double ownerBefore = timeRemaining(battle, owner);

        int fired = battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertEquals(1, fired);
        Assertions.assertEquals(0, timeRemaining(battle, battle.characters.get(1)), EPS, "teammate 1 acts now");
        Assertions.assertEquals(0, timeRemaining(battle, battle.characters.get(2)), EPS, "teammate 2 acts now");
        Assertions.assertEquals(ownerBefore, timeRemaining(battle, owner), EPS,
                "…and the owner keeps their place: that is what 「除自身以外」 means");
        Assertions.assertTrue(timeRemaining(battle, battle.enemyUnits().getFirst()) > 0,
                "the enemies are not ours to advance");
    }

    /**
     * The contrast case: {@code all_allies} is the whole side, owner included.
     *
     * <p>Pinned together with the case above because the two spellings are one word apart and only this pair shows
     * that they are not the same thing.
     */
    @Test
    public void allAlliesIncludesTheOwner() {
        Battle battle = new Battle(party("all_allies"), List.of(dummy()), new Random(0));
        Character owner = battle.characters.getFirst();
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertEquals(0, timeRemaining(battle, owner), EPS, "「我方全体」 includes the wearer");
        Assertions.assertEquals(0, timeRemaining(battle, battle.characters.get(1)), EPS);
        Assertions.assertEquals(0, timeRemaining(battle, battle.characters.get(2)), EPS);
    }

    /** The group is the <b>camp</b>: a player-side summon is one of us and is advanced with the rest. */
    @Test
    public void aSummonInOurCampIsOneOfUs() {
        Battle battle = new Battle(party(), List.of(dummy()), new Random(0));
        Character owner = battle.characters.getFirst();
        battle.startBattle();
        Summon minion = battle.summon(owner, MONSTER, 1);
        battle.processRequests();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertEquals(0, timeRemaining(battle, minion), EPS,
                "「我方」 is the side, not the character list -- the same reading all_allies has");
    }

    // ==================================================================
    // 2. The spelling is closed
    // ==================================================================

    /** A near miss is refused where the file is read, and the message names the vocabulary. */
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

    /** Her ultimate's first sentence, end to end: the two teammates act, Robin does not. */
    @Test
    public void theAuthoredRobinRuleAdvancesHerTeammatesOnly() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Character first = CharacterFactory.create(OWNER, LEVEL);
        Character second = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(robin, first, second), List.of(dummy()), new Random(0));
        battle.startBattle();
        double robinBefore = timeRemaining(battle, robin);

        battle.fireTriggers(TriggerEvent.ULT_CAST, robin, null, 0, 0);

        Assertions.assertEquals(0, timeRemaining(battle, first), EPS, "「使除自身以外的队友立即行动」");
        Assertions.assertEquals(0, timeRemaining(battle, second), EPS);
        Assertions.assertEquals(robinBefore, timeRemaining(battle, robin), EPS,
                "her own turn is untouched by this clause (【协奏】 is what does something else to her)");
    }

    /** …and the rule is filed as her ultimate's first sentence, with the fraction that means 立即行动. */
    @Test
    public void theAuthoredRobinRuleStatesItsShape() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Battle battle = new Battle(List.of(robin), List.of(dummy()), new Random(0));
        battle.startBattle();

        List<TriggerTable.CompiledRule> rules = TriggerTables.of(ROBIN).matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(robin, robin, null, 0, 0, null, battle));

        Assertions.assertEquals(1, rules.size());
        List<EffectSpec> effects = rules.getFirst().effects();
        Assertions.assertEquals(1, effects.size());
        Assertions.assertEquals("ADVANCE", effects.getFirst().getOp());
        Assertions.assertEquals(1.0, effects.getFirst().getPercent(), EPS, "all of the remaining wait");
        Assertions.assertEquals("other_allies", effects.getFirst().getTarget());
    }

    // ==================================================================
    // 4. The pair: 「指定我方单体及其召唤物」
    // ==================================================================

    /**
     * {@code target_and_summon} is the chosen unit <b>and its</b> summon — not the rule owner's.
     *
     * <p>This is the case that separates it from every neighbouring word: {@code target} alone would leave the
     * summon standing, and {@code summon} is the <b>rule owner's</b> — so the fixture gives <b>both</b> units a
     * summon and asserts that only one of the two moves.
     */
    @Test
    public void thePairIsTheChosenUnitAndItsOwnSummon() {
        Battle battle = new Battle(party(TARGET_AND_SUMMON), List.of(dummy()), new Random(0));
        Character owner = battle.characters.getFirst();
        Character chosen = battle.characters.get(1);
        battle.startBattle();
        Summon ownersSummon = battle.summon(owner, MONSTER, 1);
        Summon chosenSummon = battle.summon(chosen, MONSTER, 1);
        battle.processRequests();
        double ownerBefore = timeRemaining(battle, owner);

        // The ULT_CAST event carries the unit the cast was AIMED AT (M-35), which is what "指定" means.
        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, chosen, 0, 0);

        Assertions.assertEquals(0, timeRemaining(battle, chosen), EPS, "「指定我方单体」acts now");
        Assertions.assertEquals(0, timeRemaining(battle, chosenSummon), EPS, "「及其召唤物」 acts with it");
        Assertions.assertTrue(timeRemaining(battle, ownersSummon) > 0,
                "…but the RULE OWNER's summon is untouched: that would be the `summon` selector, a different unit");
        Assertions.assertEquals(ownerBefore, timeRemaining(battle, owner), EPS,
                "and the owner itself is not part of the pair either");
    }

    /**
     * A chosen unit with no summon is just that unit — no error, nothing invented.
     *
     * <p>「及其召唤物」 only has something to add when there is one, and this Skill is cast on ordinary allies all
     * the time (that is most of the cast).
     */
    @Test
    public void aChosenUnitWithoutASummonIsJustTheUnit() {
        Battle battle = new Battle(party(TARGET_AND_SUMMON), List.of(dummy()), new Random(0));
        Character owner = battle.characters.getFirst();
        Character chosen = battle.characters.get(1);
        battle.startBattle();

        int fired = battle.fireTriggers(TriggerEvent.ULT_CAST, owner, chosen, 0, 0);

        Assertions.assertEquals(1, fired, "the rule fires; having no summon is not a failure");
        Assertions.assertEquals(0, timeRemaining(battle, chosen), EPS, "the chosen unit acts now");
    }

    /** Only the summon <b>that is out</b> counts: a dead one is not part of the pair. */
    @Test
    public void aDeadSummonIsNotPartOfThePair() {
        Battle battle = new Battle(party(TARGET_AND_SUMMON), List.of(dummy()), new Random(0));
        Character owner = battle.characters.getFirst();
        Character chosen = battle.characters.get(1);
        battle.startBattle();
        Summon chosenSummon = battle.summon(chosen, MONSTER, 1);
        battle.processRequests();
        chosenSummon.takeDamage(9_999_999);
        battle.processRequests();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, chosen, 0, 0);

        Assertions.assertEquals(0, timeRemaining(battle, chosen), EPS,
                "「及其召唤物」 means the one that is on the field -- a corpse is not advanced");
    }

    /**
     * The pair is a <b>list</b>, so an op that resolves exactly one target refuses it loudly.
     *
     * <p>Pinned because the tempting implementation — let the singular resolver return the chosen unit and ignore
     * the summon — would silently drop half of 「及其召唤物」.
     */
    @Test
    public void aSingleTargetOpRefusesThePair() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "EXTRA_TURN");
        TriggerSpecs.set(effect, "target", TARGET_AND_SUMMON);
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ULT_CAST", null, effect))));
        Battle battle = new Battle(List.of(owner, CharacterFactory.create(1210, LEVEL)), List.of(dummy()),
                new Random(0));
        battle.startBattle();

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains(TARGET_AND_SUMMON), refused.getMessage());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** Three characters; the first carries the rule under test, the others are plain. */
    private static List<Character> party() {
        return party("other_allies");
    }

    private static List<Character> party(String selector) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("ULT_CAST", null, advance(selector)))));
        return List.of(owner, CharacterFactory.create(1210, LEVEL), CharacterFactory.create(1202, LEVEL));
    }

    /** 「立即行动」: skip all of the target's remaining wait. */
    private static EffectSpec advance(String selector) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADVANCE");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", selector);
        return effect;
    }

    /** How much action value the unit still has — zero means "acts now". */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
