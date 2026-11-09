package com.laosun.aluminium.test.data;

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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <b>Every {@code _gaps} entry that describes open work carries the marker.</b>
 *
 * <p>The ledger's "unresolved" count is a character count of {@code ⛔}. On 2026-11-09 that count was 49 while <b>126</b> entries
 * described open work without the glyph - so the headline number was a fraction of the truth, and two files marked
 * {@code implemented} still had unwritten clauses with nothing failing. Seven rounds of marking brought the backlog to zero; the
 * ratchet that used to pin it is gone, and what remains is the plain rule.
 *
 * <p>An entry is "open-sounding but unmarked" when it has no {@code ⛔}, does not start with ✅ or ⭐, and contains one of
 * 未写 / 未读 / 未核实 / 尚未. Quoting those words inside a landed record is fine - that is why the ✅/⭐ check comes first.
 */
public class GapMarkerTest {

    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");
    private static final Pattern OPEN_WORDS = Pattern.compile("未写|未读|未核实|尚未");

    @Test
    public void everyOpenGapCarriesTheMarker() throws IOException {
        List<String> offenders = new ArrayList<>();
        int marked = 0;
        try (Stream<Path> files = Files.list(CHARACTERS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!root.isJsonObject() || !root.getAsJsonObject().has("_gaps")) {
                    continue;
                }
                String cid = file.getFileName().toString().replace(".json", "");
                for (JsonElement gap : root.getAsJsonObject().getAsJsonArray("_gaps")) {
                    String entry = gap.getAsString();
                    if (entry.contains("\u26D4")) {
                        marked++;
                        continue;
                    }
                    if (entry.startsWith("\u2705") || entry.startsWith("\u2B50")) {
                        continue;
                    }
                    if (OPEN_WORDS.matcher(entry).find()) {
                        offenders.add(cid + ": " + entry.substring(0, Math.min(70, entry.length())));
                    }
                }
            }
        }
        System.out.println("[gap-marker] marked=" + marked + " unmarked=" + offenders.size());
        Assertions.assertTrue(offenders.isEmpty(),
                "a gap entry that describes open work must carry the marker (⛔), or the ledger's count stops meaning anything:"
                        + "\n" + String.join("\n", offenders));
        Assertions.assertTrue(marked > 0, "if this is 0 the corpus claims nothing is open - recheck before believing it");
    }
}
