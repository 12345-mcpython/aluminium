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
 * widening the production API with setters nobody else would call - the same choice
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
     * A {@code SHIELD} effect with a flat amount.
     *
     * @param turns how many of the shielded unit's turns it lasts, or {@code null} for a shield that is only
     *              removed by being used up
     */
    static EffectSpec shield(double amount, Integer turns, String target) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "SHIELD");
        set(effect, "amount", amount);
        set(effect, "turns", turns);
        set(effect, "target", target);
        return effect;
    }

    /**
     * A {@code GAIN_RESOURCE} effect (credited to the owner unless {@code target} says otherwise).
     *
     * @param resource the resource id, as the character declares it
     * @param amount   how much to add
     */
    static EffectSpec gainResource(String resource, int amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "GAIN_RESOURCE");
        set(effect, "resource", resource);
        set(effect, "amount", (double) amount);
        return effect;
    }

    /** A {@code SPEND_RESOURCE} effect. */
    static EffectSpec spendResource(String resource, int amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "SPEND_RESOURCE");
        set(effect, "resource", resource);
        set(effect, "amount", (double) amount);
        return effect;
    }

    /** {@code MODIFY_DAMAGE_TAKEN} with a turn count (positive = 易伤, negative = 减伤). */
    static EffectSpec modifyDamageTaken(double percent, int turns) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        set(effect, "percent", percent);
        set(effect, "turns", turns);
        set(effect, "target", "target");
        return effect;
    }

    /**
     * {@code APPLY_BUFF}: a named state.
     *
     * @param state     the state's name as the documents spell it (协奏)
     * @param turns     how many turns it lasts, or {@code null} for a permanent one
     * @param permanent {@code true} for "整场战斗", or {@code null}
     */
    static EffectSpec applyBuff(String state, Integer turns, Boolean permanent) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "APPLY_BUFF");
        set(effect, "buff", state);
        set(effect, "turns", turns);
        set(effect, "permanent", permanent);
        set(effect, "target", "target");
        return effect;
    }

    /** {@code TAUNT} for a number of turns. */
    static EffectSpec taunt(int turns) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "TAUNT");
        set(effect, "turns", turns);
        set(effect, "target", "target");
        return effect;
    }

    /**
     * An {@code EXTEND_BUFF} effect: "…的持续时间增加 N 回合".
     *
     * @param buff      the state's name (or 护盾) the rule lengthens, or {@code null} when filtering by attribute
     * @param attribute the attribute a modifier sits on, or {@code null} when filtering by name
     * @param turns     how many turns to add, or {@code null} to leave the field out
     */
    static EffectSpec extendBuff(String buff, String attribute, Integer turns) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "EXTEND_BUFF");
        set(effect, "buff", buff);
        set(effect, "attribute", attribute);
        set(effect, "turns", turns);
        set(effect, "target", "target");
        return effect;
    }

    /**
     * An {@code APPLY_DOT} effect: "使目标陷入…状态，每回合造成…伤害，持续 N 回合".
     *
     * @param element  the {@code DamageElement} spelling ({@code "Fire"} to 灼烧), or {@code null} to leave it out
     * @param amount   a flat per-turn amount, or the constant term of a derived one, or {@code null}
     * @param scale    {@code "self_attr:<ATTRIBUTE>"} for a value derived from the rule owner, or {@code null}
     * @param percent  the share of that attribute, or {@code null}
     * @param turns    how many of the victim's turns it lasts, or {@code null} to leave the field out
     * @param chance   the base chance (基础概率), or {@code null} for one that always lands
     */
    static EffectSpec dot(String element, Double amount, String scale, Double percent, Integer turns,
                          Double chance) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "APPLY_DOT");
        set(effect, "element", element);
        set(effect, "amount", amount);
        set(effect, "scale", scale);
        set(effect, "percent", percent);
        set(effect, "turns", turns);
        set(effect, "baseChance", chance);
        // Defaulted to the event's subject, because that is what every DOT case means: the difference between
        // `self` (the engine's default when no target is stated) and `target` is invisible in a one-unit test.
        set(effect, "target", "target");
        return effect;
    }

    /**
     * An {@code APPLY_CONTROL} effect: "有 X% 基础概率使目标陷入…状态，持续 N 回合".
     *
     * @param control the state's name as the documents spell it (冻结 / 纠缠 / 禁锢)
     * @param turns   how many of the victim's turns it lasts, or {@code null} to leave the field out (which the
     *                loader refuses - a control with no duration would never end)
     * @param chance  the base chance (基础概率), or {@code null} for a state that always lands
     * @param target  the optional target selector, or {@code null}
     */
    static EffectSpec applyControl(String control, Integer turns, Double chance, String target) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "APPLY_CONTROL");
        set(effect, "control", control);
        set(effect, "turns", turns);
        set(effect, "baseChance", chance);
        set(effect, "target", target);
        return effect;
    }

    /**
     * A {@code DAMAGE} effect: the numbers come out of the named skill's own parameter row.
     *
     * @param skill  the skill slot ({@code "TALENT"}, {@code "ULTRA"}, …)
     * @param param  the 0-based column of the row
     * @param level  the 1-based row, or {@code null} for the skill's own level
     * @param target the optional target selector, or {@code null}
     */
    static EffectSpec damage(String skill, int param, Integer level, String target) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "DAMAGE");
        set(effect, "skill", skill);
        set(effect, "damageParam", param);
        set(effect, "damageLevel", level);
        set(effect, "target", target);
        return effect;
    }

    /**
     * Sets a private field on one of the trigger beans.
     *
     * @throws IllegalStateException when the field does not exist - a renamed field must fail here,
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
