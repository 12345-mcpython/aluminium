package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.PredicateSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.Predicates;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The two condition shapes the content used to keep as strings because the predicate vocabulary had no word for them:
 * <b>facts about the firing</b> ({@code {"type": "event"}}) and <b>strict bounds</b> ({@code {"gt": ...}}).
 *
 * <p><b>Why they had to exist.</b> {@code when} held two kinds of statement the entity predicate could not make: "this cast
 * was data slot 1" is a fact about the <b>event</b>, not about any unit; and "速度<b>大于</b> 200" has no inclusive spelling
 * ({@code >= 201} is a different statement about a non-integer). With both added, the captured party's rules state every
 * condition as a predicate and no string {@code when} is left in their files.
 */
public class EventPredicateTest {

    /** A predicate as the files write it: parsed, because the bean has getters only (Gson fills the fields). */
    private static PredicateSpec spec(String json) {
        return new com.google.gson.Gson().fromJson(json, PredicateSpec.class);
    }

    private static TriggerTable.TriggerContext context(Character unit, Battle battle) {
        return new TriggerTable.TriggerContext(unit, unit, battle.enemies().getFirst(), 0, 0, null, battle,
                SkillCategory.UNSPECIFIED);
    }

    /**
     * The event type compiles through the condition parser and reads back as the sentence it stands for.
     */
    @Test
    public void theEventTypeReadsBackAsItsSentence() {
        PredicateSpec event = spec("{\"type\": \"event\", \"json\": {\"from_skill_id\": 1}}");

        var compiled = Predicates.compile(event, TriggerSpecs.rule("SKILL_CAST", List.of()), null);

        Assertions.assertEquals("from_skill_id == 1.0", compiled.source(),
                "the event type keeps the parser's own spelling, so a witness reads what the file wrote");
    }

    /**
     * The strict bound: {@code {"gt": 200}} fails at exactly 200 and holds just above it - the reason it could not be
     * spelled as {@code >= 200} (which would include the boundary) or {@code >= 201} (which is about a different number).
     */
    @Test
    public void theStrictBoundExcludesTheBoundary() {
        Character unit = CharacterFactory.create(1001, 80);
        Battle battle = new Battle(List.of(unit), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        var compiled = Predicates.compile(spec("{\"type\": \"entity\", \"entity\": \"self\", \"json\": {\"attribute\": {\"name\": \"SPEED\", \"gt\": 200}}}"),
                TriggerSpecs.rule("TURN_START", List.of()), null);
        TriggerTable.TriggerContext ctx = context(unit, battle);

        unit.getAttribute(AttributeType.SPEED).addBase(200 - unit.getAttribute(AttributeType.SPEED).get());
        Assertions.assertEquals(200, unit.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "precondition: speed is exactly the boundary");
        Assertions.assertFalse(compiled.test(ctx), "「大于 200」 excludes 200");

        unit.getAttribute(AttributeType.SPEED).addBase(1);
        Assertions.assertTrue(compiled.test(ctx), "201 is above the boundary");

        Assertions.assertEquals("self_attr:speed > 200.0", compiled.source(),
                "and it reads back as the strict comparison the file wrote");
    }
}
