package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.state.AttributeSheet;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.state.ConditionalHealBonuses;
import com.laosun.aluminium.state.EnergyState;
import com.laosun.aluminium.state.HealthState;
import com.laosun.aluminium.state.RuleAmendments;
import com.laosun.aluminium.state.RuleChanceState;
import com.laosun.aluminium.state.RuleLimits;
import com.laosun.aluminium.state.ShieldState;
import com.laosun.aluminium.state.SkillBonuses;
import com.laosun.aluminium.state.SkillSet;
import com.laosun.aluminium.state.TriggerCooldowns;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The combatant's rule bookkeeping and core state live in components, not in raw collections on
 * {@link CanHit}. This pins that composition: adding a new bookkeeping map back to the combatant is
 * exactly the kind of growth the split exists to prevent.
 */
public class CanHitCompositionTest {

    /**
     * Every component extracted from CanHit is still a field of the combatant.
     */
    @Test
    public void theComponentsAreStillWired() {
        Set<Class<?>> types = Set.of(
                TriggerCooldowns.class,
                RuleLimits.class,
                RuleAmendments.class,
                RuleChanceState.class,
                SkillBonuses.class,
                ConditionalHealBonuses.class,
                ShieldState.class,
                HealthState.class,
                EnergyState.class,
                SkillSet.class,
                AttributeSheet.class);
        Set<Class<?>> fields = Set.copyOf(Arrays.stream(CanHit.class.getDeclaredFields())
                .map(Field::getType)
                .toList());
        Assertions.assertTrue(fields.containsAll(types),
                "a composition component was removed from CanHit: " + types);
    }

    /**
     * No raw collection field remains on the combatant.
     */
    @Test
    public void noRawCollectionsRemain() {
        List<String> raw = Arrays.stream(CanHit.class.getDeclaredFields())
                .filter(field -> Map.class.isAssignableFrom(field.getType())
                        || Set.class.isAssignableFrom(field.getType())
                        || List.class.isAssignableFrom(field.getType()))
                .map(Field::getName)
                .toList();
        Assertions.assertEquals(List.of(), raw,
                "rule/core bookkeeping must live in a component, not a raw collection field");
    }
}
