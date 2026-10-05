package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.TriggerTable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a light cone's trigger rules from {@code resources/light_cones/<weaponId>.json} (2026-09-29).
 *
 * <h2>Why a light cone reuses the trigger table</h2>
 * Its ability is the same kind of sentence as a relic set's or a character's -- "when &lt;event&gt;, do &lt;something the
 * engine can already do&gt;" -- so it reuses {@link TriggerTable}, the same JSON shape, the same op vocabulary and the same
 * condition DSL, exactly as {@link RelicTriggerTables} does. What it needed first was a KEY, and {@code Weapon} now keeps the
 * id it was already handed at construction.
 *
 * <p>⚠ <b>The rank axis is the next step, not this one.</b> Upstream states a light cone's numbers per superimposition rank
 * ({@code EquipmentSkillConfig}: {@code SkillID} + {@code Level}, five rows; {@code weapons.json} already carries per-level
 * rows too). Until content selects a row by rank, only clauses with NO rank-varying magnitude are authored, and each such
 * file says so in its note rather than approximating a value.
 */
public final class WeaponTriggerTables {
    private static final String DIR = "light_cones";
    private static final Gson GSON = new Gson();
    private static final Map<Integer, TriggerTable> CACHE = new HashMap<>();

    private WeaponTriggerTables() {
    }

    /** Whether this light cone has a rule file at all (most do not, yet). */
    public static boolean has(int weaponId) {
        return WeaponTriggerTables.class.getResource(pathFor(weaponId)) != null;
    }

    /**
     * The light cone's rules, or an empty table when it has none -- "no file" is the ordinary case, not an error, which is
     * the same reading {@code RelicTriggerTables} takes for a set with no rules.
     */
    public static synchronized TriggerTable of(int weaponId, int rank) {
        int key = weaponId * 10 + Math.max(1, Math.min(9, rank));
        TriggerTable cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        // ★ A light cone may DECLARE resources (2026-09-30): its file allows a top-level "resources" array next to the
        // rank keys, the same declaration shape a character's file uses. ⚠ The merged table refuses two declaring
        // sides, so this is a cone-side declaration only while no character declares the same name.
        TriggerTable table = new TriggerTable(weaponId, read(weaponId, rank), readResources(weaponId));
        CACHE.put(key, table);
        return table;
    }

    /**
     * The resource declarations of a light cone's file ({@code { "resources": [...], "1": [...], ... }}).
     *
     * <p>★ P8-8's shape, reused: {@code TriggerTable.plus} already carries declarations across a merge, and
     * {@code CharacterFactory} already registers whatever the merged table declares -- so the only missing piece was a
     * loader that reads them.
     *
     * @param weaponId the light cone id
     * @return the declarations, or an empty list
     */
    private static List<com.laosun.aluminium.beans.ResourceSpec> readResources(int weaponId) {
        String path = pathFor(weaponId);
        if (WeaponTriggerTables.class.getResource(path) == null) {
            return List.of();
        }
        try (InputStream stream = WeaponTriggerTables.class.getResourceAsStream(path)) {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(
                    new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("resources")) {
                return List.of();
            }
            List<com.laosun.aluminium.beans.ResourceSpec> declared =
                    GSON.fromJson(root.getAsJsonObject().get("resources"), RESOURCE_LIST);
            return declared == null ? List.of() : declared;
        } catch (Exception unreadable) {
            throw new IllegalStateException("Failed to read resource declarations of light cone " + weaponId, unreadable);
        }
    }

    private static final java.lang.reflect.Type RESOURCE_LIST =
            new com.google.gson.reflect.TypeToken<List<com.laosun.aluminium.beans.ResourceSpec>>() { }.getType();

    private static List<TriggerSpec> read(int weaponId, int rank) {
        Map<String, com.google.gson.JsonElement> file = parse(weaponId);
        if (file == null || file.isEmpty()) {
            return List.of();
        }
        com.google.gson.JsonElement flat = file.get("rules");
        if (flat != null) {
            return GSON.fromJson(flat, new TypeToken<List<TriggerSpec>>() {
            }.getType());                            // one set of rules for every rank
        }
        // ⚠ The EXACT rank, not "the highest threshold met": a light cone's superimposition does not accumulate the way a
        // relic set's piece count does. A rank with no row falls back to the lowest one that exists, so a file with fewer
        // rows than the game's five still works.
        com.google.gson.JsonElement exact = file.get(String.valueOf(rank));
        if (exact == null) {
            // ★ Only NUMERIC keys are ranks: a cone's file may also carry a top-level "resources" declaration, and
            // parsing that as a rank would throw. (Measured: the shape is new, the fallback was written before it.)
            String lowest = file.keySet().stream()
                    .filter(key -> !key.isEmpty() && key.chars().allMatch(Character::isDigit))
                    .min(java.util.Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow();
            exact = file.get(lowest);
        }
        return GSON.fromJson(exact, new TypeToken<List<TriggerSpec>>() {
        }.getType());
    }

    private static Map<String, com.google.gson.JsonElement> parse(int weaponId) {
        String path = pathFor(weaponId);
        if (WeaponTriggerTables.class.getResource(path) == null) {
            return null;
        }
        try (InputStream stream = WeaponTriggerTables.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, new TypeToken<Map<String, com.google.gson.JsonElement>>() {
            }.getType());
        } catch (Exception error) {
            throw new IllegalStateException("light cone " + weaponId + " has an unreadable rule file " + path, error);
        }
    }

    private static String pathFor(int weaponId) {
        return "/" + DIR + "/" + weaponId + ".json";
    }

}
