package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
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
        this.cid = cid;
        if (specs == null) {
            return;
        }
        for (int index = 0; index < specs.size(); index++) {
            TriggerSpec spec = specs.get(index);
            TriggerEvent event = resolveEvent(spec);
            for (CompiledRule rule : compile(spec, event, index)) {
                byEvent.computeIfAbsent(event, k -> new ArrayList<>()).add(rule);
            }
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
     * unequipped character therefore behaves exactly as it did before relic rules existed.
     *
     * @param other the table to append (may be {@code null})
     * @return the merged table; this instance when {@code other} is null or empty
     */
    public TriggerTable plus(TriggerTable other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        TriggerTable merged = new TriggerTable(cid, List.of());
        copyRulesInto(merged);
        other.copyRulesInto(merged);
        return merged;
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
        }
        return List.of(new CompiledRule(event, conditions, effects, spec.getSource(),
                ruleKey(spec, index), validateCooldown(spec),
                Boolean.TRUE.equals(spec.getOncePerBattle()), validateChance(spec),
                validateMinEidolon(spec)));
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
     * <p>{@code self_attr:<ATTRIBUTE>} is the one <b>parameterised</b> member and is deliberately not listed
     * here: its second half is not a fixed name but a member of {@link AttributeType}, validated separately
     * (and rejected there for the four {@code *_PERCENT} builder keys, whose runtime slot is null).
     */
    private static final Set<String> NUMERIC_VARIABLES =
            Set.of("hit_count", "hp_percent", "target_debuff_count", "self_summon_count");

    /**
     * The prefix of the one parameterised numeric variable: {@code self_attr:SPEED}.
     *
     * <p>Why the subject is fixed at {@code self} and not a general {@code <subject>_attr:<TYPE>}: every
     * attribute threshold in the shipped data is about the wearer (「装备者的速度/暴击率/击破特攻/生命上限…」),
     * and an axis with one used value is an axis nobody has tested. Adding {@code target_attr:} later is a
     * few lines in the same place once some content actually needs it.
     */
    private static final String SELF_ATTR_PREFIX = "self_attr:";

    /**
     * The keyword of the named-state condition, and the parties it may ask about.
     *
     * <p>The keyword has to stand alone (a state whose name contains {@code has_state} must not be
     * mistaken for the operator), hence the lookarounds rather than a plain {@code contains}.
     */
    private static final Pattern HAS_STATE =
            Pattern.compile("(?<![\\w])has_state(?![\\w])", Pattern.CASE_INSENSITIVE);

    private static final Set<String> STATE_SUBJECTS = Set.of("self", "actor", "target");

    private static Condition parseCondition(String raw, TriggerSpec spec) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty trigger condition (source: " + spec.getSource() + ")");
        }
        String text = raw.trim();

        // `has_state`: "<who> has_state <name>". Checked before the operator branch because this shape has
        // no symbol operator at all -- without it, "self has_state 协奏" would be reported as an unknown
        // shorthand, which sends the author looking in the wrong place.
        Matcher hasState = HAS_STATE.matcher(text);
        if (hasState.find()) {
            String subject = normalize(text.substring(0, hasState.start()));
            String state = text.substring(hasState.end()).trim();
            return new HasState(requireStateSubject(subject, raw, spec), state, raw, spec);
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
            if ("self".equals(left) || "self".equals(right)) {
                String other = "self".equals(left) ? right : left;
                String variable = requireIdentityVariable(other, raw, spec);
                return new Equality(variable, "self", negated);
            }
            // Neither side is "self", so this is not an identity comparison — it is a numeric one, and
            // `hit_count == 2` has been in this DSL's documentation since its first version while the parser
            // reported it as "compares two variables" (found 2026-09-27 by a test that needed `== 0`). Fall
            // through to the numeric path; a comparison between two *names* still gets the clearer message.
            if (!isNumeric(left) && !isNumeric(right)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' compares two variables; only comparisons against "
                                + "\"self\", or against a numeric literal (e.g. \"hit_count == 2\"), are "
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
        if (!NUMERIC_VARIABLES.contains(variable) && !variable.startsWith(SELF_ATTR_PREFIX)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' compares unknown variable '" + variable
                            + "'; known numeric variables: " + String.join(", ", knownNumericVariables())
                            + ", plus \"self_attr:<ATTRIBUTE>\" for one of my own attribute values, e.g. "
                            + "\"self_attr:SPEED >= 145\" (source: " + spec.getSource() + ")");
        }
        return new Numeric(variable, selfAttributeOf(variable, raw, spec), operator, literal, literalOnLeft);
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
        if (!"actor".equals(token) && !"target".equals(token)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' compares \"self\" with an unknown variable '" + token
                            + "'; identity variables are actor (who caused the event) and "
                            + "target (what it happened to) (source: " + spec.getSource() + ")");
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
     * @param cooldownTurns the owner's turns between two firings ({@code 0} = unlimited)
     * @param oncePerBattle {@code true} = at most one firing per battle
     * @param chance        the probability of firing at all, as a fraction of 1 ({@code 1.0} = always)
     * @param minEidolon    the Eidolon rank the owner needs ({@code 0} = ungated)
     */
    public record CompiledRule(TriggerEvent event, List<Condition> conditions,
                               List<EffectSpec> effects, String source, String key,
                               int cooldownTurns, boolean oncePerBattle, double chance, int minEidolon) {

        /**
         * Whether this rule limits how often it may fire at all.
         *
         * <p>An unlimited rule never touches the owner's limit counters, so adding this vocabulary
         * cannot change the behaviour of any rule that does not use it.
         */
        public boolean isLimited() {
            return cooldownTurns > 0 || oncePerBattle;
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
     */
    public record TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,
                                 Damage damage, Battle battle) {

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

        public static TriggerContext of(CanHit owner, CanHit actor) {
            return new TriggerContext(owner, actor, null, 0, 0, null, null);
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
    private static final class HasState implements Condition {

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
        public boolean test(TriggerContext ctx) {
            CanHit who = switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
            return who != null && who.getBuffManager().hasState(state);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Identity comparison: {@code actor == self} / {@code actor != self} / {@code target == self} / …
     *
     * <p>{@code actor} is who caused the event, {@code target} is what it happened to. See
     * {@link TriggerContext}.
     */
    private static final class Equality implements Condition {

        private final String variable;
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
            CanHit right = "self".equals(otherToken) ? ctx.owner() : null;
            boolean equal = subject == right;
            return negated != equal;
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
        private final String operator;
        private final double literal;
        private final boolean literalOnLeft;

        Numeric(String variable, AttributeType attribute, String operator, double literal,
                boolean literalOnLeft) {
            this.variable = variable;
            this.attribute = attribute;
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
            return switch (variable) {
                case "hit_count" -> ctx.hitCount();
                case "hp_percent" -> hpPercent(ctx.owner());
                case "target_debuff_count" -> ctx.target() == null ? Double.NaN : ctx.target().getBuffManager().debuffCount();
                case "self_summon_count" -> summonCount(ctx);
                default -> Double.NaN;
            };
        }

        /**
         * How many living summons the owner has on the field.
         *
         * <p>Read from the battlefield, so a context with no battle answers {@code NaN} — the same "cannot
         * read it, therefore the condition fails" rule the other variables follow, and never a silent
         * "0 summons". ⚠ That matters more here than elsewhere: a rule gated on 「忆灵在场时」 would be
         * <em>silently disabled</em> if a missing battlefield read as "none out", which is a wrong answer
         * with no symptom.
         */
        private static double summonCount(TriggerContext ctx) {
            if (ctx.battle() == null || ctx.owner() == null) {
                return Double.NaN;
            }
            return ctx.battle().summonCountOf(ctx.owner());
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
