package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A character's compiled trigger table (P8-7): the data form of its mechanics.
 *
 * <p>Loadable from {@code resources/characters/<cid>.json}; an unregistered character simply has an
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
     * The resources this character <b>declares</b> (P8-8): 「充能，上限3点」 written down once, where the
     * character is built. Empty for a character with no stacks — the ordinary state, and the reason
     * {@link #isEmpty()} keeps meaning "no rules".
     *
     * <p>⚠ They are carried by the table rather than by a separate loader because they live in the character's
     * own file, next to the rules that read them, and the loader therefore has them in hand already. Registering
     * them onto the combatant is the assembly point's job ({@code CharacterFactory}), like every other
     * "which character is this" decision.
     */
    private final List<ResourceSpec> resources;

    /**
     * Every resource name this table's rules <b>read</b> — from a {@code self_resource:<NAME>} condition or
     * from a {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} effect.
     *
     * <p>Exists so the assembly point can refuse a rule that names a resource its character never declares.
     * Failing there (when the character is built) rather than at fire time is the whole point: an undeclared
     * resource answers {@code 0} to every read and swallows every gain, so the rule would otherwise be a
     * silent no-op — see {@link #resources}.
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
     * The same, with the character's resource declarations (P8-8).
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
                byEvent.computeIfAbsent(event, k -> new ArrayList<>()).add(rule);
            }
            // 「施放战技和终结技时」: the SAME compiled rules under extra events (一条规则听多个事件).
            // ⚠ Compiled once, not per event: a second compilation would mint a second rule with the same id, which validateAmendments rightly
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
                    byEvent.computeIfAbsent(other, k -> new ArrayList<>())
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
     * <p><b>Why the shape check is not optional.</b> 「增加1次」 has nothing to increase on a rule that states no
     * {@code per_turn} (and {@code 0 + 1 = 1} would silently <i>impose</i> a one-per-turn cap where there was none),
     * and 「基础概率提高15%」 has nothing to raise on a rule whose effects state no {@code base_chance}. Both would
     * load, fire, and change a number nobody asked about — the failure mode this vocabulary exists to prevent.
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
                    if (!"MODIFY_RULE".equalsIgnoreCase(
                            effect.getOp() == null ? "" : effect.getOp().trim())) {
                        continue;
                    }
                    requireAmendable(rule, effect, byId);
                }
                // `has_shield from_rule <id>`: same kind of reference, so the same pass resolves it. It must name a
                // rule in this file AND that rule must actually create a shield — otherwise the condition can never
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
     * One {@code MODIFY_RULE} effect against its target: the id resolves, and the target states the number.
     *
     * @param amender the rule carrying the effect
     * @param effect  the {@code MODIFY_RULE} effect (its fields were already validated by the interpreter)
     * @param byId    every named rule in this file
     */
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
        return switch (op.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "ADD_STACK", "APPLY_BUFF", "MODIFY_ATTR", "RESIST_DEBUFF", "SHIELD" -> true;
            default -> false;
        };
    }

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
        if (effect.getEffectPercent() != null) {
            boolean statesPercent = named.effects().stream().anyMatch(e -> e.getPercent() != null);
            if (!statesPercent) {
                throw new IllegalArgumentException(
                        "MODIFY_RULE raises the value of rule \"" + target + "\", but none of that rule's effects "
                                + "states a \"percent\"; there is no number to raise (source: "
                                + amender.source() + ")");
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
     * This table's rules followed by another table's — one table with both sets of mechanics.
     *
     * <p><b>Why this exists.</b> A character's effective mechanics are not always in one file: the
     * character's own {@code resources/characters/<cid>.json} supplies its kit, and every relic set
     * worn in sufficient numbers supplies its own rules
     * ({@code resources/relic_sets/<setId>.json}). The assembly point
     * ({@code CharacterFactory}) needs one table on {@link Character#getTriggerTable()}, and the
     * alternative — teaching {@code Battle} to consult several tables — would spread the composition
     * across the hot path instead of doing it once, at build time.
     *
     * <p>Order is <b>this table first</b>, then the other's, per event, and it is preserved. Nothing
     * in the engine depends on it today (effects run in the order written <em>within</em> a rule), but
     * "the character's own rules come before the equipment's" is the order a reader expects.
     *
     * <p>Either side may be {@code null} or empty, in which case the other is returned unchanged — an
     * unequipped character therefore behaves exactly as it did before relic rules existed. ⚠ "Empty" here
     * means <b>no rules and no declarations</b>: a table that declares a resource but has no rules of its own
     * is not nothing, and returning the other side for it would drop the declarations (and with them every
     * {@code self_resource:} read in the merged table).
     *
     * <p><b>Resource declarations.</b> The character's own file is the only place they can come from today
     * (a relic set's file is a bare array — see {@code TriggerTables}), so the merge carries whichever side
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
     * Whether this table carries nothing at all — no rules and no resource declarations.
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
     * Every resource name the table's rules read — see {@link #referencedResources}.
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
     * All rules matching an event and a given context.
     *
     * @param event the fired event
     * @param ctx   the context the conditions are evaluated against
     * @return the matching rules, in table order; never {@code null}
     */
    /**
     * The compiled rules of one event, <b>without</b> evaluating their conditions — the list
     * {@link TriggerInterpreter#fire} walks when it wants to check each rule's conditions as it reaches it.
     *
     * <p>⚠ Why the interpreter does not simply use {@link #matching}: conditions evaluated all at once cannot see what
     * an <b>earlier rule of the same event</b> just did (a counter marked on this very attack, for instance), and
     * 「每 2 次…后」 is exactly that shape. `matching` stays the pure predicate it always was — the data-binding tests
     * and {@code ruleCount} read it — and this accessor is what lets firing differ.
     */
    public List<CompiledRule> rulesFor(TriggerEvent event) {
        return byEvent.getOrDefault(event, List.of());
    }

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
                conditions.add(parseCondition(raw, spec));
            }
        }
        List<EffectSpec> effects = spec.getDoEffects() == null ? List.of() : spec.getDoEffects();
        if (effects.isEmpty()) {
            throw new IllegalArgumentException(
                    "Trigger on " + event.value() + " has no effects (source: " + spec.getSource() + ")");
        }
        for (EffectSpec effect : effects) {
            TriggerInterpreter.validate(effect, spec);
            // ⚠ The "which events can carry this op" checks live HERE, not in the interpreter's validation switch: that
            // switch sees one effect at a time and knows no event, while every rule here is compiled with its own.
            // `SUPER_BREAK` reads 「本次伤害的削韧值」 off the settled instance, so DEALING_DAMAGE is the only moment it can
            // be attached to (the same rule ADD_DAMAGE follows, which the interpreter could enforce because it already
            // knew the event).
            // ⚠ A `damage_type` is validated HERE, where the rule's own source is available and the failure is loud at load
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
        // 「对所有<b>触电状态下的</b>敌方目标…」 (M-53): each effect may name conditions its TARGETS must satisfy. Parsed
        // here, with the rule's own event, so `from_skill` and friends mean the same thing inside a filter.
        List<List<Condition>> targetFilters = new ArrayList<>();
        for (EffectSpec effect : effects) {
            List<Condition> filter = new ArrayList<>();
            if (effect.getTargetWhen() != null) {
                for (String raw : effect.getTargetWhen()) {
                    filter.add(parseCondition(raw, spec));
                }
            }
            targetFilters.add(List.copyOf(filter));
        }
        return List.of(new CompiledRule(event, conditions, effects, spec.getSource(),
                ruleKey(spec, index), validateId(spec), validateCooldown(spec),
                Boolean.TRUE.equals(spec.getOncePerBattle()), validateChance(spec),
                validateMinEidolon(spec), validatePerTurn(spec), validatePerAttack(spec), List.copyOf(targetFilters)));
    }

    /**
     * A rule's optional <b>name</b>, trimmed, or {@code ""} when it states none.
     *
     * <p>Only a name that is present is meaningful: an unnamed rule can still be referred to by nothing, which is the
     * ordinary case and the reason this is not an error. ⚠ The name is scoped to its file (uniqueness and
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
     * reached, so the rule would be silently dead — the failure mode this vocabulary exists to prevent.
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
     * Validates a rule's probability and returns it as a fraction of 1 ({@code 1.0} = always).
     *
     * <p>Validated at load time for the same reason as the cooldown: {@code chance: 0} would load and never fire,
     * and a value above 1 reads like a percentage when the field is a fraction — the two mistakes look like a
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
     * A rule's stable identity, used by the per-combatant firing limits
     * ({@code CanHit.isTriggerReady} / {@code startTriggerCooldown}).
     *
     * <p><b>Why the source is not enough on its own.</b> A file may ship several rules that share one
     * provenance string — {@code characters/1403.json} has exactly that, because its two rules come from
     * the same trace (「岔路旁的小石子？」 states both the battle-start energy and the per-target
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
     * exactly that trap — it reads like "no cooldown", which is already spelled by omitting the field.
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
     * Validates a per-turn cap (「该效果每回合可触发 N 次」) and returns it ({@code 0} = no cap).
     *
     * <p>Two load-time rejections, both of them things that would otherwise read as a working rule:
     * <ul>
     *   <li>{@code per_turn: 0} (or negative) — it reads like "no per-turn limit", which is already spelled by
     *       omitting the field, and a cap of 0 would be a rule that can never fire at all;</li>
     *   <li>{@code per_turn} together with {@code cooldown} — "at most N per turn, and at least M turns apart"
     *       has two readings that disagree once {@code N > 1} (does the cooldown start on the first firing or
     *       the last?), and the engine would have to pick one silently. {@code cooldown: 1} is exactly the
     *       {@code N = 1} case of this field, so the pair is never the only way to write something.</li>
     * </ul>
     * {@code once_per_battle} <b>may</b> be combined with it: the two are cumulative ("twice per turn, and only
     * once in the whole battle"), which is a reading that cannot be misread.
     */
    /**
     * The per-ATTACK cap: {@code once_per_attack: true} means one, {@code per_attack: N} means N.
     *
     * <p>\u26a0 Both at once is refused rather than resolved: they are two spellings of one dimension, and picking one
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
    //   actor == summon       MY OWN summon acted                <- relic 123's 「装备者的忆灵攻击时」
    //   target == summon      it happened to my own summon       <- 「装备者的忆灵受到攻击后」
    //   hit_count > 0         the attack connected with at least one target
    //   hit_count == 2        exact hit count
    //   hp_percent <= 0.5     the owner's own HP is at or below half   <- set 106's "at the beginning
    //                                                                     of the turn, if the wearer's
    //                                                                     HP percentage is <= 50%"
    //   target_debuff_count >= 3  the event's subject carries 3 debuffs <- Silver Wolf's "if the enemy has
    //                                                                      >= 3 debuffs, the RES shred is
    //                                                                      reduced further"
    //   self_attr:SPEED >= 145    one of MY OWN attribute values      <- the planar ornament 2-pieces'
    //                                                                     "当装备者的速度大于等于145时" shape
    //   self_summon_count >= 1    I have a summon of my own on the field <- the memosprite family's
    //                                                                     "忆灵在场时" / "存在装备者召唤的目标时"
    //   self has_state 协奏   I am in the named state 协奏             <- Robin's 即兴装饰 "处于【协奏】状态时"
    //   target has_state 触电 it happened to someone in that state     <- Kafka's "触电状态下的敌方目标"
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
    // owner. ⚠ The literal is in the ATTRIBUTE'S OWN UNITS, which differ by kind: flat attributes are
    // absolute (SPEED >= 145) while every ratio attribute is a fraction (CRIT_CHANCE >= 0.7, i.e. 70%,
    // BREAKING_EFFECT >= 1.5, i.e. 150%). Writing the percentage as 70 would compile, load, and simply never
    // fire -- see SelfAttributeConditionTest, which pins both scales.
    // ==================================================================

    /**
     * The numeric variables {@code hit_count}, {@code hp_percent}, {@code target_debuff_count} and
     * {@code self_summon_count} are the complete, closed set of <b>plain</b> names.
     *
     * <p>Each reads from a different place, which is why the names say so: {@code hit_count} comes from the
     * event, {@code hp_percent} from the rule's owner (a fact about me), and {@code target_debuff_count} from
     * the event's <b>subject</b> (「目标身上有几个负面效果」 — the count that matters is the one on the unit the
     * rule is talking about, not on me).
     *
     * <p>{@code self_summon_count} is the one that reads the <b>field</b> rather than the event or the owner:
     * how many living summons are out whose master is this rule's owner. It is spelled {@code self_}…
     * although {@code hp_percent} does not bother, because a bare {@code summon_count} would read like "how
     * many summons are on the battlefield" — a different question with a different answer, and one nobody has
     * asked for yet.
     *
     * <p>⚠ {@code self_attr:<ATTRIBUTE>} and {@code self_resource:<NAME>} are the two parameterised
     * members: neither is listed above, and both are validated separately — the first against
     * {@link AttributeType}, the second for shape here and for existence where the character is assembled.
     */
    private static final Set<String> NUMERIC_VARIABLES =
            Set.of("ally_count", "enemy_count", "hit_count", "weakness_hit_count", "target_weakness_count", "hp_percent", "target_hp_percent", "target_hp_percent_before", "target_debuff_count", "self_summon_count",
                    "target_summon_count", "self_max_energy", "from_skill_id", "target_dot_count");

    /**
     * The prefix of one parameterised numeric variable: {@code self_attr:SPEED}.
     *
     * <p>Why the subject is fixed at {@code self} and not a general {@code <subject>_attr:<TYPE>}: every
     * attribute threshold in the shipped data is about the wearer (「装备者的速度/暴击率/击破特攻/生命上限…」),
     * and an axis with one used value is an axis nobody has tested. Adding {@code target_attr:} later is a
     * few lines in the same place once some content actually needs it.
     *
     * <p>⚠ Package-private rather than private because {@code TriggerInterpreter} spells the <b>same</b> prefix for
     * an effect's {@code "scale"} (「提高数值等同于<我自己的属性>的 X%」, M-42): the condition's "read my attribute"
     * and the effect's "derive from my attribute" are one concept, and two literals would be able to drift.
     */
    static final String SELF_ATTR_PREFIX = "self_attr:";

    /**
     * The effect-side spelling for "how many targets <b>this cast really applied</b> that state to"
     * (「终结技每冻结1个目标，为三月七恢复6点能量」) — {@code "scale": "cast_applied:冻结"}.
     *
     * <p>It sits beside {@link #SELF_ATTR_PREFIX} for the same reason that one exists: the content, the interpreter's
     * resolver and the loader's validator all have to spell one prefix, and a second literal would be able to drift
     * from this one. ⚠ Unlike {@code self_attr:} this is <b>not</b> a condition variable — a condition asks whether
     * something is true about a unit, while this is a derived <i>magnitude</i> (a count) used by an amount, and only
     * inside a cast's own events (the loader refuses it elsewhere).
     */
    static final String CAST_APPLIED_PREFIX = "cast_applied:";

    /**
     * The prefix of the other parameterised numeric variable: {@code self_resource:充能} — 「我的【充能】现在
     * 有几层」.
     *
     * <p><b>Why it had to exist.</b> A stack/charge resource is not an attribute (so {@code self_attr} cannot
     * read it), not energy (so {@code self_max_energy} cannot), and not a state (so {@code has_state} cannot):
     * it is a number that lives on the combatant's {@code ResourceManager}. 41 of the 97 character documents
     * gate something on 「充能达到上限」 / 「层数 ≥ N」, which is the largest single hole in the corpus — the ops
     * to <b>write</b> such a resource have existed since P8-8, and nothing could read one back.
     *
     * <p>⚠ Reading an <b>undeclared</b> resource answers {@code NaN}, never {@code 0} — "cannot read it, so the
     * condition fails", the same rule {@code self_summon_count} follows. {@code ResourceManager.value} answers
     * {@code 0} for an id nobody registered, and a rule silently gated on 「0」 is exactly the wrong answer with
     * no symptom this project refuses. (In a shipped character that state is impossible: the assembly point
     * refuses a rule that reads a resource its character never declares.)
     */
    private static final String SELF_RESOURCE_PREFIX = "self_resource:";

    /**
     * The ops whose {@code "resource"} argument names a resource the character must declare.
     *
     * <p>Used only to collect {@link #referencedResources}; the ops themselves were validated long before this
     * (they refuse a blank {@code "resource"} at load time).
     */
    private static final Set<String> RESOURCE_OPS = Set.of("GAIN_RESOURCE", "SPEND_RESOURCE");

    /**
     * The keyword of the named-state condition, and the parties it may ask about.
     *
     * <p>The keyword has to stand alone (a state whose name contains {@code has_state} must not be
     * mistaken for the operator), hence the lookarounds rather than a plain {@code contains}.
     */
    private static final Pattern HAS_STATE =
            Pattern.compile("(?<![\\w])has_state(?![\\w])", Pattern.CASE_INSENSITIVE);

    private static final Set<String> STATE_SUBJECTS = Set.of("self", "actor", "target");

    /**
     * \u2705 The keyword of the "that attribute of mine/theirs was lowered" condition (2026-09-30; readers: cone 22000's
     * \u300c\u653b\u51fb\u9632\u5fa1\u529b\u88ab\u964d\u4f4e\u7684\u654c\u65b9\u76ee\u6807\u540e\u6062\u590d\u80fd\u91cf\u300d and cone 21044's \u300c\u5904\u4e8e\u9632\u5fa1\u964d\u4f4e\u6216\u51cf\u901f\u72b6\u6001\u4e0b\u7684\u654c\u4eba\u300d).
     *
     * <p>\u2605 Why not {@code DebuffClass}: that enum names the two FAMILIES the corpus groups states into (control, dot),
     * and a lowered attribute belongs to neither. The fact that actually exists in the engine is a MODIFIER whose source is
     * {@link DoubleValue.Modifier.ModifierSource#DEBUFF}, which is exactly what \u300c\u88ab\u964d\u4f4e\u300d asserts.
     */
    private static final Pattern DEBUFF_ON =
            Pattern.compile("(?<![\\w])(?<subject>self|actor|target)_debuff:(?<attribute>[A-Za-z_]+)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_path} keyword, read exactly like {@link #HAS_STATE}.
     */
    private static final Pattern HAS_PATH =
            Pattern.compile("(?<![\\w])has_path(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_ally} keyword: "&lt;who&gt; is on OUR side" — read exactly like the other two predicates.
     */
    private static final Pattern IS_ALLY =
            Pattern.compile("(?<![\\w])is_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_other_ally} keyword (2026-09-28): "<b>that unit is on our side and is not me</b>".
     *
     * <p>⚠ Why it exists: the corpus says 「卡芙卡的**队友**对敌方目标施放普攻后…」 in 16 files, and neither existing spelling fits —
     * {@code actor is_ally} counts the owner herself (she is in {@code battle.allies}), while {@code !actor == self} is refused
     * because negation only applies to party conditions and an equality is not one.
     */
    private static final Pattern IS_OTHER_ALLY =
            Pattern.compile("(?<![\\w])is_other_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_same_path_ally} keyword: "somebody else on my side walks my Path".
     */
    private static final Pattern SAME_PATH_ALLY =
            Pattern.compile("(?<![\\w])has_same_path_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_same_element_as_self} keyword (2026-09-30): "<b>that unit carries the same element as me</b>".
     *
     * <p>\u2605 It is the per-CANDIDATE sibling of {@code damage_element_is_self}: that one asks about the damage instance
     * being settled, this one about a unit -- which is what a per-target filter ({@code target_when}) needs, because
     * there the candidate sits in {@code target} and the rule\u2019s owner in {@code owner()}. Readers: relic set 312\u2019s
     * \u300c\u4e0e\u88c5\u5907\u8005\u76f8\u540c\u5c5e\u6027\u7684\u5176\u4ed6\u6211\u65b9\u89d2\u8272\u300d.
     */
    private static final Pattern IS_SAME_ELEMENT =
            Pattern.compile("(?<![\\w])is_same_element_as_self(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_other_same_element_as_self} variant: the same question with the owner excluded --
     * \u300c\u4e0e\u88c5\u5907\u8005\u76f8\u540c\u5c5e\u6027\u7684**\u5176\u4ed6**\u6211\u65b9\u89d2\u8272\u300d (relic set 312). \u2605 It exists separately because the\n     * owner is not a candidate the sentence means, and because a filter on a BATTLE_START rule cannot state a
     * `target`-subjected condition at all (measured: the loader refuses those -- that event carries no
     * actor and no target, so only argument-less keywords work there).
     */
    private static final Pattern IS_OTHER_SAME_ELEMENT =
            Pattern.compile("(?<![\\w])is_other_same_element_as_self(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_weakness} keyword: "&lt;who&gt; is weak to this element".
     */
    private static final Pattern HAS_WEAKNESS =
            Pattern.compile("(?<![\\w])has_weakness(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_shield} keyword: "&lt;who&gt; currently holds a shield" — read exactly like the other
     * argument-less predicates.
     */
    private static final Pattern HAS_SHIELD =
            Pattern.compile("(?<![\\w])has_shield(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code from_rule} qualifier of {@code has_shield}: the shield was created by the rule with this id.
     */
    private static final Pattern FROM_RULE =
            Pattern.compile("(?<![\\w])from_rule(?![\\w])\\s*", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code from_skill} keyword: "the instance that caused this event came from this slot"
     * (「施放<b>战技</b>对敌方目标造成弱点击破时」).
     */
    private static final Pattern FROM_SKILL =
            Pattern.compile("(?<![\\w])from_skill(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code from_category} keyword: "the cast that caused this event was of this CATEGORY"
     * (\u300c\u88c5\u5907\u8005\u65bd\u653e**\u6b22\u6109\u6280**\u65f6\u300d, \u5149\u9525 21064 / 21066 / 23058 / 23064).
     *
     * <p>\u2605 Why it exists next to {@code from_skill}: the slot names a SKILL SLOT ({@code SKILL} = an ordinary Skill), while
     * a memosprite's or a \u6b22\u6109 kit's cast is a category the slot vocabulary does not have at all --
     * {@code SkillCategory.ELATION_DAMAGE} is precisely the one {@code SkillType} lacks. Reading the category directly is
     * the honest spelling for those sentences; {@code from_skill} keeps its meaning for the slot ones.
     */
    private static final Pattern FROM_CATEGORY =
            Pattern.compile("(?<![\\w])from_category(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The bare keyword {@code damage_is_attack}: "the instance being settled counts as an attack".
     *
     * <p><b>Why this exists</b> (2026-09-28): additional damage is settled as a real instance with
     * {@code notCountsAsAttack()} set (see {@code Battle.applyAdditionalDamage}), and it fires {@code DEALING_DAMAGE} like any
     * other instance — so a rule that reacts to "my attack hit a burning target" would react to its own additional damage,
     * forever. {@code !} cannot express the guard (negation is only for party conditions), so the guard is stated positively:
     * this is true exactly when the instance is an ordinary attack, and false for additional damage.
     */
    static final String DAMAGE_IS_ATTACK = "damage_is_attack";
    /**
     * \u2705 The bare keyword \u300c\u9020\u6210**\u4e0e\u88c5\u5907\u8005\u76f8\u540c\u5c5e\u6027**\u7684\u4f24\u5bb9\u300d (2026-09-30; readers: light cone 21011 and
     * relic set 312). No subject and no value, like {@link #DAMAGE_IS_ATTACK}: the second party is the RULE\u2019S OWNER, so
     * "same Type as the wearer" is the only reading it can have.
     *
     * <p>\u2605 The element lives on the unit ({@code Character.element}, set from the character data\u2019s own attribute), not on
     * the skill, and the damage instance carries the element it was built with -- so this compares the two directly. For a
     * memosprite owner the master\u2019s element is the one the sentence means, which is the same convention the MEMORY damage
     * hook uses in {@code Battle.assemble}.
     */
    static final String DAMAGE_ELEMENT_IS_SELF = "damage_element_is_self";

    /**
     * The events whose {@code TriggerContext} carries the <b>causing cast</b>, and therefore the only ones on which
     * {@code from_skill} can ever be true.
     *
     * <p>⚠ Checked at load time, because the alternative is the failure mode this project keeps refusing: a rule that
     * loads, fires on every matching event, and silently never matches its condition. A kill and a weakness break both
     * happen while an instance is being settled ({@code Battle.applyDamage} / {@code Battle.reduceToughness}),
     * {@code DEALING_DAMAGE} is the instance's own event, and {@code ALLY_ATTACK} carries the cast's category since
     * 2026-09-28 (it is the one event that fires <b>once per cast</b>, which is what 「施放 2 次普攻/战技/终结技」 counts).
     */
    private static final Set<TriggerEvent> DAMAGE_CARRYING_EVENTS =
            Set.of(TriggerEvent.DEALING_DAMAGE, TriggerEvent.BREAK, TriggerEvent.KILL, TriggerEvent.ALLY_ATTACK);

    /**
     * The events whose context carries the <b>cast category</b>, which is what {@code from_skill} reads.
     *
     * <p>\u2605 A superset of {@link #DAMAGE_CARRYING_EVENTS}: {@code CAST_SETUP} carries the category without a damage instance,
     * and it is precisely the event a rule needs for \u300c\u65bd\u653e\u2026\u65f6\u300d -- the modifier must exist while the cast's own
     * heal settles, and {@code ULT_CAST} / {@code SKILL_CAST} fire after that. Keeping the sets apart is what lets
     * {@code damage_is_attack} stay damage-only.
     */
    private static final Set<TriggerEvent> CAST_CARRYING_EVENTS =
            Set.of(TriggerEvent.DEALING_DAMAGE, TriggerEvent.BREAK, TriggerEvent.KILL, TriggerEvent.ALLY_ATTACK,
                    TriggerEvent.CAST_SETUP);

    private static Condition parseCondition(String raw, TriggerSpec spec) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty trigger condition (source: " + spec.getSource() + ")");
        }
        String text = raw.trim();

        // `!` negates the condition that follows it. The list of conditions is an AND, so without this the DSL
        // can only say "the target is on some Path" and never 「对「同谐」命途的角色…无法触发」 (星期日's Skill, the
        // first user) — and writing that as nine positive rules is the shape this prefix exists to avoid.
        if (text.startsWith("!")) {
            String inner = text.substring(1).trim();
            if (inner.isEmpty() || inner.startsWith("!")) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' is not a negation of anything: write \"!\" followed by one "
                                + "condition, e.g. \"!target has_path 同谐\" (source: " + spec.getSource() + ")");
            }
            Condition negated = parseCondition(inner, spec);
            if (!(negated instanceof PartyCondition party)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' negates a condition that does not read a party "
                                + "(self / actor / target); only those can be negated, because for a number "
                                + "\"cannot read it\" and \"is zero\" are different facts. Write the opposite "
                                + "comparison instead, e.g. \"self_summon_count == 0\" "
                                + "(source: " + spec.getSource() + ")");
            }
            return new Negated(party, raw);
        }

        // `has_state`: "<who> has_state <name>". Checked before the operator branch because this shape has
        // no symbol operator at all -- without it, "self has_state 协奏" would be reported as an unknown
        // shorthand, which sends the author looking in the wrong place.
        Matcher hasState = HAS_STATE.matcher(text);
        if (hasState.find()) {
            String subject = normalize(text.substring(0, hasState.start()));
            String state = text.substring(hasState.end()).trim();
            return new HasState(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), state, raw,
                    spec);
        }

        // `has_weakness`: "<who> has_weakness Fire" — the target's weakness ELEMENT, which nothing else can ask.
        Matcher hasWeakness = HAS_WEAKNESS.matcher(text);
        if (hasWeakness.find()) {
            String subject = normalize(text.substring(0, hasWeakness.start()));
            String element = text.substring(hasWeakness.end()).trim();
            return new HasWeakness(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    requireElement(element, raw, spec), raw, spec);
        }

        // `self has_same_path_ally` — a party question with no argument, checked with the other predicates.
        Matcher samePath = SAME_PATH_ALLY.matcher(text);
        if (samePath.find()) {
            String subject = normalize(text.substring(0, samePath.start()));
            String trailing = text.substring(samePath.end()).trim();
            if (!"self".equals(subject) || !trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' must be written \"self has_same_path_ally\": the question is about "
                                + "MY OWN side (which teammates share my Path), so it has no other subject and no "
                                + "argument (source: " + spec.getSource() + ")");
            }
            return new HasSamePathAlly(raw, spec);
        }

        // `is_ally`: "<who> is_ally" — no argument at all, so it is checked before the operator branch too.
        // `is_other_ally`: "<who> is_other_ally" -- same shape as `is_ally`, one clause stricter.
        Matcher isOtherAlly = IS_OTHER_ALLY.matcher(text);
        if (isOtherAlly.find()) {
            String subject = normalize(text.substring(0, isOtherAlly.start()));
            String trailing = text.substring(isOtherAlly.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_other_ally\": it takes no argument "
                                + "(write \"actor is_other_ally\") (source: " + spec.getSource() + ")");
            }
            return new IsOtherAlly(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw);
        }

        Matcher isAlly = IS_ALLY.matcher(text);
        if (isAlly.find()) {
            String subject = normalize(text.substring(0, isAlly.start()));
            String trailing = text.substring(isAlly.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_ally\": it takes no argument "
                                + "(write \"target is_ally\", or \"!target is_ally\" for the opposite) "
                                + "(source: " + spec.getSource() + ")");
            }
            return new IsAlly(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw, spec);
        }

        // `has_path`: "<who> has_path 同谐" — the same shape, and for the same reason checked here first.
        Matcher hasPath = HAS_PATH.matcher(text);
        if (hasPath.find()) {
            String subject = normalize(text.substring(0, hasPath.start()));
            String name = text.substring(hasPath.end()).trim();
            return new HasPath(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    requirePath(name, raw, spec), raw, spec);
        }

        // `has_shield`: "<who> has_shield" — argument-less like `is_ally` — or "<who> has_shield from_rule <id>",
        // which asks about the shield's ORIGIN rather than its existence (「战技提供的护盾」, 1001 星魂 6).
        Matcher hasShield = HAS_SHIELD.matcher(text);
        if (hasShield.find()) {
            String subject = normalize(text.substring(0, hasShield.start()));
            String trailing = text.substring(hasShield.end()).trim();
            String ruleId = "";
            if (!trailing.isEmpty()) {
                Matcher fromRule = FROM_RULE.matcher(trailing);
                if (!fromRule.lookingAt()) {
                    throw new IllegalArgumentException(
                            "Condition '" + raw + "' writes something after \"has_shield\": it takes no argument, or "
                                    + "\"from_rule <id>\" to ask which rule created the shield "
                                    + "(source: " + spec.getSource() + ")");
                }
                ruleId = trailing.substring(fromRule.end()).trim();
                if (ruleId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Condition '" + raw + "' says \"from_rule\" but names no rule; give the id of the rule "
                                    + "that creates the shield (that rule states \"id\": \"…\") "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            return new HasShield(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    ruleId, raw, spec);
        }

        // `from_skill COMMON|SKILL|ULTRA|TALENT`: the causing instance's cast category. No subject: the "who" is
        // already a separate condition (`actor == self`), and the sentence never names a second unit.
        // `damage_is_attack`: a bare keyword, no subject and no value. Kept positive on purpose -- see DAMAGE_IS_ATTACK.
        if (text.trim().equalsIgnoreCase(DAMAGE_IS_ATTACK)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance is an attack, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageIsAttack(raw);
        }

        // `damage_element_is_self`: the bare-keyword sibling of `damage_is_attack`, one clause further in --
        // it asks about the instance\u2019s ELEMENT instead of its type, and compares it with the rule owner\u2019s own.
        if (text.trim().equalsIgnoreCase(DAMAGE_ELEMENT_IS_SELF)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance shares my element, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageElementIsSelf(raw);
        }

        // `<who> is_same_element_as_self`: argument-less like `is_ally`, and stated about a UNIT rather than about an
        // instance. The subject is optional because a per-target filter already means `target`; any other subject is
        // refused rather than ignored, so a typo cannot look like a working rule.
        if (IS_OTHER_SAME_ELEMENT.matcher(text).find()) {
            return new IsSameElementAsSelf(raw, true);
        }
        Matcher sameElement = IS_SAME_ELEMENT.matcher(text);
        if (sameElement.find()) {
            String subject = normalize(text.substring(0, sameElement.start()));
            String trailing = text.substring(sameElement.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException("Condition '" + raw + "' writes something after "
                        + "\"is_same_element_as_self\": it takes no argument (source: " + spec.getSource() + ")");
            }
            if (!subject.isEmpty() && !"target".equals(subject)) {
                throw new IllegalArgumentException("Condition '" + raw + "' states the subject '" + subject
                        + "' for \"is_same_element_as_self\"; it is asked about the unit being tested "
                        + "(write \"target is_same_element_as_self\", or nothing at all inside target_when) "
                        + "(source: " + spec.getSource() + ")");
            }
            return new IsSameElementAsSelf(raw);
        }

        // `<subject>_debuff:<ATTR>`: the party carries a negative modifier on that attribute -- "\u9632\u5fa1\u529b\u88ab\u964d\u4f4e"
        // / "\u51cf\u901f" name the ATTRIBUTE that was lowered, and a modifier's DEBUFF source is the fact that says so.
        Matcher debuffOn = DEBUFF_ON.matcher(text);
        if (debuffOn.find()) {
            String subject = normalize(debuffOn.group("subject"));
            String attribute = debuffOn.group("attribute") == null ? "" : debuffOn.group("attribute").trim();
            String trailing = text.substring(debuffOn.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes \"" + trailing + "\" after the attribute name; the keyword "
                                + "takes exactly one attribute, for example \"target_debuff:DEFENCE\" "
                                + "(source: " + spec.getSource() + ")");
            }
            return new HasDebuffOn(subject, attribute, raw, spec);
        }

        Matcher fromSkill = FROM_SKILL.matcher(text);
        if (fromSkill.find()) {
            String before = normalize(text.substring(0, fromSkill.start()));
            String slot = text.substring(fromSkill.end()).trim();
            if (!before.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something before \"from_skill\": the keyword takes no "
                                + "subject -- who caused it is its own condition, so write for example "
                                + "\"actor == self\" and \"from_skill SKILL\" (source: " + spec.getSource() + ")");
            }
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !CAST_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks which slot caused the event, but " + spec.getOn()
                                + " carries no cast category, so the condition could never hold; it works on "
                                + String.join(" / ", CAST_CARRYING_EVENTS.stream().map(Enum::name).sorted().toList())
                                + " (source: " + spec.getSource() + ")");
            }
            return new FromSkill(requireInBattleSlot(slot, raw, spec), raw, spec);
        }

        Matcher fromCategory = FROM_CATEGORY.matcher(text);
        if (fromCategory.find()) {
            String before = normalize(text.substring(0, fromCategory.start()));
            String stated = text.substring(fromCategory.end()).trim();
            if (!before.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something before \"from_category\": the keyword takes no "
                                + "subject -- who cast it is its own condition, so write for example "
                                + "\"actor == self\" and \"from_category ELATION_DAMAGE\" (source: " + spec.getSource() + ")");
            }
            // \u2605 Two spellings, one fact: the skill data says `ElationDamage`, the enum's own name is `ELATION_DAMAGE`.
            // The project already lives with this pair for attributes (JSON name vs enum name), so both are accepted --
            // `fromString` for the data's spelling, `valueOf` for the engine's.
            SkillCategory wanted = SkillCategory.fromString(stated);
            if (wanted == null) {
                try {
                    wanted = SkillCategory.valueOf(stated.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                    wanted = null;
                }
            }
            if (wanted == null || !wanted.isKnownValue()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names the cast category '" + stated + "', which the engine does not "
                                + "know; the categories are the ones the skill data spells (source: " + spec.getSource() + ")");
            }
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !CAST_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks the cast's category, but " + spec.getOn()
                                + " carries none, so the condition could never hold; it works on "
                                + String.join(" / ", CAST_CARRYING_EVENTS.stream().map(Enum::name).sorted().toList())
                                + " (source: " + spec.getSource() + ")");
            }
            return new FromCategory(wanted, raw);
        }

        if (!containsOperator(text)) {
            if ("self".equals(normalize(text))) {
                return new Equality("actor", "self", false);
            }
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' is not a known shorthand; use e.g. \"self\" or "
                            + "\"actor != self\" (source: " + spec.getSource() + ")");
        }

        String[] parts = splitOperator(text, spec);
        String left = normalize(parts[0]);
        String operator = parts[1];
        String right = normalize(parts[2]);

        if ("==".equals(operator) || "!=".equals(operator)) {
            boolean negated = "!=".equals(operator);
            // An identity comparison: one side names a PARTY (`self` = the rule's owner, `summon` = a summon
            // of the owner) and the other names WHICH party (`actor` / `target`). Written symmetrically, so
            // `summon == actor` reads the same as `actor == summon`.
            String term = IDENTITY_TERMS.contains(left) ? left
                    : IDENTITY_TERMS.contains(right) ? right : null;
            if (term != null) {
                String other = term.equals(left) ? right : left;
                String variable = requireIdentityVariable(other, raw, spec);
                requireCarriedParty(variable, raw, spec);
                return new Equality(variable, term, negated);
            }
            // Neither side is "self", so this is not an identity comparison — it is a numeric one, and
            // `hit_count == 2` has been in this DSL's documentation since its first version while the parser
            // reported it as "compares two variables" (found 2026-09-27 by a test that needed `== 0`). Fall
            // through to the numeric path; a comparison between two *names* still gets the clearer message.
            if (!isNumeric(left) && !isNumeric(right)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' compares two variables; only comparisons against a party ("
                                + String.join(", ", IDENTITY_TERMS.stream().sorted().toList())
                                + "), or against a numeric literal (e.g. \"hit_count == 2\"), are "
                                + "supported (source: " + spec.getSource() + ")");
            }
        }

        // Numeric comparison against a literal.
        String variable;
        double literal;
        boolean literalOnLeft = isNumeric(left);
        if (literalOnLeft) {
            variable = right;
            literal = Double.parseDouble(left);
        } else if (isNumeric(right)) {
            variable = left;
            literal = Double.parseDouble(right);
        } else {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' has no numeric literal on either side (source: "
                            + spec.getSource() + ")");
        }
        if (!NUMERIC_VARIABLES.contains(variable) && !variable.startsWith(SELF_ATTR_PREFIX)
                && !variable.startsWith(SELF_RESOURCE_PREFIX) && !variable.startsWith(SELF_STACKS_PREFIX)
                && !variable.startsWith(TARGET_STACKS_PREFIX) && !variable.startsWith(ACTOR_STACKS_PREFIX)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' compares unknown variable '" + variable
                            + "'; known numeric variables: " + String.join(", ", knownNumericVariables())
                            + ", plus \"self_attr:<ATTRIBUTE>\" for one of my own attribute values, e.g. "
                            + "\"self_attr:SPEED >= 145\", \"self_resource:<NAME>\" for how much of one "
                            + "of MY declared resources I hold, e.g. \"self_resource:充能 >= 3\", and "
                            + "\"self_stacks:<NAME>\" / \"target_stacks:<NAME>\" for how many times a named "
                            + "counter has been marked, e.g. \"target_stacks:承负 >= 2\" "
                            + "(source: " + spec.getSource() + ")");
        }
        boolean onActor = variable.startsWith(ACTOR_STACKS_PREFIX);
        return new Numeric(variable, selfAttributeOf(variable, raw, spec),
                selfResourceOf(variable, raw, spec),
                onActor ? variable.substring(ACTOR_STACKS_PREFIX.length()).trim()
                        : stacksNameOf(variable, raw, spec),
                variable.startsWith(TARGET_STACKS_PREFIX), onActor, operator, literal, literalOnLeft);
    }

    /**
     * The attribute behind a {@code self_attr:<ATTRIBUTE>} variable, or {@code null} for a plain name.
     *
     * <p>Two load-time rejections, both of them things that would otherwise look like a working rule:
     * <ul>
     *   <li>an attribute name that is not in {@link AttributeType} — a typo would compare against nothing
     *       and the rule would simply never fire;</li>
     *   <li>one of the four {@code *_PERCENT} builder keys ({@link AttributeType#isPercentVariant()}). They
     *       are inputs to {@code AttributeBuilder}, not runtime attributes: the builder folds them into
     *       their base attribute and stores the slot as {@code null}, so reading one would NPE at fire time
     *       — during a battle, in the middle of a damage calculation. The author means {@code
     *       HEALTH_PERCENT}'s <em>base</em> here, so say which one they want instead.</li>
     * </ul>
     *
     * @param variable the already-normalised variable token
     * @param raw      the original condition text, for the error messages
     * @param spec     the owning rule, for the source
     * @return the attribute, or {@code null} when the variable is a plain numeric name
     */
    private static AttributeType selfAttributeOf(String variable, String raw, TriggerSpec spec) {
        if (!variable.startsWith(SELF_ATTR_PREFIX)) {
            return null;
        }
        String name = variable.substring(SELF_ATTR_PREFIX.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + SELF_ATTR_PREFIX + "\" with no attribute after it; "
                            + "give one, e.g. \"self_attr:SPEED >= 145\" (source: " + spec.getSource() + ")");
        }
        AttributeType type;
        try {
            type = AttributeType.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' reads unknown attribute '" + name + "'; use a name from "
                            + "AttributeType, e.g. SPEED / ATTACK / CRIT_CHANCE / BREAKING_EFFECT "
                            + "(source: " + spec.getSource() + ")");
        }
        if (type.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' reads '" + name + "', which is one of the four *_PERCENT "
                            + "AttributeBuilder input keys: the builder folds it into its base attribute and "
                            + "leaves the runtime slot null, so this could only ever fail at fire time. Read "
                            + "the base attribute instead (" + type.attributeString.replace("_percent", "")
                            + ") (source: " + spec.getSource() + ")");
        }
        return type;
    }

    /**
     * The resource behind a {@code self_resource:<NAME>} variable, or {@code null} for anything else.
     *
     * <p>Only the <b>shape</b> is checked here; whether the character declares that resource is checked where
     * the character is assembled, because a rule file cannot know its own cid and a <b>relic</b> rule is shared
     * by every wearer (the same reason {@code SUMMON} is checked there). Two shapes are refused outright:
     * <ul>
     *   <li>nothing after the prefix — a variable that reads no resource at all;</li>
     *   <li>a name containing whitespace, which the DSL cannot even preserve: the condition is split on the
     *       operator and both halves trimmed, so {@code self_resource:我的 资源 >= 3} would silently read
     *       「我的」. A declaration refuses such a name for the same reason ({@code ResourceSpec}).</li>
     * </ul>
     *
     * @param variable the already-normalised variable token
     * @param raw      the original condition text, for the error messages
     * @param spec     the owning rule, for the source
     * @return the resource name, or {@code null} when the variable is not a resource read
     */
    /** The two spellings that read a named counter ({@code ADD_STACK}); the subject is the prefix. */
    /**
     * The two counter spellings, shared with the interpreter's {@code scale} family (2026-09-29): a condition says
     * "I have 3 layers", and a scale says "the number is a share of how many layers I have" -- one wording, and making
     * them public is what keeps the two from drifting (the same reason {@link #SELF_ATTR_PREFIX} is not private).
     */
    public static final String SELF_STACKS_PREFIX = "self_stacks:";
    /**
     * \u2705 The counter of the unit that CAUSED the event (2026-09-30; reader: cone 23061's \u300c\u6211\u65b9\u4efb\u610f\u89d2\u8272\u5728\u81ea\u8eab\u540c\u4e00\u56de\u5408\u5185\u7d2f\u8ba1\u6d88\u8017 \u2265 4 \u70b9\u6218\u6280\u70b9\u300d).
     *
     * <p>\u2605 The third subject, and it was missing: {@code self_stacks:} reads the rule's OWNER and {@code target_stacks:}
     * the event's target, but "any of our characters spends" puts the counter on the SPENDER -- a unit that is neither.
     * With {@code SKILL_POINT_SPENT} now naming its spender as the actor ({@code onSpent(user, amount)}), a rule owned by
     * the light cone's wearer can finally read it.
     */
    public static final String ACTOR_STACKS_PREFIX = "actor_stacks:";
    public static final String TARGET_STACKS_PREFIX = "target_stacks:";

    /**
     * The counter a {@code self_stacks:<NAME>} / {@code target_stacks:<NAME>} variable reads, or {@code null}.
     *
     * <p>⚠ Two spellings rather than one plus a subject, following the house convention ({@code hp_percent} vs
     * {@code target_hp_percent}, {@code self_summon_count} vs {@code target_summon_count}): the subject is part of the
     * name, so a rule cannot read "somebody's count" without saying whose.
     */
    private static String stacksNameOf(String variable, String raw, TriggerSpec spec) {
        String prefix;
        if (variable.startsWith(SELF_STACKS_PREFIX)) {
            prefix = SELF_STACKS_PREFIX;
        } else if (variable.startsWith(TARGET_STACKS_PREFIX)) {
            prefix = TARGET_STACKS_PREFIX;
        } else {
            return null;
        }
        String name = variable.substring(prefix.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + prefix + "\" with no counter name after it; give the name "
                            + "an ADD_STACK effect created, e.g. \"" + prefix + "承负 >= 2\" "
                            + "(source: " + spec.getSource() + ")");
        }
        return name;
    }

    private static String selfResourceOf(String variable, String raw, TriggerSpec spec) {
        if (!variable.startsWith(SELF_RESOURCE_PREFIX)) {
            return null;
        }
        String name = variable.substring(SELF_RESOURCE_PREFIX.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + SELF_RESOURCE_PREFIX + "\" with no resource after "
                            + "it; give one of the resources the character declares, e.g. "
                            + "\"self_resource:充能 >= 3\" (source: " + spec.getSource() + ")");
        }
        // ⚠ Spelled `java.lang.Character`: this package has its own `Character`, and the unqualified name
        // would be the combatant rather than the code-point test.
        if (name.chars().anyMatch(c -> java.lang.Character.isWhitespace(c))) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' reads the resource \"" + name + "\", which contains whitespace; "
                            + "a condition is split on its operator and trimmed, so a name with a space in it "
                            + "cannot be read back (source: " + spec.getSource() + ")");
        }
        return name;
    }

    /**
     * The known numeric variable names, sorted, for the error message.
     *
     * <p>Derived from {@link #NUMERIC_VARIABLES} rather than typed out, so the message cannot drift
     * away from the set the parser actually accepts — a message that lies about the vocabulary is
     * worse than no message, because the author trusts it.
     */
    private static List<String> knownNumericVariables() {
        return NUMERIC_VARIABLES.stream().sorted().toList();
    }

    /**
     * The two variables an identity comparison can ask about: who caused the event, and what it happened to.
     */
    private static final Set<String> IDENTITY_VARIABLES = Set.of("actor", "target");

    /**
     * The two parties an identity comparison can compare against: the rule's own character, and the summons
     * that character owns.
     *
     * <p>Deliberately not the whole target-selector vocabulary. The other selectors are either not a single
     * unit ({@code all_allies} / {@code party}) or would make the comparison vacuous ({@code attacker} can only
     * be the actor, and {@code target == target} is a tautology because the event already says who it happened
     * to) — and a spelling that can never mean anything is exactly what this DSL refuses at load time rather
     * than letting an author write it and wonder.
     */
    private static final Set<String> IDENTITY_TERMS = Set.of("self", "summon", "countdown");

    /**
     * Checks that an identity comparison names a variable the DSL knows.
     *
     * <p>The set is closed on purpose: a typo such as {@code actor == sself} must fail at load time
     * rather than evaluate to false forever.
     *
     * @param token the variable name (already lower-cased)
     * @param raw   the original condition text, for the error message
     * @param spec  the owning rule, for the source
     * @return the variable name
     */
    private static String requireIdentityVariable(String token, String raw, TriggerSpec spec) {
        if (!IDENTITY_VARIABLES.contains(token)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' compares a known party with '" + token
                            + "', which is neither an identity variable ("
                            + String.join(", ", IDENTITY_VARIABLES.stream().sorted().toList())
                            + ": who caused the event / what it happened to) nor an identity term ("
                            + String.join(", ", IDENTITY_TERMS.stream().sorted().toList())
                            + ": the rule's owner / a summon of the rule's owner) (source: "
                            + spec.getSource() + ")");
        }
        return token;
    }

    /**
     * Checks that a {@code has_state} condition asks about a party the DSL knows.
     *
     * <p>{@code self} is allowed here (unlike in an identity comparison, where it would be the pointless
     * "self == self"): "which state am I in" is the most common question the condition exists for.
     *
     * @param subject the party token (already lower-cased)
     * @param raw     the original condition text, for the error message
     * @param spec    the owning rule, for the source
     * @return the party token
     */
    private static String requireStateSubject(String subject, String raw, TriggerSpec spec) {
        if (!STATE_SUBJECTS.contains(subject)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' asks whether '" + subject + "' is in a named state, but the "
                            + "parties are " + String.join(", ", STATE_SUBJECTS.stream().sorted().toList())
                            + " (self = the character whose table fired, actor = who caused the event, "
                            + "target = what it happened to) (source: " + spec.getSource() + ")");
        }
        return subject;
    }

    /**
     * The Path a {@code has_path} condition names, checked against the closed vocabulary of the nine.
     *
     * <p>Unlike {@code Path.fromName} — which degrades an unknown name to {@link
     * com.laosun.aluminium.enums.Path#OTHER} because data may legitimately carry a Path this build does not know —
     * a <b>rule file</b> may not: {@code target has_path 同谐} with a typo would silently become "the target is on
     * some other Path", and for 星期日's sentence that means the exception fires on exactly the units it was
     * written to exclude. So the name is refused while the file is read.
     */
    private static com.laosun.aluminium.enums.Path requirePath(String name, String raw, TriggerSpec spec) {
        com.laosun.aluminium.enums.Path path = com.laosun.aluminium.enums.Path.fromNameOrNull(name);
        if (path == null) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' has unknown \"has_path\" name '" + name + "'; the Paths are "
                            + String.join(", ", com.laosun.aluminium.enums.Path.names().stream().sorted().toList())
                            + " (source: " + spec.getSource() + ")");
        }
        return path;
    }

    /**
     * Refuses a condition that reads a party the event <b>never carries</b>.
     *
     * <p>{@link TriggerEvent#BATTLE_START} is fired once, to every character's table, with <b>no</b> actor and no
     * target ({@code Battle.startBattle} → {@code fireTriggers(TriggerEvent.BATTLE_START)}). A rule written there
     * as {@code "when": ["actor == self"]} therefore <b>can never fire</b> — and it looks completely reasonable,
     * which is exactly the failure mode this DSL refuses everywhere else: a rule that loads, is filed under the
     * right event, and silently does nothing. (Written by hand first, caught by the rule failing to grant anything,
     * 2026-09-27.) On this event "my own table" is already the unit of delivery — the table <i>is</i> the owner's —
     * so the condition was never needed.
     *
     * <p>{@code self} is always carried (the owner exists for every event) and is therefore always allowed.
     *
     * @param party the party token (already validated as a known one)
     * @param raw   the original condition text, for the message
     * @param spec  the owning rule, for the source and the event
     * @return the party token
     */
    private static String requireCarriedParty(String party, String raw, TriggerSpec spec) {
        if ("self".equals(party)) {
            return party;
        }
        if (TriggerEvent.fromString(spec.getOn()) == TriggerEvent.BATTLE_START) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' asks about '" + party + "', but " + TriggerEvent.BATTLE_START.value()
                            + " carries no actor and no target, so the rule could never fire. This event is delivered "
                            + "to every character's own table, so \"my own battle start\" needs no condition at all "
                            + "(source: " + spec.getSource() + ")");
        }
        return party;
    }

    private static boolean containsOperator(String text) {
        return text.contains("==") || text.contains("!=")
                || text.contains(">=") || text.contains("<=")
                || text.contains(">") || text.contains("<");
    }

    private static String[] splitOperator(String text, TriggerSpec spec) {
        for (String op : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
            int at = text.indexOf(op);
            if (at >= 0) {
                String left = text.substring(0, at).trim();
                String right = text.substring(at + op.length()).trim();
                if (!left.isEmpty() && !right.isEmpty()) {
                    return new String[]{left, op, right};
                }
                break;
            }
        }
        throw new IllegalArgumentException(
                "Malformed condition '" + text + "' (source: " + spec.getSource() + ")");
    }

    private static String normalize(String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isNumeric(String token) {
        if (token.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * A validated rule: the event it listens for, its conditions, its effects, and how often it may fire.
     *
     * @param event         the subscribed event
     * @param conditions    all must hold
     * @param effects       run in order
     * @param source        where the rule came from, propagated into error messages
     * @param key           stable identity for the per-combatant firing limits (source + position)
     * @param id            the rule's optional name ({@code ""} when it states none), the handle a
     *                      {@code MODIFY_RULE} effect points at
     * @param cooldownTurns the owner's turns between two firings ({@code 0} = unlimited)
     * @param oncePerBattle {@code true} = at most one firing per battle
     * @param chance        the probability of firing at all, as a fraction of 1 ({@code 1.0} = always)
     * @param minEidolon    the Eidolon rank the owner needs ({@code 0} = ungated)
     */
    public record CompiledRule(TriggerEvent event, List<Condition> conditions,
                               List<EffectSpec> effects, String source, String key, String id,
                               int cooldownTurns, boolean oncePerBattle, double chance, int minEidolon,
                               int perTurn, int perAttack, List<List<Condition>> effectTargetFilters) {

        /**
         * The per-target conditions of one effect ({@code target_when}), by that effect's index in {@link #effects}.
         *
         * <p>Parsed at load time — a misspelled condition is refused rather than ignored — and kept <b>per effect</b>,
         * because two effects of one rule may filter differently.
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
            return cooldownTurns > 0 || oncePerBattle || perTurn > 0 || perAttack > 0;
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
     * is {@code target == self} — and when I am hit, the actor is the <b>attacker</b>, not me.
     *
     * <p><b>{@code battle}, and why a context carries one.</b> Some questions are about the
     * <b>field</b> rather than about the event: 「忆灵在场时」 ("while my memosprite is out") asks what
     * units exist, and 「我方全体」 has to name them as effect targets. No event can carry that, because
     * it is not a fact about what just happened — it is a fact about the battlefield, and the battlefield
     * is {@link Battle}. It is here for exactly those two questions and nothing else: a condition must
     * not use it to reach into engine state (that is what ops are for). A context built by hand without a
     * battle (as several tests do) makes any such condition <b>fail</b>, the same way a missing party
     * does — never silently pass.
     *
     * @param owner    the character this table belongs to ("self")
     * @param actor    who caused the event (may be {@code null})
     * @param target   the event's subject (may be {@code null})
     * @param hitCount how many targets an attack connected with (0 for non-attack events)
     * @param amount   the event's magnitude where it has one (energy credited, damage dealt, ...)
     * @param damage   the instance being settled, for the one event that has one
     *                 ({@link TriggerEvent#DEALING_DAMAGE}); {@code null} everywhere else
     * @param battle   the battle in progress, for questions about the field (may be {@code null} in a
     *                 hand-built context, which makes those conditions fail rather than guess)
     * @param fromCast the {@link SkillCategory} of the cast that produced this event's instance, when the event can
     *                 name one ({@link TriggerEvent#BREAK} / {@link TriggerEvent#KILL} / {@link
     *                 TriggerEvent#DEALING_DAMAGE}); {@code null} = "no cast caused it", which is what a break from a
     *                 rule-driven toughness reduction honestly is
     * @param ruleId   the {@code id} of the rule being applied ({@code ""} when it states none), written per <b>rule</b>
     *                 by the interpreter. It is what lets a buff remember which rule created it
     *                 ({@code AbstractBuff.getRuleId()}), which is how 「**战技提供的**护盾」 is asked about
     */
    public record TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,
                                 Damage damage, Battle battle, SkillCategory fromCast, String ruleId,
                                 List<Condition> targetFilter, int skillId, int weakHitCount) {

        /**
         * The same context for an event that carries no cast category — i.e. the common case.
         */
        public TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,
                              Damage damage, Battle battle, SkillCategory fromCast) {
            this(owner, actor, target, hitCount, amount, damage, battle, fromCast, "", List.of(), 0, 0);
        }

        /**
         * The same context, as the rule under it: the one caller that knows which rule is firing.
         *
         * <p><b>Why a copy rather than a mutable field.</b> A rule can fire inside another rule's effect (a counter
         * that strikes back), so "the rule being applied" is a per-firing fact, exactly like the rest of the context
         * — a shared mutable field would report the inner rule to the outer one's remaining effects.
         */
        public TriggerContext withRule(String id) {
            return new TriggerContext(owner, actor, target, hitCount, amount, damage, battle, fromCast,
                    id == null ? "" : id, targetFilter, skillId, weakHitCount);
        }
        /**
         * The same context, saying <b>which data row</b> of a skill produced this event (2026-09-28).
         *
         * <p>⚠ It is the row, not the slot: `Skill.getSkillSlot()` is what the data tables are indexed by, so an enhanced
         * attack (a row of its own, e.g. 1111's 【直冲碎天拳】 = 111108) is distinguishable from the ordinary basic attack it
         * replaces — which is exactly what 「**强化普攻**命中…」 asks. A shared mutable field would leak between nested
         * firings, so this is a copy like {@link #withRule(String)}.
         */
        public TriggerContext withSkillId(int id) {
            return new TriggerContext(owner, actor, target, hitCount, amount, damage, battle, fromCast, ruleId,
                    targetFilter, id, weakHitCount);
        }

        /**
         * The same context for an event that carries no cast category — i.e. the common case.
         */
        public TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,
                              Damage damage, Battle battle) {
            this(owner, actor, target, hitCount, amount, damage, battle, null, "", List.of(), 0, 0);
        }

        /**
         * The context of an event that carries no damage instance — i.e. every event but
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
         * \u2705 The same context carrying how many hit targets shared the attack's weakness (2026-09-30; reader: cone 21040).
         *
         * <p>\u26a0 A COPY helper rather than a wider constructor on purpose: the cast events are built by chaining these
         * ({@code new TriggerContext(...).withSkillId(...)}), so a value that only the canonical constructor knows is
         * silently dropped by every chain that starts from a compact one -- measured, and the reason this exists.
         */
        public TriggerContext withWeakHitCount(int count) {
            return new TriggerContext(owner, actor, target, hitCount, amount, damage, battle, fromCast, ruleId,
                    targetFilter, skillId, count);
        }

        /**
         * The same context with this firing's <b>per-target filter</b> (an effect's {@code target_when}).
         *
         * <p>Set by the interpreter once per effect and read only by {@code resolveTargets} — that being the single
         * place that decides which units an effect reaches, no op had to learn this vocabulary.
         */
        public TriggerContext withTargetFilter(List<Condition> filter) {
            return new TriggerContext(owner, actor, target, hitCount, amount, damage, battle, fromCast, ruleId,
                    filter == null ? List.of() : filter, skillId, weakHitCount);
        }

        /**
         * The same context with a different subject and <b>no filter</b>: the context a per-target condition is tested
         * in (its {@code target} is the candidate). ⚠ The filter is dropped deliberately — asking a candidate about the
         * filter that is asking about it would recurse.
         */
        public TriggerContext withSubject(CanHit candidate) {
            return new TriggerContext(owner, actor, candidate, hitCount, amount, damage, battle, fromCast, ruleId,
                    List.of(), 0, weakHitCount);
        }

        /** Whether {@code candidate} passes the per-target conditions (an empty filter admits everything). */
        public boolean passesTargetFilter(CanHit candidate) {
            for (Condition condition : targetFilter) {
                if (!condition.test(withSubject(candidate))) {
                    return false;
                }
            }
            return true;
        }

        public static TriggerContext of(CanHit owner, CanHit actor) {
            return new TriggerContext(owner, actor, null, 0, 0, null, null, null, "", List.of(), 0, 0);
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
    private interface PartyCondition extends Condition {
        /**
         * The party this condition reads, or {@code null} when it does not exist for this event.
         */
        CanHit partyOf(TriggerContext ctx);
    }

    /**
     * {@code !<condition>} — the condition fails exactly when the one after it passes.
     *
     * <p><b>Why a prefix and not a "not equal" spelling per family.</b> Every family would otherwise need its own
     * negation ({@code has_state} / {@code has_path} / the numeric comparisons), and the ones that already have
     * one ({@code actor != self}) would have two ways to say it. One prefix covers the ones that need it, and it
     * is read <b>before</b> anything else, so {@code !target has_path 同谐} cannot be confused with the {@code !=}
     * operator.
     *
     * <p>⚠ <b>Only party-reading conditions may be negated</b> (see {@link PartyCondition}), and the check is done
     * while the file is read. Two reasons, both about silence:
     * <ul>
     *   <li>"the party does not exist" must stay a <b>failure</b> in both polarities — for the numeric variables
     *       that fact is {@code NaN}, and {@code !(NaN > 0)} is <b>true</b>, i.e. negating a number would turn
     *       "cannot read it" into "matches";</li>
     *   <li>numbers already have the opposite spelling ({@code self_summon_count == 0}), so refusing the
     *       ambiguous one points the author at the better form instead of guessing.</li>
     * </ul>
     * The inner condition is compiled by the same {@code parseCondition}, so a negated typo is still refused with
     * the message that names the typo (not "unknown condition").
     */
    private static final class Negated implements Condition {

        private final Condition inner;
        private final PartyCondition party;
        private final String raw;

        Negated(PartyCondition inner, String raw) {
            this.inner = inner;
            this.party = inner;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return party.partyOf(ctx) != null && !inner.test(ctx);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Weakness test: {@code target has_weakness Fire} — 「命中具有火属性弱点的敌人时」 (relic set 316).
     *
     * <p><b>Why it needs a word of its own.</b> A weakness is a property of the <b>enemy</b>
     * ({@link com.laosun.aluminium.models.enemy.Enemy#isWeakTo}), not a buff and not an attribute: no existing
     * condition can ask about it, and the sentences that need it are otherwise pure data
     * (「命中具有 X 属性弱点的敌人时，装备者的击破特攻提高 20%，持续 2 回合」).
     *
     * <p>⚠ Only an {@link com.laosun.aluminium.models.enemy.Enemy} answers: a character (or a summon) has no weakness
     * bar, so the condition is <b>false</b> for one — the "cannot read it, therefore it fails" rule the whole family
     * follows, rather than "no weakness means no weakness to Fire".
     */
    private static final class HasWeakness implements Condition, PartyCondition {

        private final String subject;
        private final DamageElement element;
        private final String raw;

        HasWeakness(String subject, DamageElement element, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.element = element;
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
            return partyOf(ctx) instanceof com.laosun.aluminium.models.enemy.Enemy enemy
                    && enemy.isWeakTo(element);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * The element a {@code has_weakness} condition names, checked against the closed enum.
     *
     * <p>Both spellings the data uses are accepted ({@code Fire} and {@code FIRE}); anything else is refused while the
     * file is read, because a misspelled element would make the condition silently false on every enemy — 「命中具有
     * 火属性弱点的敌人时」 would simply never fire, with nothing to see.
     */
    private static DamageElement requireElement(String name, String raw, TriggerSpec spec) {
        DamageElement element = DamageElement.fromString(name);
        if (element == null && name != null) {
            element = DamageElement.fromString(name.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (element == null) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names an unknown element '" + name + "'; write one of "
                            + java.util.Arrays.toString(DamageElement.values())
                            + " (source: " + spec.getSource() + ")");
        }
        return element;
    }

    /**
     * Party-composition test: {@code self has_same_path_ally} — 「若至少存在一名与装备者命途相同的队友」.
     *
     * <p><b>Why it reads the field.</b> A relic can be worn by anybody, so 「与装备者命途相同」 cannot be written as
     * a Path name: the rule has to compare whoever is wearing it against the rest of the side. That is a fact about
     * the battlefield, like {@code self_summon_count} — and it is the first condition whose answer depends on the
     * <b>party</b> rather than on the owner, the event or the subject.
     *
     * <p>⚠ Three things it deliberately does not count: a <b>dead</b> ally (a corpse is not somebody on the field —
     * the same reading {@code Battle.summonsOf} uses), the wearer itself, and a Path the engine cannot name
     * ({@link com.laosun.aluminium.enums.Path#OTHER}): two units whose data carries an unknown Path are not "the same
     * Path", they are both unknown, and matching them would be a coincidence dressed as a rule.
     */
    private static final class HasSamePathAlly implements Condition, PartyCondition {

        private final String raw;

        HasSamePathAlly(String raw, TriggerSpec spec) {
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return ctx.owner();
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (ctx.battle() == null || !(ctx.owner() instanceof Character me)) {
                return false;
            }
            com.laosun.aluminium.enums.Path mine = me.getPath();
            if (mine == com.laosun.aluminium.enums.Path.OTHER) {
                return false;
            }
            for (CanHit ally : ctx.battle().allies) {
                if (ally != me && !ally.isDeath() && ally instanceof Character other && other.getPath() == mine) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Side test: {@code target is_ally} — "the unit this cast was aimed at is on our side".
     *
     * <p><b>Why the DSL needs it.</b> Three shipped relic abilities say 「对<b>己方角色</b>施放终结技/战技时」 (sets 114,
     * 118, 121). Since M-35 a cast event carries the unit it <b>aimed at</b> ({@code ctx.target()}), which is what
     * 「对…施放」 names — but the event says nothing about that unit's <b>side</b>, and a damaging ultimate aimed at an
     * enemy carries a target too. Without this predicate the rule would fire on every cast of that slot: an
     * over-trigger with nothing to report.
     *
     * <p>The side is taken from the battlefield ({@code battle.allies}, the same roster {@code all_allies} reads), so
     * a context with no battle answers "no" — the "cannot read it, therefore the condition fails" rule every
     * field-reading condition follows. ⚠ It is a {@link PartyCondition}, so {@code !target is_ally} means the
     * opposite <b>and</b> still fails when there is no target at all (a missing party must never become a match).
     */
    /**
     * {@code <who> is_other_ally} ? the unit is on our side <b>and is not the rule's owner</b> (2026-09-28).
     *
     * <p>The "other" half is the whole point: {@code is_ally} already answers "on our side", and the owner satisfies it. A rule
     * that means 「my ALLY did something」 must exclude its own actions, or the follow-up it grants would trigger itself.
     */
    private static final class IsOtherAlly implements Condition, PartyCondition {
        private final String subject;
        private final String raw;

        IsOtherAlly(String subject, String raw) {
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
            if (who == null || who == ctx.owner() || ctx.battle() == null) {
                return false;
            }
            return ctx.battle().allies.contains(who);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    private static final class IsAlly implements Condition, PartyCondition {

        private final String subject;
        private final String raw;

        IsAlly(String subject, String raw, TriggerSpec spec) {
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
            return who != null && ctx.battle() != null && ctx.battle().allies.contains(who);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Shield test: {@code target has_shield} / {@code self has_shield}.
     *
     * <p><b>Why the condition DSL needs it.</b> 三月七's Talent is 「当<b>持有护盾的</b>我方目标受到敌方目标攻击后，三月七
     * 立即向攻击者发起反击」 — the trigger is about a shielded ally, and the engine's shield is a plain number on
     * the combatant ({@code CanHit.getShield()}) that no condition could ask about. 13 of the 97 character documents
     * say 「持盾时」 somewhere (符玄 / 砂金 / 杰帕德 and the shield family), which makes it the second-largest hole
     * after the resource count.
     *
     * <p>⚠ <b>"Has a shield" means the value is above 0</b>, not "a shield was granted at some point": a shield
     * that has been used up is gone, and a clause gated on 「持有护盾的」 must stop matching then. That is also why
     * it reads the <b>live</b> value rather than asking the buff manager for a {@code ShieldBuff} — the two agree
     * while a timed shield is up (the buff installs it), and the value is the one the damage path actually drains.
     *
     * <p>⚠ <b>The origin is askable too</b> ({@code has_shield from_rule <id>}, 2026-09-28): 「战技提供的护盾」 names the
     * ability, not just the giver, and 三月七 has two shields of her own — so the shield records the rule that created it
     * ({@code CanHit.getShieldRuleId()}) and this condition can require it. ⚠ A raw grant states no rule, so a
     * {@code from_rule} question correctly answers "no" for it.
     */
    private static final class HasShield implements Condition, PartyCondition {
        private final String subject;
        private final String ruleId;
        private final String raw;

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
            // what makes 「战技提供的」 different from 「三月七给的」 (see CanHit.getShieldRuleId).
            return ruleId.isEmpty() || ruleId.equals(who.getShieldRuleId());
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code from_skill COMMON|SKILL|ULTRA|TALENT} — the instance that caused this event came from that slot.
     *
     * <p><b>Why the DSL needs it.</b> 1003 姬子's 星魂 4 says 「**施放战技**对敌方目标造成弱点击破时，姬子额外获得1点充能」 and
     * her ultimate pays per kill 「**每消灭1个**敌方目标」. {@code BREAK} and {@code KILL} already carry <b>who</b> caused
     * them ({@code actor}), but not <b>which ability</b>: without this term `actor == self` on a break would also pay for
     * a break left by her basic attack or by her talent's follow-up, which the text excludes — a wrong number with
     * nothing to report.
     *
     * <p>⚠ <b>No subject.</b> "Who did it" is its own condition ({@code actor == self}), so writing one here would be a
     * second way to say the same thing.
     *
     * <p>⚠ A break/kill with no causing instance (a rule that reduces toughness directly, an enemy skill that builds
     * its damage inline) answers <b>false</b>: the event happened, but nothing can say which skill produced it.
     */
    /**
     * {@code damage_is_attack} ? "the instance being settled counts as an attack" (2026-09-28).
     *
     * <p>Additional damage is an instance too, and it is deliberately marked {@code notCountsAsAttack()}, so this is the one
     * question that tells the two apart where it matters: a rule that would otherwise react to its own extra instance.
     */
    /**
     * \u2605 \u300c\u4e0e\u88c5\u5907\u8005\u76f8\u540c\u5c5e\u6027\u7684\u5176\u4ed6\u6211\u65b9\u89d2\u8272\u300d: the candidate\u2019s element against the rule owner\u2019s own. A unit with no
     * element (or the placeholder) is not "the same element" -- the same refusal {@code has_same_path_ally} makes about
     * unknown Paths, because matching two unknowns would be a coincidence dressed as a rule.
     */
    private static final class IsSameElementAsSelf implements Condition, PartyCondition {
        private final String raw;
        private final boolean otherOnly;

        IsSameElementAsSelf(String raw) {
            this(raw, false);
        }

        IsSameElementAsSelf(String raw, boolean otherOnly) {
            this.raw = raw;
            this.otherOnly = otherOnly;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return ctx.target();
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (otherOnly && ctx.target() == ctx.owner()) {
                return false;          // \u300c\u5176\u4ed6\u6211\u65b9\u89d2\u8272\u300d does not include the wearer
            }
            com.laosun.aluminium.enums.DamageElement mine = elementOf(ctx.owner());
            com.laosun.aluminium.enums.DamageElement theirs = elementOf(ctx.target());
            return mine != null && mine == theirs;
        }

        private static com.laosun.aluminium.enums.DamageElement elementOf(CanHit unit) {
            if (unit instanceof com.laosun.aluminium.models.Character character) {
                return character.getElement();
            }
            if (unit instanceof com.laosun.aluminium.models.Summon summon
                    && summon.getMaster() instanceof com.laosun.aluminium.models.Character master) {
                return master.getElement();
            }
            return null;
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    /** \u2605 The element half of {@link #DAMAGE_IS_ATTACK}: the instance\u2019s element against the rule owner\u2019s own. */
    private static final class DamageElementIsSelf implements Condition {
        private final String raw;

        DamageElementIsSelf(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (ctx.damage() == null || ctx.damage().getElement() == null) {
                return false;
            }
            CanHit owner = ctx.owner();
            if (owner instanceof com.laosun.aluminium.models.Summon summon && summon.getMaster() != null) {
                owner = summon.getMaster();
            }
            if (!(owner instanceof com.laosun.aluminium.models.Character character)) {
                return false;
            }
            return character.getElement() != null && character.getElement() == ctx.damage().getElement();
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    private static final class DamageIsAttack implements Condition {
        private final String raw;

        DamageIsAttack(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            // \u2605 Was `!ctx.damage().isCountsAsAttack()`, i.e. the exact opposite of the keyword's contract (measured
            // 2026-09-30: a rule guarded by `damage_is_attack` fired on ADDITIONAL damage and never on a real attack --
            // cone 23008's energy clause read +0.0 because of it). `Damage.countsAsAttack` defaults to true, so the
            // negation made every ordinary attack fail the guard it was written to pass.
            return ctx.damage() != null && ctx.damage().isCountsAsAttack();
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /** \u2605 {@code from_category}: the causing cast's category, read straight from the event's context. */
    private static final class FromCategory implements Condition {

        private final SkillCategory wanted;
        private final String raw;

        FromCategory(SkillCategory wanted, String raw) {
            this.wanted = wanted;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return ctx.fromCast() != null && ctx.fromCast() == wanted;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    private static final class FromSkill implements Condition {

        private final SkillType slot;
        private final String raw;

        FromSkill(SkillType slot, String raw, TriggerSpec spec) {
            this.slot = slot;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            SkillCategory wanted = SkillCategory.of(slot);
            return ctx.fromCast() != null && ctx.fromCast() == wanted;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Reads the slot {@code from_skill} names, refusing anything that is not an <b>in-battle</b> cast.
     *
     * <p>Closed set, like every other vocabulary here: a typo would make the condition never true, and 秘技/地图普攻
     * ({@code MAZE} / {@code TECHNIQUE}) never produce an in-battle damage instance at all — so they are refused with
     * the list instead of silently never matching.
     */
    private static SkillType requireInBattleSlot(String name, String raw, TriggerSpec spec) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' has no slot after \"from_skill\": it needs one of "
                            + inBattleSlots() + " (source: " + spec.getSource() + ")");
        }
        SkillType slot;
        try {
            slot = SkillType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names the unknown skill slot '" + name + "'; known slots are "
                            + inBattleSlots() + " (source: " + spec.getSource() + ")");
        }
        if (SkillCategory.of(slot) == null) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names the slot " + slot + ", which is not an in-battle cast "
                            + "(秘技 / 地图普攻 produce no in-battle damage instance), so the condition could never "
                            + "hold; use one of " + inBattleSlots() + " (source: " + spec.getSource() + ")");
        }
        return slot;
    }

    private static String inBattleSlots() {
        return String.join(" / ", List.of(SkillType.COMMON.name(), SkillType.SKILL.name(),
                SkillType.ULTRA.name(), SkillType.TALENT.name()));
    }

    /**
     * Path test: {@code target has_path 同谐} / {@code self has_path 存护}.
     *
     * <p><b>Why the condition DSL needs it.</b> 星期日's Skill says 「当星期日对「同谐」命途的角色施放该技能时，
     * <b>无法触发</b>立即行动效果」 — an exception keyed on the target's <b>Path</b>, which no other condition can
     * ask about. The Path is already engine knowledge ({@code Character.getPath()}, the aggro tier), so this is a
     * vocabulary addition, not new data.
     *
     * <p>Read off the party named on the left, and <b>only</b> a character has one: a summon (or an enemy with no
     * character data) answers "no", which is the "cannot read it, therefore the condition fails" rule
     * {@link HasState} follows. ⚠ A Path the build does not recognise is {@link
     * com.laosun.aluminium.enums.Path#OTHER} — that is <b>not</b> equal to any of the nine, so a rule asking for
     * 同谐 does not accidentally match it.
     */
    private static final class HasPath implements Condition, PartyCondition {

        private final String subject;
        private final com.laosun.aluminium.enums.Path path;
        private final String raw;

        HasPath(String subject, com.laosun.aluminium.enums.Path path, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.path = path;
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
            return who instanceof Character character && character.getPath() == path;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Named-state test: {@code self has_state 协奏} / {@code target has_state 触电}.
     *
     * <p>Reads the state off the party named on the left through
     * {@link com.laosun.aluminium.models.buff.BuffManager#hasState(String)}, so "the target is shocked" and
     * "I am in 【转魄】" are the same mechanism — an ordinary buff that happens to be a
     * {@link com.laosun.aluminium.models.buff.StateBuff}. The state name is <b>not</b> lower-cased: it is
     * data, spelled by the rule file, and the two sides must agree exactly.
     *
     * <p>A party that does not exist for this event ({@code target} on an event with no subject)
     * <b>fails</b> the condition, exactly like {@link Equality} and {@link Numeric}: "the rule matched" must
     * never be the accidental outcome of a missing party.
     */
    /**
     * \u2705 "That party carries a negative modifier on this attribute" (2026-09-30).
     *
     * <p>\u2605 A party that does not exist for this event FAILS, exactly like {@link HasState}: "the rule matched" must never
     * be the accidental outcome of a missing party.
     */
    private static final class HasDebuffOn implements Condition, PartyCondition {

        private final String subject;
        private final String attribute;
        private final String raw;

        HasDebuffOn(String subject, String attribute, String raw, TriggerSpec spec) {
            if (!STATE_SUBJECTS.contains(subject)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks about a debuff on \"" + subject + "\"; the keyword names its "
                                + "party with one of " + STATE_SUBJECTS + " before \"_debuff:\" "
                                + "(source: " + spec.getSource() + ")");
            }
            if (attribute.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names no attribute after \"_debuff:\"; write the attribute whose "
                                + "value was lowered, for example \"target_debuff:DEFENCE\" "
                                + "(source: " + spec.getSource() + ")");
            }
            if (AttributeType.fromString(attribute) == null) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names the attribute \"" + attribute + "\", which is not one this "
                                + "engine knows (source: " + spec.getSource() + ")");
            }
            this.subject = subject;
            this.attribute = attribute;
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
            if (who == null) {
                return false;
            }
            AttributeType type = AttributeType.fromString(attribute);
            return !who.getAttribute(type).filterBySource(DoubleValue.Modifier.ModifierSource.DEBUFF).isEmpty();
        }

        @Override
        public String source() {
            return raw;
        }
    }

    private static final class HasState implements Condition, PartyCondition {

        private final String subject;
        private final String state;
        private final String raw;

        HasState(String subject, String state, String raw, TriggerSpec spec) {
            if (state.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names no state after \"has_state\" "
                                + "(source: " + spec.getSource() + ")");
            }
            this.subject = subject;
            this.state = state;
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
            return who != null && who.getBuffManager().hasState(state);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Identity comparison: {@code actor == self} / {@code actor != self} / {@code target == self} /
     * {@code actor == summon} / …
     *
     * <p>{@code actor} is who caused the event, {@code target} is what it happened to; {@code self} is the
     * rule's owner and {@code summon} is one of that owner's summons. See {@link TriggerContext}.
     */
    private static final class Equality implements Condition {

        private final String variable;
        /**
         * {@code self} or {@code summon} — which party the variable is compared against.
         */
        private final String otherToken;
        private final boolean negated;

        Equality(String variable, String otherToken, boolean negated) {
            this.variable = variable;
            this.otherToken = otherToken;
            this.negated = negated;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit subject = switch (variable) {
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
            if (!"summon".equals(otherToken) && !"countdown".equals(otherToken)) {
                boolean equal = subject == ctx.owner();
                return negated != equal;
            }
            // ⚠ Both remaining terms are read off the FIELD, so a context with no battle cannot answer them and the
            // condition fails for BOTH polarities -- the same convention `summon` follows (answering "no battlefield,
            // therefore not mine" would make `actor != countdown` silently true everywhere; see the note below).
            if (ctx.battle() == null || ctx.owner() == null) {
                return false;
            }
            if ("countdown".equals(otherToken)) {
                boolean own = ctx.battle().countdownsOf(ctx.owner()).contains(subject);
                return negated != own;
            }
            // "…is one of my summons" is read off the FIELD, so a context with no battle cannot answer it and
            // the condition fails — for BOTH polarities. That is the same rule `self_summon_count` follows
            // (NaN compares false against everything), and it matters more here: answering "no battlefield,
            // therefore not my summon" would make `actor != summon` silently TRUE for every event, i.e. a rule
            // that says "anyone but my summon attacked" would fire on everything. A wrong answer with no
            // symptom is what this convention exists to prevent.
            if (ctx.battle() == null || ctx.owner() == null) {
                return false;
            }
            // \u2b50 Identity, not headcount (2026-09-30; reader: cone 20022's \u300c\u5fc6\u7075\u6d88\u5931\u65f6\u79fb\u9664\u2026\u3010\u7f05\u6000\u3011\u300d): a summon that has ALREADY
            // DIED is still "one of my summons" -- the camp roster keeps its corpse, and the case that needs this is
            // exactly the KILL whose victim is that corpse. \u26a0 `summonsOf` deliberately answers the LIVING question
            // (self_summon_count reads it, and a headcount of corpses is not what it means), so identity asks the unit
            // itself instead. Measured: with the living list, `target == summon` was false for a just-killed memosprite.
            boolean own = subject instanceof Summon summon && summon.getMaster() == ctx.owner();
            return negated != own;
        }

        @Override
        public String source() {
            return variable + (negated ? " != " : " == ") + otherToken;
        }
    }

    /**
     * Numeric comparison such as {@code hit_count > 0} or {@code self_attr:SPEED >= 145}.
     */
    private static final class Numeric implements Condition {

        private final String variable;
        /**
         * The attribute a {@code self_attr:<ATTRIBUTE>} variable reads, or {@code null} for a plain name
         * (in which case {@link #variable} selects one of {@link #NUMERIC_VARIABLES}).
         */
        private final AttributeType attribute;
        /**
         * The resource a {@code self_resource:<NAME>} variable reads, or {@code null} for everything else.
         *
         * <p>Package-visible to the enclosing table, which collects the names into
         * {@link TriggerTable#referencedResources} for the assembly-point check.
         */
        private final String resource;
        /** The named counter a {@code *_stacks:<NAME>} variable reads, or {@code null}. */
        private final String stacksName;
        /** Whether that counter is read off the event's <b>target</b> rather than the rule's owner. */
        private final boolean stacksOnTarget;
        /** Whether it is read off the event's <b>actor</b> (the unit that caused it) instead. */
        private final boolean stacksOnActor;
        private final String operator;
        private final double literal;
        private final boolean literalOnLeft;

        Numeric(String variable, AttributeType attribute, String resource, String stacksName, boolean stacksOnTarget,
                boolean stacksOnActor, String operator, double literal, boolean literalOnLeft) {
            this.variable = variable;
            this.attribute = attribute;
            this.resource = resource;
            this.stacksName = stacksName;
            this.stacksOnTarget = stacksOnTarget;
            this.stacksOnActor = stacksOnActor;
            this.operator = operator;
            this.literal = literal;
            this.literalOnLeft = literalOnLeft;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            double value = numericValue(ctx);
            double left = literalOnLeft ? literal : value;
            double right = literalOnLeft ? value : literal;
            return switch (operator) {
                case ">" -> left > right;
                case ">=" -> left >= right;
                case "<" -> left < right;
                case "<=" -> left <= right;
                // Equality on numbers, which the DSL has always documented (`hit_count == 2`) and only
                // started accepting on 2026-09-27 -- see parseCondition. NaN compares false against
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
            if (attribute != null) {
                return ownerAttribute(ctx.owner(), attribute);
            }
            if (resource != null) {
                return resourceValue(ctx.owner(), resource);
            }
            if (stacksName != null) {
                // 「每当我方目标对【承负】状态下的敌方目标施放 2 次…」 reads the counter on the ENEMY (target), while a
                // counter of "how many refunds so far" would be read on the owner -- hence the two spellings. An
                // unreadable subject gives NaN, like every other variable that needs one.
                // \u2605 Three subjects now: the owner (self_\u2026), the event's target, and the event's actor -- the last one is what
                // makes \u300c\u6211\u65b9\u4efb\u610f\u89d2\u8272\u2026\u6d88\u8017\u300d readable from the light cone wearer's own table.
                CanHit holder = stacksOnActor ? ctx.actor() : stacksOnTarget ? ctx.target() : ctx.owner();
                return holder == null ? Double.NaN : holder.getBuffManager().stacksOf(stacksName);
            }
            return switch (variable) {
                case "weakness_hit_count" -> ctx.weakHitCount();   // \u2705 how many hit targets share the attack's weakness
                    case "hit_count" -> ctx.hitCount();
                // 「强化普攻命中…」: the DATA ROW of the skill that produced this event (0 = the event named none, which
                // makes the comparison false rather than accidentally true for the row 0 that no skill has).
                case "from_skill_id" -> ctx.skillId();
                case "hp_percent" -> hpPercent(ctx.owner());
                case "target_weakness_count" -> ctx.target() instanceof com.laosun.aluminium.models.enemy.Enemy weak
                            ? weak.weaknessCount() : 0;
                    case "target_debuff_count", "target_dot_count" -> ctx.target() == null ? Double.NaN
                        : ("target_dot_count".equals(variable)
                                ? ctx.target().getBuffManager().countBuffs(com.laosun.aluminium.models.buff.DotBuff.class)
                                : ctx.target().getBuffManager().debuffCount());
                // 「若该目标当前生命值百分比大于等于 30%」 -- the OTHER unit's HP, which `hp_percent` cannot ask
                // (that one reads the rule's owner). Unreadable with no subject, like every other variable here.
                case "target_hp_percent" -> ctx.target() == null ? Double.NaN : hpPercent(ctx.target());
                // ? The same fraction BEFORE this event's loss (2026-09-29): 「降到50%或以下」 is a CROSSING, not "is below half", and the
                // difference is firing once versus firing on every later hit. HP_LOST carries the loss in `amount`, so before = (current + amount) / max.
                case "target_hp_percent_before" -> ctx.target() == null || ctx.target().getMaxHp() <= 0
                        ? Double.NaN
                        : Math.min(1.0, (ctx.target().getCurrentHp() + ctx.amount()) / ctx.target().getMaxHp());
                case "self_summon_count" -> summonCount(ctx.owner(), ctx);
                // 「若目标拥有召唤物」 — the same question about the OTHER unit. It is a separate name rather
                // than a subject prefix because the two are asked in the same sentence often (relic 127 asks
                // about the wearer, 星期日's Skill asks about the ally it was cast on).
                case "target_summon_count" -> summonCount(ctx.target(), ctx);
                // 「场上敌方目标数量」 -- a count of the OTHER camp as a whole, which no subject prefix fits: `self_*` and
                // `target_*` are both about one unit. Its reader is 1413 长夜月's talent, whose thresholds are 4+/3/2/1 enemies.
                case "enemy_count" -> enemyCount(ctx);
                // 「我方目标数量」 -- the mirror of `enemy_count`, and the blocker set 321's entry names ("there is still no
                // condition on the battlefield's PARTY SIZE, so the number of stacks cannot be computed").
                case "ally_count" -> allyCount(ctx);
                // 「若装备者的能量上限大于等于…」 -- not an attribute (`CanHit.getMaxEnergy()` is a field, and the
                // attribute table has no slot for it), which is exactly why it needed a variable of its own.
                case "self_max_energy" -> ctx.owner() == null ? Double.NaN : ctx.owner().getMaxEnergy();
                default -> Double.NaN;
            };
        }

        /**
         * How many living summons one unit has on the field.
         *
         * <p>Read from the battlefield, so a context with no battle — or a unit that does not exist for this
         * event — answers {@code NaN}: the same "cannot read it, therefore the condition fails" rule the other
         * variables follow, and never a silent "0 summons". ⚠ That matters more here than elsewhere: a rule
         * gated on 「忆灵在场时」 would be <em>silently disabled</em> if a missing battlefield read as "none
         * out", which is a wrong answer with no symptom.
         */
        private static double summonCount(CanHit who, TriggerContext ctx) {
            if (ctx.battle() == null || who == null) {
                return Double.NaN;
            }
            return ctx.battle().summonCountOf(who);
        }

        /**
         * How many living enemies are on the field -- 「场上敌方目标数量」.
         *
         * <p>⚠ {@code NaN} when there is no battlefield, exactly like {@link #summonCount}: a rule gated on a count that cannot
         * be read must FAIL rather than read as zero, because "no enemies" and "cannot tell" are different answers and the
         * second one has no symptom. The dead are skipped, which is what 「场上」 means.
         */
        private static double enemyCount(TriggerContext ctx) {
            if (ctx.battle() == null || ctx.battle().enemyUnits() == null) {
                return Double.NaN;
            }
            return ctx.battle().enemyUnits().stream().filter(enemy -> !enemy.isDeath()).count();
        }

        /**
         * How many living allies are on the field -- 「我方目标数量」, the mirror of {@link #enemyCount}.
         *
         * <p>⚠ {@code NaN} when there is no battlefield, the rule every count in this vocabulary follows: "cannot read it" and
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
         * <p>⚠ {@code NaN} when the owner does not exist <b>or does not declare that resource</b>. That second
         * case cannot happen for a shipped character — the assembly point refuses a rule that reads an
         * undeclared resource — but a hand-built context (a test, or a table compiled on its own, as several
         * tests do) can reach it, and {@code ResourceManager.value} would answer {@code 0} for it. A rule
         * silently gated on a resource nobody registered is the wrong answer with no symptom this whole
         * vocabulary is built to avoid, so it fails the condition instead. The same rule every other
         * unreadable variable follows.
         */
        private static double resourceValue(CanHit owner, String resource) {
            if (owner == null || !owner.getResources().has(resource)) {
                return Double.NaN;
            }
            return owner.getResources().value(resource);
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
         * <p>Read from the <b>owner</b> — "the wearer's HP percentage" is a fact about the character
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
