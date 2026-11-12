package com.laosun.aluminium.utils;

import com.google.gson.Gson;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Utility for loading JSON data from classpath resources under {@code /data/}.
 *
 * <p>Uses Gson for deserialization. All resources are read with UTF-8 encoding.
 *
 * <p><b>Most data files are not in the repository.</b> {@code src/main/resources/data/*} is ignored by
 * {@code .gitignore}, because those tables are produced by the generator named in {@code README.md}. Three of them
 * <b>are</b> checked in, because the generator cannot produce them: {@code monster_attack_modify_ratio.json}
 * (hand-written patch table), {@code skill_effects.json} (hand-maintained - it says how to read a non-damaging skill)
 * and {@code enemy_skills.json} (self-built, because the game data has no enemy skill table). Everything else, the
 * skill rows included, must be generated before the engine can start; when a file is missing this class throws
 * {@link IllegalStateException} carrying the file name and the generation hint - see
 * {@link #fromJSON(String, Type)}.
 *
 * <p>The same is true for the other way data can be unusable: a file that exists but parses to
 * {@code null} (zero bytes, or a literal {@code null}). It is reported at the same place, with the same
 * file name, rather than being returned as a {@code null} table for the caller to trip over later.
 */
public final class JSONReader {
    private static final Gson GSON = new Gson();

    /**
     * The directory where game data is stored (relative to the classpath), and also the path shown to the
     * user in error messages.
     */
    private static final String DATA_DIR = "/data/";

    /**
     * Reads and deserializes a JSON resource file.
     *
     * @param jsonName the file name under {@code /data/} (e.g. "weapons.json")
     * @param type     the target Gson type token
     * @param <T>      the expected return type
     * @return the deserialized object
     * @throws IllegalStateException the resource does not exist, or parses to {@code null} (usually the
     *                               data has not been generated yet, see the class javadoc)
     */
    @SneakyThrows
    public static <T> T fromJSON(String jsonName, Type type) {
        String resourcePath = DATA_DIR + jsonName;
        InputStream stream = JSONReader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            // Deliberately neither throwing NPE nor returning null: missing data means "the environment is
            // not prepared", not "the code has a bug", so the error MUST be self-explanatory - otherwise a
            // newcomer only sees an ExceptionInInitializerError unrelated to the real cause (any exception
            // thrown from Constant's static block turns into that).
            throw new IllegalStateException("Missing data file %s/%s".formatted(resourcePath, DATA_DIR));
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, type);
            if (parsed == null) {
                // the same "the environment is not prepared" case as a missing file, one step
                // later. Gson returns null for a zero-byte file or a literal `null`, and handing that
                // back would put a null *table* into Constant (WEAPONS = frozen(null)) - it then blows
                // up as an NPE on some unrelated line, or, worse, behaves like a silently empty table.
                // A file that merely has no rows is unaffected: `{ }` parses to an empty map, not null.
                throw new IllegalStateException("\"%s\" data is null"
                        .formatted(resourcePath));
            }
            return parsed;
        }
    }
}
