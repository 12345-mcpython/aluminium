package com.laosun.aluminium.trigger;

import java.util.List;

/**
 * The rule/effect half of a trigger context: which rule is firing, and that effect's per-target
 * filter.
 *
 * <p>Grouping them keeps "the rule being applied" one value. The rule id is written per rule by the
 * interpreter and the filter is set once per effect; both are copies, never mutable fields, because a
 * rule can fire inside another rule's effect and a shared field would report the inner rule to the
 * outer one's remaining effects.
 *
 * @param ruleId       the rule id ({@code ""} when the rule states none)
 * @param targetFilter the effect's per-target conditions (empty = admit everything)
 */
public record RuleScope(String ruleId, List<TriggerTable.Condition> targetFilter) {

    /**
     * No rule scope: the value a context carries before the interpreter supplies one.
     */
    public static final RuleScope EMPTY = new RuleScope("", List.of());

    /**
     * Normalizes {@code null} to the empty spellings.
     */
    public RuleScope {
        ruleId = ruleId == null ? "" : ruleId;
        targetFilter = targetFilter == null ? List.of() : List.copyOf(targetFilter);
    }

    /**
     * @param id the rule id
     * @return the same scope with that rule id
     */
    public RuleScope withRuleId(String id) {
        return new RuleScope(id, targetFilter);
    }

    /**
     * @param filter the per-target conditions
     * @return the same scope with that filter
     */
    public RuleScope withTargetFilter(List<TriggerTable.Condition> filter) {
        return new RuleScope(ruleId, filter);
    }
}
