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
 * <p><b>The data files are not in the repository.</b> {@code src/main/resources/data/} is excluded by
 * {@code .gitignore} (the only one checked in is the patch file {@code monster_attack_modify_ratio.json}),
 * so right after a {@code git clone} none of these files exist and they must first be produced with the
 * generator script in the README. When a file is missing, this class throws {@link IllegalStateException}
 * carrying the file name and generation guidance — see {@link #fromJSON(String, Type)}.
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
            // not prepared", not "the code has a bug", so the error MUST be self-explanatory — otherwise a
            // newcomer only sees an ExceptionInInitializerError unrelated to the real cause (any exception
            // thrown from Constant's static block turns into that).
            throw new IllegalStateException("""
                    缺少数据文件 %s（应位于 src/main/resources%s）
                    游戏数据不在仓库里（.gitignore 排除了 src/main/resources/data/），\
                    请先按 README 的 generator 一节生成数据，否则所有测试都会失败。"""
                    .formatted(resourcePath, DATA_DIR));
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, type);
            if (parsed == null) {
                // M-17: the same "the environment is not prepared" case as a missing file, one step
                // later. Gson returns null for a zero-byte file or a literal `null`, and handing that
                // back would put a null *table* into Constant (WEAPONS = frozen(null)) — it then blows
                // up as an NPE on some unrelated line, or, worse, behaves like a silently empty table.
                // A file that merely has no rows is unaffected: `{ }` parses to an empty map, not null.
                throw new IllegalStateException("""
                        数据文件 %s 解析结果为 null（文件是空的，或内容就是字面 null）
                        通常是上一次生成数据被中断留下的空文件，请按 README 的 generator 一节重新生成。"""
                        .formatted(resourcePath));
            }
            return parsed;
        }
    }
}
