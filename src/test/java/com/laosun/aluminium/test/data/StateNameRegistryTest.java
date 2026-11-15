package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code game/state_names.json} is the <b>declaration site</b> every state / stack / resource identity must appear in, and
 * the {@code owner} it carries is the part that makes the table honest.
 *
 * <p><b>Why an owner is needed at all.</b> Measured on the shipped content: {@code 充能} is the word used by
 * <b>eight</b> different mechanisms (max 3 / 3 / 4 / 8 / 8 / 10 / 10 / 100 / unbounded) - 1503's 【深度学习】 charge,
 * 1504's 不死途 charge, 1015's, 1223's, 1224's, 1314's, 1317's, 1412's (declared again by 1415 because a rule lives
 * there) and 8007's memosprite charge. {@code 结界} is seven characters' own zone. A table keyed by the NAME alone would
 * merge all of those, which is exactly the conflation a localization layer cannot survive. So the identity is
 * <b>(owner, name)</b>:
 * <ul>
 *   <li>{@code battle} - the battle model's own vocabulary: controls, DoT kinds, break, the technique state, and the
 *       Elation subsystem's counters. Shared by everybody, so no owner;</li>
 *   <li>a cid ({@code 1202}) - that character's term, whether it is a proper noun (「赐福」) or a reused general word
 *       (「充能」, 「结界」);</li>
 *   <li>{@code internal} - this project's bookkeeping counter, which has no display meaning at all.</li>
 * </ul>
 *
 * <p><b>Why the walk is structural.</b> {@code _note} / {@code source} / {@code _gaps} quote conditions verbatim
 * (「`self has_state sys:technique`」), so a text scan counts prose as usage - measured while building this: it turned 311 real
 * identities into 372 with a dozen backtick-suffixed phantoms.
 */
public class StateNameRegistryTest {

    private static final Path GAME = Path.of("src", "main", "resources", "game");
    private static final Path REGISTRY = GAME.resolve("state_names.json");
    /** {@code sys:technique}, {@code break:weakness}, {@code char:1314/charge}, {@code local:1015/loop_layers}. */
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]*(:[a-z0-9_./-]+)+$");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private static final List<Pattern> CONDITIONS = List.of(
            Pattern.compile("\\bhas_state\\s+([^\\s]+)"),
            Pattern.compile("\\bstate_ended\\s+([^\\s]+)"),
            Pattern.compile("\\bstack_added\\s+([^\\s]+)"),
            Pattern.compile("\\benemy_state_count:([^\\s<>=]+)"),
            Pattern.compile("\\bcast_applied:([^\\s<>=]+)"),
            Pattern.compile("\\bresource_changed:([^\\s<>=]+)"),
            Pattern.compile("\\b(?:self|actor|target|summon)_stacks:([^\\s<>=]+)"),
            Pattern.compile("\\b(?:self|summon|party|max_party)_resource(?:_before)?:([^\\s<>=]+)"));

    /**
     * Spells a counter name followed by <b>numbers</b>: {@code self_attr_above:<name>:60},
     * {@code weight_steps: ["<name>:60:-9"]} and {@code steps}/{@code per_steps: "<name>:120"}.
     *
     * <p>The name is everything before the trailing numbers, so the split is from the RIGHT - a name may carry colons of
     * its own ({@code char:1506/hidden_score}), and the engine's readers were changed to split the same way
     * ({@code TriggerInterpreter.weightOf} / {@code TriggerTable.referencedResources} / {@code TriggerMagnitudes}).
     */
    private static final List<String> STEP_FIELDS = List.of("steps", "per_steps");

    /** {@code <name>:<threshold>:<delta>} - the {@code weight_steps} entry shape. */
    private static final Pattern WEIGHT_STEP = Pattern.compile("^(.*):-?\\d+(?:\\.\\d+)?:-?\\d+(?:\\.\\d+)?$");

    /** Fields holding a <b>list</b> of state names. */
    private static final List<String> STATE_LIST_FIELDS = List.of("ally_states");

    /** Fields whose value IS a name, on any op. */
    private static final List<String> NAME_FIELDS = List.of(
            "resource", "while_state", "while_holder_state", "counts_as",
            "amount_from_resource", "percent_from_resource", "amount_percent_from_resource", "control");

    /**
     * Fields that name a <b>counter</b>, and may therefore spell it behind a prefix ({@code self_stacks:归纳}) or use one
     * of the engine's own count variables ({@code enemy_count}) instead of a counter at all.
     */
    private static final List<String> COUNTER_FIELDS = List.of("per_stack", "counter");

    /** The engine's own counts - values a counter field may hold that are <b>not</b> state names. */
    private static final Set<String> COUNT_VARIABLES = Set.of(
            "enemy_count", "shielded_count", "target_debuff_count", "target_dot_count", "target_weakness_count");

    /**
     * The ops whose {@code buff} is a state <b>identity</b> - something {@code REMOVE_STATE} can take off later.
     *
     * <p>⚠ Every other op's {@code buff} is a display label for an otherwise unnamed modifier, and those read like prose
     * (measured: {@code "113/4 暴击率"}, {@code "1506天赋-暴击伤害"}). A label is not an identity.
     */
    private static final Set<String> STATE_OPS = Set.of(
            "APPLY_BUFF", "REMOVE_STATE", "ADD_STACK", "REMOVE_STACK");

    /**
     * Fields whose value may name a state behind a selector. They are scanned <b>anywhere</b> in the value: a
     * {@code times_from} reads {@code steps:actor_stacks:攻击段数/1} and an {@code attacker} reads
     * {@code holder_of:赐福}, so an anchored pattern finds neither (measured: the Lord struck zero times after a rename
     * that skipped {@code times_from}, and 停云's talent refused to load).
     */
    private static final List<String> SELECTOR_FIELDS =
            List.of("target", "scale", "times_from", "attacker", "victim", "holder");

    /** The spellings a selector value may use to name a state. `/` is allowed: a key carries a slug after it. */
    private static final List<Pattern> SELECTOR_PATTERNS = List.of(
            Pattern.compile("(?:self|actor|target|summon)_stacks:([^\\s<>=]+)"),
            Pattern.compile("(?:holder_of|highest_stacks_enemy|lowest_stacks_enemy):([^\\s<>=]+)"),
            Pattern.compile("(?:self_resource|summon_resource|party_resource|max_party_resource):([^\\s<>=]+)"));

    @Test
    public void everyIdentityUsedByAFileIsDeclaredExactlyOnce() throws IOException {
        Map<String, Set<String>> usage = usageByFile();
        List<Entry> entries = entries();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> byName : usage.entrySet()) {
            for (String file : byName.getValue()) {
                List<Entry> matches = new ArrayList<>();
                for (Entry entry : entries) {
                    if (entry.name.equals(byName.getKey()) && entry.serves(file)) {
                        matches.add(entry);
                    }
                }
                if (matches.isEmpty()) {
                    problems.add(file + " uses \"" + byName.getKey() + "\" but no declared identity covers that file");
                } else if (matches.size() > 1) {
                    problems.add(file + " uses \"" + byName.getKey() + "\" and " + matches.size()
                            + " identities claim it: " + matches.stream().map(entry -> entry.owner).toList());
                }
            }
        }
        dumpUsage(usage);
        System.out.println("[state-names] identities=" + entries.size() + " names=" + usage.size()
                + " files=" + usage.values().stream().flatMap(Set::stream).distinct().count());
        Assertions.assertTrue(problems.isEmpty(),
                "every (file, name) pair must resolve to exactly one identity in " + REGISTRY + ":\n  "
                        + String.join("\n  ", problems));
    }

    @Test
    public void everyIdentityIsWellFormed() throws IOException {
        Map<String, Set<String>> usage = usageByFile();
        List<String> problems = new ArrayList<>();
        Map<String, String> keyOwners = new TreeMap<>();
        for (Entry entry : entries()) {
            if (!"battle".equals(entry.owner) && !entry.owner.matches("\\d{3,6}")) {
                problems.add(entry.name + ": owner must be \"battle\" or a content id "
                        + "(3-6 digits: a character, a light cone or a relic set), got \"" + entry.owner + "\"");
            }
            if (entry.kind == null || !Set.of("battle", "char", "internal").contains(entry.kind)) {
                problems.add(entry.name + " (" + entry.owner + "): kind must be battle / char / internal, got \""
                        + entry.kind + "\"");
            }
            if ("battle".equals(entry.owner) && !"battle".equals(entry.kind)) {
                problems.add(entry.name + ": owner battle must carry kind battle");
            }
            // A battle identity is created by the engine, so its ASCII key is assigned now; a character's or an
            // internal counter's key arrives when that family is migrated.
            if ("battle".equals(entry.kind) && entry.key == null) {
                problems.add(entry.name + ": a battle identity needs an ASCII \"key\"");
            }
            for (String file : entry.usedBy) {
                if (!usage.getOrDefault(entry.name, Set.of()).contains(file)) {
                    problems.add(entry.name + " (" + entry.owner + "): \"used_by\" lists " + file
                            + ", which does not use it any more");
                }
            }
            if (entry.key == null) {
                continue;
            }
            if (!KEY.matcher(entry.key).matches() || CJK.matcher(entry.key).find()) {
                problems.add(entry.name + " (" + entry.owner + "): key \"" + entry.key + "\" is not namespaced ASCII");
            }
            String previous = keyOwners.put(entry.key, entry.owner + "/" + entry.name);
            if (previous != null) {
                problems.add("key \"" + entry.key + "\" is claimed by both " + previous + " and "
                        + entry.owner + "/" + entry.name);
            }
            if (entry.key.equals(entry.name) && entry.zh == null) {
                problems.add(entry.name + ": once the key IS the name, \"zh\" must keep the Chinese term");
            }
        }
        Assertions.assertTrue(problems.isEmpty(), "the state-name registry is malformed:\n  "
                + String.join("\n  ", problems));
    }

    // ==================================================================

    /** One declared identity. */
    private record Entry(String name, String owner, String kind, String key, String zh, Set<String> usedBy) {
        /** @return whether this identity is the one a given file's use of {@code name} refers to */
        boolean serves(String file) {
            return usedBy.isEmpty() ? "battle".equals(owner) : usedBy.contains(file);
        }
    }

    private static List<Entry> entries() throws IOException {
        JsonObject root = read(REGISTRY);
        JsonArray array = root.getAsJsonArray("entries");
        Assertions.assertNotNull(array, "state_names.json must carry an \"entries\" array");
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            Set<String> usedBy = new LinkedHashSet<>();
            if (entry.has("used_by")) {
                for (JsonElement file : entry.getAsJsonArray("used_by")) {
                    usedBy.add(file.getAsString());
                }
            }
            entries.add(new Entry(
                    entry.get("name").getAsString(),
                    entry.has("owner") ? entry.get("owner").getAsString() : "",
                    entry.has("kind") ? entry.get("kind").getAsString() : null,
                    entry.has("key") ? entry.get("key").getAsString() : null,
                    entry.has("zh") ? entry.get("zh").getAsString() : null,
                    usedBy));
        }
        return entries;
    }

    /** Writes {@code name<TAB>files} to {@code build/} so a migration script can work from data, not from a message. */
    private static void dumpUsage(Map<String, Set<String>> usage) throws IOException {
        Path out = Path.of("build", "state-name-usage.txt");
        Files.createDirectories(out.getParent());
        StringBuilder text = new StringBuilder();
        usage.forEach((name, files) -> text.append(name).append('\t')
                .append(String.join(",", new TreeSet<>(files))).append('\n'));
        Files.writeString(out, text.toString(), StandardCharsets.UTF_8);
    }

    /** @return name -> the file base names that use it, read structurally (never from prose) */
    private static Map<String, Set<String>> usageByFile() throws IOException {
        Map<String, Set<String>> users = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(GAME)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> paths = Files.list(dir)) {
                    paths.filter(path -> path.toString().endsWith(".json"))
                            .filter(path -> !path.getFileName().toString().startsWith("_"))
                            .sorted().forEach(files::add);
                }
            }
        }
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonElement document = readDocument(file);
            List<JsonObject> rules = new ArrayList<>();
            if (document.isJsonArray()) {
                collect(document.getAsJsonArray(), rules);
            } else {
                rules.addAll(rulesOf(document.getAsJsonObject()));
            }
            if (GLOBAL_FILES.contains(name)) {
                // The hand-maintained data table is keyed by cid and then by slot, and its rows carry `castable_when`
                // lists that quote conditions ("self has_state 往昔的涟漪"). Walk it as a tree: any object holding one of
                // the condition keys is a decision point, wherever it sits.
                scanDeep(document, users, name);
                continue;
            }
            for (JsonObject rule : rules) {
                for (JsonElement when : conditionsOf(rule)) {
                    if (when.isJsonObject()) {
                        // A PREDICATE object names states too: {"type":"entity","json":{"state":"秘技"}}. The guard read
                        // only string conditions at first, so a rename of 「秘技」 left 27 of these behind while the
                        // guard stayed green - green because it could not see them (HANDOVER §270).
                        predicateNames(when.getAsJsonObject(), users, name);
                        continue;
                    }
                    if (!when.isJsonPrimitive()) {
                        continue;
                    }
                    for (Pattern pattern : CONDITIONS) {
                        Matcher matcher = pattern.matcher(when.getAsString());
                        while (matcher.find()) {
                            note(users, matcher.group(1), name);
                        }
                    }
                }
                for (JsonElement effect : arrayOf(rule, "do")) {
                    if (!effect.isJsonObject()) {
                        continue;
                    }
                    JsonObject op = effect.getAsJsonObject();
                    String operator = op.has("op") ? op.get("op").getAsString() : "";
                    if (STATE_OPS.contains(operator) && op.has("buff") && op.get("buff").isJsonPrimitive()) {
                        note(users, op.get("buff").getAsString(), name);
                    }
                    for (String field : NAME_FIELDS) {
                        if (op.has(field) && op.get(field).isJsonPrimitive()) {
                            note(users, op.get(field).getAsString(), name);
                        }
                    }
                    for (String field : COUNTER_FIELDS) {
                        if (!op.has(field) || !op.get(field).isJsonPrimitive()) {
                            continue;
                        }
                        String spelled = op.get(field).getAsString().trim();
                        if (spelled.isEmpty() || COUNT_VARIABLES.contains(spelled)) {
                            continue;
                        }
                        note(users, stripCounterPrefix(spelled), name);
                    }
                    for (String selectorField : SELECTOR_FIELDS) {
                        if (!op.has(selectorField) || !op.get(selectorField).isJsonPrimitive()) {
                            continue;
                        }
                        String spelled = op.get(selectorField).getAsString();
                        for (Pattern pattern : SELECTOR_PATTERNS) {
                            Matcher matcher = pattern.matcher(spelled);
                            while (matcher.find()) {
                                note(users, withoutStepsDivisor(matcher.group(1)), name);
                            }
                        }
                        // `self_attr_above:<name>:<n>` and `steps:<counter>:<n>` name an attribute OR a counter behind
                        // trailing numbers. Only the COUNTER case is a state identity: an attribute name (SPEED,
                        // DEFENCE, CRIT_CHANCE) is an {@code AttributeType} and has its own closed set.
                        noteCounterSpelling(users, spelled, name);
                    }
                    for (String stepField : STEP_FIELDS) {
                        for (String spelled : stringsAt(op, stepField)) {
                            noteCounterSpelling(users, "steps:" + spelled.trim(), name);
                        }
                    }
                    for (String spelled : stringsAt(op, "weight_steps")) {
                        Matcher matcher = WEIGHT_STEP.matcher(spelled.trim());
                        if (matcher.matches()) {
                            note(users, matcher.group(1).trim(), name);
                        }
                    }
                    for (String stateListField : STATE_LIST_FIELDS) {
                        for (String spelled : stringsAt(op, stateListField)) {
                            note(users, spelled.trim(), name);
                        }
                    }
                }
            }
            if (document.isJsonObject()) {
                for (JsonElement resource : arrayOf(document.getAsJsonObject(), "resources")) {
                    if (resource.isJsonObject() && resource.getAsJsonObject().has("id")) {
                        note(users, resource.getAsJsonObject().get("id").getAsString(), name);
                    }
                }
            }
        }
        return users;
    }

    /** The rule arrays of a file: {@code rules} for characters, the numbered rank keys for cones and relics. */
    private static List<JsonObject> rulesOf(JsonObject root) {
        List<JsonObject> rules = new ArrayList<>();
        collect(root.has("rules") ? root.getAsJsonArray("rules") : null, rules);
        for (String key : root.keySet()) {
            if (key.matches("\\d+")) {
                collect(root.getAsJsonArray(key), rules);
            }
        }
        return rules;
    }

    /**
     * Notes every state name a <b>predicate object</b> mentions: {@code json.state} (a name or a list of names),
     * {@code json.stacks.name} and {@code json.resource.name}, recursing through {@code all_of} / {@code any_of} /
     * {@code not}.
     *
     * <p>⚠ {@code json.attribute.name} is deliberately <b>not</b> a state: it names an {@code AttributeType}.
     *
     * @param predicate the predicate object
     * @param users     the name → files map being built
     * @param file      the file being walked
     */
    private static void predicateNames(JsonObject predicate, Map<String, Set<String>> users, String file) {
        if (predicate.has("term") && predicate.get("term").isJsonObject()) {
            predicateNames(predicate.getAsJsonObject("term"), users, file);
        }
        for (JsonElement term : arrayOf(predicate, "terms")) {
            if (term.isJsonObject()) {
                predicateNames(term.getAsJsonObject(), users, file);
            }
        }
        if (!predicate.has("json") || !predicate.get("json").isJsonObject()) {
            return;
        }
        JsonObject json = predicate.getAsJsonObject("json");
        if (json.has("state")) {
            JsonElement state = json.get("state");
            if (state.isJsonPrimitive()) {
                note(users, state.getAsString(), file);
            } else if (state.isJsonArray()) {
                for (JsonElement one : state.getAsJsonArray()) {
                    if (one.isJsonPrimitive()) {
                        note(users, one.getAsString(), file);
                    }
                }
            }
        }
        for (String key : List.of("stacks", "resource")) {
            if (json.has(key) && json.get(key).isJsonObject()) {
                JsonObject named = json.getAsJsonObject(key);
                if (named.has("name") && named.get("name").isJsonPrimitive()) {
                    note(users, named.get("name").getAsString(), file);
                }
            }
        }
    }

    private static void collect(JsonArray array, List<JsonObject> out) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                out.add(element.getAsJsonObject());
            }
        }
    }

    /**
     * The name inside a counter spelling: {@code self_stacks:归纳} names 「归纳」, but a bare identity that happens to
     * contain a colon ({@code char:21041/trick}) must be kept whole.
     *
     * @param spelled the counter field's value
     * @return the name it refers to
     */
    private static String stripCounterPrefix(String spelled) {
        for (String prefix : List.of("self_stacks:", "actor_stacks:", "target_stacks:", "summon_stacks:")) {
            if (spelled.startsWith(prefix)) {
                return spelled.substring(prefix.length()).trim();
            }
        }
        return spelled;
    }

    /**
     * Notes the counter a `self_attr_above:` / `steps:` spelling reads, when that name is <b>not</b> an attribute.
     *
     * <p>The engine tries {@code AttributeType} first, so `self_attr_above:SPEED:120` is an attribute read and has no
     * state identity behind it. Only the fall-back branch - a counter the unit owns - is a name this table knows.
     *
     * @param users   the name → files map being built
     * @param spelled the scale's value
     * @param file    the file being walked
     */
    private static void noteCounterSpelling(Map<String, Set<String>> users, String spelled, String file) {
        String trimmed = spelled.trim();
        String rest;
        if (trimmed.startsWith("self_attr_above:")) {
            rest = trimmed.substring("self_attr_above:".length());
        } else if (trimmed.startsWith("steps:")) {
            rest = trimmed.substring("steps:".length());
        } else {
            return;
        }
        // A stack spelling carries its own prefix and maybe a `/N` divisor: `actor_stacks:<name>/1`.
        for (String prefix : List.of("self_stacks:", "actor_stacks:", "target_stacks:", "summon_stacks:")) {
            if (rest.startsWith(prefix)) {
                String name = withoutStepsDivisor(rest.substring(prefix.length()).trim());
                if (!name.isEmpty()) {
                    note(users, name, file);
                }
                return;
            }
        }
        if (rest.matches(".*/\\d+$")) {
            rest = withoutStepsDivisor(rest);
        }
        // An attribute spelling may subtract a number (`CRIT_ATTACK-1.2`): the attribute is what precedes it.
        Matcher attributeMinus = Pattern.compile("^(.+?)-\\d+(?:\\.\\d+)?$").matcher(rest);
        String candidate = attributeMinus.matches() ? attributeMinus.group(1).trim() : rest;
        if (COUNT_VARIABLES.contains(candidate)) {
            return;      // an engine counter, not a content state
        }
        // Drop the trailing step (`:0.2`), then a subtracted threshold (`-1.2`): what is left is the name the step reads.
        int lastColon = candidate.lastIndexOf(':');
        if (lastColon >= 0) {
            candidate = candidate.substring(0, lastColon).trim();
        }
        Matcher minusSomething = Pattern.compile("^(.+?)-\\d+(?:\\.\\d+)?$").matcher(candidate);
        if (minusSomething.matches()) {
            candidate = minusSomething.group(1).trim();
        }
        if (candidate.isEmpty() || COUNT_VARIABLES.contains(candidate)) {
            return;      // blank, or an engine counter such as max_energy / target_debuffs_from_self
        }
        try {
            com.laosun.aluminium.enums.AttributeType.fromString(candidate);
            return;      // an attribute: the engine reads it as one, so it is not a state identity
        } catch (IllegalArgumentException notAnAttribute) {
            note(users, candidate, file);
        }
    }

    /** Files that hold names but belong to no single content id, so any declared identity may serve them. */
    private static final Set<String> GLOBAL_FILES = Set.of("skill_effects");

    /**
     * Walks a data table as a tree and notes every condition it finds: an object carrying {@code when} /
     * {@code target_when} / {@code castable_when} is a decision point, wherever it sits in the nesting.
     *
     * @param node  the current node
     * @param users the name → files map being built
     * @param file  the file being walked
     */
    private static void scanDeep(JsonElement node, Map<String, Set<String>> users, String file) {
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                scanDeep(child, users, file);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        for (JsonElement when : conditionsOf(object)) {
            if (when.isJsonPrimitive()) {
                for (Pattern pattern : CONDITIONS) {
                    Matcher matcher = pattern.matcher(when.getAsString());
                    while (matcher.find()) {
                        note(users, matcher.group(1), file);
                    }
                }
            }
        }
        for (String key : object.keySet()) {
            scanDeep(object.get(key), users, file);
        }
    }

    /** The string values of a field that may be a single string or an array of them ({@code weight_steps}, {@code steps},
     * {@code ally_states}).
     *
     * @param op    the effect spec
     * @param field the JSON key
     * @return every string it holds
     */
    private static List<String> stringsAt(JsonObject op, String field) {
        List<String> values = new ArrayList<>();
        if (!op.has(field)) {
            return values;
        }
        JsonElement element = op.get(field);
        if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement one : element.getAsJsonArray()) {
                if (one.isJsonPrimitive()) {
                    values.add(one.getAsString());
                }
            }
        }
        return values;
    }

    /**
     * A {@code steps:} expression ends with a numeric divisor ({@code actor_stacks:X/1}), while an identity's own key
     * carries a slug after its slash ({@code char:1202/blessing}). Both arrive as one captured run, so the divisor is
     * dropped and the slug kept.
     *
     * @param captured the text after a selector prefix
     * @return the identity it names
     */
    private static String withoutStepsDivisor(String captured) {
        int slash = captured.lastIndexOf('/');
        if (slash > 0 && captured.substring(slash + 1).matches("\\d+")) {
            return captured.substring(0, slash);
        }
        return captured;
    }

    /** Every condition list on a rule: `when` and the target-scoped `target_when`. */
    private static List<JsonElement> conditionsOf(JsonObject rule) {
        List<JsonElement> conditions = new ArrayList<>();
        for (String key : List.of("when", "target_when", "castable_when")) {
            for (JsonElement element : arrayOf(rule, key)) {
                conditions.add(element);
            }
        }
        return conditions;
    }

    private static JsonArray arrayOf(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    /**
     * Engine vocabulary a step spelling may name that is <b>not</b> a content state: the battle skill points, the
     * owner's max energy, and the debuffs it applied. Filtered here rather than in each matcher, so no path can
     * reintroduce them as identities.
     */
    private static final Set<String> ENGINE_NAMES =
            Set.of("max_energy", "target_debuffs_from_self", "skill_points", "enemy_count", "shielded_count",
                    "target_debuff_count", "target_dot_count", "target_weakness_count");

    private static void note(Map<String, Set<String>> users, String name, String file) {
        if (ENGINE_NAMES.contains(name)) {
            return;
        }
        users.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(file);
    }

    private static JsonObject read(Path path) throws IOException {
        JsonElement document = readDocument(path);
        Assertions.assertTrue(document.isJsonObject(), path + " must have an object root");
        return document.getAsJsonObject();
    }

    private static JsonElement readDocument(Path path) throws IOException {
        Assertions.assertTrue(Files.isRegularFile(path), "missing file: " + path.toAbsolutePath());
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
    }
}
