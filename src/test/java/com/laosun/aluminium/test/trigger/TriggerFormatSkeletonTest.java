package com.laosun.aluminium.test.trigger;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The trigger format skeleton: a file header, annotations that are not runtime fields, one condition array with
 * both spellings, and one {@code RAISE_SKILL_LEVEL} effect that names several slots.
 *
 * <p>Each of these is a <b>format</b> change, not a new mechanic, so the judges here are about what the loader
 * accepts and what reaches the compiled rule. The existing behaviour is pinned by the rest of the suite; these
 * cases only add the new shapes and the refusals that make them safe.
 */
public class TriggerFormatSkeletonTest {

    private static final int LEVEL = 80;

    /**
     * Structured status is machine-readable: a closed status value plus string arrays for gaps and divergences.
     */
    @Test
    public void structuredStatusAnnotationsLoad() {
        var rule = TriggerTables.of(9021).rulesFor(TriggerEvent.BATTLE_START).getFirst();
        Assertions.assertEquals("status_ok", rule.id());
    }

    /**
     * A closed status vocabulary is the point: {@code "done"} is not one of the four states.
     */
    @Test
    public void anUnknownStatusIsRefused() {
        String message = messageOf(9022);
        Assertions.assertTrue(message.contains("_status") && message.contains("done"),
                "the loader names the unknown status: " + message);
    }

    /**
     * A non-implemented status must name the work: {@code partial} with no gap is an empty claim.
     */
    @Test
    public void aPartialStatusNeedsAGap() {
        String message = messageOf(9023);
        Assertions.assertTrue(message.contains("partial") && message.contains("_gaps"),
                "the loader explains the missing gap: " + message);
    }

    /**
     * {@code source} may be the legacy string or the structured object; the engine keeps the text for error messages
     * and the structured half is for readers / parameter reconciliation.
     */
    @Test
    public void sourceObjectKeepsItsText() {
        var rule = TriggerTables.of(9024).rulesFor(TriggerEvent.BATTLE_START).getFirst();
        Assertions.assertEquals("fixture source text", rule.source());
    }

    /**
     * The object form's one required field is the text: a source without it is useless in an error message.
     */
    @Test
    public void sourceObjectWithoutTextIsRefused() {
        String message = messageOf(9025);
        Assertions.assertTrue(message.contains("text"), "the loader names the missing text: " + message);
    }

    /**
     * A structured source's params are checked like the old {@code _params}: #N keys and numeric values, with skill/row.
     */
    @Test
    public void malformedSourceParamsAreRefused() {
        String message = messageOf(9026);
        Assertions.assertTrue(message.contains("#x") || message.contains("#1"),
                "the loader names the malformed param key: " + message);
    }

    /**
     * The object form may state its schema and owner, carry comments, and annotate every level with underscore
     * keys. None of the annotations becomes a field of the rule.
     */
    @Test
    public void theHeaderAndAnnotationsLoadWithoutBecomingRuntimeFields() {
        TriggerTable table = TriggerTables.of(9007);
        var rule = table.rulesFor(TriggerEvent.BATTLE_START).getFirst();

        Assertions.assertEquals("annotated_rule", rule.id());
        Assertions.assertEquals("legacy source stays readable", rule.source(),
                "the legacy source is still the provenance a failure message quotes");
        Assertions.assertEquals(1, rule.effects().size());
        Assertions.assertEquals("GAIN_ENERGY", rule.effects().getFirst().getOp());
        Assertions.assertEquals(1.0, rule.effects().getFirst().getAmount(), 1e-9);
    }

    /**
     * One {@code when} array holds a DSL string and predicate objects, and all of them are compiled into the same
     * condition list. The structured half is exactly the shape {@code when_predicates} already used.
     */
    @Test
    public void oneWhenArrayHoldsBothStringsAndPredicateObjects() {
        var rule = TriggerTables.of(9008).rulesFor(TriggerEvent.TURN_START).getFirst();

        Assertions.assertEquals(3, rule.conditions().size(),
                "two structured entries and one string: " + rule.conditions());
        Assertions.assertEquals("actor == self", rule.conditions().getFirst().source());
        String anyOf = rule.conditions().getLast().source();
        Assertions.assertTrue(anyOf.contains("||"), "the any_of is an OR: " + anyOf);
        Assertions.assertTrue(anyOf.contains("hp_pct") || anyOf.contains("is_ally"),
                "the entity clauses keep their own spelling: " + anyOf);
    }

    /**
     * The skeleton refuses the shapes that would otherwise load and mean something else.
     */
    @Test
    public void theSkeletonRefusesWhatItCannotRead() {
        Assertions.assertTrue(messageOf(9010).contains("schema"),
                "a newer schema is not silently read as this one: " + messageOf(9010));
        Assertions.assertTrue(messageOf(9011).contains("character"),
                "a file copied under another cid is refused: " + messageOf(9011));
        Assertions.assertTrue(messageOf(9009).contains("jsno"),
                "a misspelled predicate key is not dropped: " + messageOf(9009));
        Assertions.assertTrue(messageOf(9013).contains("twice"),
                "a slot listed twice would raise it twice: " + messageOf(9013));
    }

    /**
     * One {@code RAISE_SKILL_LEVEL} effect can raise several slots by one amount: the file's level convention is
     * one fact, and four copies of the number can drift apart.
     */
    @Test
    public void oneEffectCanRaiseSeveralSkills() {
        TriggerTable table = TriggerTables.of(9012);
        EffectSpec effect = table.rulesFor(TriggerEvent.BATTLE_START).getFirst().effects().getFirst();
        Assertions.assertEquals(List.of("COMMON", "SKILL", "ULTRA", "TALENT"), effect.getSkills());

        Character original = CharacterFactory.create(1003, LEVEL);
        original.setTriggerTable(table);
        Battle battle = new Battle(List.of(original), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Character hero = (Character) battle.allies().stream()
                .filter(unit -> unit instanceof Character)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the battle has no character of ours"));

        for (SkillType slot : List.of(SkillType.COMMON, SkillType.SKILL, SkillType.ULTRA, SkillType.TALENT)) {
            Assertions.assertEquals(9, hero.skillLevelBonus(slot), slot + ": one amount, every listed slot");
        }
    }

    /**
     * The old spelling still validates the same way: a list with no slot says nothing, and one slot twice is a
     * counting accident. Both are hand-built so the refusal does not depend on a fixture file existing.
     */
    @Test
    public void theSkillListRefusesAnEmptyOrRepeatedSlot() {
        Assertions.assertTrue(handBuilt("{\"op\":\"RAISE_SKILL_LEVEL\",\"skills\":[],\"amount\":1}")
                        .contains("empty"),
                "an empty list is refused");
        Assertions.assertTrue(handBuilt("{\"op\":\"RAISE_SKILL_LEVEL\",\"skills\":[\"ULTRA\",\"ULTRA\"],\"amount\":1}")
                        .contains("twice"),
                "a repeated slot is refused");
        Assertions.assertTrue(handBuilt("{\"op\":\"RAISE_SKILL_LEVEL\",\"skills\":[\"SKILL\"],\"skill\":\"ULTRA\","
                        + "\"amount\":1}")
                        .contains("both"),
                "two spellings of the same field are refused");
    }

    private static String handBuilt(String effectJson) {
        EffectSpec effect = new Gson().fromJson(effectJson, EffectSpec.class);
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, effect);
        return Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1003, List.of(rule))).getMessage();
    }

    private static String messageOf(int cid) {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(cid), "cid " + cid + " must be refused");
        return String.valueOf(failure.getMessage());
    }
}
