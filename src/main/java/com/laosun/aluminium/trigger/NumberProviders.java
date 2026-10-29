package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.trigger.TriggerTable.TriggerContext;
import com.laosun.aluminium.entity.Character;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The <b>number provider</b> vocabulary: which {@code type}s a {@code percent_from} / {@code amount_from} may name, how
 * each is resolved, and what the loader must refuse before a battle starts.
 *
 * <p><b>Why a provider instead of more fields.</b> A magnitude used to name its source by field name
 * ({@code percent_from_skill_param}, {@code percent_from_cast_param}, ...): one field per (source, slot-kind) pair, and
 * no way to add two sources. The provider object splits "which number" from "how it is written": the type names the
 * source, and the arithmetic types ({@code add} / {@code mul} / ...) compose providers. That is the whole point - a
 * rule that needs "the owner's #1 plus the cast's #2" now spells it instead of waiting for a new field.
 *
 * <p>⚠ <b>Deliberately not a condition.</b> The condition DSL decides <i>whether</i> an effect runs; this decides
 * <i>which number</i> it uses. A branching provider ({@code conditional}) is left out on purpose: branching already has
 * a spelling (two rules with a condition each), and a second one would be a second execution model inside one number.
 *
 * <p><b>The reader</b> is {@link TriggerMagnitudes}: every source type here calls the same method the field spelling
 * calls, so the two spellings of one source cannot drift.
 */
public final class NumberProviders {

    /**
     * The closed set of types. Kept in one place so the loader's error can list it and a new type has exactly one
     * place to be registered.
     */
    static final Set<String> TYPES = Set.of(
            "const", "skill_param", "cast_param", "summon_param", "resource",
            "add", "sub", "mul", "div", "min", "max", "negate", "abs", "avg");

    private NumberProviders() {
    }

    /**
     * Resolves one provider to a number.
     *
     * @param provider the provider
     * @param effect   the effect being resolved, for the readers' error messages
     * @param ctx      the firing context
     * @return the number
     */
    public static double resolve(NumberProvider provider, EffectSpec effect, TriggerContext ctx) {
        if (provider == null || provider.getType() == null) {
            throw new IllegalStateException("a number provider needs a \"type\"");
        }
        return switch (provider.getType()) {
            case "const" -> requireValue(provider);
            case "skill_param" -> TriggerMagnitudes.ownerSkillParamValue(effect, ctx,
                    requireSkill(provider) + ":" + requireIndex(provider));
            case "cast_param" -> TriggerMagnitudes.castParamValue(effect, ctx, String.valueOf(requireIndex(provider)));
            case "summon_param" -> TriggerMagnitudes.summonSkillParamValue(effect, ctx,
                    requireSlot(provider) + ":" + requireIndex(provider));
            case "resource" -> resourceOf(provider, ctx);
            case "add" -> fold(provider, effect, ctx, "add", 0.0, (left, right) -> left + right);
            case "mul" -> fold(provider, effect, ctx, "mul", 1.0, (left, right) -> left * right);
            case "min" -> fold(provider, effect, ctx, "min", Double.POSITIVE_INFINITY, Math::min);
            case "max" -> fold(provider, effect, ctx, "max", Double.NEGATIVE_INFINITY, Math::max);
            case "avg" -> {
                List<NumberProvider> inputs = requireInputs(provider, "avg");
                double total = 0;
                for (NumberProvider input : inputs) {
                    total += resolve(input, effect, ctx);
                }
                yield total / inputs.size();
            }
            case "sub" -> resolve(requireOperand(provider.getLeft(), "sub", "left"), effect, ctx)
                    - resolve(requireOperand(provider.getRight(), "sub", "right"), effect, ctx);
            case "div" -> {
                double divisor = resolve(requireOperand(provider.getRight(), "div", "right"), effect, ctx);
                if (divisor == 0) {
                    throw new IllegalStateException("a \"div\" provider divides by zero - a share of nothing is not a number");
                }
                yield resolve(requireOperand(provider.getLeft(), "div", "left"), effect, ctx) / divisor;
            }
            case "negate" -> -resolve(requireOperand(provider.getInput(), "negate", "input"), effect, ctx);
            case "abs" -> Math.abs(resolve(requireOperand(provider.getInput(), "abs", "input"), effect, ctx));
            default -> throw new IllegalStateException("unknown number provider type \"" + provider.getType()
                    + "\"; known types: " + String.join(" / ", new java.util.TreeSet<>(TYPES)));
        };
    }

    /**
     * The load-time half: refuses a provider the resolver would otherwise only trip over at fire time.
     *
     * <p>Checked here, not at fire time, for the reason the {@code scale} family gives: a typo in a {@code type}, a
     * missing {@code index} or a {@code skill_param} without a {@code skill} would otherwise load happily and fail on
     * the first firing - or worse, resolve to a neighbouring column.
     *
     * @param provider the provider as written
     * @param field    the JSON key ({@code percent_from} / {@code amount_from}), for the message
     * @param op       the op, for the message
     * @param spec     the rule, for its {@code source}
     */
    public static void validate(NumberProvider provider, String field, String op, TriggerSpec spec) {
        if (provider == null) {
            return;
        }
        String where = " (source: " + spec.getSource() + ")";
        if (provider.getType() == null || provider.getType().isBlank()) {
            throw new IllegalArgumentException("\"" + field + "\" needs a \"type\"; known types: "
                    + String.join(" / ", new java.util.TreeSet<>(TYPES)) + where);
        }
        if (!TYPES.contains(provider.getType())) {
            throw new IllegalArgumentException("\"" + field + "\" names the unknown provider type \""
                    + provider.getType() + "\"; known types: " + String.join(" / ", new java.util.TreeSet<>(TYPES)) + where);
        }
        switch (provider.getType()) {
            case "const" -> {
                if (provider.getValue() == null) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"const\" needs \"value\"" + where);
                }
            }
            case "skill_param" -> {
                if (provider.getSkill() == null || provider.getSkill().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"skill_param\" needs \"skill\" "
                            + "(a skill slot: SKILL / ULTRA / TALENT / COMMON)" + where);
                }
                requireIndex(provider);
            }
            case "cast_param" -> requireIndex(provider);
            case "summon_param" -> {
                requireSlot(provider);
                requireIndex(provider);
            }
            case "resource" -> {
                if (provider.getResource() == null || provider.getResource().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"resource\" needs \"resource\" "
                            + "(the name the character's own resources block declares)" + where);
                }
            }
            case "add", "mul", "min", "max", "avg" -> {
                List<NumberProvider> inputs = provider.getInputs();
                if (inputs == null || inputs.isEmpty()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"" + provider.getType()
                            + "\" needs a non-empty \"inputs\"" + where);
                }
                for (NumberProvider input : inputs) {
                    validate(input, field, op, spec);
                }
            }
            case "sub", "div" -> {
                validate(requireOperand(provider.getLeft(), provider.getType(), "left"), field, op, spec);
                validate(requireOperand(provider.getRight(), provider.getType(), "right"), field, op, spec);
            }
            case "negate", "abs" -> validate(requireOperand(provider.getInput(), provider.getType(), "input"),
                    field, op, spec);
            default -> {
                // Unreachable: the closed set above is checked first.
            }
        }
    }

    /**
     * @param provider the provider
     * @return the inputs it states, refusing an empty list
     */
    private static List<NumberProvider> requireInputs(NumberProvider provider, String type) {
        List<NumberProvider> inputs = provider.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalStateException("a \"" + type + "\" provider needs a non-empty \"inputs\"");
        }
        return inputs;
    }

    private static NumberProvider requireOperand(NumberProvider operand, String type, String name) {
        if (operand == null) {
            throw new IllegalStateException("the \"" + type + "\" provider needs \"" + name + "\"");
        }
        return operand;
    }

    private static double requireValue(NumberProvider provider) {
        if (provider.getValue() == null) {
            throw new IllegalStateException("a \"const\" provider needs \"value\"");
        }
        return provider.getValue();
    }

    private static String requireSkill(NumberProvider provider) {
        if (provider.getSkill() == null || provider.getSkill().isBlank()) {
            throw new IllegalStateException("a \"skill_param\" provider needs \"skill\"");
        }
        return provider.getSkill();
    }

    private static int requireIndex(NumberProvider provider) {
        if (provider.getIndex() == null) {
            throw new IllegalStateException("a \"" + provider.getType() + "\" provider needs \"index\"");
        }
        return provider.getIndex();
    }

    private static int requireSlot(NumberProvider provider) {
        if (provider.getSlot() == null) {
            throw new IllegalStateException("a \"summon_param\" provider needs \"slot\" (the memosprite data slot)");
        }
        return provider.getSlot();
    }

    /**
     * A resource carried by the rule owner.
     *
     * <p>{@code basis_points} exists because one capture states the share in basis points: reading it as a plain
     * number would be a share 10000x too large.
     */
    private static double resourceOf(NumberProvider provider, TriggerContext ctx) {
        if (provider.getResource() == null || provider.getResource().isBlank()) {
            throw new IllegalStateException("a \"resource\" provider needs \"resource\"");
        }
        if (!(ctx.owner() instanceof Character owner)) {
            throw new IllegalStateException("a \"resource\" provider needs its rule owner to be a character, but this"
                    + " rule's owner is " + (ctx.owner() == null ? "nobody" : ctx.owner().getClass().getSimpleName()));
        }
        double raw = owner.getResources().value(provider.getResource().trim());
        return Boolean.TRUE.equals(provider.getBasisPoints()) ? raw / 10000.0 : raw;
    }

    private static double fold(NumberProvider provider, EffectSpec effect, TriggerContext ctx, String type,
                               double identity, java.util.function.DoubleBinaryOperator operator) {
        double result = identity;
        List<NumberProvider> inputs = requireInputs(provider, type);
        for (NumberProvider input : inputs) {
            result = operator.applyAsDouble(result, resolve(input, effect, ctx));
        }
        return result;
    }

    /**
     * A shallow copy, so a rule that amends one of the arithmetic inputs cannot reach into the compiled rule.
     *
     * @param provider the provider to copy, or {@code null}
     * @return the copy, or {@code null}
     */
    public static NumberProvider copyOf(NumberProvider provider) {
        if (provider == null) {
            return null;
        }
        NumberProvider copy = new NumberProvider();
        copy.setType(provider.getType());
        copy.setValue(provider.getValue());
        copy.setSkill(provider.getSkill());
        copy.setIndex(provider.getIndex());
        copy.setSlot(provider.getSlot());
        copy.setResource(provider.getResource());
        copy.setBasisPoints(provider.getBasisPoints());
        if (provider.getInputs() != null) {
            List<NumberProvider> inputs = new ArrayList<>();
            for (NumberProvider input : provider.getInputs()) {
                inputs.add(copyOf(input));
            }
            copy.setInputs(inputs);
        }
        copy.setLeft(copyOf(provider.getLeft()));
        copy.setRight(copyOf(provider.getRight()));
        copy.setInput(copyOf(provider.getInput()));
        return copy;
    }
}
