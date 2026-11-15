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
    private static final Pattern RECORDS = Pattern.compile("(\\d+) 条internal:1512/record");
    private static final Pattern BLOCKERS = Pattern.compile("(\\d+) 条阻塞");
    private static final Pattern NO_GAP = Pattern.compile("0 缺口");
    private static final Pattern DONE = Pattern.compile("\\*\\*完全实现\\*\\*");
    private static final Pattern OPEN = Pattern.compile("未完全实现");

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

    /**
     * The other column the counts guard cannot see: the row's <b>status word</b> against the file's {@code _status}.
     *
     * <p><b>Why it had to be added.</b> Measured on 2026-11-11: seven rows said 「未完全实现」 for a file that had said
     * {@code implemented} with zero {@code ⛔} for weeks (1005 / 1107 / 1203 / 1204 / 1208 / 1405 / 1409). Every number in
     * those rows was right, so the counts guard passed - and the ledger, which the file's own header calls "two-way
     * consistent with the JSON", understated the finished work by seven characters. The status word is the column a reader
     * consults to decide whether a character needs work at all, which makes it the most expensive one to get wrong.
     *
     * <p>It reads the same two spellings the table uses: {@code **完全实现**} (a finished file) and {@code 未完全实现}
     * (one with an open clause). {@code 完全未实现} rows are about characters with no content file and are checked by
     * {@code LedgerListsEveryShippedCharacterTest}, so they are skipped here.
     */
    @Test
    public void everyRowAgreesWithTheFilesOwnStatus() throws IOException {
        Map<String, Boolean> implemented = statusByCid();
        List<String> problems = new ArrayList<>();
        int rows = 0;
        for (String line : Files.readAllLines(LEDGER, StandardCharsets.UTF_8)) {
            Matcher row = RULE_ROW.matcher(line);
            if (!row.find()) {
                continue;
            }
            String cid = row.group(1);
            Boolean done = implemented.get(cid);
            if (done == null) {
                continue; // no file: 完全未实现 rows are the other guard's business
            }
            rows++;
            boolean ledgerSaysDone = DONE.matcher(line).find();
            boolean ledgerSaysOpen = OPEN.matcher(line).find();
            if (ledgerSaysDone == ledgerSaysOpen) {
                problems.add(cid + ": the row states " + (ledgerSaysDone ? "both" : "neither") + " of 「完全实现」 / "
                        + "「未完全实现», so it claims nothing a reader can act on");
                continue;
            }
            if (ledgerSaysDone != done) {
                problems.add(cid + ": the ledger says 「" + (ledgerSaysDone ? "完全实现" : "未完全实现")
                        + "」 and the file's _status is " + (done ? "implemented" : "partial"));
            }
        }
        Assertions.assertTrue(rows >= 90, "the guard must have read the character rows (read " + rows + ")");
        Assertions.assertTrue(problems.isEmpty(),
                "the ledger's status column must agree with the files' own _status (the file is authoritative):\n"
                        + String.join("\n", problems));
    }

    private record Counts(int rules, int records, int blockers) {
    }

    /**
     * {@code cid -> is its file implemented} for every character file that exists.
     */
    private static Map<String, Boolean> statusByCid() throws IOException {
        Map<String, Boolean> statuses = new LinkedHashMap<>();
        try (var stream = Files.list(CHARACTERS)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String cid = file.getFileName().toString().replace(".json", "");
                JsonObject object = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                statuses.put(cid, object.has("_status") && "implemented".equals(object.get("_status").getAsString()));
            }
        }
        return statuses;
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
