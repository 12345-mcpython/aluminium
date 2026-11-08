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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ledger's per-character numbers are the JSON file's numbers.
 *
 * <p><b>Why a guard and not a habit.</b> {@code CONTENT-STATUS.md} carries "N 条规则、M 条记录（K 条阻塞）" for every character, and
 * nothing checked them: measured on 2026-11-08, one row (1112) still said 6 rules / 3 records against a file that had grown to 9 /
 * 4. The row is what a reader consults <b>instead of</b> the file, so a stale count is a claim nobody can verify at a glance -
 * the same family as the ledger's "0 缺口" rows that hid a registered blocker.
 *
 * <p><b>What it reads, and what it deliberately does not.</b> Only the three counts, with the same three currencies the file uses:
 * rules ({@code rules}). records ({@code _gaps}) and blockers (the {@code _gaps} entries that carry {@code ⛔}). Prose is left
 * alone - the ledger's wording may say whatever the author needs it to, and this guard would only strangle that.
 *
 * <p>⚠ The ledger's {@code 0 缺口} spelling is read as "no blocker", not "empty {@code _gaps}": an implemented character may keep
 * {@code ⭐}/{@code ✅} records from the rounds that closed it (five files do), and those are records rather than gaps.
 */
public class LedgerCountsMatchJsonTest {
    private static final Path LEDGER = Path.of("CONTENT-STATUS.md");
    private static final Path CHARACTERS = Path.of("src", "main", "resources", "game", "characters");
    private static final String BLOCKER = "\u26D4";

    private static final Pattern RULE_ROW = Pattern.compile("^\\|\\s*(\\d{4})\\s*\\|");
    private static final Pattern RULES = Pattern.compile("(\\d+) 条规则");
    private static final Pattern RECORDS = Pattern.compile("(\\d+) 条记录");
    private static final Pattern BLOCKERS = Pattern.compile("(\\d+) 条阻塞");
    private static final Pattern NO_GAP = Pattern.compile("0 缺口");

    @Test
    public void everyRowStatesTheFilesOwnNumbers() throws IOException {
        Map<String, Counts> files = countsByCid();
        List<String> problems = new ArrayList<>();
        int rows = 0;
        for (String line : Files.readAllLines(LEDGER, StandardCharsets.UTF_8)) {
            Matcher row = RULE_ROW.matcher(line);
            if (!row.find()) {
                continue;
            }
            String cid = row.group(1);
            Counts actual = files.get(cid);
            if (actual == null) {
                continue; // a row without a file is the other guard's business (LedgerListsEveryShippedCharacterTest)
            }
            rows++;
            Matcher rules = RULES.matcher(line);
            if (rules.find()) {
                int stated = Integer.parseInt(rules.group(1));
                if (stated != actual.rules()) {
                    problems.add(cid + ": ledger says " + stated + " rules, the file has " + actual.rules());
                }
            } else {
                problems.add(cid + ": the row states no rule count");
            }
            Matcher records = RECORDS.matcher(line);
            if (records.find()) {
                int stated = Integer.parseInt(records.group(1));
                if (stated != actual.records()) {
                    problems.add(cid + ": ledger says " + stated + " records, the file has " + actual.records());
                }
            }
            Matcher blockers = BLOCKERS.matcher(line);
            if (blockers.find()) {
                int stated = Integer.parseInt(blockers.group(1));
                if (stated != actual.blockers()) {
                    problems.add(cid + ": ledger says " + stated + " blockers, the file has " + actual.blockers());
                }
            } else if (NO_GAP.matcher(line).find() && actual.blockers() != 0) {
                problems.add(cid + ": the row says \"0 缺口\" but the file carries " + actual.blockers() + " ⛔ record(s)");
            }
        }
        Assertions.assertTrue(rows >= 90, "the guard must have read the character rows (read " + rows + ")");
        Assertions.assertTrue(problems.isEmpty(),
                "CONTENT-STATUS.md must state the files' own numbers (the file is authoritative):\n"
                        + String.join("\n", problems));
    }

    private record Counts(int rules, int records, int blockers) {
    }

    private static Map<String, Counts> countsByCid() throws IOException {
        Assertions.assertTrue(Files.isDirectory(CHARACTERS), "the character directory must exist: " + CHARACTERS.toAbsolutePath());
        Map<String, Counts> counts = new LinkedHashMap<>();
        try (var stream = Files.list(CHARACTERS)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String cid = file.getFileName().toString().replace(".json", "");
                JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                JsonObject object = root.getAsJsonObject();
                JsonArray rules = object.has("rules") && object.get("rules").isJsonArray()
                        ? object.getAsJsonArray("rules") : new JsonArray();
                JsonArray gaps = object.has("_gaps") && object.get("_gaps").isJsonArray()
                        ? object.getAsJsonArray("_gaps") : new JsonArray();
                int blockers = 0;
                for (JsonElement gap : gaps) {
                    if (gap.getAsString().contains(BLOCKER)) {
                        blockers++;
                    }
                }
                counts.put(cid, new Counts(rules.size(), gaps.size(), blockers));
            }
        }
        return counts;
    }
}
