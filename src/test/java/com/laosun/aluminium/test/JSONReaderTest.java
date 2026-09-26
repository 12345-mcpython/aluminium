package com.laosun.aluminium.test;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.utils.JSONReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * M-17: "the environment is not prepared" must never surface as a far-away NPE.
 *
 * <p>{@code JSONReader} already explained a <b>missing</b> file (an {@link IllegalStateException}
 * naming the path and the generator). The other half was unguarded: {@code GSON.fromJson} returns
 * {@code null} for a zero-byte file or a literal {@code null}, and that {@code null} used to travel
 * into {@code Constant} as e.g. {@code WEAPONS = frozen(null)} — it then surfaced much later as an NPE
 * on an unrelated line, or (worse, because it is silent) as a table that merely looks empty.
 *
 * <p>Why this is the same class of bug as the missing-file branch: the cause is "the data was not
 * generated/regenerated properly", not "the engine has a bug", so it has to be reported where it is
 * detected, with the file name attached.
 *
 * <p>The fixtures live in {@code src/test/resources/data/} — they cannot share the main data
 * directory, which is gitignored (and therefore absent right after a clone). The last case is a
 * control: it reads a real generated file, so the new guard cannot pass by simply always throwing.
 */
public class JSONReaderTest {

    private static final TypeToken<Map<Integer, Double>> NUMBER_TABLE = new TypeToken<>() {
    };

    /**
     * The pre-existing half, pinned so both halves of "the data is not usable" stay side by side.
     */
    @Test
    public void missingFileIsReportedWithItsPath() {
        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> JSONReader.fromJSON("this_file_does_not_exist.json", NUMBER_TABLE.getType()));
        Assertions.assertTrue(error.getMessage().contains("this_file_does_not_exist.json"),
                "the message must name the file, otherwise nobody can act on it");
    }

    /**
     * A file whose whole content is {@code null}.
     */
    @Test
    public void nullLiteralContentIsReportedInsteadOfReturningNull() {
        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> JSONReader.fromJSON("null_literal.json", NUMBER_TABLE.getType()),
                "Gson returns null here; returning it would make Constant hold a null table");
        Assertions.assertTrue(error.getMessage().contains("null_literal.json"),
                "the message must name the file: " + error.getMessage());
    }

    /**
     * A zero-byte file (what a generator run interrupted halfway leaves behind).
     */
    @Test
    public void emptyFileIsReportedInsteadOfReturningNull() {
        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> JSONReader.fromJSON("empty_table.json", NUMBER_TABLE.getType()));
        Assertions.assertTrue(error.getMessage().contains("empty_table.json"),
                "the message must name the file: " + error.getMessage());
    }

    /**
     * The generated files are read through the same method, so the guard must not reject them.
     *
     * <p>Empty is <b>not</b> the same as "unusable" in general: an empty table is a legitimate table.
     * Only the {@code null} <i>result</i> is rejected — {@code {}} still parses to an empty map, which
     * is what the loaders that tolerate a missing file rely on.
     */
    @Test
    public void realDataFileStillParsesAndEmptyTablesAreStillLegal() {
        Map<Integer, Double> breakingRate = JSONReader.fromJSON("breaking_rate.json", NUMBER_TABLE.getType());
        Assertions.assertNotNull(breakingRate, "a real generated file must still parse");
        Assertions.assertFalse(breakingRate.isEmpty(), "and it really has content");

        Map<Integer, Double> emptyTable = JSONReader.fromJSON("empty_table_but_valid.json", NUMBER_TABLE.getType());
        Assertions.assertNotNull(emptyTable, "{ } is an empty table, not a failure");
        Assertions.assertTrue(emptyTable.isEmpty());
    }
}
