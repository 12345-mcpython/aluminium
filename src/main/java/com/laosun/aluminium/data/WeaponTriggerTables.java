package com.laosun.aluminium.data;

import com.google.gson.Gson;
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
    public static synchronized TriggerTable of(int weaponId) {
        TriggerTable cached = CACHE.get(weaponId);
        if (cached != null) {
            return cached;
        }
        TriggerTable table = new TriggerTable(weaponId, read(weaponId));
        CACHE.put(weaponId, table);
        return table;
    }

    private static List<TriggerSpec> read(int weaponId) {
        String path = pathFor(weaponId);
        if (WeaponTriggerTables.class.getResource(path) == null) {
            return List.of();
        }
        try (InputStream stream = WeaponTriggerTables.class.getResourceAsStream(path);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            File file = GSON.fromJson(reader, File.class);
            return file == null || file.rules == null ? List.of() : List.copyOf(file.rules);
        } catch (Exception error) {
            throw new IllegalStateException("light cone " + weaponId + " has an unreadable rule file " + path, error);
        }
    }

    private static String pathFor(int weaponId) {
        return "/" + DIR + "/" + weaponId + ".json";
    }

    /** The file is {@code {"rules": [ ... ]}} -- the same shape a character file may take. */
    private static final class File {
        private List<TriggerSpec> rules;
    }
}
