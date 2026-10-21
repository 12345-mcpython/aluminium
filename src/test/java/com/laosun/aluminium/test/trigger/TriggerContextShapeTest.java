package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.models.trigger.CastFacts;
import com.laosun.aluminium.models.trigger.EventFacts;
import com.laosun.aluminium.models.trigger.RuleScope;
import com.laosun.aluminium.models.trigger.TriggerSubject;
import com.laosun.aluminium.models.trigger.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

/**
 * The trigger context has been narrowed deliberately: one component per kind of fact, not one field
 * per event. Adding an event-scoped field back to the top level would make every condition able to
 * read it; this guard says the next one has to go into the matching record instead.
 */
public class TriggerContextShapeTest {

    /**
     * The context is five grouped facts, in declaration order.
     */
    @Test
    public void theContextIsFiveGroupedFacts() {
        Assertions.assertEquals(List.of("subject", "eventFacts", "battle", "ruleScope", "castFacts"),
                components(TriggerTable.TriggerContext.class));
    }

    /**
     * The groups keep the finer facts they were created for.
     */
    @Test
    public void theGroupsKeepTheirFacts() {
        Assertions.assertEquals(List.of("owner", "actor", "target"),
                components(TriggerSubject.class));
        Assertions.assertEquals(List.of("hitCount", "amount", "damage", "fromCast"),
                components(EventFacts.class));
        Assertions.assertEquals(List.of("ruleId", "targetFilter"),
                components(RuleScope.class));
        Assertions.assertEquals(List.of("skillId", "weakHitCount", "attackHitTargets"),
                components(CastFacts.class));
    }

    /**
     * @param type a record type
     * @return its component names, in declaration order
     */
    private static List<String> components(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
