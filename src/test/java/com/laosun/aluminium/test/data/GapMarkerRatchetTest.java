package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
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
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <b>How many open items are recorded WITHOUT the {@code ⛔} marker.</b>
 *
 * <p><b>Why.</b> The ledger's "unresolved" count is a character count of {@code ⛔} in {@code _gaps}. That is only honest if every
 * entry that describes unfinished work carries the glyph - and on 2026-11-09 it did not: <b>126</b> entries across the corpus say
 * 「未写 / 未读 / 未核实 / 尚未」 while carrying no marker, so the headline number (49) was a fraction of the truth. Two of them
 * lived in files marked {@code implemented} (1107's 星魂 6 residuals, 1013's four), which is how a "complete" character can still
 * be missing clauses with nothing failing.
 *
 * <p><b>Why a ratchet instead of a rule.</b> Requiring the glyph on all 126 today would mean editing 126 entries blind - the exact
 * churn this project refuses. So the count is pinned and may only go <b>down</b>: a new unmarked open item fails, and every turn
 * that closes or re-marks one lowers the ceiling. Convert an entry by giving it {@code ⛔} (still open) or by making it a landed
 * record (starts with ✅ / ⭐ and says what landed).
 *
 * <p>An entry is "open-sounding but unmarked" when it has no {@code ⛔}, does not start with ✅ or ⭐, and contains one of
 * 未写 / 未读 / 未核实 / 尚未. Quoting those words inside a landed record is fine - that is why the ✅/⭐ check comes first.
 */
public class GapMarkerRatchetTest {

    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");
    private static final Pattern OPEN_WORDS = Pattern.compile("未写|未读|未核实|尚未");

    /**
     * Measured 2026-11-09: 126 entries described open work without the marker; the same round turned 14 of them into real records
     * (3 大地图「不建模」 entries and 11 that are genuinely open and now carry the marker), so the ceiling was 112; 1401 added three records in the same style, so it was 109; 1306 turned four of its own over, so it is 105. ⚠ It may only go DOWN - lower it in the
     * same commit that marks or lands an entry.
     */
    private static final int BASELINE = 100;

    @Test
    public void unmarkedOpenRecordsMayOnlyGoDown() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(CHARACTERS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!root.isJsonObject()) {
                    continue;
                }
                JsonObject object = root.getAsJsonObject();
                JsonElement gaps = object.get("_gaps");
                if (gaps == null || !gaps.isJsonArray()) {
                    continue;
                }
                String cid = file.getFileName().toString().replace(".json", "");
                JsonArray array = gaps.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    String entry = array.get(i).getAsString();
                    if (entry.contains("\u26D4") || entry.startsWith("\u2705") || entry.startsWith("\u2B50")) {
                        continue;
                    }
                    if (!OPEN_WORDS.matcher(entry).find()) {
                        continue;
                    }
                    offenders.add(cid + " #" + i + ": " + entry.substring(0, Math.min(60, entry.length())));
                }
            }
        }
        System.out.println("[gap-marker] unmarked open records=" + offenders.size() + " baseline=" + BASELINE);
        Assertions.assertTrue(offenders.size() <= BASELINE,
                "a gap entry that describes open work must carry the marker (⛔) so the ledger's count means what it says;"
                        + " count grew from " + BASELINE + " to " + offenders.size() + ". New ones: "
                        + String.join(" | ", offenders.subList(Math.min(BASELINE, offenders.size()),
                        Math.min(offenders.size(), BASELINE + 8))));
    }

    /** The per-file breakdown, printed so the next round can pick the biggest offender instead of guessing. */
    @Test
    public void theBacklogIsVisiblePerCharacter() throws IOException {
        TreeMap<String, Integer> byCid = new TreeMap<>();
        try (Stream<Path> files = Files.list(CHARACTERS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!root.isJsonObject() || !root.getAsJsonObject().has("_gaps")) {
                    continue;
                }
                String cid = file.getFileName().toString().replace(".json", "");
                for (JsonElement gap : root.getAsJsonObject().getAsJsonArray("_gaps")) {
                    String entry = gap.getAsString();
                    if (entry.contains("\u26D4") || entry.startsWith("\u2705") || entry.startsWith("\u2B50")) {
                        continue;
                    }
                    if (OPEN_WORDS.matcher(entry).find()) {
                        byCid.merge(cid, 1, Integer::sum);
                    }
                }
            }
        }
        System.out.println("[gap-marker] per character: " + byCid);
        Assertions.assertFalse(byCid.isEmpty(),
                "if this is empty the corpus finally marks every open item - drop BASELINE to 0 and delete this test");
    }
}
