package com.laosun.aluminium.test.compare;

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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The <b>2026-10-30</b> capture's payload, and the three things it carries that the older one did not.
 *
 * <h2>What the older payload lacked, and what this one states per event</h2>
 * <ul>
 *   <li><b>Identity</b> - {@code cast_index}, {@code skill_id}, {@code skill_slot}, {@code attacker_entity} (the summon's
 *       own uid), {@code target.uid}, {@code target_kind}, {@code turn}. The old file had the attacker's uid and nothing
 *       else, which is why "which cast is this hit part of" had to be inferred from event order;</li>
 *   <li><b>The toughness bar</b> - {@code target_toughness_current / _max / _after} and {@code stance_reduced}. The old
 *       file had none, so a break could only be checked by back-solving its number;</li>
 *   <li><b>State and accounting</b> - {@code buffs} (a list per event), {@code hp_before/_after},
 *       {@code energy_before/_after/_max}, {@code shield_absorbed}, {@code overkill_damage}.</li>
 * </ul>
 *
 * <h2>What this case pins</h2>
 * The shape, so a later export cannot quietly drop a field the tooling depends on; the <b>toughness arithmetic</b> (every
 * reduction lands exactly on the after-value); and the panels, because {@code allies[uid].properties} carries the same
 * base + ratio + delta triple the account dump is read for - so the game's own panel can be checked against the engine's
 * derived one.
 */
public class CapturePayloadShapeTest {

    private static final String FILE = "/captures/veritas-2026-10-30.json";

    /** The panel the engine derives for each member (see {@code PanelFromDumpTest}). */
    private static final String[][] PANELS = {
            {"1409", "1325.447027", "1375.416965"},
            {"1413", "1500.771705", "1201.083677"},
            {"1415", "1275.372000", "1261.328347"},
            {"1512", "2655.858655", "1043.408938"},
    };

    private static JsonObject capture() throws IOException {
        try (InputStream stream = CapturePayloadShapeTest.class.getResourceAsStream(FILE)) {
            Assertions.assertNotNull(stream, "missing capture " + FILE);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static double number(JsonObject object, String field) {
        return object.get(field).getAsDouble();
    }

    /** A missing ratio means zero, not a broken payload: the export omits fields that happen to be 0. */
    private static double numberOrZero(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? 0 : value.getAsDouble();
    }

    /**
     * Every event states the identity, the toughness and the accounting fields - no event may come without them.
     */
    @Test
    public void everyEventCarriesItsIdentityAndItsBar() throws IOException {
        JsonArray events = capture().getAsJsonArray("damage_events");
        Assertions.assertTrue(events.size() > 100, "the capture is a whole fight: " + events.size() + " events");

        Set<String> castIndices = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (JsonElement element : events) {
            JsonObject event = element.getAsJsonObject();
            for (String field : List.of("cast_index", "skill_id", "skill_slot", "attacker_entity", "attacker_kind",
                    "target", "target_kind", "turn", "buffs", "hp_before", "hp_after", "energy_before", "energy_after",
                    "energy_max", "shield_absorbed", "overkill_damage", "stance_reduced", "target_toughness_current",
                    "target_toughness_max", "target_toughness_after")) {
                if (event.get(field) == null) {
                    missing.add(event.get("type").getAsString() + " has no " + field);
                }
            }
            castIndices.add(text(event, "cast_index"));
        }
        Assertions.assertTrue(missing.isEmpty(), "events without the fields the tooling needs:\n  "
                + String.join("\n  ", missing.subList(0, Math.min(10, missing.size()))));
        Assertions.assertTrue(castIndices.size() > 5, "the capture groups its events into casts: " + castIndices);
    }

    /**
     * The toughness arithmetic: every reduction lands exactly on the after-value the same event states.
     */
    @Test
    public void everyToughnessReductionLandsOnItsAfterValue() throws IOException {
        int checked = 0;
        List<String> wrong = new ArrayList<>();
        for (JsonElement element : capture().getAsJsonArray("damage_events")) {
            JsonObject event = element.getAsJsonObject();
            double reduced = number(event, "stance_reduced");
            if (reduced == 0) {
                continue;                                   // no reduction: the arithmetic says nothing
            }
            double current = number(event, "target_toughness_current");
            double after = number(event, "target_toughness_after");
            Assertions.assertTrue(after >= 0, "a bar cannot go below zero");
            Assertions.assertEquals(current - reduced, after, Math.max(1e-6, Math.abs(current) * 1e-9),
                    "event " + checked + " (" + event.get("type").getAsString() + "): " + current + " - " + reduced);
            checked++;
        }
        Assertions.assertTrue(checked > 20, "enough reductions to be a real check: " + checked);
    }

    /**
     * A break event names a target whose bar has a maximum - the number the old payload could not state at all.
     */
    @Test
    public void theBreakPointsAtATargetWithAKnownBar() throws IOException {
        for (JsonElement element : capture().getAsJsonArray("damage_events")) {
            JsonObject event = element.getAsJsonObject();
            if (!"Break".equals(text(event, "type"))) {
                continue;
            }
            Assertions.assertTrue(number(event, "target_toughness_max") > 0,
                    "the broken target has a bar of a stated size");
            Assertions.assertEquals(0, number(event, "target_toughness_current"), 1e-9,
                    "a break happens when the bar is empty");
            return;
        }
        Assertions.fail("the capture states no Break event");
    }

    /**
     * The panels: the payload's own base + ratio + delta must agree with the engine's derived panel for every member.
     */
    @Test
    public void thePayloadsOwnPanelMatchesTheDerivedOne() throws IOException {
        JsonObject allies = capture().getAsJsonObject("allies");
        for (String[] expected : PANELS) {
            JsonObject properties = allies.getAsJsonObject(expected[0]).getAsJsonObject("properties");
            double attack = number(properties, "BaseAttack") * (1 + numberOrZero(properties, "AttackAddedRatio"))
                    + numberOrZero(properties, "AttackDelta");
            double defence = number(properties, "BaseDefence") * (1 + numberOrZero(properties, "DefenceAddedRatio"))
                    + numberOrZero(properties, "DefenceDelta");
            Assertions.assertEquals(Double.parseDouble(expected[1]), attack, 1e-4,
                    expected[0] + " attack: payload vs the panel derived from the account dump");
            Assertions.assertEquals(Double.parseDouble(expected[2]), defence, 1e-4,
                    expected[0] + " defence: payload vs the panel derived from the account dump");
        }
    }
}
