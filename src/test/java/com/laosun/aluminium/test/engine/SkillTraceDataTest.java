package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.skill.SkillTrace;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * The trace table is a data input now: the static default and an explicit empty table must differ.
 */
public class SkillTraceDataTest {

    /**
     * The default trace table for a real character is non-empty; an explicit empty table is empty.
     */
    @Test
    public void theTraceTableCanBeReplaced() {
        Assertions.assertFalse(SkillTrace.init(1003).isEmpty(), "the default data has traces for 1003");
        Assertions.assertTrue(SkillTrace.init(Map.of(), 1003).isEmpty(),
                "the explicit empty table is honoured");
    }

    /**
     * Building from an empty trace table drops the trace contribution from the stat sheet.
     */
    @Test
    public void anEmptyTraceTableRemovesTheTraceBonus() {
        Character withTraces = CharacterFactory.create(1003, 80, true);
        Character withoutTraces = CharacterFactory.create(GameData.withSkillTraces(Map.of()),
                1003, 80, true, null, null, 0);

        Assertions.assertTrue(withoutTraces.getAttribute(AttributeType.ATTACK).get()
                        < withTraces.getAttribute(AttributeType.ATTACK).get(),
                "the trace attack bonus must be absent when the table is empty");
    }
}
