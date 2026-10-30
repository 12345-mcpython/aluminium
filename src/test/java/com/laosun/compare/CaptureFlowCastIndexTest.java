package com.laosun.compare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The newer payload states <b>which cast</b> every event belongs to ({@code cast_index}), so the flow is a partition of
 * the payload instead of the adjacency guess the older file forced.
 *
 * <p><b>Why this matters.</b> With no cast identity, "the events whose actor and attack type did not change" is the only
 * available rule - and a rider delivered after the NEXT cast has begun lands in the wrong group. The fold-order problem the
 * replay used to report is exactly that. Here the payload's own index is used, and this case pins that it is honoured:
 * every group carries one of the payload's indices, every index appears, and every event is accounted for exactly once.
 */
public class CaptureFlowCastIndexTest {

    private static final String FILE = "/captures/veritas-2026-10-30.json";

    private static JsonObject capture() throws IOException {
        try (InputStream stream = CaptureFlowCastIndexTest.class.getResourceAsStream(FILE)) {
            Assertions.assertNotNull(stream, "missing capture " + FILE);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Every group's index is one the payload states, and every stated index produced at least one group.
     */
    @Test
    public void everyGroupCarriesThePayloadsOwnCastIndex() throws IOException {
        JsonArray events = capture().getAsJsonArray("damage_events");
        Set<Integer> stated = new TreeSet<>();
        for (JsonElement element : events) {
            stated.add((int) Math.rint(element.getAsJsonObject().get("cast_index").getAsDouble()));
        }

        List<CaptureFlow.Cast> flow = CaptureFlow.group(events);
        Set<Integer> used = new TreeSet<>();
        for (CaptureFlow.Cast cast : flow) {
            used.add(cast.index());
            Assertions.assertTrue(stated.contains(cast.index()),
                    "a group was made for cast " + cast.index() + ", which the payload never states");
        }
        Assertions.assertEquals(stated, used, "every stated cast produced a group, and only those");
        Assertions.assertTrue(stated.size() > 10, "the fight has many casts: " + stated.size());
    }

    /**
     * Every event is accounted for exactly once: the groups' {@code covered} counts add up to the event count.
     */
    @Test
    public void everyEventIsAccountedForOnce() throws IOException {
        JsonArray events = capture().getAsJsonArray("damage_events");
        int covered = CaptureFlow.group(events).stream().mapToInt(CaptureFlow.Cast::covered).sum();

        Assertions.assertEquals(events.size(), covered,
                "a payload with cast identity must partition exactly: " + covered + " of " + events.size());
    }

    /**
     * Rule-delivered damage is its own group, attributed to the cast it came from, and it names the skill row it belongs
     * to - the identity the older payload could not supply.
     */
    @Test
    public void ruleDeliveredDamageIsAttributedToItsCastAndSkill() throws IOException {
        List<CaptureFlow.Cast> derived = CaptureFlow.group(capture().getAsJsonArray("damage_events")).stream()
                .filter(cast -> "derived".equals(cast.classification()))
                .toList();

        Assertions.assertFalse(derived.isEmpty(), "the capture contains rule-delivered damage");
        Set<Integer> casts = new LinkedHashSet<>();
        for (CaptureFlow.Cast cast : derived) {
            Assertions.assertEquals(1140901L, cast.skillId(),
                    "the rule damage belongs to 小伊卡's own skill row (1140901)");
            casts.add(cast.index());
        }
        Assertions.assertTrue(casts.size() >= 3, "and it is spread over the casts that produced it: " + casts);
    }
}
