package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.trigger.TriggerTable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads a light cone's trigger rules from {@code resources/game/light_cones/<weaponId>.json}.
 *
 * <h2>Why a light cone reuses the trigger table</h2>
 * Its ability is the same kind of sentence as a relic set's or a character's -- "when &lt;event&gt;, do &lt;something the
 * engine can already do&gt;" -- so it reuses {@link TriggerTable}, the same JSON shape, the same op vocabulary and the same
 * condition DSL, exactly as {@link RelicTriggerTables} does. What it needed first was a KEY, and {@code Weapon} now keeps the
 * id it was already handed at construction.
 *
 * <p>Note: <b>The rank axis is the next step, not this one.</b> Upstream states a light cone's numbers per superimposition rank
 * ({@code EquipmentSkillConfig}: {@code SkillID} + {@code Level}, five rows; {@code weapons.json} already carries per-level
 * rows too). Until content selects a row by rank, only clauses with NO rank-varying magnitude are authored, and each such
 * file says so in its note rather than approximating a value.
 */
public final class WeaponTriggerTables {
    private static final String DIR = "game/light_cones";
    private static final Gson GSON = new Gson();
    private static final Map<Integer, TriggerTable> CACHE = new HashMap<>();

    private WeaponTriggerTables() {
    }

    /**
     * Whether this light cone has a rule file at all (most do not, yet).
     */
    public static boolean has(int weaponId) {
        return WeaponTriggerTables.class.getResource(pathFor(weaponId)) != null;
    }

    /**
     * The light cone's rules, or an empty table when it has none -- "no file" is the ordinary case, not an error, which is
     * the same reading {@code RelicTriggerTables} takes for a set with no rules.
     *
     * <p><b>The file header.</b> A cone file may state {@code schema} and {@code light_cone}, the same pair a character
     * file states as {@code schema} / {@code character}: the version this engine reads, and the id the file belongs to.
     * Both are optional (a file written before the header existed stays readable), both are validated when present, and
     * every key that is neither a rank, {@code rules}, {@code resources} nor an annotation is <b>refused</b> -- until
     * 2026-10-31 an unknown top-level key here was silently ignored, which is the one failure shape this loader was
     * supposed to prevent.
     */
    public static synchronized TriggerTable of(int weaponId, int rank) {
        int key = weaponId * 10 + Math.max(1, Math.min(9, rank));
        TriggerTable cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        JsonObject file = file(weaponId);
        // A light cone may DECLARE resources: its file allows a top-level "resources" array next to the
        // rank keys, the same declaration shape a character's file uses. Note: The merged table refuses two declaring
        // sides, so this is a cone-side declaration only while no character declares the same name.
        TriggerTable table = new TriggerTable(weaponId, rules(weaponId, rank, file), resources(weaponId, file));
        CACHE.put(key, table);
        return table;
    }

    /**
     * The resource declarations of a light cone's file ({@code { "resources": [...], "1": [...], ... }}).
     *
     * <p>The resource shape, reused: {@code TriggerTable.plus} already carries declarations across a merge, and
     * {@code CharacterFactory} already registers whatever the merged table declares -- so the only missing piece was a
     * loader that reads them.
     */
    private static List<com.laosun.aluminium.beans.ResourceSpec> resources(int weaponId, JsonObject file) {
        if (file == null || !file.has("resources") || file.get("resources").isJsonNull()) {
            return List.of();
        }
        try {
            List<com.laosun.aluminium.beans.ResourceSpec> declared =
                    GSON.fromJson(file.get("resources"), RESOURCE_LIST);
            return declared == null ? List.of() : declared;
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException("light cone " + weaponId + ": unreadable \"resources\": "
                    + unreadable.getMessage(), unreadable);
        }
    }

    private static final java.lang.reflect.Type RESOURCE_LIST =
            new com.google.gson.reflect.TypeToken<List<com.laosun.aluminium.beans.ResourceSpec>>() {
            }.getType();

    private static List<TriggerSpec> rules(int weaponId, int rank, JsonObject file) {
        if (file == null || file.isEmpty()) {
            return List.of();
        }
        JsonElement flat = file.get("rules");
        if (flat != null && !flat.isJsonNull()) {
            return GSON.fromJson(flat, new TypeToken<List<TriggerSpec>>() {
            }.getType());                            // one set of rules for every rank
        }
        // Note: The EXACT rank, not "the highest threshold met": a light cone's superimposition does not accumulate the way a
        // relic set's piece count does. A rank with no row falls back to the lowest one that exists, so a file with fewer
        // rows than the game's five still works.
        JsonElement exact = file.get(String.valueOf(rank));
        if (exact == null || exact.isJsonNull()) {
            // Only NUMERIC keys are ranks: a cone's file may also carry a top-level "resources" declaration, and
            // parsing that as a rank would throw. (Measured: the shape is new, the fallback was written before it.)
            String lowest = file.keySet().stream()
                    .filter(WeaponTriggerTables::isRankKey)
                    .min(java.util.Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow(() -> new IllegalStateException("light cone " + weaponId
                            + " has no rank rows and no \"rules\" array, so it declares nothing"
                            + " (write \"1\": [...] or \"rules\": [...])"));
            exact = file.get(lowest);
        }
        return GSON.fromJson(exact, new TypeToken<List<TriggerSpec>>() {
        }.getType());
    }

    /**
     * Parses and validates one cone file.
     *
     * <p>The allowed top level is: numeric rank keys, {@code rules}, {@code resources}, {@code schema},
     * {@code light_cone} and the annotation keys. Every other key is refused rather than ignored, and each rules array
     * runs the same vocabulary guards a character file gets (rule keys, effect keys, condition keys, status
     * annotations), so a typo fails at load in all three content kinds alike.
     *
     * @return the file, or {@code null} when the cone has no file
     */
    private static JsonObject file(int weaponId) {
        String path = pathFor(weaponId);
        if (WeaponTriggerTables.class.getResource(path) == null) {
            return null;
        }
        try (InputStream stream = WeaponTriggerTables.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("a light cone rule file must be an object keyed by rank");
            }
            JsonObject object = root.getAsJsonObject();
            Set<String> annotations = TriggerTables.annotationKeys();
            for (String key : object.keySet()) {
                if (isRankKey(key) || HEADER_KEYS.contains(key) || annotations.contains(key)) {
                    continue;
                }
                throw new IllegalArgumentException("unknown key \"" + key + "\"; a light cone file is keyed by rank"
                        + " and may state " + HEADER_KEYS.stream().sorted().toList() + " plus annotations");
            }
            TriggerTables.requireFileHeader(object, "light_cone", weaponId, "light cone " + weaponId);
            TriggerTables.requireFileStatus(object, "light cone " + weaponId);
            for (String key : object.keySet()) {
                if ("rules".equals(key) || isRankKey(key)) {
                    TriggerTables.requireRuleVocabulary(object.get(key), "light cone " + weaponId + " [" + key + "]");
                }
            }
            return object;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("light cone " + weaponId + " has an unreadable rule file " + path, error);
        } catch (RuntimeException refusal) {
            throw new IllegalStateException("Invalid trigger rules for light cone " + weaponId + " in " + path
                    + ": " + refusal.getMessage(), refusal);
        }
    }

    /**
     * Top-level keys that are metadata rather than rank rows.
     */
    private static final Set<String> HEADER_KEYS = Set.of("schema", "light_cone", "rules", "resources");

    private static boolean isRankKey(String key) {
        return key != null && !key.isEmpty() && key.chars().allMatch(Character::isDigit);
    }

    private static String pathFor(int weaponId) {
        return "/" + DIR + "/" + weaponId + ".json";
    }

}

