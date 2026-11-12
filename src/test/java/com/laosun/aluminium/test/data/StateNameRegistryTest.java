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
 * (「`self has_state 秘技`」), so a text scan counts prose as usage - measured while building this: it turned 311 real
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
            Pattern.compile("\\b(?:self|actor|target|summon)_stacks:([^\\s<>=]+)"),
            Pattern.compile("\\b(?:self|summon|party|max_party)_resource(?:_before)?:([^\\s<>=]+)"));

    /** Fields whose value IS a name, on any op. */
    private static final List<String> NAME_FIELDS = List.of(
            "resource", "while_state", "while_holder_state", "counts_as",
            "amount_from_resource", "percent_from_resource", "amount_percent_from_resource");

    /**
     * The ops whose {@code buff} is a state <b>identity</b> - something {@code REMOVE_STATE} can take off later.
     *
     * <p>⚠ Every other op's {@code buff} is a display label for an otherwise unnamed modifier, and those read like prose
     * (measured: {@code "113/4 暴击率"}, {@code "1506天赋-暴击伤害"}). A label is not an identity.
     */
    private static final Set<String> STATE_OPS = Set.of(
            "APPLY_BUFF", "REMOVE_STATE", "ADD_STACK", "REMOVE_STACK");

    private static final List<Pattern> SELECTOR_FIELDS = List.of(
            Pattern.compile("^(?:holder_of|highest_stacks_enemy|lowest_stacks_enemy):(.+)$"),
            Pattern.compile("^(?:self_resource|summon_resource|party_resource|max_party_resource):(.+)$"));

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
            for (JsonObject rule : rules) {
                for (JsonElement when : arrayOf(rule, "when")) {
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
                    for (String selectorField : List.of("target", "scale")) {
                        if (op.has(selectorField) && op.get(selectorField).isJsonPrimitive()) {
                            String spelled = op.get(selectorField).getAsString();
                            for (Pattern pattern : SELECTOR_FIELDS) {
                                Matcher matcher = pattern.matcher(spelled);
                                if (matcher.matches()) {
                                    note(users, matcher.group(1), name);
                                }
                            }
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

    private static JsonArray arrayOf(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static void note(Map<String, Set<String>> users, String name, String file) {
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
