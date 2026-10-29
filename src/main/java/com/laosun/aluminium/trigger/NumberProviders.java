package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;
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
            "const", "skill_param", "cast_param", "summon_param", "resource", "attr", "stacks",
            "original_damage", "settled_damage_this_event", "elation_base", "cast_applied",
            "party_resource", "energy", "skill_point", "steps",
            "add", "sub", "mul", "div", "min", "max", "negate", "abs", "avg",
            "uniform", "weighted_list");

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
            case "attr" -> attributeOf(provider, ctx);
            case "stacks" -> stacksOf(provider, ctx);
            case "original_damage" -> TriggerMagnitudes.originalDamageOf(effect, ctx);
            case "settled_damage_this_event" -> TriggerMagnitudes.settledDamageOf(ctx);
            case "elation_base" -> TriggerMagnitudes.elationBase(ctx);
            case "cast_applied" -> castAppliedOf(provider, ctx);
            case "party_resource" -> partyResourceOf(provider, ctx);
            case "energy" -> energyOf(provider, ctx);
            case "skill_point" -> {
                if (ctx.battle() == null) {
                    throw new IllegalStateException("a \"skill_point\" provider needs the battle whose points it reads");
                }
                yield ctx.battle().getSkillPoints();
            }
            case "steps" -> stepsOf(provider, ctx);

            case "uniform" -> {
                double low = resolve(requireOperand(provider.getMin(), "uniform", "min"), effect, ctx);
                double high = resolve(requireOperand(provider.getMax(), "uniform", "max"), effect, ctx);
                if (high < low) {
                    throw new IllegalStateException("a \"uniform\" provider has max (" + high + ") below min (" + low
                            + "); the interval is empty");
                }
                yield low + (high - low) * random(ctx).nextDouble();
            }
            case "weighted_list" -> weighted(provider, effect, ctx);
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
            case "attr" -> {
                if (provider.getAttribute() == null || provider.getAttribute().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"attr\" needs \"attribute\" "
                            + "(e.g. ATTACK / DEFENCE / SPEED)" + where);
                }
                if (provider.getWhose() != null && !provider.getWhose().equals("owner")
                        && !provider.getWhose().equals("target")) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"attr\" names whose \""
                            + provider.getWhose() + "\"; it is owner or target" + where);
                }
            }
            case "stacks" -> {
                if (provider.getBuff() == null || provider.getBuff().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"stacks\" needs \"buff\"" + where);
                }
                if (provider.getWhose() != null && !provider.getWhose().equals("owner")
                        && !provider.getWhose().equals("target")) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"stacks\" names whose \""
                            + provider.getWhose() + "\"; it is owner or target" + where);
                }
            }

            case "cast_applied" -> {
                if (provider.getState() == null || provider.getState().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"cast_applied\" needs \"state\""
                            + where);
                }
            }            case "party_resource" -> {
                if (provider.getResource() == null || provider.getResource().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"party_resource\" needs \"resource\""
                            + where);
                }
            }
            case "energy" -> {
                if (provider.getWhose() != null && !provider.getWhose().equals("owner")
                        && !provider.getWhose().equals("target")) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"energy\" names whose \""
                            + provider.getWhose() + "\"; it is owner or target" + where);
                }
                if (provider.getRelation() != null && !provider.getRelation().equals("current")
                        && !provider.getRelation().equals("max")) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"energy\" names relation \""
                            + provider.getRelation() + "\"; it is current or max" + where);
                }
            }
            case "skill_point" -> {
                // No fields: the battle has exactly one skill-point pool.
            }
            case "steps" -> {
                if (provider.getCounter() == null || provider.getCounter().isBlank()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"steps\" needs \"counter\"" + where);
                }
                if (provider.getStep() == null || provider.getStep() <= 0) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"steps\" needs a positive \"step\""
                            + where);
                }
            }            case "uniform" -> {
                if (provider.getMin() == null || provider.getMax() == null) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"uniform\" needs both \"min\" and "
                            + "\"max\"" + where);
                }
                validate(provider.getMin(), field, op, spec);
                validate(provider.getMax(), field, op, spec);
            }
            case "weighted_list" -> {
                List<NumberProvider.Weighted> distribution = provider.getDistribution();
                if (distribution == null || distribution.isEmpty()) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"weighted_list\" needs a non-empty "
                            + "\"distribution\" of {\"weight\": ..., \"value\": {...}}" + where);
                }
                boolean positive = false;
                for (NumberProvider.Weighted entry : distribution) {
                    if (entry.getWeight() != null && entry.getWeight() > 0) {
                        positive = true;
                    }
                    if (entry.getValue() == null) {
                        throw new IllegalArgumentException("\"" + field + "\" of type \"weighted_list\" has an entry with"
                                + " no \"value\"" + where);
                    }
                    validate(entry.getValue(), field, op, spec);
                }
                if (!positive) {
                    throw new IllegalArgumentException("\"" + field + "\" of type \"weighted_list\" has no positive weight"
                            + where);
                }
            }
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

    /**
     * One attribute of one unit: {@code {"type": "attr", "attribute": "DEFENCE", "whose": "owner"}}.
     *
     * <p>The {@code owner} form goes through the same {@code self_attr:} reader the {@code scale} family uses, so the two
     * spellings cannot drift. {@code target} reads the event's own target, and a missing target is loud - a share of
     * nobody is not a number. {@code above} is the {@code self_attr_above:} reading: keep only the part over the
     * threshold, which is how "the Speed above 100" is spelled.
     */
    private static double attributeOf(NumberProvider provider, TriggerContext ctx) {
        if (provider.getAttribute() == null || provider.getAttribute().isBlank()) {
            throw new IllegalStateException("an \"attr\" provider needs \"attribute\"");
        }
        String whose = provider.getWhose() == null ? "owner" : provider.getWhose();
        double value;
        if ("owner".equals(whose)) {
            value = TriggerMagnitudes.resolveScale("self_attr:" + provider.getAttribute().trim(), 1.0, ctx);
        } else if ("target".equals(whose)) {
            CanHit target = ctx.target();
            if (target == null) {
                throw new IllegalStateException("an \"attr\" provider with whose \"target\" needs the event to carry a"
                        + " target, and this one carries none");
            }
            com.laosun.aluminium.DoubleValue attribute = target.getAttribute(
                    com.laosun.aluminium.enums.AttributeType.fromString(provider.getAttribute().trim()));
            if (attribute == null) {
                throw new IllegalStateException("the \"attr\" provider reads " + provider.getAttribute() + " from "
                        + target.getName() + ", which has no resolved value for it");
            }
            value = attribute.get();
        } else {
            throw new IllegalStateException("an \"attr\" provider names whose \"" + whose + "\"; it is owner or target");
        }
        return provider.getAbove() == null ? value : Math.max(0, value - provider.getAbove());
    }

    /**
     * One counter's stack count: {@code {"type": "stacks", "buff": "充能"}}.
     *
     * <p>Same reading as the {@code self_stacks:} / {@code target_stacks:} scales ({@code BuffManager.stacksOf}), so a
     * rule that spells the count as a provider and one that spells it as a scale agree by construction.
     */
    private static double stacksOf(NumberProvider provider, TriggerContext ctx) {
        if (provider.getBuff() == null || provider.getBuff().isBlank()) {
            throw new IllegalStateException("a \"stacks\" provider needs \"buff\"");
        }
        String whose = provider.getWhose() == null ? "owner" : provider.getWhose();
        String scale = ("target".equals(whose) ? "target_stacks:" : "self_stacks:") + provider.getBuff().trim();
        Double value = TriggerMagnitudes.stackScale(scale, 1.0, ctx);
        if (value == null) {
            throw new IllegalStateException("the \"stacks\" provider reads \"" + scale + "\", which is not a counter scale");
        }
        return value;
    }

    /**
     * A weighted choice: {@code {"type": "weighted_list", "distribution": [{"weight": 3, "value": {...}}, ...]}}.
     *
     * <p>A zero-weight entry is legal and never picked, which is how a rule states "this fallback only when nothing else
     * applies". Weights are <b>relative</b>, so they need not sum to 1.
     */
    private static double weighted(NumberProvider provider, EffectSpec effect, TriggerContext ctx) {
        List<NumberProvider.Weighted> distribution = provider.getDistribution();
        if (distribution == null || distribution.isEmpty()) {
            throw new IllegalStateException("a \"weighted_list\" provider needs a non-empty \"distribution\"");
        }
        double total = 0;
        for (NumberProvider.Weighted entry : distribution) {
            if (entry.getWeight() == null || entry.getWeight() <= 0) {
                continue;
            }
            total += entry.getWeight();
        }
        if (total <= 0) {
            throw new IllegalStateException("a \"weighted_list\" provider has no positive weight");
        }
        double roll = random(ctx).nextDouble() * total;
        double accumulated = 0;
        for (NumberProvider.Weighted entry : distribution) {
            if (entry.getWeight() == null || entry.getWeight() <= 0) {
                continue;
            }
            accumulated += entry.getWeight();
            if (roll < accumulated) {
                return resolve(requireOperand(entry.getValue(), "weighted_list", "value"), effect, ctx);
            }
        }
        // Floating-point edge: the roll landed exactly on the end of the last entry.
        for (int i = distribution.size() - 1; i >= 0; i--) {
            NumberProvider.Weighted entry = distribution.get(i);
            if (entry.getWeight() != null && entry.getWeight() > 0) {
                return resolve(requireOperand(entry.getValue(), "weighted_list", "value"), effect, ctx);
            }
        }
        throw new IllegalStateException("a \"weighted_list\" provider has no positive weight");
    }

    /**
     * How many times this cast actually applied a state: {@code {"type": "cast_applied", "state": "冻结"}}.
     *
     * <p>Same counter as the {@code cast_applied:<state>} scale ({@code Battle.castAppliedCount}), so "for each target
     * frozen" reads the same number whichever spelling a rule uses. A count of 0 is a legal answer - nothing landed.
     */
    private static double castAppliedOf(NumberProvider provider, TriggerContext ctx) {
        if (provider.getState() == null || provider.getState().isBlank()) {
            throw new IllegalStateException("a \"cast_applied\" provider needs \"state\"");
        }
        if (ctx.battle() == null) {
            throw new IllegalStateException("a \"cast_applied\" provider needs the battle whose cast it counts, and this"
                    + " event carries none");
        }
        return ctx.battle().castAppliedCount(provider.getState().trim());
    }

    /**
     * A <b>party counter</b> (a battle-scoped resource such as the Aha moment's 笑点): the provider form of the
     * {@code party_resource:<name>} scale.
     *
     * <p>A counter a file never declares is loud, exactly as the scale is: a counter that answers 0 turns "a share of
     * the tally" into "a share of nothing", which is a wrong number with no symptom.
     */
    private static double partyResourceOf(NumberProvider provider, TriggerContext ctx) {
        if (provider.getResource() == null || provider.getResource().isBlank()) {
            throw new IllegalStateException("a \"party_resource\" provider needs \"resource\"");
        }
        if (ctx.battle() == null || ctx.battle().partyResource(provider.getResource().trim()) == null) {
            throw new IllegalStateException("a \"party_resource\" provider reads the counter '"
                    + provider.getResource().trim() + "', which no file declares or which has no battle to live on");
        }
        return ctx.battle().partyResourceValue(provider.getResource().trim());
    }

    /**
     * A unit's energy bar: {@code {"type": "energy", "whose": "owner", "relation": "max"}}.
     *
     * <p>{@code current} is the default because that is the number the documents usually mean ("restore energy equal
     * to 30% of the current energy"), and {@code max} is the cap a threshold sentence compares against.
     */
    private static double energyOf(NumberProvider provider, TriggerContext ctx) {
        String whose = provider.getWhose() == null ? "owner" : provider.getWhose();
        CanHit unit;
        if ("owner".equals(whose)) {
            unit = ctx.owner();
        } else if ("target".equals(whose)) {
            unit = ctx.target();
        } else {
            throw new IllegalStateException("an \"energy\" provider names whose \"" + whose + "\"; it is owner or target");
        }
        if (unit == null) {
            throw new IllegalStateException("an \"energy\" provider with whose \"" + whose + "\" has no such unit in this"
                    + " event");
        }
        String relation = provider.getRelation() == null ? "current" : provider.getRelation();
        return switch (relation) {
            case "current" -> unit.getCurrentEnergy();
            case "max" -> unit.getMaxEnergy();
            default -> throw new IllegalStateException("an \"energy\" provider names relation \"" + relation
                    + "\"; it is current or max");
        };
    }

    /**
     * How many whole steps a counter has reached: {@code {"type": "steps", "counter": "里程碑配额", "step": 120}} is the
     * provider form of {@code per_steps}.
     *
     * <p>Same reader ({@code TriggerInterpreter.stepsOfCounter} to {@code CounterSteps.wholeSteps}), which is why the
     * counter may be an attribute, one of the owner's own resources, or the battle's skill points.
     */
    private static double stepsOf(NumberProvider provider, TriggerContext ctx) {
        if (provider.getCounter() == null || provider.getCounter().isBlank()) {
            throw new IllegalStateException("a \"steps\" provider needs \"counter\"");
        }
        if (provider.getStep() == null || provider.getStep() <= 0) {
            throw new IllegalStateException("a \"steps\" provider needs a positive \"step\"");
        }
        return TriggerInterpreter.stepsOfCounter(provider.getCounter().trim() + ":" + provider.getStep(), ctx);
    }

    /**
     * The battle's random source, so a seeded battle reproduces and a test can pin the draw.
     */
    private static java.util.Random random(TriggerContext ctx) {
        if (ctx.battle() == null) {
            throw new IllegalStateException("a random provider needs a battle to draw from, and this event carries none");
        }
        return ctx.battle().getRng();
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
        copy.setAttribute(provider.getAttribute());
        copy.setWhose(provider.getWhose());
        copy.setAbove(provider.getAbove());
        copy.setBuff(provider.getBuff());
        copy.setState(provider.getState());
        copy.setCounter(provider.getCounter());
        copy.setStep(provider.getStep());
        copy.setRelation(provider.getRelation());
        copy.setMin(copyOf(provider.getMin()));
        copy.setMax(copyOf(provider.getMax()));
        if (provider.getDistribution() != null) {
            List<NumberProvider.Weighted> distribution = new ArrayList<>();
            for (NumberProvider.Weighted entry : provider.getDistribution()) {
                NumberProvider.Weighted copyEntry = new NumberProvider.Weighted();
                copyEntry.setWeight(entry.getWeight());
                copyEntry.setValue(copyOf(entry.getValue()));
                distribution.add(copyEntry);
            }
            copy.setDistribution(distribution);
        }
        return copy;
    }
}
