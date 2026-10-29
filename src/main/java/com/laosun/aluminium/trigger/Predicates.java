package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.PredicateSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.trigger.TriggerTable.Condition;
import com.laosun.aluminium.trigger.TriggerTable.PartyCondition;
import com.laosun.aluminium.trigger.TriggerTable.TriggerContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a {@link PredicateSpec} into a {@link Condition}, so a rule can state ORs, nesting and reusable named
 * predicates where {@code when} only offers a flat ANDed list of strings.
 *
 * <p><b>What is reused, and why that is the point.</b> A {@code condition} leaf goes through
 * {@link TriggerConditionParser#parseCondition} - the same parser, the same vocabulary, the same error messages as
 * {@code when}. This file adds <b>composition</b>; it deliberately does not add a second condition language, because
 * two spellings of "the actor is on my side" is how the two would drift apart.
 *
 * <p><b>Negation keeps the party rule.</b> {@code not} on a condition that asks about a party becomes the same
 * {@link TriggerConditionParser.Negated} the {@code !} prefix produces: when the party does not exist the condition <b>fails</b>,
 * negated or not. A plain boolean negation would turn "no such party" into "matches", which is the one thing the
 * condition DSL refuses to do.
 */
public final class Predicates {

    private Predicates() {
    }

    /**
     * Compiles one predicate tree.
     *
     * @param spec     the predicate as written
     * @param rule     the rule it belongs to, for its {@code source} in error messages
     * @param registry the named predicates ({@code null} = none, so a {@code ref} is an error)
     * @return the condition
     */
    public static Condition compile(PredicateSpec spec, TriggerSpec rule, Map<String, PredicateSpec> registry) {
        return compile(spec, rule, registry, new LinkedHashSet<>());
    }

    private static Condition compile(PredicateSpec spec, TriggerSpec rule, Map<String, PredicateSpec> registry,
                                     Set<String> visiting) {
        String where = " (source: " + rule.getSource() + ")";
        if (spec == null || spec.getType() == null || spec.getType().isBlank()) {
            throw new IllegalArgumentException("a predicate needs a \"type\": all_of / any_of / not / entity / ref"
                    + where);
        }
        return switch (spec.getType().trim()) {
            case "entity" -> EntityPredicates.compile(spec, rule);
            case "all_of", "any_of" -> {
                if (spec.getTerms() == null || spec.getTerms().isEmpty()) {
                    throw new IllegalArgumentException("an \"" + spec.getType() + "\" predicate needs a non-empty"
                            + " \"terms\"" + where);
                }
                List<Condition> children = new ArrayList<>();
                for (PredicateSpec term : spec.getTerms()) {
                    children.add(compile(term, rule, registry, visiting));
                }
                yield "all_of".equals(spec.getType()) ? new AllOf(List.copyOf(children))
                        : new AnyOf(List.copyOf(children));
            }
            case "not" -> {
                if (spec.getTerm() == null) {
                    throw new IllegalArgumentException("a \"not\" predicate needs \"term\"" + where);
                }
                Condition inner = compile(spec.getTerm(), rule, registry, visiting);
                // Note: the party rule is kept exactly as the `!` prefix keeps it - a missing party fails the
                // condition BEFORE the negation is applied, never after.
                yield inner instanceof PartyCondition party ? new TriggerConditionParser.Negated(party, "!" + inner.source())
                        : new Not(inner);
            }
            case "ref" -> {
                if (spec.getRef() == null || spec.getRef().isBlank()) {
                    throw new IllegalArgumentException("a \"ref\" predicate needs \"ref\"" + where);
                }
                String name = spec.getRef().trim();
                if (!visiting.add(name)) {
                    throw new IllegalArgumentException("the named predicates loop: " + String.join(" -> ", visiting)
                            + " -> " + name + where);
                }
                PredicateSpec named = registry == null ? null : registry.get(name);
                if (named == null) {
                    throw new IllegalArgumentException("the named predicate \"" + name + "\" is not declared in"
                            + " game/predicates.json (declared: "
                            + (registry == null || registry.isEmpty() ? "none" : String.join(" / ", registry.keySet()))
                            + ")" + where);
                }
                yield compile(named, rule, registry, visiting);
            }
            default -> throw new IllegalArgumentException("unknown predicate type \"" + spec.getType()
                    + "\"; known types: all_of / any_of / not / entity / ref" + where);
        };
    }

    /**
     * "All of these": the AND a {@code terms} list states.
     */
    private record AllOf(List<Condition> children) implements Condition {

        @Override
        public boolean test(TriggerContext ctx) {
            for (Condition child : children) {
                if (!child.test(ctx)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String source() {
            return children.stream().map(Condition::source).reduce((a, b) -> a + " && " + b).orElse("true");
        }
    }

    /**
     * "Any of these": the OR a rule cannot otherwise state.
     */
    private record AnyOf(List<Condition> children) implements Condition {

        @Override
        public boolean test(TriggerContext ctx) {
            for (Condition child : children) {
                if (child.test(ctx)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String source() {
            return "(" + children.stream().map(Condition::source).reduce((a, b) -> a + " || " + b).orElse("false") + ")";
        }
    }

    /**
     * A plain boolean negation, for conditions that are <b>not</b> {@link PartyCondition}s - those get
     * {@link TriggerConditionParser.Negated} instead, so "no such party" keeps failing.
     */
    private record Not(Condition inner) implements Condition {

        @Override
        public boolean test(TriggerContext ctx) {
            return !inner.test(ctx);
        }

        /**
         * The condition read as the file spells it: the DSL writes a negation as a leading {@code !}
         * ({@code !target has_state 冻结}), so a log line and a test that pins "this rule's guard" keep reading the
         * same string whether the negation came from {@code when} or from a {@code not} predicate.
         */
        @Override
        public String source() {
            return "!" + inner.source();
        }
    }
}
