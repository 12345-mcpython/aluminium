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
import java.util.LinkedHashMap;
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
 * {@code game/state_names.json} is the <b>declaration site</b> every state / stack / resource name must appear in.
 *
 * <p><b>Why this exists.</b> Resources already declare themselves ({@code resources[]} with an {@code id}),
 * paths and controls already pair an ASCII identity with a Chinese name ({@code Path.fromName("欢愉") -> ELATION},
 * {@code new ControlEffect("FROZEN", "冻结", …)}). States did not: a name was a free string that appeared in whatever rule
 * used it, which cost two things at once -
 * <ul>
 *   <li>a localization layer has no anchor to join on, because the key and the displayed text are the same string;</li>
 *   <li>a single wrong character is a <b>silent</b> no-op ({@code BuffManager.hasState} is an exact match), which is the
 *       failure mode this project refuses everywhere else.</li>
 * </ul>
 *
 * <p><b>What a declared name carries.</b> {@code kind} decides where its ASCII key lives:
 * <ul>
 *   <li>{@code engine} - created or known by the engine itself (「秘技」, 「弱点击破」, the control and DoT families);</li>
 *   <li>{@code shared} - a game term that more than one content file uses (a party resource, a zone, a cross-file state);</li>
 *   <li>{@code local} - a counter this project invented for ONE file. Its key must carry that file
 *       ({@code local:<file>/<slug>}) and the guard refuses it the moment a second file reads it.</li>
 * </ul>
 *
 * <p><b>Why the guard reads structurally, not by scanning text.</b> {@code _note} / {@code source} / {@code _gaps} quote
 * conditions verbatim (「`self has_state 秘技`」), so a text scan counts prose as usage - measured while building this:
 * it turned 372 real names into 250 with a dozen backtick-suffixed phantoms. The walk below only visits the fields that
 * carry a name.
 */
public class StateNameRegistryTest {

    private static final Path GAME = Path.of("src", "main", "resources", "game");
    private static final Path REGISTRY = GAME.resolve("state_names.json");
    private static final Set<String> KINDS = Set.of("engine", "shared", "local");
    /** {@code sys:technique}, {@code break:weakness}, {@code local:1015/loop_layers}. */
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]*(:[a-z0-9_.-]+)+$");
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
     * The ops whose {@code buff} is a <b>state identity</b> - something {@code REMOVE_STATE} can take off later.
     *
     * <p>⚠ Every other op's {@code buff} is a <b>display label</b> for an otherwise unnamed modifier, and those read like
     * prose (measured: {@code "113/4 暴击率"}, {@code "1506天赋-暴击伤害"}). Treating a label as a name would put that
     * prose in the registry, and the registry's job is the identities only.
     */
    private static final Set<String> STATE_OPS = Set.of(
            "APPLY_BUFF", "REMOVE_STATE", "ADD_STACK", "REMOVE_STACK");

    /** Fields whose value may carry a name behind a selector prefix ({@code holder_of:X}, {@code self_resource:X}). */
    private static final List<Pattern> SELECTOR_FIELDS = List.of(
            Pattern.compile("^(?:holder_of|highest_stacks_enemy|lowest_stacks_enemy):(.+)$"),
            Pattern.compile("^(?:self_resource|summon_resource|party_resource|max_party_resource):(.+)$"));

    @Test
    public void everyNameUsedByContentIsDeclared() throws IOException {
        Map<String, Set<String>> users = usageByFile();
        JsonObject registry = read(REGISTRY);
        JsonObject names = registry.getAsJsonObject("names");
        Assertions.assertNotNull(names, "state_names.json must carry a \"names\" object");

        List<String> undeclared = new ArrayList<>();
        List<String> unused = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : users.entrySet()) {
            if (!names.has(entry.getKey())) {
                undeclared.add(entry.getKey() + " -> " + String.join(",", new TreeSet<>(entry.getValue())));
            }
        }
        for (String declared : names.keySet()) {
            if (!users.containsKey(declared)) {
                unused.add(declared);
            }
        }
        dumpUsage(users);
        System.out.println("[state-names] used=" + users.size() + " declared=" + names.size());
        Assertions.assertTrue(undeclared.isEmpty(),
                "these names are used by content but not declared in " + REGISTRY + " (name -> files):\n  "
                        + String.join("\n  ", undeclared));
        Assertions.assertTrue(unused.isEmpty(),
                "these declared names are used by no rule any more, so the registry is stale: " + unused);
    }

    @Test
    public void everyDeclarationStatesItsKindAndAnAsciiKey() throws IOException {
        JsonObject names = read(REGISTRY).getAsJsonObject("names");
        Map<String, Set<String>> users = usageByFile();
        Map<String, String> keyOwners = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
            String name = entry.getKey();
            JsonObject declaration = entry.getValue().getAsJsonObject();
            String kind = declaration.has("kind") ? declaration.get("kind").getAsString() : "";
            if (!KINDS.contains(kind)) {
                problems.add(name + ": kind must be one of " + new TreeSet<>(KINDS) + ", got \"" + kind + "\"");
                continue;
            }
            Set<String> files = users.getOrDefault(name, Set.of());
            if ("local".equals(kind)) {
                if (files.size() != 1) {
                    problems.add(name + ": declared local but read by " + files.size() + " files "
                            + new TreeSet<>(files) + " - a private counter that leaked becomes shared");
                }
                String file = declaration.has("file") ? declaration.get("file").getAsString() : "";
                if (!file.equals(files.stream().findFirst().orElse(""))) {
                    problems.add(name + ": local name must state \"file\" = " + files + ", got \"" + file + "\"");
                }
                if (declaration.has("key")) {
                    String key = declaration.get("key").getAsString();
                    if (!key.startsWith("local:" + file + "/")) {
                        problems.add(name + ": a local key must start \"local:" + file + "/\", got \"" + key + "\"");
                    }
                    checkKey(name, key, keyOwners, problems);
                }
                continue;
            }
            // An `engine` name is created by the engine, so its ASCII key is assigned here and now. A `shared` name
            // is still written in Chinese until its family is migrated (Phase D), so the key is optional for now - the
            // moment it is present it must be well-formed and unique.
            if (!declaration.has("key")) {
                if ("engine".equals(kind)) {
                    problems.add(name + ": an engine name needs an ASCII \"key\" (the engine creates it)");
                }
                continue;
            }
            String key = declaration.get("key").getAsString();
            checkKey(name, key, keyOwners, problems);
            if (key.equals(name) && !declaration.has("zh")) {
                problems.add(name + ": once the key IS the name, the entry must keep the Chinese term in \"zh\" - "
                        + "otherwise the sentence-to-key mapping is lost");
            }
        }
        Assertions.assertTrue(problems.isEmpty(),
                "the state-name registry is malformed:\n  " + String.join("\n  ", problems));
    }

    // ==================================================================

    /**
     * Writes the structured usage list ({@code name<TAB>file,file}) to {@code build/} so a migration script can
     * regenerate the registry from data instead of from a failure message - measured: one line of JUnit's appended
     * "expected/but was" text ended up inside a file name that way.
     *
     * @param users name to the files that use it
     * @throws IOException when build/ cannot be written
     */
    private static void dumpUsage(Map<String, Set<String>> users) throws IOException {
        Path out = Path.of("build", "state-name-usage.txt");
        Files.createDirectories(out.getParent());
        StringBuilder text = new StringBuilder();
        users.forEach((name, files) -> text.append(name).append('\t')
                .append(String.join(",", new TreeSet<>(files))).append('\n'));
        Files.writeString(out, text.toString(), StandardCharsets.UTF_8);
    }

    private static void checkKey(String name, String key, Map<String, String> keyOwners, List<String> problems) {
        if (!KEY.matcher(key).matches()) {
            problems.add(name + ": key \"" + key + "\" is not namespaced ASCII "
                    + "(shape: <namespace>:<slug>, lower-case letters, digits, _ . - /)");
            return;
        }
        if (CJK.matcher(key).find()) {
            problems.add(name + ": key \"" + key + "\" still contains CJK");
        }
        String previous = keyOwners.put(key, name);
        if (previous != null) {
            problems.add(name + ": key \"" + key + "\" is already taken by \"" + previous + "\"");
        }
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
