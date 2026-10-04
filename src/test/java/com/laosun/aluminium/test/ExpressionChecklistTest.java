package com.laosun.aluminium.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards <b>EXPRESSION.md</b>, the 「完全表达」 checklist (objective ⑥, 2026-10-02).
 *
 * <p><b>Why a test and not just a document.</b> A checklist written from memory rots: it cites files that were renamed,
 * rules that were withdrawn, and families that shipped without anyone updating the row. This test makes the document
 * answerable to the tree, so the list can only claim what the repository can back up:
 *
 * <ul>
 *   <li>§1 must keep all four steps of the procedure (find the sentence / split the clauses / try to write it /
 *       register what cannot be written) -- the checklist is about the METHOD as much as the rows;</li>
 *   <li>every row of §2 (a sentence family that IS writable) must cite at least one path that exists, and must name a
 *       judge whose file exists -- 「有出货内容或明确读者 ＋ 测试 ＋ 实测变异 ＋ 文档」 has to be checkable;</li>
 *   <li>every row of §3 (a registered family) must fill all three columns: what is missing, who the readers are, and
 *       what the prerequisite is. ⚠ A row that says "readers: to be named" is exactly the vague claim the objective
 *       refuses, so an empty cell fails here.</li>
 * </ul>
 *
 * <p>\u26a0 It deliberately does NOT try to check the prose's meaning -- it checks that every claim has a file behind it, and
 * that no row is half-filled. Meaning is the reviewer's job; existence is this test's.
 */
public class ExpressionChecklistTest {
    private static final Path DOC = Path.of("EXPRESSION.md");
    private static final Path TEST_DIR = Path.of("src", "test", "java", "com", "laosun", "aluminium", "test");
    private static final Pattern PATH_TOKEN = Pattern.compile("src/[A-Za-z0-9_/.\\-]+\\.(json|java)");

    /** §2: every writable family points at files that exist, and names a judge that exists. */
    @Test
    public void everyWritableFamilyPointsAtRealEvidence() throws IOException {
        List<String> rows = tableRows(section(heading("## §2")));
        Assertions.assertFalse(rows.isEmpty(), "§2 must list the writable sentence families");
        for (String row : rows) {
            List<String> cells = cells(row);
            Assertions.assertEquals(4, cells.size(), "§2 rows have four columns: " + row);

            Matcher paths = PATH_TOKEN.matcher(cells.get(2));
            List<String> cited = new ArrayList<>();
            while (paths.find()) {
                cited.add(paths.group());
            }
            Assertions.assertFalse(cited.isEmpty(), "§2 row cites no file: " + row);
            for (String path : cited) {
                Assertions.assertTrue(Files.exists(Path.of(path)),
                        "§2 cites a file that does not exist: " + path + " (" + row + ")");
            }

            Path judge = TEST_DIR.resolve(cells.get(3) + ".java");
            Assertions.assertTrue(Files.exists(judge),
                    "§2 names a judge that does not exist: " + cells.get(3) + " (" + row + ")");
        }
    }

    /** §3: a registered family must say what is missing, WHO the readers are, and what the prerequisite is. */
    @Test
    public void everyRegisteredFamilyFillsAllThreeColumns() throws IOException {
        List<String> rows = tableRows(section(heading("## §3")));
        Assertions.assertFalse(rows.isEmpty(), "§3 must list the registered sentence families");
        for (String row : rows) {
            List<String> cells = cells(row);
            Assertions.assertEquals(4, cells.size(), "§3 rows have four columns: " + row);
            for (int column = 1; column <= 3; column++) {
                Assertions.assertFalse(cells.get(column).isBlank(),
                        "§3 column " + column + " is empty -- 「缺什么／读者是谁／前置是什么」 must all be stated: " + row);
            }
            Assertions.assertTrue(cells.get(2).matches(".*\\d.*"),
                    "§3 must count its readers (a named family with a number): " + row);
        }
    }

    /** §1: the four steps of the procedure are the part that must not be dropped. */
    @Test
    public void theProcedureKeepsItsFourSteps() throws IOException {
        List<String> body = section(heading("## §1"));
        String text = String.join("\n", body);
        for (String step : List.of("找到句子", "拆成从句", "试写", "登记")) {
            Assertions.assertTrue(text.contains(step), "§1 lost the step: " + step);
        }
    }

    // ==================================================================

    private static String heading(String prefix) {
        return prefix;
    }

    private static String read() throws IOException {
        Assertions.assertTrue(Files.exists(DOC), "EXPRESSION.md must exist at the project root");
        return Files.readString(DOC, StandardCharsets.UTF_8);
    }

    private static List<String> section(String prefix) throws IOException {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : read().split("\n")) {
            if (line.startsWith("## ")) {
                inside = line.startsWith(prefix);
                continue;
            }
            if (inside) {
                out.add(line);
            }
        }
        Assertions.assertFalse(out.isEmpty(), "the document has no section " + prefix);
        return out;
    }

    /** The data rows of a markdown table: no header, no separator. */
    private static List<String> tableRows(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|") || trimmed.contains("---") || trimmed.startsWith("| 句族")) {
                continue;
            }
            out.add(trimmed);
        }
        return out;
    }

    private static List<String> cells(String row) {
        List<String> out = new ArrayList<>();
        for (String cell : row.split("\\|")) {
            // \u26a0 Backticks are markdown, not part of the name: without this the judge cell reads
            // "`TalismanSavesAnAllyTest`" and no such file exists. Measured -- the first version of this guard failed on it.
            String trimmed = cell.trim().replace("`", "");
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
