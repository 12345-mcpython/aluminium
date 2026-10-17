package com.laosun.aluminium.test.engine;

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
 * AGENT.md §2.4: the ledger states <b>rules</b>, not measurements - "how many suites / files / lines there are right
 * now" is answered by running a command, and a number copied into the file is wrong the next round.
 *
 * <p><b>Why a machine guard, and why now.</b> The rule was written as prose and was broken twice by the same shape:
 * first with suite/test counts, then again by a sentence about a cleanup that carried "N places / M files". Prose did
 * not stop it either time (§2.2: a rule that keeps being trodden on needs a guard, not a longer paragraph).
 *
 * <p><b>What it checks.</b> Only the shapes §2.4 names: {@code file:line} references, and "N + unit" for suites, test
 * cases, files, documents, methods, classes, rules, bytes and commits, plus the "N places" counting idiom. Formula
 * numbers ({@code 3.5}, {@code 0.01}, {@code 200+10L}) and dated experiment readings are deliberately <b>not</b>
 * flagged - §2.4 allows them.
 *
 * <p>⚠ When this fails, fix the sentence rather than the number: say what was measured <b>in words</b> ("没有内容读者"
 * instead of "0 个读者"), or put the command that prints it. If a number really must stay (it describes one dated
 * experiment), prefix the sentence with its date - and if even that is a state count, it belongs in a report, not here.
 */
public class LedgerNumberGuardTest {
    private static final Path LEDGER = Path.of("AGENT.md");

    /**
     * One banned shape, with the name a failure message should use.
     */
    private record Rule(Pattern pattern, String what) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\.(java|md|json):\\d+"), "行号引用（file:line）"),
            new Rule(Pattern.compile("\\d+\\s*(套件|用例)"), "套件 / 用例数"),
            new Rule(Pattern.compile("\\d+\\s*个文件"), "文件数"),
            new Rule(Pattern.compile("\\d+\\s*份(文件|文档)"), "文件 / 文档数"),
            new Rule(Pattern.compile("\\d+\\s*个(方法|类|规则)"), "方法 / 类 / 规则数"),
            new Rule(Pattern.compile("\\d+\\s*(MB|KB|字节)"), "大小"),
            new Rule(Pattern.compile("\\d+\\s*次提交"), "提交数"),
            new Rule(Pattern.compile("\\d+\\s*处"), "「N 处」式计数"));

    @Test
    public void theLedgerCarriesNoNumbersThatRot() throws IOException {
        Assertions.assertTrue(Files.isRegularFile(LEDGER),
                "the ledger must be at " + LEDGER.toAbsolutePath() + "; the guard reads the real file, not a copy");
        String text = Files.readString(LEDGER, StandardCharsets.UTF_8);
        List<String> lines = text.lines().toList();
        // Show the work: a guard that "passed" because it read nothing must not look like a guard that found nothing.
        System.out.println("[ledger] " + LEDGER + " lines=" + lines.size() + " chars=" + text.length());

        List<String> hits = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (Rule rule : RULES) {
                Matcher matcher = rule.pattern.matcher(lines.get(i));
                if (matcher.find()) {
                    hits.add("line " + (i + 1) + " [" + rule.what + "] \"" + matcher.group() + "\"  << "
                            + lines.get(i).trim());
                }
            }
        }
        Assertions.assertTrue(hits.isEmpty(),
                "AGENT.md §2.4: no numbers about the repository's current state - measure on the spot instead.\n"
                        + String.join("\n", hits));
    }
}
