package com.laosun.compare;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The cast table is <b>derived</b> from the payload: {@code cast_index} says which cast, {@code skill_id} names the row, and
 * {@code skills.json} maps that row to its {@code (cid, slot)}.
 *
 * <p>This is the piece that used to be hand-written. With it, the plan's {@code casts} array is no longer the source of
 * truth for "who cast what" - it can be checked against the capture, and a slot mismatch cannot hide.
 */
public class DerivedCastsTest {

    private static final String FILE = "/captures/veritas-2026-10-30.json";

    private static List<DerivedCasts.Entry> entries() throws IOException {
        try (InputStream stream = DerivedCastsTest.class.getResourceAsStream(FILE)) {
            Assertions.assertNotNull(stream, "missing capture " + FILE);
            JsonObject capture = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return DerivedCasts.of(capture);
        }
    }

    private static Map<Integer, DerivedCasts.Entry> byCast(List<DerivedCasts.Entry> entries) {
        return entries.stream().collect(Collectors.toMap(DerivedCasts.Entry::castIndex, Function.identity()));
    }

    /**
     * Every cast that names a skill resolves to a row - and the capture's casts are all there.
     */
    @Test
    public void everyNamedSkillResolvesToARow() throws IOException {
        List<DerivedCasts.Entry> entries = entries();
        Assertions.assertEquals(19, entries.size(), "the capture's casts");

        List<DerivedCasts.Entry> unresolved = entries.stream()
                .filter(entry -> entry.skillId() != 0 && entry.cid() == 0)
                .toList();
        Assertions.assertTrue(unresolved.isEmpty(), "skills not found in skills.json: " + unresolved);
        for (DerivedCasts.Entry entry : entries) {
            Assertions.assertTrue(entry.hits() > 0, "cast " + entry.castIndex() + " settled nothing?");
        }
    }

    /**
     * The rows the four are actually driven at, read off the payload: 长夜 slot 1, 小伊卡 slot 1, 德谬歌 slot 1,
     * 晴空乐手 slot 1, and 昔涟's <b>slot 8</b> (the enhanced basic) - the last one is only reachable inside her ultimate,
     * so seeing it here is the payload confirming the ripple form.
     */
    @Test
    public void theRowsMatchTheKitsTheyBelongTo() throws IOException {
        Map<Integer, DerivedCasts.Entry> casts = byCast(entries());

        assertRow(casts.get(1), 11413, 1, "长夜's 忆灵技1");
        assertRow(casts.get(9), 11409, 1, "小伊卡's 忆灵技1");
        assertRow(casts.get(18), 11413, 7, "长夜's 「迷梦，流失，如露」");
        assertRow(casts.get(51), 11415, 1, "德谬歌's 忆灵技1");
        assertRow(casts.get(52), 11512, 1, "晴空乐手's 忆灵技1");
        assertRow(casts.get(59), 1415, 8, "昔涟's 强化普攻 - only castable inside 【往昔的涟漪】");
        assertRow(casts.get(76), 1415, 8, "the same row, a second time");
    }

    private static void assertRow(DerivedCasts.Entry entry, int cid, int slot, String what) {
        Assertions.assertNotNull(entry, what + " is missing from the derived table");
        Assertions.assertEquals(cid, entry.cid(), what + ": cid");
        Assertions.assertEquals(slot, entry.slot(), what + ": slot");
    }
}
