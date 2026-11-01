package com.laosun.aluminium.test.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ledger ({@code CONTENT-STATUS.md}) and the shipped files must describe the same set of characters.
 *
 * <p><b>Why this guard exists.</b> 2026-11-01: the ledger carried a character row whose numbers had already been corrected
 * in the content (and a row whose rule was written while the row still said "未写"), because nothing compared the two
 * once the file was committed. A ledger that disagrees with the code is worse than no ledger: it is read as fact.
 *
 * <p>The invariant here is the cheap half that can be checked mechanically: every character FILE has a row, and every row
 * either has a file or states 「无内容文件」 (the three characters with no content at all). The deeper half - "do the
 * numbers in the row match the rules?" - is what the reviewers' protocol is for.
 */
public class LedgerListsEveryShippedCharacterTest {

    private static final Pattern ROW = Pattern.compile("^\\|\\s*(\\d{4})\\s*\\|");

    @Test
    public void everyCharacterFileHasALedgerRowAndEveryRowHasAFileOrSaysSo() throws Exception {
        Set<String> files = new LinkedHashSet<>();
        try (var stream = Files.list(Path.of("src/main/resources/game/characters"))) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> files.add(p.getFileName().toString().replace(".json", "")));
        }
        Assertions.assertTrue(files.size() > 90, "precondition: the scan must see the character files");

        Set<String> rows = new LinkedHashSet<>();
        List<String> rowsWithoutFile = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("CONTENT-STATUS.md"), StandardCharsets.UTF_8)) {
            Matcher matcher = ROW.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String cid = matcher.group(1);
            rows.add(cid);
            if (!files.contains(cid) && !line.contains("无内容文件")) {
                rowsWithoutFile.add(cid);
            }
        }

        List<String> filesWithoutRow = new ArrayList<>();
        for (String cid : files) {
            if (!rows.contains(cid)) {
                filesWithoutRow.add(cid);
            }
        }
        System.out.println("[ledger] files=" + files.size() + " rows=" + rows.size()
                + " filesWithoutRow=" + filesWithoutRow.size() + " rowsWithoutFile=" + rowsWithoutFile.size());
        Assertions.assertTrue(filesWithoutRow.isEmpty(),
                "every character file needs a row in CONTENT-STATUS.md; these do not: " + filesWithoutRow);
        Assertions.assertTrue(rowsWithoutFile.isEmpty(),
                "a row without a file must say 「无内容文件」; these do not: " + rowsWithoutFile);
    }
}