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
     * @throws IllegalStateException the resource does not exist (usually the data has not been generated
     *                               yet, see the class javadoc)
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
            return GSON.fromJson(reader, type);
        }
    }
}
