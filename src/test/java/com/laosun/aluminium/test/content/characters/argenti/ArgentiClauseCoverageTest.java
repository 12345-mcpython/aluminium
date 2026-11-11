package com.laosun.aluminium.test.content.characters.argenti;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1302 银枝 clause coverage: every section of {@code 1302_银枝.md} maps to a rule or to the data row it reads.
 *
 * <p><b>Why this file exists now.</b> Its {@code _gaps} carried a single line that ended in 「⛔ 仍开：其余技能从句与星魂」 while every one of
 * those sections — the talent's stacks, the three traces, all six Eidolons, the technique and the enhanced Ultimate — already had a rule.
 * The status said {@code implemented}, so nothing ever re-read that line; this map is what settles it.
 */
public class ArgentiClauseCoverageTest {

    @Test
    public void theFileNoLongerClaimsGapsItDoesNotHave() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1302.json");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Assertions.assertEquals("implemented", root.get("_status").getAsString());
        Assertions.assertTrue(root.getAsJsonArray("_gaps").isEmpty(),
                "the old line said 「其余技能从句与星魂」 while all six Eidolons and the traces already had rules");
    }

}
