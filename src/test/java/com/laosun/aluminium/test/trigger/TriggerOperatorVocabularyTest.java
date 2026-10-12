package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.models.trigger.TriggerOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The op vocabulary is closed, and this pins its <b>size and membership</b>.
 *
 * <p><b>Why a pin.</b> {@code TriggerOperator} is now the single source of the op names:
 * {@code TriggerVocabulary.WIRED} and {@code PLANNED} are derived from its {@code wired} flag, and
 * the load-time validator refuses a rule whose op is not in those sets. That makes adding a
 * constant a one-line change with no other edit required -- which is the point, and also the
 * hazard: a constant added by accident, or one whose flag was flipped while editing nearby, would
 * silently change which character files load. The list below is the deliberate record, in the same
 * spirit as the pins the condition vocabulary and the numeric-variable set already carry.
 *
 * <p>Note: When this test fails it is not asking you to update a number. It is asking whether the
 * new op is <b>wired</b> (the engine performs it) or <b>planned</b> (declared, phase not landed) --
 * the distinction the load-time error message depends on, because those two failures need different
 * things said to the author.
 */
public class TriggerOperatorVocabularyTest {

    /**
     * The wired ops, in enum order. Additions here mean the engine gained a capability.
     */
    private static final List<String> WIRED = List.of(
            "ABSORB_DAMAGE", "ADD_DAMAGE", "ADD_ELEMENTAL_WEAKNESS", "ADD_STACK", "ADVANCE",
            "APPLY_BUFF", "APPLY_CONTROL", "APPLY_DOT", "APPLY_REGEN", "BOOST_DAMAGE",
            "BOOST_TOUGHNESS", "CAST_SKILL", "COMMAND_SUMMON", "CONSUME_HP", "DAMAGE",
            "DELEGATE_DAMAGE", "DISPEL", "EXTEND_BUFF", "EXTRA_TURN", "GAIN_ENERGY",
            "GAIN_RESOURCE", "GAIN_SKILL_POINT", "HEAL", "INSERT_ACTION", "MODIFY_ATTR",
            "MODIFY_DAMAGE_TAKEN", "MODIFY_RULE", "RAISE_ENERGY_TO", "RAISE_RESOURCE_CAP",
            "RAISE_SKILL_HITS", "RAISE_SKILL_LEVEL", "RAISE_SKILL_POINT_CAP", "REMOVE_BUFF",
            "REMOVE_RESOURCE", "REMOVE_STACK", "REMOVE_STATE", "REPLACE_SKILL", "RESET_RESOURCE_CAP",
            "RESET_RULE_CHANCE", "RESET_TRIGGER_LIMIT", "RESIST_DEBUFF", "SHIELD", "SPEND_RESOURCE",
            "SPEND_STEPS", "START_COUNTDOWN", "SUMMON", "SUMMON_SERVANT", "SUPER_BREAK", "TAUNT",
            "TICK_DOT");

    /**
     * Declared in the roadmap, not yet performed. A rule naming one is refused with the reason.
     */
    private static final List<String> PLANNED = List.of("REDUCE_TOUGHNESS");

    @Test
    public void theVocabularyIsExactlyThePinnedSet() {
        List<String> actual = Arrays.stream(TriggerOperator.values()).map(Enum::name).sorted().toList();
        List<String> expected = java.util.stream.Stream.concat(WIRED.stream(), PLANNED.stream())
                .sorted().toList();
        Assertions.assertEquals(expected, actual,
                "the op vocabulary changed: a new op needs a deliberate wired/planned decision, "
                        + "and both lists above updated");
    }

    @Test
    public void theDerivedSetsMatchTheFlagThatProducedThem() {
        Assertions.assertEquals(WIRED.size(), TriggerOperator.wiredNames().size());
        Assertions.assertEquals(PLANNED.size(), TriggerOperator.plannedNames().size());
        for (String name : WIRED) {
            TriggerOperator op = TriggerOperator.parse(name);
            Assertions.assertNotNull(op, name + " must parse back from its own spelling");
            Assertions.assertTrue(op.isWired(), name + " is pinned as wired");
            Assertions.assertFalse(op.isPlanned(), name + " cannot be both");
        }
        for (String name : PLANNED) {
            TriggerOperator op = TriggerOperator.parse(name);
            Assertions.assertNotNull(op, name + " must parse back from its own spelling");
            Assertions.assertTrue(op.isPlanned(), name + " is pinned as planned");
        }
    }

    @Test
    public void everyWiredOpCanBeSpelledTheWayTheValidatorWillSeeIt() {
        // The validator compares against the sets, so a name that does not round-trip through
        // parse() would be refused for a character file that spells it correctly.
        for (TriggerOperator op : TriggerOperator.values()) {
            Assertions.assertEquals(op, TriggerOperator.parse(op.name()),
                    op + " must survive a round trip");
            Assertions.assertEquals(op, TriggerOperator.parse("  " + op.name().toLowerCase() + "  "),
                    op + " must be read leniently, as the reading code always has");
        }
    }

    @Test
    public void anUnknownOrMissingSpellingParsesToNothingRatherThanGuessing() {
        // Null, not an exception, and not a default: the caller distinguishes "typo" from
        // "declared but not wired", and it cannot do that if this invents an answer.
        Assertions.assertNull(TriggerOperator.parse(null));
        Assertions.assertNull(TriggerOperator.parse(""));
        Assertions.assertNull(TriggerOperator.parse("   "));
        Assertions.assertNull(TriggerOperator.parse("NOT_AN_OP"));
        // A near miss is still a miss: this is the silent-wrong-answer shape the closed set exists
        // to stop, so it must NOT be folded onto GAIN_ENERGY by some fuzzy match.
        Assertions.assertNull(TriggerOperator.parse("GAIN_ENERGIES"));
        Assertions.assertNull(TriggerOperator.parse("GAIN ENERGY"));
    }
}
