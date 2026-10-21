package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import lombok.Getter;

import java.util.*;

/**
 * A character's compiled trigger table: the data form of its mechanics.
 *
 * <p>Loadable from {@code resources/game/characters/<cid>.json}; an unregistered character simply has an
 * <b>empty table</b>, which is normal rather than an error (there are 93 characters and only a
 * handful are data-ised so far).
 *
 * <p>Two design points worth keeping:
 * <ul>
 *   <li><b>Conditions are compiled once, at load time.</b> A rule whose {@code when} expression is
 *       malformed, or which names an event the engine does not emit yet, is rejected while the
 *       table is built -- so a typo surfaces immediately instead of silently never firing. That is
 *       the "data errors fail fast" half of the project's rule.</li>
 *   <li><b>The table has no side effects.</b> It only answers "which rules match this event?";
 *       {@link TriggerInterpreter} performs the effects. That keeps the table trivially testable
 *       and keeps all the engine-poking in one auditable place.</li>
 * </ul>
 */
public class TriggerTable {

    /**
     * An empty table: no rules, matches nothing. The state every character starts in.
     */
    public static final TriggerTable EMPTY = new TriggerTable(List.of());

    /**
     * Rules grouped by the event they subscribe to, so a lookup is a single map hit.
     */
    private final Map<TriggerEvent, List<CompiledRule>> byEvent = new HashMap<>();

    /**
     * Who a per-turn or cooldown count may belong to when it is not the rule owner.
     */
    private static final java.util.Set<String> SUBJECTS = java.util.Set.of("self", "target", "actor");

    /**
     * The resources this character <b>declares</b>: "charge, cap 3 points" written down once, where the
     * character is built. Empty for a character with no stacks - the ordinary state, and the reason
     * {@link #isEmpty()} keeps meaning "no rules".
     *
     * <p>Note: They are carried by the table rather than by a separate loader because they live in the character's
     * own file, next to the rules that read them, and the loader therefore has them in hand already. Registering
     * them onto the combatant is the assembly point's job ({@code CharacterFactory}), like every other
     * "which character is this" decision.
     */
    private final List<ResourceSpec> resources;

    /**
     * Every resource name this table's rules <b>read</b> - from a {@code self_resource:<NAME>} condition or
     * from a {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} effect.
     *
     * <p>Exists so the assembly point can refuse a rule that names a resource its character never declares.
     * Failing there (when the character is built) rather than at fire time is the whole point: an undeclared
     * resource answers {@code 0} to every read and swallows every gain, so the rule would otherwise be a
     * silent no-op - see {@link #resources}.
     */
    private final Set<String> referencedResources = new HashSet<>();

    @Getter
    private final int cid;

    /**
     * Builds a table from raw specs, validating everything.
     *
     * @param cid   the owning character id (informational; the engine never branches on it)
     * @param specs the raw rules, may be {@code null}
     * @throws IllegalArgumentException on an unknown event, an unwired event, an unknown condition,
     *                                  an unknown op or a missing required argument
     */
    public TriggerTable(int cid, List<TriggerSpec> specs) {
        this(cid, specs, List.of());
    }

    /**
     * The same, with the character's resource declarations.
     *
     * @param cid       the owning character id (informational; the engine never branches on it)
     * @param specs     the raw rules, may be {@code null}
     * @param resources the declared resources, may be {@code null} (= none)
     * @throws IllegalArgumentException on an unknown event, an unwired event, an unknown condition,
     *                                  an unknown op or a missing required argument
     */
    public TriggerTable(int cid, List<TriggerSpec> specs, List<ResourceSpec> resources) {
        this.cid = cid;
        this.resources = resources == null ? List.of() : List.copyOf(resources);
        Set<String> declaredIds = new HashSet<>();
        for (ResourceSpec declared : this.resources) {
            // Two declarations of one name: the second would silently win (ResourceManager.register replaces),
            // so the cap a rule is gated on could be the one that was overwritten.
            if (!declaredIds.add(declared.id())) {
                throw new IllegalArgumentException(
                        "Resource \"" + declared.id() + "\" is declared twice (cid " + cid
                                + "); the later declaration would silently replace the earlier one");
            }
        }
        if (specs == null) {
            return;
        }
        for (int index = 0; index < specs.size(); index++) {
            TriggerSpec spec = specs.get(index);
            TriggerEvent event = resolveEvent(spec);
            for (CompiledRule rule : compile(spec, event, index)) {
                collectReferencedResources(rule);
                byEvent.computeIfAbsent(event, _ -> new ArrayList<>()).add(rule);
            }
            // "when casting the Skill and the Ultimate": the SAME compiled rules under extra events (one rule listening to several events).
            // Note: Compiled once, not per event: a second compilation would mint a second rule with the same id, which validateAmendments rightly
            // refuses as ambiguous. The primary `on` is still the one every validator reads, so this only ADDS firings.
            if (spec.getOnAny() != null) {
                for (String extra : spec.getOnAny()) {
                    TriggerEvent other = TriggerEvent.fromString(extra);
                    if (other == null) {
                        throw new IllegalArgumentException(
                                "Unknown trigger event '" + extra + "' in on_any (source: " + spec.getSource() + ")");
                    }
                    if (other == event) {
                        throw new IllegalArgumentException(
                                "on_any repeats the primary event '" + extra + "' (source: " + spec.getSource()
                                        + "); it would double-fire in one trigger");
                    }
                    byEvent.computeIfAbsent(other, _ -> new ArrayList<>())
                            .addAll(byEvent.getOrDefault(event, new ArrayList<>()));
                }
            }
        }
        // Cross-rule checks last: `MODIFY_RULE` points at another rule in this same file, so the reference can only
        // be resolved once every rule has been compiled (see validateAmendments).
        validateAmendments();
    }

    /**
     * Checks every {@code MODIFY_RULE} reference: the named rule must exist <b>in this file</b>, ids must be unique,
     * and the named rule must actually state the number being raised.
     *
     * <p><b>Why the shape check is not optional.</b> "increase by 1 time" has nothing to increase on a rule that states no
     * {@code per_turn} (and {@code 0 + 1 = 1} would silently <i>impose</i> a one-per-turn cap where there was none),
     * and "base chance increased by 15%" has nothing to raise on a rule whose effects state no {@code base_chance}. Both would
     * load, fire, and change a number nobody asked about - the failure mode this vocabulary exists to prevent.
     */
    private void validateAmendments() {
        Map<String, CompiledRule> byId = new HashMap<>();
        for (List<CompiledRule> rules : byEvent.values()) {
            for (CompiledRule rule : rules) {
                if (rule.id().isEmpty()) {
                    continue;
                }
                CompiledRule clash = byId.putIfAbsent(rule.id(), rule);
                if (clash == rule) {
                    // The same rule object under a second event (`on_any`): one rule, two triggers, so a MODIFY_RULE
                    // reference to that id is not ambiguous at all.
                    continue;
                }
                if (clash != null) {
                    throw new IllegalArgumentException(
                            "Two rules in one file share the id \"" + rule.id() + "\", so a MODIFY_RULE "
                                    + "reference to it would be ambiguous (source: " + rule.source() + ")");
                }
            }
        }
        // Named counters (`ADD_STACK`) and the `*_stacks:<name>` conditions that read them: a name nobody creates
        // would be a condition that can never hold -- the silent failure this vocabulary refuses.
        Set<String> createdNames = new java.util.HashSet<>();
        Set<String> readNames = new java.util.HashSet<>();
        for (List<CompiledRule> rules : byEvent.values()) {
            for (CompiledRule rule : rules) {
                for (EffectSpec effect : rule.effects()) {
                    if (effect.getBuff() != null && !effect.getBuff().isBlank() && isNameCreating(effect.getOp())) {
                        createdNames.add(effect.getBuff().trim());
                    }
                }
                for (Condition condition : rule.conditions()) {
                    if (condition instanceof Numeric numeric && numeric.stacksName != null) {
                        readNames.add(numeric.stacksName);
                    }
                }
            }
        }
        for (String read : readNames) {
            if (!createdNames.contains(read)) {
                throw new IllegalArgumentException(
                        "A condition reads the counter \"" + read + "\", which no effect in this file creates; an "
                                + "ADD_STACK effect with \"buff\": \"" + read + "\" is what marks it, and a counter "
                                + "nobody marks can never reach its threshold (counters this file creates: "
                                + createdNames.stream().sorted().toList() + ")");
            }
        }
        for (List<CompiledRule> rules : byEvent.values()) {
            for (CompiledRule rule : rules) {
                for (EffectSpec effect : rule.effects()) {
                    String op = effect.getOp() == null ? "" : effect.getOp().trim();
                    if ("MODIFY_RULE".equalsIgnoreCase(op)) {
                        requireAmendable(rule, effect, byId);
                    } else if ("RESET_RULE_CHANCE".equalsIgnoreCase(op)) {
                        requireDecayingRule(rule, effect, byId);
                    }
                }
                // `has_shield from_rule <id>`: same kind of reference, so the same pass resolves it. It must name a
                // rule in this file AND that rule must actually create a shield - otherwise the condition can never
                // hold, which is the silent failure this vocabulary keeps refusing.
                for (Condition condition : rule.conditions()) {
                    if (condition instanceof HasShield shield && !shield.ruleId.isEmpty()) {
                        requireShieldSource(rule, shield, byId);
                    }
                }
            }
        }
    }

    /**
     * One {@code RESET_RULE_CHANCE} effect against its target: the id resolves, and that rule really has a decaying
     * probability to reset.
     *
     * <p><b>Why the shape check is not optional</b> (the same reason {@code requireAmendable} gives): a rule that does
     * not decay answers the reset by not changing - a rule that reads as if it restarts something, with nothing to see.
     * Reader: 1506 Silver Wolf LV.999's 【崩坏级伤害演示】 - 「将触发【头号补给盲盒】的固定概率重置为初始值」.
     *
     * @param holder the rule carrying the effect
     * @param effect the {@code RESET_RULE_CHANCE} effect (its {@code rule} is the reference)
     * @param byId   every named rule in this file
     */
    private static void requireDecayingRule(CompiledRule holder, EffectSpec effect, Map<String, CompiledRule> byId) {
        String target = effect.getRule() == null ? "" : effect.getRule().trim();
        CompiledRule named = byId.get(target);
        if (named == null) {
            throw new IllegalArgumentException(
                    "RESET_RULE_CHANCE names the rule \"" + target + "\", which is not in this file; a reference only "
                            + "resolves inside the same table (known ids: " + (byId.isEmpty()
                            ? "none -- no rule here states an \"id\"" : String.join(", ", byId.keySet().stream().sorted().toList()))
                            + ") (source: " + holder.source() + ")");
        }
        if (!(named.chanceDecay() > 0)) {
            throw new IllegalArgumentException(
                    "RESET_RULE_CHANCE names the rule \"" + target + "\", but that rule states no \"chance_decay\", so "
                            + "it has no decaying probability to reset and the effect would change nothing "
                            + "(source: " + holder.source() + ")");
        }
    }

    /**
     * Checks {@code has_shield from_rule <id>}: the id resolves, and the rule it names really creates a shield.
     *
     * @param holder the rule carrying the condition
     * @param shield the condition (its {@code ruleId} is the reference)
     * @param byId   every named rule in this file
     */
    private static void requireShieldSource(CompiledRule holder, HasShield shield, Map<String, CompiledRule> byId) {
        CompiledRule named = byId.get(shield.ruleId);
        if (named == null) {
            throw new IllegalArgumentException(
                    "Condition '" + shield.raw + "' asks for a shield from the rule \"" + shield.ruleId
                            + "\", which is not in this file; a reference only resolves inside the same table "
                            + "(known ids: " + (byId.isEmpty() ? "none -- no rule here states an \"id\""
                            : String.join(", ", byId.keySet().stream().sorted().toList())) + ") (source: "
                            + holder.source() + ")");
        }
        boolean makesAShield = named.effects().stream().anyMatch(
                effect -> "SHIELD".equalsIgnoreCase(effect.getOp() == null ? "" : effect.getOp().trim()));
        if (!makesAShield) {
            throw new IllegalArgumentException(
                    "Condition '" + shield.raw + "' asks for a shield from the rule \"" + shield.ruleId
                            + "\", but that rule states no SHIELD effect, so the condition could never hold "
                            + "(source: " + holder.source() + ")");
        }
    }


    /**
     * Whether an op may <b>create the name</b> a {@code *_stacks:<name>} condition reads.
     *
     * <p>Deliberately a small closed set: the ops that put a name on a buff. A typo in a {@code REMOVE_STATE} does not
     * create anything, so it cannot make a counter readable (that would be a removal with no counter to remove).
     */
    private static boolean isNameCreating(String op) {
        if (op == null) {
            return false;
        }
        TriggerOperator operator = TriggerOperator.parse(op);
        return switch (operator == null ? null : operator) {
            case ADD_STACK, APPLY_BUFF, MODIFY_ATTR, RESIST_DEBUFF, SHIELD -> true;
            case null, default -> false;
        };
    }

    /**
     * One {@code MODIFY_RULE} effect against its target: the id resolves, and the target states the number.
     *
     * @param amender the rule carrying the effect
     * @param effect  the {@code MODIFY_RULE} effect (its fields were already validated by the interpreter)
     * @param byId    every named rule in this file
     */
    private static void requireAmendable(CompiledRule amender, EffectSpec effect, Map<String, CompiledRule> byId) {
        String target = effect.getRule() == null ? "" : effect.getRule().trim();
        CompiledRule named = byId.get(target);
        if (named == null) {
            throw new IllegalArgumentException(
                    "MODIFY_RULE names the rule \"" + target + "\", which is not in this file; a reference only "
                            + "resolves inside the same table (known ids: "
                            + (byId.isEmpty() ? "none -- no rule here states an \"id\"" : String.join(", ",
                            byId.keySet().stream().sorted().toList())) + ") (source: " + amender.source() + ")");
        }
        if (effect.getAmount() != null) {
            if (named.perTurn() < 1) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE raises the per-turn limit of rule \"" + target + "\", but that rule states no "
                                + "\"per_turn\"; adding a limit to an unlimited rule would silently restrict it "
                                + "(source: " + amender.source() + ")");
            }
            return;
        }
        if (effect.getEffectMaxStacks() != null) {
            boolean statesCap = named.effects().stream().anyMatch(e -> e.getMaxStacks() != null);
            if (!statesCap) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE raises the stack cap of rule \"" + target + "\", but none of that rule's "
                                + "effects states a \"max_stacks\"; there is no cap to raise (source: "
                                + amender.source() + ")");
            }
            return;
        }
        if (effect.getEffectTurns() != null) {
            boolean statesTurns = named.effects().stream().anyMatch(e -> e.getTurns() != null);
            if (!statesTurns) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE raises the duration of rule \"" + target + "\", but none of that rule's effects "
                                + "states a \"turns\"; an effect with no duration (a `permanent` one, or an instant "
                                + "one) has no number to raise (source: " + amender.source() + ")");
            }
            return;
        }
        if (effect.getCooldownDelta() != null) {
            if (named.cooldownTurns() < 1) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE amends the cooldown of rule \"" + target + "\", but that rule states no "
                                + "\"cooldown\"; an unlimited rule has no number to lower or raise "
                                + "(source: " + amender.source() + ")");
            }
            if (named.cooldownTurns() + effect.getCooldownDelta() < 0) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE amends the cooldown of rule \"" + target + "\" by " + effect.getCooldownDelta()
                                + ", but that rule's cooldown is " + named.cooldownTurns()
                                + " turns and the result may not be negative (source: " + amender.source() + ")");
            }
            return;
        }
        if (effect.getEffectPercent() != null) {
            // A share stated by a SPELLING counts as a number to raise: the target rule may read its share from the owner's skill (`percent_from_skill_param`),
            // which is the honest way to write a value that varies with level -- demanding a literal would force a lie.
            boolean statesPercent = named.effects().stream().anyMatch(e -> e.getPercent() != null
                    || e.getPercentFromSkillParam() != null || e.getPercentFromCastParam() != null
                    || e.getPercentFromResource() != null);
            if (!statesPercent) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE raises the value of rule \"" + target + "\", but none of that rule's effects "
                                + "states a \"percent\"; there is no number to raise (source: "
                                + amender.source() + ")");
            }
            return;
        }
        if (effect.getEffectPercentFromResource() != null) {
            // Same requirement as `effect_percent`: there must be a number on the target to raise.
            boolean statesPercent = named.effects().stream().anyMatch(e -> e.getPercent() != null);
            if (!statesPercent) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE raises the value of rule \"" + target + "\", but none of that rule's effects "
                                + "states a \"percent\"; there is no number to raise (source: " + amender.source() + ")");
            }
            return;
        }
        boolean statesAChance = named.effects().stream().anyMatch(e -> e.getBaseChance() != null);
        if (!statesAChance) {
            throw new IllegalArgumentException(
                    "MODIFY_RULE raises the base chance of rule \"" + target + "\", but none of that rule's effects "
                            + "states a \"base_chance\"; an unstated chance is 100% and has no number to raise "
                            + "(source: " + amender.source() + ")");
        }
    }

    /**
     * Convenience constructor for a table with no owning character (tests, {@link #EMPTY}).
     */
    public TriggerTable(List<TriggerSpec> specs) {
        this(0, specs);
    }

    /**
     * Whether this table has no rules at all.
     */
    public boolean isEmpty() {
        return byEvent.isEmpty();
    }

    /**
     * This table's rules followed by another table's - one table with both sets of mechanics.
     *
     * <p><b>Why this exists.</b> A character's effective mechanics are not always in one file: the
     * character's own {@code resources/game/characters/<cid>.json} supplies its kit, and every relic set
     * worn in sufficient numbers supplies its own rules
     * ({@code resources/game/relic_sets/<setId>.json}). The assembly point
     * ({@code CharacterFactory}) needs one table on {@link Character#getTriggerTable()}, and the
     * alternative - teaching {@code Battle} to consult several tables - would spread the composition
     * across the hot path instead of doing it once, at build time.
     *
     * <p>Order is <b>this table first</b>, then the other's, per event, and it is preserved. Nothing
     * in the engine depends on it today (effects run in the order written <em>within</em> a rule), but
     * "the character's own rules come before the equipment's" is the order a reader expects.
     *
     * <p>Either side may be {@code null} or empty, in which case the other is returned unchanged - an
     * unequipped character therefore behaves exactly as it did before relic rules existed. Note: "Empty" here
     * means <b>no rules and no declarations</b>: a table that declares a resource but has no rules of its own
     * is not nothing, and returning the other side for it would drop the declarations (and with them every
     * {@code self_resource:} read in the merged table).
     *
     * <p><b>Resource declarations.</b> The character's own file is the only place they can come from today
     * (a relic set's file is a bare array - see {@code TriggerTables}), so the merge carries whichever side
     * has them and <b>refuses</b> a merge of two declaring tables rather than picking one: "the rules of two
     * different resources are now one table" has no reading that is obviously right, and a wrong pick would
     * leave a rule reading a cap that was never registered.
     *
     * @param other the table to append (may be {@code null})
     * @return the merged table; this instance when {@code other} is null or carries nothing
     */
    public TriggerTable plus(TriggerTable other) {
        if (other == null || other.hasNothing()) {
            return this;
        }
        if (hasNothing()) {
            return other;
        }
        if (!resources.isEmpty() && !other.resources.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot merge two trigger tables that both declare resources (cid " + cid + "): "
                            + "declarations come from the character's own file, and a merge of two sets of "
                            + "them has no defined owner");
        }
        TriggerTable merged = new TriggerTable(cid, List.of(),
                resources.isEmpty() ? other.resources : resources);
        copyRulesInto(merged);
        other.copyRulesInto(merged);
        merged.referencedResources.addAll(referencedResources);
        merged.referencedResources.addAll(other.referencedResources);
        return merged;
    }

    /**
     * Whether this table carries nothing at all - no rules and no resource declarations.
     *
     * <p>Distinct from {@link #isEmpty()} on purpose: that one answers "no rules", which is what the fire path
     * and the diagnostics want, while a merge has to treat declarations as content too.
     */
    private boolean hasNothing() {
        return isEmpty() && resources.isEmpty();
    }

    /**
     * The resources this character declares, in file order.
     */
    public List<ResourceSpec> resources() {
        return resources;
    }

    /**
     * Every resource name the table's rules read - see {@link #referencedResources}.
     */
    public Set<String> referencedResources() {
        return Set.copyOf(referencedResources);
    }

    /**
     * Records what one compiled rule reads, so the assembly point can check it against the declarations.
     *
     * <p>Read off the <b>compiled</b> rule rather than off the raw text: {@link Numeric} keeps the resource
     * name it parsed, so the check cannot drift from the parse (a name the parser refused is not "referenced",
     * it is an error, and a name it accepted is exactly the one the battle will read).
     */
    private void collectReferencedResources(CompiledRule rule) {
        for (Condition condition : rule.conditions()) {
            if (condition instanceof Numeric numeric && numeric.resource != null) {
                referencedResources.add(numeric.resource);
            }
        }
        for (EffectSpec effect : rule.effects()) {
            // `weight_steps` READS a counter ("<resource>:<threshold>:<delta>"), so an undeclared one must be refused
            // here exactly like a condition that reads it - otherwise the step silently counts nothing.
            if (effect.getWeightSteps() != null) {
                for (String step : effect.getWeightSteps()) {
                    String named = step == null ? "" : step.trim().split(":", 2)[0].trim();
                    if (!named.isEmpty()) {
                        referencedResources.add(named);
                    }
                }
            }
            if (effect.getOp() == null || effect.getResource() == null) {
                continue;
            }
            String op = effect.getOp().trim().toUpperCase(Locale.ROOT);
            if (RESOURCE_OPS.contains(op)) {
                referencedResources.add(effect.getResource().trim());
            }
        }
    }

    /**
     * Copies this table's compiled rules into another table, preserving per-event order.
     *
     * <p>Private because {@link #plus} is the only legitimate caller: going through {@code plus} keeps
     * the "validated rules only" invariant, since a {@link CompiledRule} can only be produced by the
     * constructor that validates it.
     */
    private void copyRulesInto(TriggerTable target) {
        for (Map.Entry<TriggerEvent, List<CompiledRule>> entry : byEvent.entrySet()) {
            target.byEvent.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
    }

    /**
     * How many rules subscribe to the given event.
     */
    public int ruleCount(TriggerEvent event) {
        return byEvent.getOrDefault(event, List.of()).size();
    }

    /**
     * Whether any rule in this table uses the given op (case-insensitively, as the JSON spells it).
     *
     * <p>Exists for checks that need to know <b>whether a table uses a capability at all</b> without caring
     * which rule does. Its one caller is the assembly point: a {@code SUMMON} rule is only meaningful for a
     * character that has a memosprite spec, and that question can only be asked once the character's own rules
     * and its equipment's have been merged (see {@code CharacterFactory}).
     *
     * @param op the op name as written in the JSON (e.g. {@code "SUMMON"})
     * @return {@code true} when at least one effect in the table names that op
     */
    public boolean usesOp(String op) {
        for (List<CompiledRule> rules : byEvent.values()) {
            for (CompiledRule rule : rules) {
                for (EffectSpec effect : rule.effects()) {
                    if (effect.getOp() != null && effect.getOp().trim().equalsIgnoreCase(op)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The limiter key of the rule carrying this id, or {@code null} when this table has no such id.
     *
     * <p>Note: An id is optional and a key is not -- that is why both exist (see {@link CompiledRule}), and why
     * a caller holding an id cannot clear a limit without this lookup. Seven shipped rules have no id at all.
     */

    public String keyOf(String id) {
        if (id == null) {
            return null;
        }
        for (List<CompiledRule> rules : byEvent.values()) {
            for (CompiledRule rule : rules) {
                if (id.equals(rule.id())) {
                    return rule.key();
                }
            }
        }
        return null;
    }

    /**
     * The compiled rules of one event, <b>without</b> evaluating their conditions - the list
     * {@link TriggerInterpreter#fire} walks when it wants to check each rule's conditions as it reaches it.
     *
     * <p>Note: Why the interpreter does not simply use {@link #matching}: conditions evaluated all at once cannot see what
     * an <b>earlier rule of the same event</b> just did (a counter marked on this very attack, for instance), and
     * "after every 2 times" is exactly that shape. `matching` stays the pure predicate it always was - the data-binding tests
     * and {@code ruleCount} read it - and this accessor is what lets firing differ.
     */

    public List<CompiledRule> rulesFor(TriggerEvent event) {
        return byEvent.getOrDefault(event, List.of());
    }

    /**
     * All rules matching an event and a given context.
     *
     * @param event the fired event
     * @param ctx   the context the conditions are evaluated against
     * @return the matching rules, in table order; never {@code null}
     */

    public List<CompiledRule> matching(TriggerEvent event, TriggerContext ctx) {
        List<CompiledRule> rules = byEvent.get(event);
        if (rules == null) {
            return List.of();
        }
        List<CompiledRule> hits = new ArrayList<>();
        for (CompiledRule rule : rules) {
            if (rule.matches(ctx)) {
                hits.add(rule);
            }
        }
        return hits;
    }

    private static TriggerEvent resolveEvent(TriggerSpec spec) {
        TriggerEvent event = TriggerEvent.fromString(spec.getOn());
        if (event == null) {
            throw new IllegalArgumentException(
                    "Unknown trigger event '" + spec.getOn() + "' (source: " + spec.getSource() + ")");
        }
        if (!event.isWired()) {
            throw new IllegalArgumentException(
                    "Trigger event '" + event.value() + "' is not emitted by the engine yet, so the rule "
                            + "could never fire (source: " + spec.getSource() + ")");
        }
        return event;
    }

    private static List<CompiledRule> compile(TriggerSpec spec, TriggerEvent event, int index) {
        List<Condition> conditions = new ArrayList<>();
        if (spec.getWhen() != null) {
            for (String raw : spec.getWhen()) {
                conditions.add(TriggerConditionParser.parseCondition(raw, spec));
            }
        }
        List<EffectSpec> effects = spec.getDoEffects() == null ? List.of() : spec.getDoEffects();
        if (effects.isEmpty()) {
            throw new IllegalArgumentException(
                    "Trigger on " + event.value() + " has no effects (source: " + spec.getSource() + ")");
        }
        for (EffectSpec effect : effects) {
            TriggerInterpreter.validate(effect, spec);
            // Note: The "which events can carry this op" checks live HERE, not in the interpreter's validation switch: that
            // switch sees one effect at a time and knows no event, while every rule here is compiled with its own.
            // `SUPER_BREAK` reads "the toughness reduction of this damage" off the settled instance, so DEALING_DAMAGE is the only moment it can
            // be attached to (the same rule ADD_DAMAGE follows, which the interpreter could enforce because it already
            // knew the event).
            // Note: A `damage_type` is validated HERE, where the rule's own source is available and the failure is loud at load
            // time: a misspelling that silently meant "all damage types" would be a wrong number with no symptom.
            if (effect.getDamageType() != null && !effect.getDamageType().isBlank()) {
                try {
                    com.laosun.aluminium.enums.DamageType.valueOf(
                            effect.getDamageType().trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                    throw new IllegalArgumentException(
                            "this rule names the damage type '" + effect.getDamageType()
                                    + "', which is not one the engine settles; known: "
                                    + java.util.Arrays.toString(com.laosun.aluminium.enums.DamageType.values())
                                    + " (source: " + spec.getSource() + ")");
                }
            }
            if (effect.getOp() != null && "SUPER_BREAK".equalsIgnoreCase(effect.getOp().trim())
                    && event != TriggerEvent.DEALING_DAMAGE) {
                throw new IllegalArgumentException(
                        "Op SUPER_BREAK reads the instance being settled (「本次伤害的削韧值」), so it can only be attached "
                                + "to DEALING_DAMAGE, not " + event.value() + " (source: " + spec.getSource() + ")");
            }
        }
        // "against all enemies <b>in the shocked state</b>...": each effect may name conditions its TARGETS must satisfy. Parsed
        // here, with the rule's own event, so `from_skill` and friends mean the same thing inside a filter.
        List<List<Condition>> targetFilters = new ArrayList<>();
        for (EffectSpec effect : effects) {
            List<Condition> filter = new ArrayList<>();
            if (effect.getTargetWhen() != null) {
                for (String raw : effect.getTargetWhen()) {
                    filter.add(TriggerConditionParser.parseCondition(raw, spec));
                }
            }
            targetFilters.add(List.copyOf(filter));
        }
        int minEidolon = validateMinEidolon(spec);
        return List.of(new CompiledRule(event, conditions, effects, spec.getSource(),
                ruleKey(spec, index), validateId(spec), validateCooldown(spec),
                Boolean.TRUE.equals(spec.getOncePerBattle()), validateChance(spec),
                minEidolon, validateMaxEidolon(spec, minEidolon), validatePerTurn(spec), validatePerAttack(spec),
                Boolean.TRUE.equals(spec.getOncePerWave()), List.copyOf(targetFilters),
                validatePerSubject(spec), validateOneOf(spec, effects), validateChanceDecay(spec),
                spec.getOneOfNoRepeat() == null ? "" : spec.getOneOfNoRepeat().trim(),
                Boolean.TRUE.equals(spec.getDeferUntilWave()), validateChanceRolls(spec)));
    }

    /**
     * Validates a rule's number of independent chance rolls and returns it ({@code 1} = the historical single
     * roll).
     *
     * <p>The two refusals are the shapes this vocabulary keeps out: a count below 1 is not a count, and a
     * count above 1 on a rule that states no {@code chance} says nothing at all ("roll twice for always" is
     * "roll once for always"). Both would load and behave as if the field had not been written, which is the
     * failure mode with no symptom that the rest of this class already refuses.
     *
     * <p>⚠ Package-visible only so the same-package guard can reach it ({@code ChanceRollsValidationTest}). The
     * alternative was to ship deliberately broken content in {@code resources/game/characters/} and load it, which
     * would put a permanently red rule in the corpus that every other content guard has to tolerate.
     */
    static int validateChanceRolls(TriggerSpec spec) {
        Integer rolls = spec.getChanceRolls();
        if (rolls == null) {
            return 1;
        }
        if (rolls < 1) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"chance_rolls\": " + rolls + ", but a count of rolls is 1 or more "
                            + "(omit the field for the single roll every rule has by default) "
                            + "(source: " + spec.getSource() + ")");
        }
        if (rolls > 1 && spec.getChance() == null) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"chance_rolls\": " + rolls + " but no \"chance\": there is no "
                            + "probability to roll twice. Rolling an always-hit rule twice changes nothing "
                            + "(source: " + spec.getSource() + ")");
        }
        return rolls;
    }

    /**
     * Whether this rule picks ONE of its effects at random - and the combinations that must be refused with it.
     *
     * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - 「随机触发 1 种效果」 - and light cone 21032's 「随机生效 1 个效果」.
     *
     * <p>⚠ The pool is the effects that are <b>not</b> marked {@code always} (reader: the same box's own 90%, which is
     * 「造成…伤害，<b>并</b>随机触发 1 种效果」 - it runs every time and is not one of the three). So the count below is the
     * POOL's, not the rule's: a box with one constant effect and two picks is a pick of two.
     *
     * <p>Refused, each being the "two readings" or "says nothing" shape this vocabulary will not carry:
     * <ul>
     *   <li>{@code always} on an effect of a rule that states no {@code one_of} - it marks nothing, and reads like a
     *       forgotten field;
     *   <li>a pool of fewer than two effects - "pick one of one" states nothing, and reads exactly like a rule that
     *       forgot the field;
     *   <li>a POOL effect carrying its own {@code min_eidolon} - the two filters then compose two ways (rank first, or
     *       pick first and possibly land on an effect the rank excludes, i.e. a firing that does nothing). An
     *       {@code always} effect may carry one: nothing picks it, so there is only one reading.</li>
     * </ul>
     */
    private static boolean validateOneOf(TriggerSpec spec, List<EffectSpec> effects) {
        String memory = spec.getOneOfNoRepeat() == null ? null : spec.getOneOfNoRepeat().trim();
        if (!Boolean.TRUE.equals(spec.getOneOf())) {
            if (memory != null && !memory.isEmpty()) {
                throw new IllegalArgumentException(
                        "Trigger rule states \"one_of_no_repeat\" but not \"one_of\": there is no pick for a memory to "
                                + "make a difference to (source: " + spec.getSource() + ")");
            }
            for (EffectSpec effect : effects) {
                if (Boolean.TRUE.equals(effect.getAlways())) {
                    throw new IllegalArgumentException(
                            "An effect states \"always\" but its rule does not state \"one_of\": there is no pick for "
                                    + "it to stay out of, so the field says nothing (source: " + spec.getSource() + ")");
                }
                if (effect.getWeight() != null || effect.getWeightSteps() != null) {
                    throw new IllegalArgumentException(
                            "An effect states a pick \"weight\" / \"weight_steps\" but its rule does not state "
                                    + "\"one_of\": there is no pick for a weight to be a share of "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            return false;
        }
        if (memory != null && memory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Trigger rule states \"one_of_no_repeat\" with a blank name, but the name IS the memory two rules "
                            + "share - write the sentence's own name, or omit the field for a uniform pick "
                            + "(source: " + spec.getSource() + ")");
        }
        List<EffectSpec> pool = new ArrayList<>();
        for (EffectSpec effect : effects) {
            if (!Boolean.TRUE.equals(effect.getAlways())) {
                pool.add(effect);
            }
        }
        if (pool.size() < 2) {
            throw new IllegalArgumentException(
                    "Trigger rule states \"one_of\" but has " + pool.size() + " effect(s) to pick from"
                            + (pool.size() == effects.size() ? "" : " (the other " + (effects.size() - pool.size())
                            + " are marked \"always\")")
                            + "; \"pick one of one\" states nothing (source: " + spec.getSource() + ")");
        }
        for (EffectSpec effect : pool) {
            if (effect.getMinEidolon() != null) {
                throw new IllegalArgumentException(
                        "Trigger rule states \"one_of\" and an effect states its own \"min_eidolon\": the two compose "
                                + "two ways - filter the ranks and then pick, or pick and possibly land on an excluded "
                                + "effect, which is a firing that does nothing (source: " + spec.getSource() + ")");
            }
        }
        return true;
    }

    /**
     * Validates {@code per_subject} and returns it ({@code ""} = the rule owner, the shipped default).
     *
     * <p>Validated at load time like the other limits: an unknown name would fall back to the owner and
     * count the wrong thing -- a wrong answer with no symptom, and the reason the family needs the field
     * at all (readers 1305, 120, 1403).
     */
    private static String validatePerSubject(TriggerSpec spec) {
        String subject = spec.getPerSubject();
        if (subject == null || subject.isBlank()) {
            return "";
        }
        String trimmed = subject.trim();
        if (!SUBJECTS.contains(trimmed)) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"per_subject\": " + trimmed + ", which is not one of " + SUBJECTS
                            + "; omit the field to count the rule owner (source: " + spec.getSource() + ")");
        }
        return trimmed;
    }

    /**
     * A rule's optional <b>name</b>, trimmed, or {@code ""} when it states none.
     *
     * <p>Only a name that is present is meaningful: an unnamed rule can still be referred to by nothing, which is the
     * ordinary case and the reason this is not an error. Note: The name is scoped to its file (uniqueness and
     * resolvability are checked in {@link #validateAmendments()}).
     */

    private static String validateId(TriggerSpec spec) {
        String id = spec.getId();
        return id == null ? "" : id.trim();
    }

    /**
     * Validates an Eidolon gate and returns the rank it needs ({@code 0} = ungated).
     *
     * <p>Validated at load time, like the other limits: {@code min_eidolon: 0} reads like "no Eidolon needed"
     * (which is spelled by omitting the field) and a rank above {@code Constant.EIDOLON_MAX_RANK} can never be
     * reached, so the rule would be silently dead - the failure mode this vocabulary exists to prevent.
     */
    private static int validateMinEidolon(TriggerSpec spec) {
        Integer rank = spec.getMinEidolon();
        if (rank == null) {
            return 0;
        }
        if (rank < 1 || rank > Constant.EIDOLON_MAX_RANK) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"min_eidolon\": " + rank + ", but Eidolon ranks are 1-"
                            + Constant.EIDOLON_MAX_RANK + "; omit the field entirely for a rule that needs none "
                            + "(source: " + spec.getSource() + ")");
        }
        return rank;
    }

    /**
     * Validates the OTHER end of the Eidolon gate and returns it ({@code 0} = no ceiling).
     *
     * <p>Validated here for the same reason as the floor: a ceiling below the floor would be a rule that can never
     * fire, which is the "silently dead" shape this vocabulary refuses rather than ships.
     */
    private static int validateMaxEidolon(TriggerSpec spec, int minEidolon) {
        Integer rank = spec.getMaxEidolon();
        if (rank == null) {
            return 0;
        }
        if (rank < 1 || rank > Constant.EIDOLON_MAX_RANK) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"max_eidolon\": " + rank + ", but Eidolon ranks are 1-"
                            + Constant.EIDOLON_MAX_RANK + "; omit the field for a rule with no ceiling "
                            + "(source: " + spec.getSource() + ")");
        }
        if (minEidolon > 0 && rank < minEidolon) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"min_eidolon\": " + minEidolon + " and \"max_eidolon\": " + rank
                            + ", so it can never fire; the ceiling must be at or above the floor "
                            + "(source: " + spec.getSource() + ")");
        }
        return rank;
    }

    /**
     * Validates a rule's probability and returns it as a fraction of 1 ({@code 1.0} = always).
     *
     * <p>Validated at load time for the same reason as the cooldown: {@code chance: 0} would load and never fire,
     * and a value above 1 reads like a percentage when the field is a fraction - the two mistakes look like a
     * working rule from the outside.
     */
    private static double validateChance(TriggerSpec spec) {
        Double chance = spec.getChance();
        if (chance == null) {
            return 1.0;
        }
        if (!(chance > 0) || chance > 1) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"chance\": " + chance + ", but a probability is a fraction of 1 "
                            + "(0.35 = 35%); omit the field entirely for \"always\" "
                            + "(source: " + spec.getSource() + ")");
        }
        return chance;
    }

    /**
     * Validates a rule's decaying probability and returns the factor ({@code 0} = the rule does not decay).
     *
     * <p>The two refusals are the "says nothing" / "can never happen again" shapes this vocabulary keeps out:
     * {@code chance_decay: 1} is not a decay at all, and a factor at or below 0 would leave a rule that can never
     * trigger again after its first success. A decay on a rule that states no {@code chance} has nothing to decay.
     */
    private static double validateChanceDecay(TriggerSpec spec) {
        Double decay = spec.getChanceDecay();
        if (decay == null) {
            return 0;
        }
        if (spec.getChance() == null) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"chance_decay\": " + decay + " but no \"chance\": there is no initial "
                            + "probability for it to decay from (source: " + spec.getSource() + ")");
        }
        if (!(decay > 0) || decay >= 1) {
            throw new IllegalArgumentException(
                    "Trigger rule has \"chance_decay\": " + decay + ", which is not a decay: it must be a fraction "
                            + "strictly between 0 and 1 (0.2 = \"the next time it triggers, the chance is 20% of what "
                            + "it was\") (source: " + spec.getSource() + ")");
        }
        return decay;
    }

    /**
     * A rule's stable identity, used by the per-combatant firing limits
     * ({@code CanHit.isTriggerReady} / {@code startTriggerCooldown}).
     *
     * <p><b>Why the source is not enough on its own.</b> A file may ship several rules that share one
     * provenance string - {@code characters/1403.json} has exactly that, because its two rules come from
     * the same trace ("A small stone beside the fork in the road?" states both the battle-start energy and the per-target
     * energy). Keyed by source alone, a cooldown on one of them would silently block the other: a wrong
     * answer with nothing to see. The index within the file makes the key unique per rule and still
     * readable in a test or a debugger.
     *
     * <p>Stable across battles by construction: the table is compiled once and cached per cid, and the
     * counters live on the combatant, not here.
     *
     * @param spec  the raw rule
     * @param index its position in the file
     * @return the key, e.g. {@code "1403 缇宝 trace 1403103#1"}
     */
    private static String ruleKey(TriggerSpec spec, int index) {
        return (spec.getSource() == null ? "rule" : spec.getSource()) + "#" + index;
    }

    /**
     * Validates a rule's firing limit at load time and returns its cooldown in the owner's turns.
     *
     * <p>The whole point of validating here is the same as for events and ops: a limit that cannot mean
     * anything must fail loudly while the file is read, not quietly behave like "no limit". {@code 0} is
     * exactly that trap - it reads like "no cooldown", which is already spelled by omitting the field.
     *
     * @param spec the raw rule
     * @return the cooldown in turns, or {@code 0} when the rule is unlimited
     * @throws IllegalArgumentException when the cooldown is below 1, or both limits are stated
     */
    private static int validateCooldown(TriggerSpec spec) {
        boolean oncePerBattle = Boolean.TRUE.equals(spec.getOncePerBattle());
        Integer cooldown = spec.getCooldown();
        if (cooldown != null && oncePerBattle) {
            throw new IllegalArgumentException(
                    "Trigger rule states both `cooldown` and `once_per_battle`, which are two different "
                            + "limits (one comes back, the other never does) -- keep one "
                            + "(source: " + spec.getSource() + ")");
        }
        if (cooldown == null) {
            return 0;
        }
        if (cooldown < 1) {
            throw new IllegalArgumentException(
                    "Trigger rule has cooldown " + cooldown + ", but a cooldown is counted in whole turns "
                            + "and must be >= 1; omit the field entirely for \"no limit\" "
                            + "(source: " + spec.getSource() + ")");
        }
        return cooldown;
    }

    /**
     * The per-ATTACK cap: {@code once_per_attack: true} means one, {@code per_attack: N} means N.
     *
     * <p>Note: Both at once is refused rather than resolved: they are two spellings of one dimension, and picking one
     * silently would make the other a lie.
     */
    private static int validatePerAttack(TriggerSpec spec) {
        boolean once = Boolean.TRUE.equals(spec.getOncePerAttack());
        Integer stated = spec.getPerAttack();
        if (once && stated != null) {
            throw new IllegalArgumentException("Trigger rule states BOTH \"once_per_attack\" and \"per_attack\": "
                    + "they are the same cap, so say one (source: " + spec.getSource() + ")");
        }
        if (stated != null && stated < 1) {
            throw new IllegalArgumentException("Trigger rule has per_attack " + stated
                    + ", but a per-attack limit is a count of firings (source: " + spec.getSource() + ")");
        }
        return once ? 1 : (stated == null ? 0 : stated);
    }

    /**
     * Validates a per-turn cap ("this effect can trigger N times per turn") and returns it ({@code 0} = no cap).
     *
     * <p>Two load-time rejections, both of them things that would otherwise read as a working rule:
     * <ul>
     *   <li>{@code per_turn: 0} (or negative) - it reads like "no per-turn limit", which is already spelled by
     *       omitting the field, and a cap of 0 would be a rule that can never fire at all;</li>
     *   <li>{@code per_turn} together with {@code cooldown} - "at most N per turn, and at least M turns apart"
     *       has two readings that disagree once {@code N > 1} (does the cooldown start on the first firing or
     *       the last?), and the engine would have to pick one silently. {@code cooldown: 1} is exactly the
     *       {@code N = 1} case of this field, so the pair is never the only way to write something.</li>
     * </ul>
     * {@code once_per_battle} <b>may</b> be combined with it: the two are cumulative ("twice per turn, and only
     * once in the whole battle"), which is a reading that cannot be misread.
     */

    private static int validatePerTurn(TriggerSpec spec) {
        Integer perTurn = spec.getPerTurn();
        if (perTurn == null) {
            return 0;
        }
        if (perTurn < 1) {
            throw new IllegalArgumentException(
                    "Trigger rule has per_turn " + perTurn + ", but a per-turn limit is a count of firings "
                            + "and must be >= 1; omit the field entirely for \"no per-turn limit\" "
                            + "(source: " + spec.getSource() + ")");
        }
        if (spec.getCooldown() != null) {
            throw new IllegalArgumentException(
                    "Trigger rule states both `per_turn` and `cooldown`. `cooldown: 1` is the per_turn: 1 case "
                            + "of the same limit, and for any larger per_turn the two limits would each have to "
                            + "start counting at a different firing -- pick the one the text actually states "
                            + "(source: " + spec.getSource() + ")");
        }
        return perTurn;
    }

    // ==================================================================
    // Condition DSL
    //
    // Deliberately small: the goal is that a reader understands a rule without learning a language.
    //
    //   self                  the actor is me (shorthand for "actor == self")
    //   actor == self         I acted
    //   actor != self         someone else on my side acted      <- Robin's "after an ally attacks"
    //   target == self        it happened to me                  <- Clara's "after I am hit"
    //   target != self        it happened to someone else        <- a healer watching a teammate
    //   actor == summon       MY OWN summon acted                <- relic 123's "when the wearer's memosprite attacks"
    //   target == summon      it happened to my own summon       <- "after the wearer's memosprite is attacked"
    //   hit_count > 0         the attack connected with at least one target
    //   hit_count == 2        exact hit count
    //   hp_percent <= 0.5     the owner's own HP is at or below half   <- set 106's "at the beginning
    //                                                                     of the turn, if the wearer's
    //                                                                     HP percentage is <= 50%"
    //   target_debuff_count >= 3  the event's subject carries 3 debuffs <- Silver Wolf's "if the enemy has
    //                                                                      >= 3 debuffs, the RES shred is
    //                                                                      reduced further"
    //   self_attr:SPEED >= 145    one of MY OWN attribute values      <- the planar ornament 2-pieces'
    //                                                                     "when the wearer's speed is >= 145" shape
    //   self_summon_count >= 1    I have a summon of my own on the field <- the memosprite family's
    //                                                                     "while the memosprite is out" / "while a target summoned by the wearer exists"
    //   self has_state <name>   I am in the named state concerto (协奏)   <- Robin's 即兴装饰 "while in the [concerto] state"
    //   target has_state <name> it happened to someone in that state     <- Kafka's "an enemy target in the shocked state"
    //
    // `actor` is who caused the event; `target` is what it happened to. WATCH OUT: when I am hit,
    // the actor is the attacker, so "I was hit" is `target == self`, NOT `self`.
    //
    // Either side may hold the literal ("0 < hit_count" works too). Unknown variables are rejected
    // at load time rather than silently evaluating to false forever.
    //
    // `hp_percent` is read from the OWNER (the character whose table fired), not from the event --
    // it is a fact about me, which is why no event has to carry it. It is a fraction (0.5 = 50%),
    // matching the game text's own placeholder (`#1[i]%` with param 0.5).
    //
    // `self_attr:<ATTRIBUTE>` is the same idea, generalised: any attribute in AttributeType, read off the
    // owner. Note: The literal is in the ATTRIBUTE'S OWN UNITS, which differ by kind: flat attributes are
    // absolute (SPEED >= 145) while every ratio attribute is a fraction (CRIT_CHANCE >= 0., i.e. 0%,
    // BREAKING_EFFECT >= 1.5, i.e. 150%). Writing the percentage as 0 would compile, load, and simply never
    // fire -- see SelfAttributeConditionTest, which pins both scales.
    // ==================================================================


    /**
     * {@code summon_attr:<ATTRIBUTE>} -- the rule owner MEMOSPRITE's own attribute (Demiurge (德谬歌)), as a magnitude: "等同于德谬歌生命上限的 #1%"
     * (1415 memosprite skill 10, data slot 13). The sibling of {@link TriggerConditionParser#SELF_ATTR_PREFIX}, one subject further out.
     */
    static final String SUMMON_ATTR_PREFIX = "summon_attr:";

    /**
     * {@code actor_attr:<ATTRIBUTE>} -- the ACTOR's own attribute, as a magnitude: "提高数值等同于德谬歌生命上限的 #1%", where Demiurge (德谬歌) is the unit
     * casting (1415 memosprite skill 10). The sibling of {@link TriggerConditionParser#SELF_ATTR_PREFIX} (the owner) and {@link #SUMMON_ATTR_PREFIX} (the owner's
     * memosprite), one subject across: this one is whoever the event is about.
     */
    static final String ACTOR_ATTR_PREFIX = "actor_attr:";

    /**
     * {@code holder_attr:<STATE>:<ATTRIBUTE>} -- the own attribute of the unit <b>holding a state</b>, as a magnitude.
     *
     * <p>The fourth subject in this family, and the one the three above cannot name: "该次欢愉伤害使用【美学底本】的属性值计算"
     * (1503 Pearl (真珠)'s enhanced basic and her Eidolon 6) states that the instance's magnitude is read off the ally her
     * ultimate marked as the Aesthetic Archetype (美学底本) -- a unit chosen in combat, not a slot in the roster, so neither
     * {@link TriggerConditionParser#SELF_ATTR_PREFIX} (the owner), {@link #SUMMON_ATTR_PREFIX} (the owner's memosprite) nor {@link #ACTOR_ATTR_PREFIX}
     * (whoever the event is about) can reach it.
     *
     * <p>It is the magnitude-keeping counterpart of the {@code holder_of:<STATE>} <b>target</b> selector
     * ({@link TriggerInterpreter}), resolved through the same lookup - so "who holds it" has one answer in the engine
     * whether the sentence is about where an effect lands or about which number it reads.
     *
     * <p>The state name can contain a colon, so the split is on the LAST colon. Note: the attribute is mandatory here
     * (unlike the target selector, where the holder itself is the whole answer): "use [美学底本]'s stats" does not say
     * which stat, and defaulting to one would silently invent the game's own choice.
     */
    static final String HOLDER_ATTR_PREFIX = "holder_attr:";

    /**
     * {@code cast_skill_param:<index>} -- a parameter of the skill that produced the event, at its CURRENT level: "等同于德谬歌生命上限的 #1%"
     * (1415 memosprite skill 10). The damage path reads the same row through {@code multiplierOf}.
     */
    static final String CAST_SKILL_PARAM_PREFIX = "cast_skill_param:";

    /**
     * {@code skill_param:<SKILLTYPE>:<index>} -- a parameter of one of the RULE OWNER's OWN skills, at its current level.
     *
     * <p>Its reader is 1403 Tribbie (缇宝)'s ultimate, whose zone rider deals "等同于缇宝 #3% 生命上限" damage on somebody else's attack. `cast_skill_param:` reads the skill
     * that PRODUCED the event (the attack), which is the wrong one; this names the slot instead.
     */
    static final String SKILL_PARAM_PREFIX = "skill_param:";


    /**
     * The effect-side spelling for "how many targets <b>this cast really applied</b> that state to"
     * ("终结技每冻结1个目标，为三月七恢复6点能量") - {@code "scale": "cast_applied:冻结"}.
     *
     * <p>It sits beside {@link TriggerConditionParser#SELF_ATTR_PREFIX} for the same reason that one exists: the content, the interpreter's
     * resolver and the loader's validator all have to spell one prefix, and a second literal would be able to drift
     * from this one. Note: Unlike {@code self_attr:} this is <b>not</b> a condition variable - a condition asks whether
     * something is true about a unit, while this is a derived <i>magnitude</i> (a count) used by an amount, and only
     * inside a cast's own events (the loader refuses it elsewhere).
     */
    static final String CAST_APPLIED_PREFIX = "cast_applied:";


    /**
     * The ops whose {@code "resource"} argument names a resource the character must declare.
     *
     * <p>Used only to collect {@link #referencedResources}; the ops themselves were validated long before this
     * (they refuse a blank {@code "resource"} at load time).
     */
    private static final Set<String> RESOURCE_OPS = Set.of("GAIN_RESOURCE", "SPEND_RESOURCE");


    /**
     * A validated rule: the event it listens for, its conditions, its effects, and how often it may fire.
     *
     * @param event          the subscribed event
     * @param conditions     all must hold
     * @param effects        run in order
     * @param source         where the rule came from, propagated into error messages
     * @param key            stable identity for the per-combatant firing limits (source + position)
     * @param id             the rule's optional name ({@code ""} when it states none), the handle a
     *                       {@code MODIFY_RULE} effect points at
     * @param cooldownTurns  the owner's turns between two firings ({@code 0} = unlimited)
     * @param oncePerBattle  {@code true} = at most one firing per battle
     * @param chance         the probability of firing at all, as a fraction of 1 ({@code 1.0} = always)
     * @param minEidolon     the Eidolon rank the owner needs ({@code 0} = ungated)
     * @param chanceDecay    what the {@code chance} is multiplied by after a successful trigger
     *                       ({@code 0} = the probability does not decay; see {@link TriggerSpec#getChanceDecay()})
     * @param oneOfNoRepeat  the memory this rule's pick is filed under ({@code ""} = no memory, a uniform pick; two rules
     *                       stating the same name share it - see {@link TriggerSpec#getOneOfNoRepeat()})
     * @param deferUntilWave {@code true} = fired with every opponent down, this rule is held until a wave brings enemies
     *                       in ({@link TriggerSpec#getDeferUntilWave()})
     */
    public record CompiledRule(TriggerEvent event, List<Condition> conditions,
                               List<EffectSpec> effects, String source, String key, String id,
                               int cooldownTurns, boolean oncePerBattle, double chance, int minEidolon,
                               int maxEidolon,
                               int perTurn, int perAttack, boolean oncePerWave,
                               List<List<Condition>> effectTargetFilters, String perSubject,
                               boolean oneOf, double chanceDecay, String oneOfNoRepeat, boolean deferUntilWave,
                               /*
                                * How many independent rolls of {@link #chance} one firing makes - 1 unless the
                                * rule states "额外增加 N 次…判定". See {@code TriggerSpec.chanceRolls} for why
                                * the count lives on the rule and not on a second, identical rule.
                                */
                               int chanceRolls) {

        /**
         * The per-target conditions of one effect ({@code target_when}), by that effect's index in {@link #effects}.
         *
         * <p>Parsed at load time - a misspelled condition is refused rather than ignored - and kept <b>per effect</b>,
         * because two effects of one rule may filter differently.
         *
         * <p>Also the vehicle for a condition that is evaluated <b>later than the firing</b>: {@code
         * REGISTER_HEAL_BONUS} hands this list to the unit so it can be asked at <b>heal time</b>, because "when I heal
         * a target below 30% HP" cannot be answered while the effect runs -- the recipient is not known yet. The list
         * is already parsed, so nothing new has to be threaded to the interpreter to make that possible.
         */
        public List<Condition> targetFilterAt(int index) {
            if (effectTargetFilters == null || index < 0 || index >= effectTargetFilters.size()) {
                return List.of();
            }
            List<Condition> filter = effectTargetFilters.get(index);
            return filter == null ? List.of() : filter;
        }

        /**
         * Whether this rule limits how often it may fire at all.
         *
         * <p>An unlimited rule never touches the owner's limit counters, so adding this vocabulary
         * cannot change the behaviour of any rule that does not use it.
         */
        public boolean isLimited() {
            return cooldownTurns > 0 || oncePerBattle || perTurn > 0 || perAttack > 0 || oncePerWave;
        }

        boolean matches(TriggerContext ctx) {
            for (Condition condition : conditions) {
                if (!condition.test(ctx)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The facts a condition can look at.
     *
     * <p>Deliberately tiny: only values every event can supply. Anything richer belongs on the event
     * object, not here.
     *
     * <p><b>{@code actor} vs {@code target}.</b> {@code actor} is who <i>caused</i> the event;
     * {@code target} is what it <i>happened to</i>. Both are needed, and conflating them is the
     * easiest mistake here: "after an ally attacks" is {@code actor != self}, while "after I am hit"
     * is {@code target == self} - and when I am hit, the actor is the <b>attacker</b>, not me.
     *
     * <p><b>{@code battle}, and why a context carries one.</b> Some questions are about the
     * <b>field</b> rather than about the event: "忆灵在场时" ("while my Memosprite (忆灵) is out") asks what
     * units exist, and "我方全体" has to name them as effect targets. No event can carry that, because
     * it is not a fact about what just happened - it is a fact about the battlefield, and the battlefield
     * is {@link Battle}. It is here for exactly those two questions and nothing else: a condition must
     * not use it to reach into engine state (that is what ops are for). A context built by hand without a
     * battle (as several tests do) makes any such condition <b>fail</b>, the same way a missing party
     * does - never silently pass.
     *
     * @param subject    the "who": owner, actor and event subject
     * @param eventFacts the event-scoped facts: hit count, amount, damage instance and cast category
     * @param battle   the battle in progress, for questions about the field (may be {@code null} in a
     *                 hand-built context, which makes those conditions fail rather than guess)
     * @param ruleScope the rule id and the effect's per-target filter
     */
    public record TriggerContext(TriggerSubject subject, EventFacts eventFacts, Battle battle,
                                 RuleScope ruleScope, CastFacts castFacts) {

        /**
         * The same context for an event that carries no cast category - i.e. the common case.
         */
        public TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,
                              Damage damage, Battle battle, SkillCategory fromCast) {
            this(new TriggerSubject(owner, actor, target), new EventFacts(hitCount, amount, damage, fromCast),
                    battle, RuleScope.EMPTY, CastFacts.EMPTY);
        }

        /**
         * @return the character this table belongs to ("self")
         */
        public CanHit owner() {
            return subject.owner();
        }

        /**
         * @return who caused the event (may be {@code null})
         */
        public CanHit actor() {
            return subject.actor();
        }

        /**
         * @return the event's subject (may be {@code null})
         */
        public CanHit target() {
            return subject.target();
        }

        /**
         * @return how many targets an attack connected with
         */
        public int hitCount() {
            return eventFacts.hitCount();
        }

        /**
         * @return the event's magnitude
         */
        public double amount() {
            return eventFacts.amount();
        }

        /**
         * @return the damage instance being settled, or {@code null}
         */
        public Damage damage() {
            return eventFacts.damage();
        }

        /**
         * @return the cast category that produced this event, or {@code null}
         */
        public SkillCategory fromCast() {
            return eventFacts.fromCast();
        }

        /**
         * @return the id of the rule being applied ({@code ""} when it states none)
         */
        public String ruleId() {
            return ruleScope.ruleId();
        }

        /**
         * @return the effect's per-target conditions
         */
        public List<Condition> targetFilter() {
            return ruleScope.targetFilter();
        }

        /**
         * The same context carrying a complete cast-facts value.
         *
         * <p>Use this instead of chaining {@link #withSkillId}/{@link #withWeakHitCount}/
         * {@link #withAttackHitTargets} when all three are known at once: each of those rebuilds the whole
         * record, so the chain copied it three times.
         *
         * @param facts the cast facts
         * @return the same context with those facts
         */
        public TriggerContext withCastFacts(CastFacts facts) {
            return new TriggerContext(subject, eventFacts, battle, ruleScope, facts);
        }

        /**
         * @return the skill row that produced this event (0 for a non-cast event)
         */
        public int skillId() {
            return castFacts.skillId();
        }

        /**
         * @return how many hit targets shared this attack's weakness
         */
        public int weakHitCount() {
            return castFacts.weakHitCount();
        }

        /**
         * @return the targets this attack hit
         */
        public List<CanHit> attackHitTargets() {
            return castFacts.attackHitTargets();
        }

        /**
         * The same context, as the rule under it: the one caller that knows which rule is firing.
         *
         * <p><b>Why a copy rather than a mutable field.</b> A rule can fire inside another rule's effect (a counter
         * that strikes back), so "the rule being applied" is a per-firing fact, exactly like the rest of the context
         * - a shared mutable field would report the inner rule to the outer one's remaining effects.
         */
        public TriggerContext withRule(String id) {
            return new TriggerContext(subject, eventFacts, battle, ruleScope.withRuleId(id), castFacts);
        }

        /**
         * The same context, saying <b>which data row</b> of a skill produced this event.
         *
         * <p>Note: It is the row, not the slot: `Skill.getSkillSlot()` is what the data tables are indexed by, so an enhanced
         * attack (a row of its own, e.g. 1111's [直冲碎天拳] = 111108) is distinguishable from the ordinary basic attack it
         * replaces - which is exactly what "强化普攻命中…" asks. A shared mutable field would leak between nested
         * firings, so this is a copy like {@link #withRule(String)}.
         */
        public TriggerContext withSkillId(int id) {
            return new TriggerContext(subject, eventFacts, battle, ruleScope, castFacts.withSkillId(id));
        }

        /**
         * The same context for an event that carries no cast category - i.e. the common case.
         */
        public TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,
                              Damage damage, Battle battle) {
            this(new TriggerSubject(owner, actor, target), new EventFacts(hitCount, amount, damage, null),
                    battle, RuleScope.EMPTY, CastFacts.EMPTY);
        }

        /**
         * The context of an event that carries no damage instance - i.e. every event but
         * {@link TriggerEvent#DEALING_DAMAGE}.
         *
         * <p>Leaves {@code battle} null; use the seven-argument constructor when a condition may ask
         * about the field. Hand-built contexts are for unit tests of the condition vocabulary, where
         * "there is no battlefield" is the honest answer and a field question therefore fails.
         */
        public TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount) {
            this(owner, actor, target, hitCount, amount, null, null);
        }

        /**
         * The same context carrying how many hit targets shared the attack's weakness (cone 21040).
         *
         * <p>Note: A COPY helper rather than a wider constructor on purpose: the cast events are built by chaining these
         * ({@code new TriggerContext(...).withSkillId(...)}), so a value that only the canonical constructor knows is
         * silently dropped by every chain that starts from a compact one -- measured, and the reason this exists.
         */
        public TriggerContext withWeakHitCount(int count) {
            return new TriggerContext(subject, eventFacts, battle, ruleScope,
                    castFacts.withWeakHitCount(count));
        }

        /**
         * The same context carrying the targets this attack hit (cone 21029).
         *
         * <p>Note: This helper CARRIES {@code weakHitCount} along rather than resetting it: every helper rebuilds the
         * whole record, so one that resets a field it does not own would make the firing ORDER significant.
         */
        public TriggerContext withAttackHitTargets(List<CanHit> targets) {
            return new TriggerContext(subject, eventFacts, battle, ruleScope,
                    castFacts.withAttackHitTargets(targets));
        }

        /**
         * The same context with this firing's <b>per-target filter</b> (an effect's {@code target_when}).
         *
         * <p>Set by the interpreter once per effect and read only by {@code resolveTargets} - that being the single
         * place that decides which units an effect reaches, no op had to learn this vocabulary.
         */
        public TriggerContext withTargetFilter(List<Condition> filter) {
            return new TriggerContext(subject, eventFacts, battle, ruleScope.withTargetFilter(filter), castFacts);
        }

        /**
         * The same context with a different subject and <b>no filter</b>: the context a per-target condition is tested
         * in (its {@code target} is the candidate). Note: The filter is dropped deliberately - asking a candidate about the
         * filter that is asking about it would recurse.
         */
        public TriggerContext withSubject(CanHit candidate) {
            return new TriggerContext(new TriggerSubject(subject.owner(), subject.actor(), candidate),
                    eventFacts, battle, new RuleScope(ruleScope.ruleId(), List.of()),
                    castFacts.withSkillId(0).withAttackHitTargets(List.of()));
        }

        /**
         * Whether {@code candidate} passes the per-target conditions (an empty filter admits everything).
         */
        public boolean passesTargetFilter(CanHit candidate) {
            for (Condition condition : ruleScope.targetFilter()) {
                if (!condition.test(withSubject(candidate))) {
                    return false;
                }
            }
            return true;
        }

        public static TriggerContext of(CanHit owner, CanHit actor) {
            return new TriggerContext(new TriggerSubject(owner, actor, null), EventFacts.EMPTY, null,
                    RuleScope.EMPTY, CastFacts.EMPTY);
        }
    }

    /**
     * One parsed condition from the {@code when} list.
     */
    public interface Condition {

        /**
         * Evaluates the condition.
         */
        boolean test(TriggerContext ctx);

        /**
         * The original text, for error messages and debugging.
         */
        String source();
    }

    /**
     * A condition that asks a question <b>about a party</b> ({@code self} / {@code actor} / {@code target}), and
     * whose answer is therefore "no" when that party does not exist for this event.
     *
     * <p>It exists so that {@code !} cannot invert "cannot read it" into "matches": {@link Negated} asks this
     * interface for the party first and fails the condition when there is none, which is the same guarantee the
     * positive spelling gives (a rule must never match because a party was missing).
     */
    interface PartyCondition extends Condition {
        /**
         * The party this condition reads, or {@code null} when it does not exist for this event.
         */
        CanHit partyOf(TriggerContext ctx);
    }


    /**
     * Shield test: {@code target has_shield} / {@code self has_shield}.
     *
     * <p><b>Why the condition DSL needs it.</b> March 7th (三月七)'s Talent is "after one of our targets <b>holding a shield</b> is attacked by an enemy target, 三月七
     * immediately launches a counter at the attacker" - the trigger is about a shielded ally, and the engine's shield is a plain number on
     * the combatant ({@code CanHit.getShield()}) that no condition could ask about. 13 of the 9character documents
     * say "while shielded" somewhere (Fu Xuan (符玄) / Aventurine (砂金) / Gepard (杰帕德) and the shield family), which makes it the second-largest hole
     * after the resource count.
     *
     * <p>Note: <b>"Has a shield" means the value is above 0</b>, not "a shield was granted at some point": a shield
     * that has been used up is gone, and a clause gated on "持有护盾的" must stop matching then. That is also why
     * it reads the <b>live</b> value rather than asking the buff manager for a {@code ShieldBuff} - the two agree
     * while a timed shield is up (the buff installs it), and the value is the one the damage path actually drains.
     *
     * <p>Note: <b>The origin is askable too</b> ({@code has_shield from_rule <id>}): "战技提供的护盾" names the
     * ability, not just the giver, and March 7th (三月七) has two shields of her own - so the shield records the rule that created it
     * ({@code CanHit.getShieldRuleId()}) and this condition can require it. Note: A raw grant states no rule, so a
     * {@code from_rule} question correctly answers "no" for it.
     */
    static final class HasShield implements Condition, PartyCondition {
        private final String subject;
        final String ruleId;
        final String raw;

        HasShield(String subject, String ruleId, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.ruleId = ruleId == null ? "" : ruleId;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            if (who == null || who.getShield() <= 0) {
                return false;
            }
            // No qualifier: any living shield counts. With one: the shield must have come from THAT rule, which is
            // what makes "战技提供的" different from "三月七给的" (see CanHit.getShieldRuleId).
            return ruleId.isEmpty() || ruleId.equals(who.getShieldRuleId());
        }

        @Override
        public String source() {
            return raw;
        }
    }


    /**
     * Slow test: {@code target is_slowed} / {@code self is_slowed}.
     *
     * <p><b>Why the condition DSL needs it.</b> "处于减速状态" is not a buff with a name: the engine's slows are
     * {@code SPEED} modifiers (the Skill's {@code MODIFY_ATTR} debuff, and {@code ControlBuff}'s own slow), so
     * {@code has_state} cannot answer it, and a named state written only by one file would answer "no" for a slow
     * another file applied - an approximation, not the sentence. Readers: 1002 Dan Heng's ultimate ("当受到攻击的
     * 敌方目标处于减速状态时，终结技造成的伤害倍率提高 120%") and his trace 罡风 ("普攻对减速状态下的敌方目标造成的
     * 伤害提高 40%"); light cone 20011 asks the same question of an enemy.
     *
     * <p>Note: The test is {@link DoubleValue#isReduced()} - "at least one modifier lowers it" - rather than
     * "current is below base". The question is whether the unit is <b>under a slow</b>, and a unit carrying
     * {@code +20%} speed next to a {@code -12%} slow still is; the net comparison would silently clear it.
     */
    static final class IsSlowed implements Condition, PartyCondition {
        private final String subject;
        private final String raw;

        IsSlowed(String subject, String raw) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            return who != null && who.getAttribute(AttributeType.SPEED) != null
                    && who.getAttribute(AttributeType.SPEED).isReduced();
        }

        @Override
        public String source() {
            return raw;
        }
    }


    /**
     * Numeric comparison such as {@code hit_count > 0} or {@code self_attr:SPEED >= 145}.
     */
    static final class Numeric implements Condition {

        private final String variable;
        /**
         * The attribute a {@code self_attr:<ATTRIBUTE>} variable reads, or {@code null} for a plain name
         * (in which case {@link #variable} selects one of {@link TriggerConditionParser#NUMERIC_VARIABLES}).
         */
        final AttributeType attribute;
        /**
         * The resource a {@code self_resource:<NAME>} variable reads, or {@code null} for everything else.
         *
         * <p>Package-visible to the enclosing table, which collects the names into
         * {@link TriggerTable#referencedResources} for the assembly-point check.
         */
        final String resource;
        /**
         * The named counter a {@code *_stacks:<NAME>} variable reads, or {@code null}.
         */
        final String stacksName;
        /**
         * Whether that counter is read off the event's <b>target</b> rather than the rule's owner.
         */
        private final boolean stacksOnTarget;
        /**
         * Whether it is read off the event's <b>actor</b> (the unit that caused it) instead.
         */
        private final boolean stacksOnActor;
        private final String operator;
        private final double literal;
        private final boolean literalOnLeft;
        /**
         * The RIGHT-hand side when it is a variable rather than a literal (light cone 21012's
         * "当前生命值百分比大于等于装备者自身当前生命值百分比"): the DSL always said comparisons were against a
         * party or a literal, and this is the third shape the corpus actually uses. Four descriptors, exactly like the
         * left-hand variable's, so a two-variable comparison needs no new vocabulary at all.
         */
        private final String otherVariable;
        private final AttributeType otherAttribute;
        private final String otherResource;
        /**
         * The Path an {@code allies_with_path:<PATH>} variable counts, or {@code null}.
         */
        private final String path;
        /**
         * The same for the right-hand variable of a two-variable comparison.
         */
        private final String otherPath;
        private final String otherStacksName;
        private final boolean otherStacksOnTarget;
        private final boolean otherStacksOnActor;

        Numeric(String variable, AttributeType attribute, String resource, String stacksName, boolean stacksOnTarget,
                boolean stacksOnActor, String operator, double literal, boolean literalOnLeft, String otherVariable,
                AttributeType otherAttribute, String otherResource, String otherStacksName, boolean otherStacksOnTarget,
                boolean otherStacksOnActor, String path, String otherPath) {
            this.variable = variable;
            this.attribute = attribute;
            this.resource = resource;
            this.stacksName = stacksName;
            this.stacksOnTarget = stacksOnTarget;
            this.stacksOnActor = stacksOnActor;
            this.operator = operator;
            this.literal = literal;
            this.literalOnLeft = literalOnLeft;
            this.otherVariable = otherVariable;
            this.otherAttribute = otherAttribute;
            this.otherResource = otherResource;
            this.otherStacksName = otherStacksName;
            this.otherStacksOnTarget = otherStacksOnTarget;
            this.otherStacksOnActor = otherStacksOnActor;
            this.path = path;
            this.otherPath = otherPath;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            double value = numericValue(ctx);
            double left;
            double right;
            if (otherVariable != null) {
                double other = otherPath != null
                        ? pathCount(ctx, otherPath)
                        : value(ctx, otherVariable, otherAttribute, otherResource, otherStacksName,
                        otherStacksOnTarget, otherStacksOnActor);
                left = literalOnLeft ? other : value;
                right = literalOnLeft ? value : other;
            } else {
                left = literalOnLeft ? literal : value;
                right = literalOnLeft ? value : literal;
            }
            return switch (operator) {
                case ">" -> left > right;
                case ">=" -> left >= right;
                case "<" -> left < right;
                case "<=" -> left <= right;
                // Equality on numbers, which the DSL has always documented (`hit_count == 2`) and only
                // started accepting -- see parseCondition. NaN compares false against
                // everything, which is the "cannot read it, so the condition fails" rule.
                case "==" -> left == right;
                case "!=" -> left != right;
                default -> false;
            };
        }

        /**
         * The variable's current value, or {@code NaN} when it cannot be read.
         *
         * <p>{@code NaN} is the honest answer: every comparison against it is {@code false}, so a
         * condition whose subject does not exist fails the rule instead of accidentally passing it. That
         * also covers a {@code null} attribute slot, which should not happen for the four rejected
         * {@code *_PERCENT} keys but must not become an NPE inside a battle if it ever does.
         */
        private double numericValue(TriggerContext ctx) {
            return path != null
                    ? pathCount(ctx, path)
                    : value(ctx, variable, attribute, resource, stacksName, stacksOnTarget, stacksOnActor);
        }

        /**
         * How many characters on OUR side have the named Path.
         *
         * <p>{@code NaN} when there is no battlefield to read, the same convention every other variable follows:
         * a comparison against NaN is false, so the condition fails rather than counting zero and passing.
         */
        private static double pathCount(TriggerContext ctx, String pathName) {
            if (ctx.battle() == null || pathName == null) {
                return Double.NaN;
            }
            com.laosun.aluminium.enums.Path want = com.laosun.aluminium.enums.Path.fromName(pathName);
            return ctx.battle().allies.stream()
                    .filter(unit -> unit instanceof com.laosun.aluminium.models.Character character
                            && character.getPath() == want)
                    .count();
        }

        /**
         * One reader for every numeric variable, so a comparison can read two of them.
         */
        private double value(TriggerContext ctx, String variable, AttributeType attribute, String resource,
                             String stacksName, boolean stacksOnTarget, boolean stacksOnActor) {
            // A whole-field count. Resolved from the variable's own name rather than from a `Numeric` field,
            // because the name IS the parameter: there is nothing to resolve at parse time beyond its shape
            // (which `requireEnemyStateName` already refused), so a field would be a slot that only ever holds
            // a suffix of `variable`.
            if (variable.startsWith(TriggerConditionParser.ENEMY_STATE_COUNT_PREFIX)) {
                return enemyStateCount(ctx,
                        variable.substring(TriggerConditionParser.ENEMY_STATE_COUNT_PREFIX.length()).trim());
            }
            if (attribute != null) {
                return ownerAttribute(ctx.owner(), attribute);
            }
            if (resource != null) {
                // `summon_resource:` reaches through the owner to its summon: the engine's `RESOURCE_CHANGED` names the RULE'S OWNER, so this is the only way to ask about
                // the memosprite's own counter.
                CanHit resourceHolder = variable.startsWith(TriggerConditionParser.SUMMON_RESOURCE_PREFIX) && ctx.battle() != null
                        ? ctx.battle().memospriteOf(ctx.owner())
                        : ctx.owner();
                return resourceValue(resourceHolder == null ? ctx.owner() : resourceHolder, resource, ctx.battle());
            }
            if (stacksName != null) {
                // "每当我方目标对[承负]状态下的敌方目标施放 2 次…" reads the counter on the ENEMY (target), while a
                // counter of "how many refunds so far" would be read on the owner -- hence the two spellings. An
                // unreadable subject gives NaN, like every other variable that needs one.
                // Three subjects now: the owner (self_…), the event's target, and the event's actor -- the last one is what
                // makes "我方任意角色…消耗" readable from the light cone wearer's own table.
                CanHit holder = stacksOnActor ? ctx.actor() : stacksOnTarget ? ctx.target() : ctx.owner();
                return holder == null ? Double.NaN : holder.getBuffManager().stacksOf(stacksName);
            }
            return switch (variable) {
                case "weakness_hit_count" -> ctx.weakHitCount();   // how many hit targets share the attack's weakness
                case "hit_count" -> ctx.hitCount();
                // "强化普攻命中…": the DATA ROW of the skill that produced this event (0 = the event named none, which
                // makes the comparison false rather than accidentally true for the row 0 that no skill has).
                case "from_skill_id" -> ctx.skillId();
                // The skill that produced a SETTLED damage: `DAMAGE_SETTLED` hands over the `Damage` but goes through the overload WITHOUT a
                // skillId, so `from_skill_id` reads 0 there -- measured, a rule that fires ungated stops firing the moment `from_skill_id == ` is added. The
                // instance carries it (`Damage.getSkillKey()`, "the loader's slot"), so this reads it where the event provides nothing.
                case "damage_skill_key" -> {
                    com.laosun.aluminium.models.Damage hit = ctx.damage();
                    yield hit == null ? 0 : hit.getSkillKey();
                }
                case "hp_percent" -> hpPercent(ctx.owner());
                // "施放欢愉技计入的笑点大于等于 20 个" (reader: 1506 Silver Wolf LV.999's trace 1506102): the laugh points
                // that Elation damage is computed from - the merged, frozen snapshots of 【好活当赏】, NOT the party's live
                // pool, which is the same distinction the damage factor itself makes. A context with no battle answers
                // NaN, like every other unreadable variable here.
                case "counted_laughs" -> ctx.battle() == null ? Double.NaN : ctx.battle().countedLaughPoints();
                case "target_weakness_count" -> ctx.target() instanceof com.laosun.aluminium.models.enemy.Enemy weak
                        ? weak.weaknessCount() : 0;
                case "target_debuff_count", "target_dot_count" -> ctx.target() == null ? Double.NaN
                        : ("target_dot_count".equals(variable)
                        ? ctx.target().getBuffManager().countBuffs(com.laosun.aluminium.models.buff.DotBuff.class)
                        : ctx.target().getBuffManager().debuffCount());
                // "若该目标当前生命值百分比大于等于 30%" -- the OTHER unit's HP, which `hp_percent` cannot ask
                // (that one reads the rule's owner). Unreadable with no subject, like every other variable here.
                case "target_hp_percent" -> ctx.target() == null ? Double.NaN : hpPercent(ctx.target());
                // The ACTOR's share (light cone 21055's "our target's current HP percentage
                // is at or above 50%"): "our unit" there is the one dealing the damage, so the third subject.
                case "actor_hp_percent" -> hpPercent(ctx.actor());
                // The same fraction BEFORE this event's loss: "降到50%或以下" is a CROSSING, not "is below half", and the
                // difference is firing once versus firing on every later hit. HP_LOST carries the loss in `amount`, so before = (current + amount) / max.
                case "target_hp_percent_before" -> ctx.target() == null || ctx.target().getMaxHp() <= 0
                        ? Double.NaN
                        : Math.min(1.0, (ctx.target().getCurrentHp() + ctx.amount()) / ctx.target().getMaxHp());
                case "self_summon_count" -> summonCount(ctx.owner(), ctx);
                // "若目标拥有召唤物" - the same question about the OTHER unit. It is a separate name rather
                // than a subject prefix because the two are asked in the same sentence often (relic 12 asks
                // about the wearer, Sunday (星期日)'s Skill asks about the ally it was cast on).
                case "target_summon_count" -> summonCount(ctx.target(), ctx);
                // "场上敌方目标数量" -- a count of the OTHER camp as a whole, which no subject prefix fits: `self_*` and
                // `target_*` are both about one unit. Its reader is 1413 Evernight (长夜月)'s talent, whose thresholds are 4+/3/2/1 enemies.
                case "enemy_count" -> enemyCount(ctx);
                // "我方目标数量" -- the mirror of `enemy_count`, and the blocker set 321's entry names ("there is still no
                // condition on the battlefield's PARTY SIZE, so the number of stacks cannot be computed").
                case "ally_count" -> allyCount(ctx);
                // "若装备者的能量上限大于等于…" -- not an attribute (`CanHit.getMaxEnergy()` is a field, and the
                // attribute table has no slot for it), which is exactly why it needed a variable of its own.
                case "self_max_energy" -> ctx.owner() == null ? Double.NaN : ctx.owner().getMaxEnergy();
                // The owner's Eidolon rank. Note: A non-character owner (a summon, a hand-built context) has no
                // Eidolons, and 0 is the answer that unlocks nothing - the same convention `eidolonRankOf` uses on the
                // rule-level gate, spelled once here as a value.
                case "eidolon" -> ctx.owner() instanceof com.laosun.aluminium.models.Character owner
                        ? owner.getEidolonRank()
                        : 0;
                // "当前能量值等于其能量上限" / "当前能量百分比小于 50%" -- the same reasoning as the variable above,
                // one step further: energy is a field rather than an attribute, so the RATIO needs its own name too.
                // Five shipped readers are already on record as blocked on exactly this spelling: light cone 2101's
                // second sentence, character 1310's third and fourth clauses, character 1215's eidolon 2, and the
                // filter light cone 21021 needs. A unit with no energy bar, or a zero maximum, yields NaN -- and
                // every comparison against NaN is false, which is the convention `self_max_energy` already set.
                case "self_energy_percent" -> ctx.owner() == null || ctx.owner().getMaxEnergy() <= 0
                        ? Double.NaN
                        : ctx.owner().getCurrentEnergy() / ctx.owner().getMaxEnergy();
                // "if the allies' Skill Points limit is #2 or higher" -- the cap of the TEAM's pool. It is not an
                // attribute and not a field of the unit: the policy owns it (F-1 made it movable), so this reads the
                // battle rather than the owner, and a context with no battle answers NaN -- "cannot read it, so the
                // condition fails", the convention every other variable follows.
                case "skill_point_max" -> ctx.battle() == null ? Double.NaN : ctx.battle().getSkillPointMax();
                // "if the Skill Points are 4 or more" / "or when Skill Points are insufficient to cast the Skill again" --
                // the CURRENT count, which is what those sentences ask about. The cap above is a different number and
                // substituting it would answer a question nobody asked (measured: no shipped rule read skill points in a
                // condition before this, and the cap is what one light cone's own sentence states). Reader: 1015 Archer
                // (Archer)'s trace 1015103, and his Skill's exit clause ("战技点不足以再次施放战技则退出").
                // Note: Same convention as everything above - no battle, so the question cannot be answered, and NaN is
                // the answer that makes the condition false instead of silently "0 points".
                case "skill_point" -> ctx.battle() == null ? Double.NaN : ctx.battle().getSkillPoints();
                default -> Double.NaN;
            };
        }

        /**
         * How many living summons one unit has on the field.
         *
         * <p>Read from the battlefield, so a context with no battle - or a unit that does not exist for this
         * event - answers {@code NaN}: the same "cannot read it, therefore the condition fails" rule the other
         * variables follow, and never a silent "0 summons". Note: That matters more here than elsewhere: a rule
         * gated on "忆灵在场时" ("while my Memosprite (忆灵) is out") would be <em>silently disabled</em> if a missing battlefield read as "none
         * out", which is a wrong answer with no symptom.
         */
        private static double summonCount(CanHit who, TriggerContext ctx) {
            if (ctx.battle() == null || who == null) {
                return Double.NaN;
            }
            return ctx.battle().summonCountOf(who);
        }

        /**
         * How many living enemies are on the field -- "场上敌方目标数量".
         *
         * <p>Note: {@code NaN} when there is no battlefield, exactly like {@link #summonCount}: a rule gated on a count that cannot
         * be read must FAIL rather than read as zero, because "no enemies" and "cannot tell" are different answers and the
         * second one has no symptom. The dead are skipped, which is what "场上" means.
         */
        private static double enemyCount(TriggerContext ctx) {
            if (ctx.battle() == null || ctx.battle().enemyUnits() == null) {
                return Double.NaN;
            }
            return ctx.battle().enemyUnits().stream().filter(enemy -> !enemy.isDeath()).count();
        }

        /**
         * How many living enemies are currently in the named state -- "场上有敌方目标处于<某状态>".
         *
         * <p>The reader is 1206 Sushang's trace 破敌, whose clause is "施放普攻或战技后，<b>若场上有敌方目标处于弱点击破
         * 状态</b>，则素裳的行动提前 15%": a question about the FIELD rather than about one unit, which neither
         * {@code has_state} (one named party) nor {@code enemy_count} (the camp as a whole) could ask.
         *
         * <p>⚠ The state is resolved by the <b>same</b> {@code BuffManager.hasState} the {@code has_state}
         * keyword calls, so the two spellings cannot disagree about what "in that state" means - including the
         * engine's own aliases ({@code BuffManager.BROKEN_STATE} is "弱点击破", which is exactly the name
         * 1206 asks about).
         *
         * <p>{@code NaN} when there is no battlefield, the rule every count in this vocabulary follows: "cannot
         * read it" and "there are none" are different answers, and only one of them has a symptom. The dead are
         * skipped - a corpse is not "on the field", and it cannot carry a state either.
         */
        private static double enemyStateCount(TriggerContext ctx, String stateName) {
            if (ctx.battle() == null || ctx.battle().enemyUnits() == null) {
                return Double.NaN;
            }
            if (stateName == null || stateName.isBlank()) {
                // Unreachable through a validated table; a hand-built one should say so rather than count zero.
                return Double.NaN;
            }
            return ctx.battle().enemyUnits().stream()
                    .filter(enemy -> !enemy.isDeath())
                    .filter(enemy -> enemy.getBuffManager().hasState(stateName))
                    .count();
        }

        /**
         * How many living allies are on the field -- "我方目标数量", the mirror of {@link #enemyCount}.
         *
         * <p>Note: {@code NaN} when there is no battlefield, the rule every count in this vocabulary follows: "cannot read it" and
         * "there are none" are different answers, and only one of them has a symptom.
         */
        private static double allyCount(TriggerContext ctx) {
            if (ctx.battle() == null || ctx.battle().allies == null) {
                return Double.NaN;
            }
            return ctx.battle().allies.stream().filter(ally -> ally != null && !ally.isDeath()).count();
        }

        /**
         * How much of one of the owner's declared resources it currently holds.
         *
         * <p>Note: {@code NaN} when the owner does not exist <b>or does not declare that resource</b>. That second
         * case cannot happen for a shipped character - the assembly point refuses a rule that reads an
         * undeclared resource - but a hand-built context (a test, or a table compiled on its own, as several
         * tests do) can reach it, and {@code ResourceManager.value} would answer {@code 0} for it. A rule
         * silently gated on a resource nobody registered is the wrong answer with no symptom this whole
         * vocabulary is built to avoid, so it fails the condition instead. The same rule every other
         * unreadable variable follows.
         */
        private static double resourceValue(CanHit owner, String resource, Battle battle) {
            if (owner != null && owner.getResources().has(resource)) {
                return owner.getResources().value(resource);
            }
            // A PARTY-scoped counter is read from the battle, whichever ally declared it. Nothing changes for a
            // resource a unit owns itself -- that case answered above.
            if (battle != null && battle.partyResource(resource) != null) {
                return battle.partyResourceValue(resource);
            }
            // Note: NaN, not 0: "nobody declares that" must fail the condition rather than read as "none left".
            return Double.NaN;
        }

        /**
         * One of the owner's attribute values, in that attribute's <b>own units</b>.
         *
         * <p>This is the same number every other consumer sees ({@code DoubleValue.get()}): flat attributes
         * are absolute and ratio attributes are fractions. Go through {@code getAttribute} and not through
         * any builder-side view, because those are the units the damage pipeline reads.
         */
        private static double ownerAttribute(CanHit owner, AttributeType attribute) {
            if (owner == null) {
                return Double.NaN;
            }
            DoubleValue value = owner.getAttribute(attribute);
            return value == null ? Double.NaN : value.get();
        }

        /**
         * The owner's HP as a fraction of its maximum ({@code 0.5} = half HP).
         *
         * <p>Read from the <b>owner</b> - "the wearer's HP percentage" is a fact about the character
         * whose table fired, not about the event, so no event has to carry it. A missing owner or a
         * maximum of 0 yields {@code NaN}, which fails every comparison rather than reporting "0%".
         */
        private static double hpPercent(CanHit owner) {
            if (owner == null) {
                return Double.NaN;
            }
            double max = owner.getMaxHp();
            if (max <= 0) {
                return Double.NaN;
            }
            return owner.getCurrentHp() / max;
        }

        @Override
        public String source() {
            return literalOnLeft
                    ? literal + " " + operator + " " + variable
                    : variable + " " + operator + " " + literal;
        }
    }
}
