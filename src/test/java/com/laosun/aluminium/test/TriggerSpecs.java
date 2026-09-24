package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

import java.util.List;

/**
 * Builds the trigger-table beans by hand, for tests that need a rule the shipped data does not
 * contain.
 *
 * <p><b>Why reflective.</b> {@code TriggerSpec} and {@code EffectSpec} are Lombok
 * {@code @Getter}-only value objects: their fields are written by Gson from the JSON files, never by
 * engine code. The project's convention is therefore to set them reflectively in tests instead of
 * widening the production API with setters nobody else would call — the same choice
 * {@code RelicTriggerTableTest} makes for its own two beans.
 *
 * <p>Kept in one place (rather than copied per test class) because two of the new test classes and
 * {@code RelicTriggerTableTest} all need it, and three copies of a reflective helper is how the
 * field names drift apart.
 */
final class TriggerSpecs {

    /** The {@code source} string used when a test does not care where the rule came from. */
    static final String TEST_SOURCE = "TriggerSpecs (test)";

    private TriggerSpecs() {
    }

    /**
     * One rule: {@code on <event>, when <conditions>, do <effects>}.
     *
     * @param event      the {@code on} value
     * @param conditions the {@code when} list ({@code null} = none)
     * @param source     the provenance string
     * @param effects    the {@code do} list
     */
    static TriggerSpec rule(String event, List<String> conditions, String source, EffectSpec... effects) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", event);
        set(spec, "when", conditions);
        set(spec, "source", source);
        set(spec, "doEffects", List.of(effects));
        return spec;
    }

    /** One rule with the default provenance. */
    static TriggerSpec rule(String event, List<String> conditions, EffectSpec... effects) {
        return rule(event, conditions, TEST_SOURCE, effects);
    }

    /**
     * A {@code MODIFY_ATTR} effect.
     *
     * @param attribute  the attribute name as the JSON spells it (e.g. {@code "ATTACK"})
     * @param percent    the magnitude (negative = debuff)
     * @param turns      the turn count, or {@code null} when {@code permanent} is used
     * @param permanent  the {@code permanent} flag, or {@code null} to leave it out
     * @param maxStacks  the stack cap, or {@code null} to leave it out
     * @param stacks     the alias spelling of the cap, or {@code null} to leave it out
     * @param target     the optional {@code target} selector, or {@code null}
     */
    static EffectSpec modifyAttr(String attribute, double percent, Integer turns, Boolean permanent,
                                 Integer maxStacks, Integer stacks, String target) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "MODIFY_ATTR");
        set(effect, "attribute", attribute);
        set(effect, "percent", percent);
        set(effect, "turns", turns);
        set(effect, "permanent", permanent);
        set(effect, "maxStacks", maxStacks);
        set(effect, "stacks", stacks);
        set(effect, "target", target);
        return effect;
    }

    /** {@code MODIFY_ATTR} with only a turn count. */
    static EffectSpec modifyAttr(String attribute, double percent, int turns) {
        return modifyAttr(attribute, percent, turns, null, null, null, null);
    }

    /** An {@code ADVANCE} effect taking a fraction of the target's remaining time. */
    static EffectSpec advance(double percent) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "ADVANCE");
        set(effect, "percent", percent);
        return effect;
    }

    /** A {@code GAIN_ENERGY} effect with a flat amount (credited to the owner). */
    static EffectSpec gainEnergy(double amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "GAIN_ENERGY");
        set(effect, "amount", amount);
        return effect;
    }

    /** A {@code HEAL} effect with a flat amount. */
    static EffectSpec heal(double amount, String target) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "HEAL");
        set(effect, "amount", amount);
        set(effect, "target", target);
        return effect;
    }

    /**
     * Sets a private field on one of the trigger beans.
     *
     * @throws IllegalStateException when the field does not exist — a renamed field must fail here,
     *                               loudly, rather than leave a test silently asserting nothing
     */
    static void set(Object target, String field, Object value) {
        try {
            var declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set " + field + " on " + target.getClass(), e);
        }
    }
}
