package com.laosun.aluminium.trigger;

import com.laosun.aluminium.enums.TriggerEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code TRIGGER.md} §4 and {@link TriggerEvent} must name the same events - both directions, plus the count in the
 * section heading.
 *
 * <p><b>Why this guard exists.</b> Found by hand on 2026-10-25: the section said 「共 39 个」 and listed 39 names, while
 * the enum had <b>40</b> - {@code SUMMON_LEFT} was wired, used by content, and missing from the one table a rule author
 * reads. Nothing failed; the omission was only visible to someone who happened to count. That is the same failure the
 * sibling {@link TriggerVocabularyDriftTest} exists for, and the same reason: <b>a copy drifts, and nothing notices
 * unless something compares it</b>.
 *
 * <p>⚠ Both directions are asserted. Only checking "every enum constant is documented" would let the doc keep
 * advertising a name that was removed - and a rule written from that name is refused at load time, i.e. the doc would
 * be actively misleading.
 *
 * <p>⚠ The parse is restricted to the <b>second cell</b> of each table row: §4's prose also mentions events by name
 * (「⚠ {@code BATTLE_START} 是最常用的一类」), and counting those would let a deleted row survive because a neighbouring
 * sentence still says the word.
 */
public class TriggerEventTableDriftTest {

    private static final Path TRIGGER_MD = Path.of("TRIGGER.md");

    /**
     * The §4 section, from its heading to the next {@code ##} heading.
     */
    private static String section() throws Exception {
        String md = Files.readString(TRIGGER_MD);
        int start = md.indexOf("## 4. 事件");
        Assertions.assertTrue(start > 0, "TRIGGER.md no longer has a §4 事件 heading; this guard cannot find its table");
        int next = md.indexOf("\n## ", start + 5);
        return next > 0 ? md.substring(start, next) : md.substring(start);
    }

    /**
     * The event names the table lists: backticked {@code UPPER_SNAKE} identifiers in the second cell of a row.
     */
    private static Set<String> documentedEvents() throws Exception {
        Set<String> out = new TreeSet<>();
        for (String line : section().split("\\R")) {
            if (!line.trim().startsWith("|")) {
                continue;
            }
            String[] cells = line.split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            Matcher name = Pattern.compile("`([A-Z][A-Z0-9_]*)`").matcher(cells[2]);
            while (name.find()) {
                out.add(name.group(1));
            }
        }
        return out;
    }

    /**
     * Every event the enum names, by reflection-free iteration - so this guard holds no second list.
     */
    private static Set<String> codeEvents() {
        Set<String> out = new TreeSet<>();
        for (TriggerEvent event : TriggerEvent.values()) {
            out.add(event.name());
        }
        return out;
    }

    @Test
    public void theDocumentedEventsAreExactlyTheEnums() throws Exception {
        Set<String> documented = documentedEvents();
        Set<String> code = codeEvents();

        Set<String> onlyInCode = new TreeSet<>(code);
        onlyInCode.removeAll(documented);
        Set<String> onlyInDoc = new TreeSet<>(documented);
        onlyInDoc.removeAll(code);

        Assertions.assertTrue(onlyInCode.isEmpty(),
                "these events exist on TriggerEvent but §4 does not list them, so nobody can find them: " + onlyInCode);
        Assertions.assertTrue(onlyInDoc.isEmpty(),
                "§4 lists these but TriggerEvent does not declare them, so a rule written from the doc cannot load: "
                        + onlyInDoc);
    }

    /**
     * The heading's own count (「共 N 个」) is the number a reader trusts before scrolling, and it is the half that was
     * actually wrong on 2026-10-25 - the names had drifted by one and the heading still said 39.
     */
    @Test
    public void theHeadingCountIsTheEnumSize() throws Exception {
        Matcher count = Pattern.compile("共\\s*(\\d+)\\s*个").matcher(section());
        Assertions.assertTrue(count.find(), "§4's heading no longer states 「共 N 个」; this guard cannot read it");
        Assertions.assertEquals(TriggerEvent.values().length, Integer.parseInt(count.group(1)),
                "§4's heading count must be the number of TriggerEvent constants");
    }

    /**
     * The probe the cases above rest on: the section is found, the table parses, and the parse is neither empty nor
     * short. Without it a renamed heading turns both cases into "two empty sets are equal" - green, and silent.
     */
    @Test
    public void theGuardSeesTheTable() throws Exception {
        Set<String> documented = documentedEvents();
        Assertions.assertTrue(documented.size() >= 30,
                "parsed only " + documented.size() + " event names from §4: " + documented);
        Assertions.assertEquals(codeEvents().size(), documented.size(),
                "same set, different size - an event is listed twice in the doc: " + documented);
    }
}
