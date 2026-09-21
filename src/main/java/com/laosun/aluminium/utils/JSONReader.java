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
 * <p><b>数据文件不在仓库里。</b>{@code src/main/resources/data/} 被 {@code .gitignore} 排除
 * （唯一入库的是补丁文件 {@code monster_attack_modify_ratio.json}），所以一次 {@code git clone}
 * 之后这些文件都不存在，必须先用 README 里那个生成脚本产出。缺文件时本类会抛
 * {@link IllegalStateException} 并带上文件名与生成指引 —— 见 {@link #fromJSON(String, Type)}。
 */
public final class JSONReader {
    private static final Gson GSON = new Gson();

    /**
     * 游戏数据的存放目录（相对 classpath），也是报错信息里给用户看的路径。
     */
    private static final String DATA_DIR = "/data/";

    /**
     * Reads and deserializes a JSON resource file.
     *
     * @param jsonName the file name under {@code /data/} (e.g. "weapons.json")
     * @param type     the target Gson type token
     * @param <T>      the expected return type
     * @return the deserialized object
     * @throws IllegalStateException 资源不存在（通常是还没生成数据，见类 javadoc）
     */
    @SneakyThrows
    public static <T> T fromJSON(String jsonName, Type type) {
        String resourcePath = DATA_DIR + jsonName;
        InputStream stream = JSONReader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            // 故意不抛 NPE 也不返回 null：数据缺失是"环境没准备好"，不是"代码有 bug"，
            // 所以报错必须自解释 —— 否则新手只会看到一个与真实原因无关的
            // ExceptionInInitializerError（Constant 静态块里抛出的任何异常都会变成它）。
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
