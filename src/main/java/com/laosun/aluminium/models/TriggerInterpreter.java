package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable.CompiledRule;
import com.laosun.aluminium.models.TriggerTable.TriggerContext;

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
 *   <tr><td>{@code MODIFY_ATTR}</td><td>{@code attribute}, {@code percent}, {@code turns}</td>
 *       <td>☐ needs the buff system (P10-3) to own and expire the modifier</td></tr>
 *   <tr><td>{@code ADD_DAMAGE} / {@code TRUE_DAMAGE}</td><td>{@code amount}, {@code element}</td>
 *       <td>☐ needs an element/damage-source decision (P8-3 for follow-up attacks)</td></tr>
 *   <tr><td>{@code APPLY_BUFF}</td><td>{@code buff}, {@code turns}</td><td>☐ P10-3</td></tr>
 *   <tr><td>{@code GAIN_RESOURCE} / {@code SPEND_RESOURCE}</td><td>{@code resource}, {@code amount}</td>
 *       <td>☐ P8-8 (no {@code ResourceManager} yet; {@code Resource} itself already exists)</td></tr>
 *   <tr><td>{@code REDUCE_TOUGHNESS}</td><td>{@code amount}</td><td>☐ needs an element + enemy target</td></tr>
 * </table>
 *
 * <p>Effects run in the order written. Every effect credits the <b>owner</b> (the character whose
 * table fired) unless it names a {@code target}; see {@link #resolveTarget}.
 */
public final class TriggerInterpreter {

    /** Ops that are implemented today. */
    private static final Set<String> WIRED = Set.of(
            "GAIN_ENERGY", "GAIN_SKILL_POINT", "HEAL", "SHIELD", "EXTRA_TURN", "ADVANCE",
            "GAIN_RESOURCE", "SPEND_RESOURCE", "DAMAGE");

    /**
     * Ops that are declared in the roadmap but whose prerequisite phase has not landed. Listing
     * them here (rather than treating them as typos) lets the error message say <i>why</i>.
     */
    private static final Set<String> PLANNED = Set.of(
            "MODIFY_ATTR", "APPLY_BUFF", "REDUCE_TOUGHNESS");

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
            case "GAIN_ENERGY", "GAIN_SKILL_POINT", "HEAL", "SHIELD" -> requireAmount(effect, op, spec);
            case "ADVANCE" -> requirePercent(effect, op, spec);
            case "GAIN_RESOURCE", "SPEND_RESOURCE" -> {
                requireAmount(effect, op, spec);
                requireResource(effect, op, spec);
            }
            case "DAMAGE" -> {
                requireSkill(effect, op, spec);
                requireDamageParam(effect, op, spec);
            }
            default -> {
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
            case "HEAL" -> battle.heal(null, resolveTarget(effect, ctx), scaledAmount(effect, ctx));
            case "SHIELD" -> battle.grantShield(resolveTarget(effect, ctx), scaledAmount(effect, ctx));
            case "EXTRA_TURN" -> battle.grantExtraTurn(resolveTarget(effect, ctx));
            case "ADVANCE" -> battle.queue.advanceActionByPercent(
                    resolveTarget(effect, ctx), effect.getPercent());
            case "GAIN_RESOURCE" -> gainResource(effect, ctx);
            case "SPEND_RESOURCE" -> spendResource(effect, ctx);
            case "DAMAGE" -> damage(battle, effect, ctx);
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
            case "target" -> require(ctx.target(), "target", ctx);
            case "attacker" -> require(ctx.actor(), "attacker", ctx);
            default -> ctx.owner();
        };
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
