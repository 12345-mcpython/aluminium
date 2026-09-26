package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable.CompiledRule;
import com.laosun.aluminium.models.TriggerTable.TriggerContext;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.skill.Skill;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs the effects of a {@link TriggerTable} (P8-7). The only place in the trigger system that
 * touches engine state.
 *
 * <p><b>The op vocabulary is restricted on purpose</b> to capabilities the engine already has --
 * the trigger table is an interpreter over existing operations, not a second engine. An op whose
 * prerequisite phase has not landed yet is <b>rejected at load time</b> with the phase named, so a
 * content author learns immediately instead of shipping a rule that silently never works.
 *
 * <table border="1">
 *   <tr><th>op</th><th>arguments</th><th>status</th></tr>
 *   <tr><td>{@code GAIN_ENERGY}</td><td>{@code amount}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code GAIN_SKILL_POINT}</td><td>{@code amount}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code HEAL}</td><td>{@code amount}, optional {@code target}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code SHIELD}</td><td>{@code amount}, optional {@code target}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code EXTRA_TURN}</td><td>optional {@code target}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code ADVANCE}</td><td>{@code percent}, optional {@code target}</td><td>✅ wired (0.0–1.0 = the fraction of the target's <b>remaining</b> time to act that gets skipped)</td></tr>
 *   <tr><td>{@code MODIFY_ATTR}</td><td>{@code attribute}, {@code percent}, <b>exactly one of</b>
 *       {@code turns} / {@code permanent}, optional {@code target}, {@code max_stacks} (alias
 *       {@code stacks})</td>
 *       <td>✅ wired (P10-3) — a negative {@code percent} becomes a {@code DEBUFF}, so a buff and a
 *           debuff on the same attribute coexist; for a ratio attribute ({@code CRIT_ATTACK} and
 *           friends) {@code percent} is the value itself (0.25 = +25 percentage points), because an
 *           additive percentage would multiply their zero base and change nothing.
 *           <b>{@code permanent: true}</b> means "until the battle ends": the modifier is never
 *           ticked, so its duration is unbounded rather than merely long. <b>{@code max_stacks}</b>
 *           (&gt; 1) makes re-applications <b>accumulate</b> up to that cap instead of replacing the
 *           previous one; each stack is an ordinary buff instance with its own id, so it can be
 *           removed on its own. Absent {@code max_stacks} keeps the historical replace behaviour.</td></tr>
 *   <tr><td>{@code APPLY_BUFF}</td><td>{@code buff}, {@code turns}</td>
 *       <td>☐ needs a named-buff registry; plain stat buffs are already covered by {@code MODIFY_ATTR}</td></tr>
 *   <tr><td>{@code GAIN_RESOURCE} / {@code SPEND_RESOURCE}</td><td>{@code resource}, {@code amount}</td>
 *       <td>✅ wired (P8-8)</td></tr>
 *   <tr><td>{@code REDUCE_TOUGHNESS}</td><td>{@code amount}</td><td>☐ needs an element + enemy target</td></tr>
 *   <tr><td>{@code DAMAGE}</td><td>{@code skill}, {@code damage_param}, optional {@code target}</td>
 *       <td>✅ wired (P8-3)</td></tr>
 * </table>
 *
 * <p>Effects run in the order written. Every effect credits the <b>owner</b> (the character whose
 * table fired) unless it names a {@code target}; see {@link #resolveTarget}.
 */
public final class TriggerInterpreter {

    /**
     * Ops that are implemented today.
     */
    private static final Set<String> WIRED = Set.of(
            "GAIN_ENERGY", "GAIN_SKILL_POINT", "HEAL", "SHIELD", "EXTRA_TURN", "ADVANCE",
            "GAIN_RESOURCE", "SPEND_RESOURCE", "DAMAGE", "MODIFY_ATTR");

    /**
     * Ops that are declared in the roadmap but whose prerequisite phase has not landed. Listing
     * them here (rather than treating them as typos) lets the error message say <i>why</i>.
     */
    private static final Set<String> PLANNED = Set.of(
            "APPLY_BUFF", "REDUCE_TOUGHNESS");

    /**
     * The selectors an effect's {@code target} may name.
     *
     * <p>This set exists to close a silent-typo hole: the resolver used to fall back to "the owner"
     * for anything it did not recognise, so a misspelled {@code target} behaved exactly like
     * {@code "self"} — a wrong answer that reports nothing. Same reasoning as the condition
     * variables being a closed set.
     */
    private static final Set<String> TARGET_SELECTORS =
            Set.of("self", "target", "attacker", "all_allies", "party");

    /**
     * The two spellings of "every one of our characters".
     */
    private static final Set<String> TARGET_ALL_ALLIES = Set.of("all_allies", "party");

    private TriggerInterpreter() {
    }

    /**
     * Validates one effect at table-load time.
     *
     * @param effect the effect to check
     * @param spec   the owning rule, used to report the source
     * @throws IllegalArgumentException when the op is unknown, planned-but-unwired, or missing a
     *                                  required argument
     */
    public static void validate(EffectSpec effect, TriggerSpec spec) {
        String op = normalizeOp(effect, spec);
        requireTargetSelector(effect, op, spec);
        if (PLANNED.contains(op)) {
            throw new IllegalArgumentException(
                    "Trigger op '" + op + "' is declared in the roadmap but not wired yet "
                            + "(see ROADMAP P8-7 / TriggerInterpreter); rule source: " + spec.getSource());
        }
        if (!WIRED.contains(op)) {
            throw new IllegalArgumentException(
                    "Unknown trigger op '" + op + "' (source: " + spec.getSource() + ")");
        }
        switch (op) {
            case "GAIN_ENERGY", "GAIN_SKILL_POINT", "HEAL", "SHIELD" -> {
                requireAmount(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "ADVANCE" -> {
                requirePercent(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "GAIN_RESOURCE", "SPEND_RESOURCE" -> {
                requireAmount(effect, op, spec);
                requireResource(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "DAMAGE" -> {
                requireSkill(effect, op, spec);
                requireDamageParam(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "MODIFY_ATTR" -> {
                requireAttribute(effect, op, spec);
                requirePercent(effect, op, spec);
                requireDuration(effect, op, spec);
                requireStackCap(effect, op, spec);
            }
            default -> {
                requireNoStackArguments(effect, op, spec);
            }
        }
    }

    /**
     * Runs every effect of a rule.
     *
     * @param battle the running battle
     * @param rule   the matched rule
     * @param ctx    the context the rule was matched with
     */
    public static void apply(Battle battle, CompiledRule rule, TriggerContext ctx) {
        for (EffectSpec effect : rule.effects()) {
            applyOne(battle, effect, ctx);
        }
    }

    /**
     * Fires an event against one table: matches the rules and runs them.
     *
     * @param battle the running battle
     * @param table  the table to fire (may be {@code null} or empty, which is a no-op)
     * @param event  the event that happened
     * @param ctx    the context
     * @return how many rules fired (useful for tests and diagnostics)
     */
    public static int fire(Battle battle, TriggerTable table, TriggerEvent event, TriggerContext ctx) {
        if (table == null || table.isEmpty()) {
            return 0;
        }
        List<CompiledRule> rules = table.matching(event, ctx);
        for (CompiledRule rule : rules) {
            apply(battle, rule, ctx);
        }
        return rules.size();
    }

    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String op = normalizeOp(effect, null);
        switch (op) {
            case "GAIN_ENERGY" -> battle.grantEnergy(resolveTarget(effect, ctx), scaledAmount(effect, ctx));
            case "GAIN_SKILL_POINT" -> battle.gainSkillPoint((int) Math.round(scaledAmount(effect, ctx)));
            case "HEAL" -> {
                double amount = scaledAmount(effect, ctx);
                for (CanHit target : resolveTargets(battle, effect, ctx)) {
                    battle.heal(null, target, amount);
                }
            }
            case "SHIELD" -> {
                double amount = scaledAmount(effect, ctx);
                for (CanHit target : resolveTargets(battle, effect, ctx)) {
                    battle.grantShield(target, amount);
                }
            }
            case "EXTRA_TURN" -> battle.grantExtraTurn(resolveTarget(effect, ctx));
            case "ADVANCE" -> battle.queue.advanceActionByPercent(
                    resolveTarget(effect, ctx), effect.getPercent());
            case "GAIN_RESOURCE" -> gainResource(effect, ctx);
            case "SPEND_RESOURCE" -> spendResource(effect, ctx);
            case "DAMAGE" -> damage(battle, effect, ctx);
            case "MODIFY_ATTR" -> modifyAttr(battle, effect, ctx);
            default -> throw new IllegalStateException(
                    "Op '" + op + "' passed validation but has no implementation");
        }
    }

    /**
     * Adds to the target's resource (P8-8).
     *
     * @param effect the effect ({@code resource} + {@code amount})
     * @param ctx    the context
     */
    private static void gainResource(EffectSpec effect, TriggerContext ctx) {
        CanHit holder = resolveTarget(effect, ctx);
        holder.getResources().gain(effect.getResource(), (int) Math.round(scaledAmount(effect, ctx)));
    }

    /**
     * Spends from the target's resource (P8-8).
     *
     * <p>A spend that does not fit is an <b>error</b>, not a silent no-op: unlike skill points (where
     * "not enough" legitimately means "the player cannot press the button"), a trigger spending a
     * resource is a rule the author wrote believing the resource would be there. Reporting it beats
     * letting a character's stack quietly fail to be consumed.
     *
     * @throws IllegalStateException when the resource is missing or does not hold enough
     */
    private static void spendResource(EffectSpec effect, TriggerContext ctx) {
        CanHit holder = resolveTarget(effect, ctx);
        int amount = (int) Math.round(scaledAmount(effect, ctx));
        String id = effect.getResource();
        if (!holder.getResources().has(id)) {
            throw new IllegalStateException(
                    "SPEND_RESOURCE '" + id + "' but " + holder.getName() + " has no such resource");
        }
        if (!holder.getResources().spendExactly(id, amount)) {
            throw new IllegalStateException(
                    "SPEND_RESOURCE '" + id + "' needs " + amount + " but " + holder.getName()
                            + " has only " + holder.getResources().value(id));
        }
    }

    /**
     * The effect's amount, scaled by the event's hit count when the rule asked for it.
     *
     * <p>See {@link EffectSpec#getPerTarget()}: the game states both "2 per attack" (Robin) and
     * "1.5 per target hit" (Tribbie), and treating them the same would be wrong by a factor equal to
     * the number of targets.
     *
     * @param effect the effect
     * @param ctx    the context supplying the hit count
     * @return the amount to apply
     */
    private static double scaledAmount(EffectSpec effect, TriggerContext ctx) {
        double amount = effect.getAmount() == null ? 0 : effect.getAmount();
        return Boolean.TRUE.equals(effect.getPerTarget()) ? amount * ctx.hitCount() : amount;
    }

    /**
     * Who an effect applies to.
     *
     * <p>The default is the <b>owner</b> (the character whose table fired) -- what nearly every
     * "my own resource" mechanic wants, and what lets the JSON stay terse. Other selectors pick a
     * party of the event instead:
     * <ul>
     *   <li>{@code "target"} — the event's subject (the one who lost HP, was healed, ...), which is
     *       what an ally-affecting rule needs;</li>
     *   <li>{@code "attacker"} — who caused the event, which is what a counter needs
     *       ("I was hit, so I hit the one who hit me").</li>
     * </ul>
     *
     * @param effect the effect
     * @param ctx    the context
     * @return the resolved entity
     * @throws IllegalStateException when the requested party is missing from this event
     */
    private static CanHit resolveTarget(EffectSpec effect, TriggerContext ctx) {
        String selector = normalizeTarget(effect);
        return switch (selector) {
            case "self" -> ctx.owner();
            case "target" -> require(ctx.target(), "target", ctx);
            case "attacker" -> require(ctx.actor(), "attacker", ctx);
            default -> throw new IllegalStateException(
                    "Effect names an unknown target selector '" + selector
                            + "'; this should have been rejected when the table was loaded");
        };
    }

    /**
     * Who an effect applies to, as a list, for the ops that can reach more than one party member.
     *
     * <p>{@code "all_allies"} (alias {@code "party"}) is what makes "all allies' ATK +X%" expressible.
     * It matters more than it looks: most buff talents in this game's data are party-wide, so without
     * this selector the trigger table would only cover self-buffs. It is resolved against
     * {@link Battle#characters} and therefore needs a battle, which the single-target selectors do not.
     *
     * @param battle the running battle (may be {@code null} only when the selector is single-target)
     * @param effect the effect
     * @param ctx    the context
     * @return the entities the effect applies to (never empty for the single-target selectors)
     * @throws IllegalStateException when {@code all_allies} is used without a battle
     */
    private static List<CanHit> resolveTargets(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String selector = normalizeTarget(effect);
        if (TARGET_ALL_ALLIES.contains(selector)) {
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + selector
                                + "\" but no battle was supplied to take the party from");
            }
            return List.copyOf(battle.characters);
        }
        return List.of(resolveTarget(effect, ctx));
    }

    private static CanHit require(CanHit entity, String what, TriggerContext ctx) {
        if (entity == null) {
            throw new IllegalStateException(
                    "Effect targets \"" + what + "\" but this event has no such party");
        }
        return entity;
    }

    private static String normalizeTarget(EffectSpec effect) {
        // `target` lives on the effect as an optional selector; absent means "self".
        return effect.getTarget() == null ? "self" : effect.getTarget().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOp(EffectSpec effect, TriggerSpec spec) {
        if (effect == null || effect.getOp() == null || effect.getOp().isBlank()) {
            throw new IllegalArgumentException(
                    "Trigger effect without an op" + (spec == null ? "" : " (source: " + spec.getSource() + ")"));
        }
        return effect.getOp().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Settles a {@code MODIFY_ATTR} effect (P10-3): "the target's {@code attribute} changes by
     * {@code percent} for {@code turns} turns" -- the generic stat buff / debuff.
     *
     * <p><b>Why this op matters more than its size suggests.</b> 72 of the 93 characters have a
     * buff / enhance talent, and before this op the only way to express one was a new Java buff
     * class per character. With it, "ATK +33% for 2 turns" is data, and the engine gained no
     * character-specific knowledge at all: it forwards (attribute, value, turns) to
     * {@link StatModifierBuff}.
     *
     * <p><b>The sign decides buff or debuff.</b> {@code percent >= 0} is applied as a
     * {@code BUFF}, a negative one as a {@code DEBUFF}. The distinction is not cosmetic: it decides
     * which half of the attribute the modifier lands in, so "ATK +50%" and "ATK -30%" can be on the
     * same character at once and be removed independently instead of overwriting each other.
     *
     * <p>{@code percent} is a decimal ({@code 0.5} = +50%).
     *
     * <p><b>What the decimal means depends on the attribute kind</b>, and the difference is not
     * cosmetic either:
     * <ul>
     *   <li>For a <b>base</b> attribute ({@code HEALTH / ATTACK / DEFENCE / SPEED}) it is an
     *       {@code ADD_PERCENT} modifier, so it sums with the character's other add-percent bonuses
     *       (traces, relics, light cones) rather than multiplying on top of them -- see
     *       {@link DoubleValue} for the formula.</li>
     *   <li>For a <b>ratio</b> attribute ({@code CRIT_CHANCE}, {@code CRIT_ATTACK}, the damage
     *       boosts, {@code BREAKING_EFFECT}, {@code ENERGY_REGENERATION_RATE}, ...) it is the value
     *       <b>itself</b>: {@code 0.25} on {@code CRIT_ATTACK} means +25 percentage points. This is
     *       the engine's existing convention for those attributes (see {@code RelicSuit.appendTo}:
     *       "any other {@code isPercent} attribute is a percentage-point value") and the reason is
     *       that their whole value is a flat modifier -- the builder writes them with
     *       {@code AttributeBuilder.addPercentPoint}, so their base is literally 0 and an
     *       {@code ADD_PERCENT} modifier would multiply zero: the rule would fire, add a modifier and
     *       change nothing. A silent no-op is the one outcome this op must not have.</li>
     * </ul>
     *
     * @param effect the effect ({@code attribute} / {@code percent} / {@code turns} or
     *               {@code permanent}, optional {@code max_stacks}, {@code target})
     * @param ctx    the context
     */
    private static void modifyAttr(Battle battle, EffectSpec effect, TriggerContext ctx) {
        AttributeType attribute = AttributeType.fromString(effect.getAttribute());
        double percent = effect.getPercent();
        boolean permanent = Boolean.TRUE.equals(effect.getPermanent());
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        int maxStacks = effect.stackCap() == null ? 1 : effect.stackCap();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            target.getBuffManager().addBuff(statModifier(attribute, percent, turns, permanent, maxStacks));
        }
    }

    /**
     * The duration handed to a permanent modifier.
     *
     * <p>Deliberately {@code 1} and not a large number: {@link StatModifierBuff} marks the buff
     * permanent, and {@code BuffManager.processBuffTick} never counts a permanent buff down, so this
     * value is never read. It exists only because {@code AbstractBuff}'s constructor takes a turn count.
     * Spelling it as {@code Integer.MAX_VALUE} would suggest the engine relies on a big number, which
     * is exactly the misconception the flag removes.
     */
    private static final int UNBOUNDED_DURATION_PLACEHOLDER = 1;

    /**
     * The modifier a {@code MODIFY_ATTR} effect produces, as a buff or a debuff according to the sign.
     *
     * <p>See {@link #modifyAttr} for why a ratio attribute gets a flat modifier and a base attribute
     * gets an additive percentage. Splitting it out keeps the "which modifier kind" decision in one
     * readable place instead of a nested conditional at the call site.
     *
     * @param attribute the attribute to touch
     * @param percent   the magnitude; {@code < 0} produces a debuff
     * @param turns     how long it lasts (validated at load time; ignored when {@code permanent})
     * @param permanent {@code true} = "for the rest of the battle", i.e. never ticked
     * @param maxStacks how many copies may accumulate; {@code 1} = replace on re-application
     */
    private static StatModifierBuff statModifier(AttributeType attribute, double percent, int turns,
                                                 boolean permanent, int maxStacks) {
        boolean debuff = percent < 0;
        // The sign is carried by `percent` itself (a negative value), which is what the original
        // percentBuff/flatBuff/… factories produced; only the modifier kind depends on the attribute.
        return StatModifierBuff.of(attribute, attribute.isPercent ? "pure" : "add_percent", percent,
                debuff ? "debuff" : "buff", turns, false, permanent, maxStacks);
    }

    /**
     * Validates an effect's optional {@code target} selector <b>at load time</b>.
     *
     * <p>This closes a silent-typo hole: the resolver used to fall back to "the owner" for anything
     * it did not recognise, so {@code "target": "atacker"} behaved exactly like {@code "self"} —
     * the rule fired, nothing was reported, and the character just quietly did the wrong thing. The
     * condition variables are already a closed set for the same reason; the selector is now too.
     *
     * <p>Absent means {@code "self"}, which is the common case and stays implicit so the JSON can
     * stay terse.
     */
    private static void requireTargetSelector(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTarget() == null || effect.getTarget().isBlank()) {
            return;
        }
        String selector = normalizeTarget(effect);
        if (!TARGET_SELECTORS.contains(selector)) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown \"target\" selector '" + effect.getTarget()
                            + "' (known: self / target / attacker / all_allies); it used to fall back "
                            + "to the owner, which made a typo behave like self "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    private static void requireAmount(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"amount\" (source: " + spec.getSource() + ")");
        }
    }

    private static void requirePercent(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getPercent() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"percent\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the {@code attribute} name of a {@code MODIFY_ATTR} effect <b>at load time</b>.
     *
     * <p>The "fail at load, not mid-battle" rule (the same one behind the op vocabulary) applies to
     * arguments too: a misspelled attribute must be reported when the table is read, not when the
     * character finally takes the action that fires the rule.
     *
     * <p>The four builder-only {@code *_PERCENT} variants are rejected here as well, because
     * {@link com.laosun.aluminium.models.CanHit#getAttribute(AttributeType)} returns {@code null}
     * for them: targeting one would raise a {@code NullPointerException} in the middle of a battle
     * instead of "this attribute cannot hold a buff".
     */
    private static void requireAttribute(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAttribute() == null || effect.getAttribute().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"attribute\" (e.g. ATTACK / DEFENCE / SPEED / CRIT_ATTACK) "
                            + "(source: " + spec.getSource() + ")");
        }
        AttributeType attribute;
        try {
            attribute = AttributeType.fromString(effect.getAttribute());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown attribute '" + effect.getAttribute()
                            + "' (source: " + spec.getSource() + ")");
        }
        if (attribute.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Op " + op + " cannot target '" + effect.getAttribute()
                            + "': it is a builder-only input key, not a runtime attribute, so a buff "
                            + "on it would silently do nothing. Name the base attribute and use "
                            + "\"percent\" instead (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that a {@code MODIFY_ATTR} effect declares how long the buff lasts — <b>exactly one</b>
     * of {@code turns} and {@code permanent}.
     *
     * <p>There is deliberately no default: an omitted duration would be either "forever" (wrong: most
     * buffs in this game expire) or a number this class invented. Making the author write it is the
     * same call as {@code damage_param} having no default.
     *
     * <p>Stating <b>both</b> is rejected for the same reason a typo is: the two answers disagree, and
     * silently letting one win would produce a rule that does not do what its text says.
     */
    private static void requireDuration(EffectSpec effect, String op, TriggerSpec spec) {
        boolean permanent = Boolean.TRUE.equals(effect.getPermanent());
        if (permanent) {
            if (effect.getTurns() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " has both \"turns\" (" + effect.getTurns()
                                + ") and \"permanent\": true; they are two different durations and only "
                                + "one may be stated (source: " + spec.getSource() + ")");
            }
            return;
        }
        if (effect.getTurns() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"turns\" (how long the modifier lasts) or \"permanent\": "
                            + "true (until the battle ends); there is no default "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getTurns() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a non-positive \"turns\" (" + effect.getTurns()
                            + "); such a buff would expire before it could do anything. Use "
                            + "\"permanent\": true for an effect with no turn limit "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the optional stack cap of a {@code MODIFY_ATTR} effect <b>at load time</b>.
     *
     * <p>Same discipline as the rest of the vocabulary ("reject loudly at load, naming the phase"): a
     * cap of {@code 0} would create a modifier that never applies, a negative one is meaningless, and a
     * misspelled argument name would otherwise be ignored by Gson and quietly leave the rule
     * non-stacking — the exact "rule fires, nothing accumulates" symptom the author would never notice.
     *
     * <p>Also rejects the two spellings being stated at once, and any use of the argument on an op that
     * has no stacking concept.
     */
    private static void requireStackCap(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getMaxStacks() != null && effect.getStacks() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states both \"max_stacks\" and its alias \"stacks\"; use one "
                            + "(source: " + spec.getSource() + ")");
        }
        Integer cap = effect.stackCap();
        if (cap == null) {
            return;
        }
        if (cap <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a non-positive stack cap (" + cap
                            + "); such a modifier would never apply (source: " + spec.getSource() + ")");
        }
        if (cap > Constant.MAX_STACKS_LIMIT) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a stack cap of " + cap + ", above the engine's limit of "
                            + Constant.MAX_STACKS_LIMIT + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Rejects the stacking / "until the battle ends" arguments on ops that do not have those concepts.
     *
     * <p>A Gson field is simply {@code null} when the JSON omits it, which means an argument written on
     * the <b>wrong op</b> is silently ignored — the author sees the rule load and the effect never
     * stack. That is the same class of silent failure the closed op vocabulary exists to prevent, so the
     * arguments are checked here rather than being read only by {@code MODIFY_ATTR}.
     */
    private static void requireNoStackArguments(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getMaxStacks() != null || effect.getStacks() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not support \"max_stacks\"/\"stacks\"; only MODIFY_ATTR "
                            + "accumulates (source: " + spec.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent())) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not support \"permanent\"; only MODIFY_ATTR has a duration "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Settles a {@code DAMAGE} effect: a hit whose shape, element and multiplier all come from the
     * skill data of a named slot (P8-3).
     *
     * <p>Why read the skill data instead of letting the data file carry a number: the attack is
     * <b>already defined</b> in {@code skills.json}. Clara's talent, for instance, says
     * "单体攻击 / Physical / 削韧 30 / params [1, 0.8, 0.1]" — everything except <i>when</i> to fire it.
     * The trigger table supplies the "when", so a follow-up attack stays a pure data rule rather than
     * a Java class with a duplicated multiplier.
     *
     * <p>Per the official definition, such a hit is settled as {@code ADDITIONAL} damage: it counts
     * toward the attacker's kill credit but "does not count as dealing 1 attack", so the target gains
     * no on-hit energy and no attack-level event fires. {@code as_attack} is accepted for future
     * use but does not change that yet — the engine has one settlement path for supplementary hits.
     *
     * <p>⚠ Consequence worth knowing: the hit causes HP loss, which fires {@code HP_LOST} again. Two
     * characters who both counter each other therefore ping-pong until
     * {@code Battle.MAX_TRIGGER_DEPTH} stops it with an exception, rather than hanging.
     *
     * @param battle the running battle
     * @param effect the effect ({@code skill}, {@code damage_param}, optional {@code target})
     * @param ctx    the context
     */
    private static void damage(Battle battle, EffectSpec effect, TriggerContext ctx) {
        CanHit attacker = ctx.owner();
        CanHit victim = resolveTarget(effect, ctx);
        if (victim == null || victim.isDeath()) {
            return;
        }
        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = attacker.getSkills().get(slot);
        if (skill == null || skill.getData() == null) {
            throw new IllegalStateException(
                    attacker.getName() + " has no " + slot + " skill to fire a DAMAGE effect from");
        }
        if (!skill.getData().getEffect().isDamaging()) {
            throw new IllegalStateException(
                    "DAMAGE effect points at " + slot + ", whose effect is "
                            + skill.getData().getEffect() + " rather than a damaging one");
        }
        double multiplier = multiplierOf(skill, effect.getDamageParam());
        double base = attacker.getAttribute(AttributeType.ATTACK).get() * multiplier;
        battle.applyAdditionalDamage(attacker, victim, skill.getData().getElement(), base);
    }

    /**
     * Reads the damage multiplier out of the skill's per-level parameter row.
     *
     * @param skill the skill
     * @param index which parameter holds the multiplier (see {@link EffectSpec#getDamageParam()})
     * @return the multiplier for this skill's current level
     * @throws IllegalStateException when the level or the index falls outside the data
     */
    private static double multiplierOf(Skill skill, int index) {
        var levels = skill.getData().getSkills();
        int row = skill.getLevel() - 1;
        if (row < 0 || row >= levels.size()) {
            throw new IllegalStateException(
                    "Skill level " + skill.getLevel() + " is outside the parameter table (rows="
                            + levels.size() + ")");
        }
        var params = levels.get(row);
        if (index >= params.size()) {
            throw new IllegalStateException(
                    "damage_param " + index + " is outside this skill's parameter row (size="
                            + params.size() + ")");
        }
        return params.get(index);
    }

    private static void requireResource(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getResource() == null || effect.getResource().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"resource\" (source: " + spec.getSource() + ")");
        }
    }

    private static void requireSkill(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getSkill() == null || effect.getSkill().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"skill\" (which slot the attack comes from, e.g. TALENT) "
                            + "(source: " + spec.getSource() + ")");
        }
        try {
            SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown skill slot '" + effect.getSkill()
                            + "' (source: " + spec.getSource() + ")");
        }
    }

    private static void requireDamageParam(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getDamageParam() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"damage_param\" (which parameter of the skill row is the "
                            + "multiplier); there is no default because the index differs per ability "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getDamageParam() < 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a negative \"damage_param\" (source: " + spec.getSource() + ")");
        }
    }
}
