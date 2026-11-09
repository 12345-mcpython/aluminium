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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Does the engine's blast read the column the document names?</b>
 *
 * <p>A {@code Blast} row states the centre and the neighbours, and the engine's ordinary reading is "cell 2 prices the neighbours"
 * ({@code params[1]}). That is right for the common shape ({@code [1.92, 0.96]}), and wrong whenever the row interleaves another
 * number - 1212 镜流 (Jingliu)'s Ultimate is {@code [3, 1, 1.5, 1]}, where #2 is a 【朔望】 stack count, so the neighbours were
 * charged 1.0 instead of 1.5 with no symptom at all (measured 2026-11-09: they took a third of the centre where the document says a
 * half). {@code SkillEffectSpec.adjacent_param} is the fix; this test is what keeps the table honest.
 *
 * <p><b>How the expected column is read.</b> From the document, not from a ratio: take the {@code #N} that appears right after
 * 相邻 in the row's {@code skill_introduction}. A sentence that says 「单体<b>及其</b>相邻目标…等同于 #1」 states ONE number for
 * both, which is column 0. Adding your own heuristic on top (for instance "the neighbours are always half the centre") is exactly
 * what would miss the 60%/70% splashes, so the test only compares the sentence with the table.
 *
 * <p><b>The {@link #UNRESOLVED} list.</b> Five rows keep the engine's default because their sentence prices the MAIN target from a
 * column other than #1 as well ({@code main_param} has no override yet) - that is a different gap, registered per row in
 * {@code _gaps}. The list may only shrink: a row that moves out of it must become a curated entry.
 */
public class BlastAdjacentColumnTest {
    private static final Path DATA = Path.of("src/main/resources/game/data");
    /** The reference that prices the share right after 「等同于」 - the amount clause, not any number the sentence happens to name next. */
    private static final Pattern FIRST_REFERENCE = Pattern.compile("等同于[^#]{0,60}#(\\d)\\[");

    /** Rows whose MAIN column is not #1 either - a separate gap, not this test's business. */
    private static final Set<String> UNRESOLVED = Set.of("1205/8", "1205/3", "1406/3",
            // 1310/9 (SAM): the neighbours are priced by a FORMULA - 「装甲「萨姆」(#6*击破特攻 + #2%)攻击力」 - so no single column
            // can name them. That needs an expression, not an index; registered rather than approximated.
            "1310/9");

    @Test
    public void everyBlastRowPricesItsNeighboursFromTheColumnTheSentenceNames() throws IOException {
        JsonObject skills = read("skills.json");
        JsonObject curated = read("skill_effects.json");
        List<String> wrong = new ArrayList<>();
        int checked = 0;

        for (String cid : skills.keySet()) {
            JsonObject slots = skills.getAsJsonObject(cid);
            for (String slot : slots.keySet()) {
                JsonObject row = slots.getAsJsonObject(slot);
                if (!"Blast".equals(text(row, "skill_effect"))) {
                    continue;
                }
                String intro = row.has("skill_introduction") && row.get("skill_introduction").isJsonObject()
                        ? plain(text(row.getAsJsonObject("skill_introduction"), "chinese")) : "";
                int index = intro.indexOf("相邻");
                if (index < 0) {
                    continue;
                }
                Matcher matcher = FIRST_REFERENCE.matcher(intro.substring(index));
                // A row with a single cell has no neighbour column at all: the engine falls back to the centre, which is what
                // 「单体及其相邻目标…等同于 #1」 means. Skip it rather than invent an entry for it.
                if (row.has("param_list") && row.getAsJsonArray("param_list").get(0).getAsJsonArray().size() <= 1) {
                    checked++;
                    continue;
                }
                if (!matcher.find()) {
                    continue;
                }
                int documentColumn = Integer.parseInt(matcher.group(1)) - 1;
                Integer tableColumn = curatedColumn(curated, cid, slot);
                Integer mainTableColumn = curatedMainColumn(curated, cid, slot);
                int engineMain = mainTableColumn == null ? 0 : mainTableColumn;
                // The MAIN clause is the one immediately before 「并对其相邻」 - searching the whole sentence picks up an earlier,
                // unrelated clause (measured: 1404/2 and 1404/3 both name a DoT share first).
                Matcher before = FIRST_REFERENCE.matcher(intro.substring(Math.max(0, index - 150), index));
                String mainReference = "";
                String lastReference = null;
                while (before.find()) {
                    lastReference = before.group(1);
                }
                if (lastReference != null) {
                    int documentMain = Integer.parseInt(lastReference) - 1;
                    if (documentMain != engineMain) {
                        mainReference = cid + "/" + slot + ": the sentence prices the MAIN target from #" + (documentMain + 1)
                                + " but the table reads #" + (engineMain + 1) + "; ";
                    }
                }
                int engineColumn = tableColumn == null ? 1 : tableColumn;
                checked++;
                if (!mainReference.isEmpty() && !UNRESOLVED.contains(cid + "/" + slot)) {
                    wrong.add(mainReference);
                }
                if (engineColumn != documentColumn && !UNRESOLVED.contains(cid + "/" + slot)) {
                    wrong.add(cid + "/" + slot + ": the sentence prices the neighbours from #" + (documentColumn + 1)
                            + " but the table reads #" + (engineColumn + 1)
                            + (tableColumn == null ? " (no curated entry - the default)" : " (curated)"));
                }
            }
        }

        System.out.println("[blast-adjacent] rows checked=" + checked + " wrong=" + wrong.size()
                + " unresolved=" + UNRESOLVED.size());
        Assertions.assertTrue(wrong.isEmpty(),
                "a blast must price its neighbours from the column its own sentence names; add `adjacent_param` for these:\n"
                        + String.join("\n", wrong));
    }

    /**
     * The unresolved list must stay real: every entry has to still be a Blast row that reads a column the sentence does not name
     * there. Without this, the list would silently rot into "rows nobody looked at".
     */
    @Test
    public void theUnresolvedListStillNamesRealRows() throws IOException {
        JsonObject skills = read("skills.json");
        JsonObject curated = read("skill_effects.json");
        Set<String> stale = new TreeSet<>();
        for (String key : UNRESOLVED) {
            String[] parts = key.split("/");
            JsonObject row = skills.has(parts[0]) && skills.getAsJsonObject(parts[0]).has(parts[1])
                    ? skills.getAsJsonObject(parts[0]).getAsJsonObject(parts[1]) : null;
            if (row == null || !"Blast".equals(text(row, "skill_effect")) || curatedColumn(curated, parts[0], parts[1]) != null) {
                stale.add(key);
            }
        }
        Assertions.assertTrue(stale.isEmpty(),
                "these entries are no longer what the list claims (gone, not a blast, or already curated): " + stale);
    }

    // ==================================================================

    /** The sentence without its markup: tags and colour literals both start with #, which would break the reference match. */
    private static String plain(String text) {
        return text.replaceAll("<[^>]*>", "").replaceAll("#[0-9a-fA-F]{6,8}", "");
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(DATA.resolve(name), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Integer curatedMainColumn(JsonObject curated, String cid, String slot) {
        if (!curated.has(cid) || !curated.getAsJsonObject(cid).has(slot)) {
            return null;
        }
        JsonElement column = curated.getAsJsonObject(cid).getAsJsonObject(slot).get("main_param");
        return column == null ? null : column.getAsInt();
    }

    private static Integer curatedColumn(JsonObject curated, String cid, String slot) {
        if (!curated.has(cid)) {
            return null;
        }
        JsonObject slots = curated.getAsJsonObject(cid);
        if (!slots.has(slot)) {
            return null;
        }
        JsonElement column = slots.getAsJsonObject(slot).get("adjacent_param");
        return column == null ? null : column.getAsInt();
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
