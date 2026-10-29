package com.laosun.aluminium.trigger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.laosun.aluminium.beans.PredicateSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.trigger.TriggerTable.Condition;
import com.laosun.aluminium.trigger.TriggerTable.TriggerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The <b>entity predicate</b>: {@code {"type": "entity", "entity": "target", "json": {...}}} - one unit, and a map of
 * sub-predicate to value that it must satisfy.
 *
 * <p><b>Why a map, and why here.</b> The questions a rule can ask about a unit grow with the content ("is it an ally",
 * "is its HP below half", "has it 3 stacks of 充能", "is its Speed at least 145"), and every one of them used to be a
 * sentence in a string. A map keeps them in <b>one closed vocabulary</b>: the key set, each key's value shape, and the
 * reader live in this file, and a misspelled key or an impossible range is refused when the table loads - not when the
 * rule first fires, and never as a rule that silently never matches.
 *
 * <p><b>Value shapes</b> (every one accepted by the loader, every other shape refused):
 * <ul>
 *   <li><b>boolean</b> - for the flags ({@code is_ally}, {@code is_dead}, ...);</li>
 *   <li><b>number</b> - shorthand for "at least this", i.e. {@code {"min": n}};</li>
 *   <li><b>{"min": n} / {"max": n} / both</b> - a range, both ends inclusive, either end may be omitted;</li>
 *   <li><b>string</b> or <b>list of strings</b> - for the closed-set questions ({@code path}, {@code element},
 *       {@code state}); a list means "any of";</li>
 *   <li><b>{"name": "...", "min"?: n, "max"?: n}</b> - for the questions that need a name ({@code attribute},
 *       {@code stacks}).</li>
 * </ul>
 *
 * <p><b>A missing subject fails.</b> Every sub-predicate is answered about a unit the event may not carry (a
 * {@code BATTLE_START} has no target, a summon's event may have no actor). The answer there is "no", never "yes": the
 * same rule the condition DSL follows, and the reason a composed guard cannot accidentally pass on an empty event.
 */
final class EntityPredicates {

    /** The subjects a predicate may be asked about. */
    static final Set<String> SUBJECTS = Set.of("self", "actor", "target", "summon");

    /** Every accepted sub-predicate, in one place so the loader's error can list it. */
    static final Set<String> KEYS = Set.of(
            "is_ally", "is_enemy", "is_dead", "is_summon",
            "same_as", "summon_count", "enemy_count", "resource", "energy_pct", "debuff_count", "master_state",
            "hp", "hp_pct", "max_hp", "energy", "shield", "shield_pct", "has_shield", "shield_from",
            "level", "cid", "speed", "eidolon", "skill_level", "state_turns",
            "attribute", "stacks", "state", "path", "element", "camp", "team");

    private EntityPredicates() {
    }

    /**
     * Compiles one {@code entity} predicate: everything is checked HERE (sub-predicate names, value shapes, the subject
     * name, and that the rule's event can carry that subject at all), so firing is a pure read.
     *
     * @param spec the predicate
     * @param rule the rule it belongs to, for its {@code source}
     * @return the condition
     */
    static Condition compile(PredicateSpec spec, TriggerSpec rule) {
        String where = " (source: " + rule.getSource() + ")";
        String subject = spec.getEntity() == null ? "self" : spec.getEntity().trim();
        if (!SUBJECTS.contains(subject)) {
            throw new IllegalArgumentException("an \"entity\" predicate names the subject \"" + subject
                    + "\"; it is self / actor / target" + where);
        }
        JsonObject json = spec.getJson();
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("an \"entity\" predicate needs a non-empty \"json\" - the sub-predicates"
                    + " to check (known: " + String.join(" / ", new TreeSet<>(KEYS)) + ")" + where);
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("the entity predicate names the unknown sub-predicate \"" + key
                        + "\"; known: " + String.join(" / ", new TreeSet<>(KEYS)) + where);
            }
            switch (key) {
                case "is_ally", "is_enemy", "is_dead", "is_summon", "has_shield" -> requireBoolean(key, entry.getValue(), where);
                case "hp", "hp_pct", "max_hp", "energy", "shield", "shield_pct", "level", "cid", "speed", "eidolon",
                        "summon_count", "enemy_count", "energy_pct", "debuff_count" ->
                        requireRange(key, entry.getValue(), where);
                case "resource" -> requireNamedRange(key, entry.getValue(), where);
                case "camp" -> requireCamp(entry.getValue(), where);
                case "team" -> requireTeam(entry.getValue(), where);
                case "skill_level" -> {
                    requireNamedRange(key, entry.getValue(), where);
                    requireSkillSlot(entry.getValue().getAsJsonObject().get("name").getAsString(), where);
                }
                case "state_turns" -> requireNamedRange(key, entry.getValue(), where);
                case "shield_from" -> {
                    String provider = requireString(key, entry.getValue(), where).trim();
                    if (!SUBJECTS.contains(provider)) {
                        throw new IllegalArgumentException("the sub-predicate \"shield_from\" names \"" + provider
                                + "\"; it is self / actor / target" + where);
                    }
                }
                case "state", "path", "element", "master_state" -> requireStrings(key, entry.getValue(), where);
                case "same_as" -> {
                    String other = requireString(key, entry.getValue(), where).trim();
                    if (!"summon".equals(other) && !SUBJECTS.contains(other)) {
                        throw new IllegalArgumentException("the sub-predicate \"same_as\" names \"" + other
                                + "\"; it is self / actor / target" + where);
                    }
                }
                case "attribute", "stacks" -> {
                    requireNamedRange(key, entry.getValue(), where);
                    if ("attribute".equals(key)) {
                        requireAttributeName(entry.getValue().getAsJsonObject().get("name").getAsString(), where);
                    }
                }
                default -> throw new IllegalStateException("unhandled sub-predicate " + key);
            }
        }
        List<Map.Entry<String, JsonElement>> checks = new ArrayList<>(json.entrySet());
        return new Entity(subject, List.copyOf(checks), json.toString());
    }

    /**
     * One compiled entity predicate.
     */
    private record Entity(String subject, List<Map.Entry<String, JsonElement>> checks, String written)
            implements TriggerTable.PartyCondition {

        /**
         * The unit this predicate asks about, or {@code null} when the event does not carry it.
         *
         * <p>{@link TriggerTable.PartyCondition} is what makes {@code not} safe: a negation asks for the party FIRST and
         * fails when there is none, instead of inverting "no such unit" into a match.
         */
        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return unitOf(subject, ctx);
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit unit = unitOf(subject, ctx);
            if (unit == null) {
                return false;                     // a missing subject is "no", never "yes"
            }
            for (Map.Entry<String, JsonElement> check : checks) {
                if (!testOne(check.getKey(), check.getValue(), unit, ctx)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The condition as a <b>rule author would spell it</b>, so an error message, a log line and a test that pins
         * "this rule's guard" keep reading the same string the file used to state: this model replaced a spelling, not a
         * meaning.
         *
         * <p>A body with more than one sub-predicate (or one this cannot render) falls back to its JSON, because such a
         * body has no single-spelling form.
         */
        @Override
        public String source() {
            if (checks.size() == 1) {
                String spelled = spell(checks.getFirst().getKey(), checks.getFirst().getValue());
                if (spelled != null) {
                    return spelled;
                }
            }
            return subject + " " + written;
        }

        /**
         * A threshold the way {@code TriggerConditionParser} spells it: an integral one carries a decimal point
         * ({@code 4.0}), so a log line and a test that pins the spelling keep reading the same string.
         */
        private static String number(com.google.gson.JsonElement value) {
            double parsed = value.getAsDouble();
            return parsed == Math.rint(parsed)
                    ? String.format(java.util.Locale.ROOT, "%.1f", parsed)
                    : String.valueOf(parsed);
        }

        private String spell(String key, com.google.gson.JsonElement value) {
            return switch (key) {
                case "same_as" -> subject + " == " + value.getAsString();
                case "is_ally" -> value.getAsBoolean() ? subject + " is_ally" : "!" + subject + " is_ally";
                case "is_summon" -> value.getAsBoolean() ? subject + " is_summon" : "!" + subject + " is_summon";
                case "is_dead" -> value.getAsBoolean() ? subject + " is_dead" : "!" + subject + " is_dead";
                case "state" -> subject + " has_state " + value.getAsString();
                case "stacks" -> {
                    var named = value.getAsJsonObject();
                    String name = named.get("name").getAsString();
                    if (named.has("min")) {
                        yield subject + "_stacks:" + name + " >= " + number(named.get("min"));
                    }
                    yield subject + "_stacks:" + name + " <= " + number(named.get("max"));
                }
                case "resource" -> {
                    var named = value.getAsJsonObject();
                    String prefix = "summon".equals(subject) ? "summon_resource:" : "self_resource:";
                    String name = named.get("name").getAsString();
                    if (named.has("min")) {
                        yield prefix + name + " >= " + number(named.get("min"));
                    }
                    yield prefix + name + " <= " + number(named.get("max"));
                }
                case "summon_count", "enemy_count" -> {
                    String name = "summon_count".equals(key) ? "self_summon_count" : "enemy_count";
                    var range = value.getAsJsonObject();
                    if (range.has("min") && range.has("max")
                            && range.get("min").getAsDouble() == range.get("max").getAsDouble()) {
                        yield name + " == " + number(range.get("min"));
                    }
                    yield name + (range.has("min") ? " >= " : " <= ")
                            + number(range.has("min") ? range.get("min") : range.get("max"));
                }
                case "energy_pct" -> {
                    var range = value.getAsJsonObject();
                    yield "self_energy_percent " + (range.has("min") ? ">= " : "<= ")
                            + number(range.has("min") ? range.get("min") : range.get("max"));
                }
                case "debuff_count" -> {
                    var range = value.getAsJsonObject();
                    yield "target_debuff_count " + (range.has("min") ? ">= " : "<= ")
                            + number(range.has("min") ? range.get("min") : range.get("max"));
                }
                case "attribute" -> {
                    var named = value.getAsJsonObject();
                    // The parser lowercases an attribute name in its spelling ("self_attr:speed >= 160.0"), so the
                    // renderer does too - otherwise a log line and the file disagree in case alone.
                    String name = named.get("name").getAsString().toLowerCase(java.util.Locale.ROOT);
                    if (named.has("gt")) {
                        yield "self_attr:" + name + " > " + number(named.get("gt"));
                    }
                    if (named.has("lt")) {
                        yield "self_attr:" + name + " < " + number(named.get("lt"));
                    }
                    if (named.has("min")) {
                        yield "self_attr:" + name + " >= " + number(named.get("min"));
                    }
                    yield "self_attr:" + name + " <= " + number(named.get("max"));
                }
                case "hp_pct" -> {
                    var range = value.getAsJsonObject();
                    if (range.has("gt")) {
                        yield "target_hp_percent > " + number(range.get("gt"));
                    }
                    if (range.has("lt")) {
                        yield "target_hp_percent < " + number(range.get("lt"));
                    }
                    yield "target_hp_percent " + (range.has("min") ? ">= " : "<= ")
                            + number(range.has("min") ? range.get("min") : range.get("max"));
                }
                default -> null;
            };
        }
    }

    /**
     * The unit a subject names, or {@code null} when this event does not carry it.
     */
    private static CanHit unitOf(String subject, TriggerContext ctx) {
        return switch (subject) {
            case "self" -> ctx.owner();
            case "actor" -> ctx.actor();
            case "target" -> ctx.target();
            // "the rule owner's memosprite": the subject the content spells `summon` (its own wording for 长夜 / 小伊卡 /
            // 德谬歌 / 晴空乐手). A memosprite that is not on the field makes every predicate about it fail.
            case "summon" -> ctx.battle() == null || ctx.owner() == null ? null : ctx.battle().summonOf(ctx.owner());
            default -> null;
        };
    }

    private static boolean testOne(String key, JsonElement value, CanHit unit, TriggerContext ctx) {
        return switch (key) {
            case "is_ally" -> (unit.getCamp() == Camp.PLAYER) == value.getAsBoolean();
            case "is_enemy" -> (unit.getCamp() == Camp.ENEMY) == value.getAsBoolean();
            case "is_dead" -> unit.isDeath() == value.getAsBoolean();
            case "is_summon" -> (unit instanceof Summon) == value.getAsBoolean();
            case "has_shield" -> (unit.getShield() > 0) == value.getAsBoolean();
            case "hp" -> inRange(unit.getCurrentHp(), value);
            case "hp_pct" -> inRange(unit.getMaxHp() <= 0 ? 0 : unit.getCurrentHp() / unit.getMaxHp(), value);
            case "shield_pct" -> inRange(unit.getMaxHp() <= 0 ? 0 : unit.getShield() / unit.getMaxHp(), value);
            case "max_hp" -> inRange(unit.getMaxHp(), value);
            case "energy" -> inRange(unit.getCurrentEnergy(), value);
            case "shield" -> inRange(unit.getShield(), value);
            case "level" -> inRange(unit.getLevel(), value);
            case "speed" -> inRange(attribute(unit, AttributeType.SPEED), value);
            case "shield_from" -> {
                CanHit provider = unitOf(value.getAsString().trim(), ctx);
                yield provider != null && unit.getShieldProvider() == provider;
            }
            case "cid" -> unit instanceof Character character && inRange(character.getCid(), value);
            case "state" -> anyOf(value, state -> unit.getBuffManager().hasState(state));
            case "path" -> anyOf(value, name -> unit instanceof Character character
                    && character.getPath() != null
                    && character.getPath().name().equalsIgnoreCase(name.trim()));
            case "element" -> anyOf(value, name -> unit instanceof Character character
                    && character.getElement() != null
                    && character.getElement().name().equalsIgnoreCase(name.trim()));
            case "attribute" -> {
                JsonObject named = value.getAsJsonObject();
                yield inRange(attribute(unit, attributeType(named.get("name").getAsString())), named);
            }
            case "stacks" -> {
                JsonObject named = value.getAsJsonObject();
                yield inRange(unit.getBuffManager().stacksOf(named.get("name").getAsString()), named);
            }
            case "state_turns" -> {
                JsonObject named = value.getAsJsonObject();
                yield inRange(unit.getBuffManager().remainingTurnsOf(named.get("name").getAsString()), named);
            }
            case "eidolon" -> unit instanceof Character character && inRange(character.getEidolonRank(), value);
            case "skill_level" -> {
                JsonObject named = value.getAsJsonObject();
                String slot = named.get("name").getAsString().trim();
                yield unit instanceof Character character
                        && character.getSkillLevel() != null
                        && character.getSkillLevel().containsKey(skillSlot(slot))
                        && inRange(character.getSkillLevel().get(skillSlot(slot)), named);
            }
            case "same_as" -> {
                // "the actor IS me": identity, not a property of the unit, and the condition `actor == self` has 1,238
                // uses in the content - the single most common condition there is.
                String other = value.getAsString().trim();
                if ("summon".equals(other)) {
                    // ⚠ NOT `battle.summonOf(owner)`: a rule on SUMMON_LEFT fires while the memosprite is LEAVING, and by
                    // then it is no longer the one on the field (measured: summonOf returns null and the rule never
                    // fires). "The actor is my memosprite" is therefore a fact about ownership, not about who is out.
                    yield unit instanceof Summon summon && (ctx.owner() == null || summon.getMaster() == ctx.owner());
                }
                CanHit party = unitOf(other, ctx);
                yield party != null && party == unit;
            }
            case "summon_count" -> {
                // "the owner has a memosprite on the field" is `{ "summon_count": { "min": 1 } }`.
                yield inRange(unit instanceof Summon ? 0 : ctx.battle() == null ? 0 : ctx.battle().summonCountOf(unit),
                        value);
            }
            case "enemy_count" -> {
                var battle = ctx.battle();
                yield battle != null && inRange(battle.getOpponents(unit).stream().filter(opponent -> !opponent.isDeath())
                        .count(), value);
            }
            case "resource" -> {
                // The same two places the `self_resource:` scale reads, in the same order: the unit's own store first, then
                // the PARTY one (a team-scoped counter lives there - 1512's 气氛值). Reading only the unit's own store made
                // every party-scoped clause fail on a member that does not carry it.
                var named = value.getAsJsonObject();
                String name = named.get("name").getAsString();
                var resources = unit.getResources();
                if (resources != null && resources.has(name)) {
                    yield inRange(resources.get(name).exactValue(), named);
                }
                var party = ctx.battle() == null ? null : ctx.battle().partyResource(name);
                if (party == null) {
                    throw new IllegalStateException("the entity predicate reads the counter \"" + name + "\", which "
                            + unit.getName() + " does not carry and the party does not declare - a counter that answers"
                            + " 0 is a wrong number with no symptom");
                }
                yield inRange(party.exactValue(), named);
            }
            case "energy_pct" -> inRange(unit.getMaxEnergy() <= 0 ? 0 : unit.getCurrentEnergy() / unit.getMaxEnergy(),
                    value);
            case "debuff_count" -> inRange(unit.getBuffManager().debuffCount(), value);
            case "master_state" -> {
                // 「持有【特邀嘉宾】的角色及其召唤物攻击时」 (1512's ultimate): the summon attacks, the STATE is on its master.
                String name = value.getAsString();
                yield unit instanceof Summon summon && summon.getMaster() != null
                        && summon.getMaster().getBuffManager().hasState(name);
            }
            case "camp" -> anyOf(value, name -> unit.getCamp() != null
                    && unit.getCamp().name().equalsIgnoreCase(name.trim()));
            case "team" -> {
                String side = value.getAsString().trim();
                CanHit owner = ctx.owner();
                boolean own = owner != null && owner.getCamp() == unit.getCamp();
                yield "own".equals(side) ? own : !own;
            }
            default -> false;
        };
    }

    private static double attribute(CanHit unit, AttributeType type) {
        var value = unit.getAttribute(type);
        return value == null ? Double.NaN : value.get();
    }

    /**
     * A numeric test against a number, or against {@code {"min": ...}} / {@code {"max": ...}}. Both ends are inclusive;
     * a {@code NaN} reading (an attribute the unit has no value for) fails every comparison, which is the safe answer.
     */
    private static boolean inRange(double actual, JsonElement value) {
        if (Double.isNaN(actual)) {
            return false;
        }
        if (value.isJsonPrimitive()) {
            return actual >= value.getAsDouble();
        }
        JsonObject range = value.getAsJsonObject();
        if (range.has("min") && actual < range.get("min").getAsDouble()) {
            return false;
        }
        if (range.has("max") && actual > range.get("max").getAsDouble()) {
            return false;
        }
        // Strict bounds, for the sentences that say 大于/小于 rather than 大于等于: 「SPEED > 200」. A closed range cannot
        // express them (`>= 201` would be a different statement about a non-integer), so they get their own keys.
        if (range.has("gt") && actual <= range.get("gt").getAsDouble()) {
            return false;
        }
        if (range.has("lt") && actual >= range.get("lt").getAsDouble()) {
            return false;
        }
        return true;
    }

    /**
     * "Any of" for the closed-set questions: a bare string or a list of them.
     */
    private static boolean anyOf(JsonElement value, java.util.function.Predicate<String> test) {
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (test.test(element.getAsString())) {
                    return true;
                }
            }
            return false;
        }
        return test.test(value.getAsString());
    }

    // --- load-time shape checks ---------------------------------------------------------------------------------------

    private static void requireBoolean(String key, JsonElement value, String where) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes true or false, got " + value + where);
        }
    }

    private static void requireRange(String key, JsonElement value, String where) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return;
        }
        if (value.isJsonObject()) {
            JsonObject range = value.getAsJsonObject();
            // `gt` / `lt` are the STRICT bounds: 「速度大于 200」 has no inclusive spelling (`>= 201` is a different
            // statement about a non-integer), so the range carries them as their own keys.
            unknownKeys(range, Set.of("min", "max", "gt", "lt"), key, where);
            if (!range.has("min") && !range.has("max") && !range.has("gt") && !range.has("lt")) {
                throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes a number or {\"min\": ...}"
                        + " / {\"max\": ...} / {\"gt\": ...} / {\"lt\": ...}, got an empty object" + where);
            }
            for (String end : new String[]{"min", "max", "gt", "lt"}) {
                if (range.has(end) && !range.get(end).getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("the sub-predicate \"" + key + "\" needs a numeric \"" + end
                            + "\", got " + range.get(end) + where);
                }
            }
            return;
        }
        throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes a number or a {\"min\"/\"max\"}"
                + " range, got " + value + where);
    }

    private static String requireString(String key, JsonElement value, String where) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes a string, got " + value + where);
    }

    /**
     * The closed set of camps: a bare name or a list meaning "any of".
     */
    private static void requireCamp(JsonElement value, String where) {
        requireStrings("camp", value, where);
        List<String> names = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                names.add(element.getAsString());
            }
        } else {
            names.add(value.getAsString());
        }
        for (String name : names) {
            if (!isCamp(name)) {
                throw new IllegalArgumentException("the sub-predicate \"camp\" names \"" + name + "\"; it is "
                        + java.util.Arrays.toString(Camp.values()) + where);
            }
        }
    }

    /**
     * {@code team}: {@code own} or {@code other}, relative to the rule owner's camp.
     */
    private static void requireTeam(JsonElement value, String where) {
        String side = requireString("team", value, where).trim();
        if (!"own".equals(side) && !"other".equals(side)) {
            throw new IllegalArgumentException("the sub-predicate \"team\" names \"" + side
                    + "\"; it is own (the rule owner's camp) or other" + where);
        }
    }

    private static boolean isCamp(String name) {
        for (Camp camp : Camp.values()) {
            if (camp.name().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    private static SkillType skillSlot(String name) {
        return SkillType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static void requireSkillSlot(String name, String where) {
        try {
            skillSlot(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("the sub-predicate \"skill_level\" names the unknown skill slot \""
                    + name + "\"; it is " + java.util.Arrays.toString(SkillType.values()) + where, unknown);
        }
    }

    private static void requireStrings(String key, JsonElement value, String where) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return;
        }
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes a string or a list of"
                            + " strings; the list holds " + element + where);
                }
            }
            return;
        }
        throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes a string or a list of strings, got "
                + value + where);
    }

    private static void requireNamedRange(String key, JsonElement value, String where) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("the sub-predicate \"" + key + "\" takes {\"name\": ..., \"min\"?/\"max\"?},"
                    + " got " + value + where);
        }
        JsonObject named = value.getAsJsonObject();
        unknownKeys(named, Set.of("name", "min", "max", "gt", "lt"), key, where);
        JsonElement name = named.get("name");
        if (name == null || !name.getAsJsonPrimitive().isString() || name.getAsString().isBlank()) {
            throw new IllegalArgumentException("the sub-predicate \"" + key + "\" needs a non-empty \"name\"" + where);
        }
        for (String end : new String[]{"min", "max", "gt", "lt"}) {
            if (named.has(end) && !named.get(end).getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("the sub-predicate \"" + key + "\" needs a numeric \"" + end
                        + "\", got " + named.get(end) + where);
            }
        }
    }

    private static void unknownKeys(JsonObject object, Set<String> allowed, String key, String where) {
        for (String name : object.keySet()) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("the sub-predicate \"" + key + "\" does not read \"" + name
                        + "\"; it reads " + String.join(" / ", new TreeSet<>(allowed)) + where);
            }
        }
    }

    private static AttributeType attributeType(String name) {
        return AttributeType.fromString(name.trim());
    }

    private static void requireAttributeName(String name, String where) {
        try {
            attributeType(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("the sub-predicate \"attribute\" names the unknown attribute \"" + name
                    + "\"" + where, unknown);
        }
    }

}
