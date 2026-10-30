package com.laosun.compare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Derives the <b>cast table</b> straight out of a capture that states {@code cast_index} and {@code skill_id}.
 *
 * <p><b>Why this exists.</b> The replay needs one line per capture cast ("who cast which row at whom"), and that line was
 * hand-written from the plan's {@code casts} array - because the older payload said only "somebody's hit, in this order".
 * The newer payload names the cast and the skill row, and {@code skills.json} maps a {@code skill_id} to its
 * {@code (cid, slot)}: so the table is a <b>read</b> of the payload, not a transcription of it, and the hand-written
 * version can be checked against this one.
 *
 * <p>Companions (true-damage riders, {@code attack_type 13}) are not casts; a break names no skill row, so its own line
 * carries {@code skill_id 0} and is reported rather than resolved.
 */
public final class DerivedCasts {

    private DerivedCasts() {
    }

    /**
     * One capture cast, as the payload states it.
     *
     * @param castIndex  the payload's {@code cast_index}
     * @param actor      the attacker's uid
     * @param kind       the payload's {@code type} of the cast's first hit
     * @param skillId    the payload's {@code skill_id} (0 when the event names none)
     * @param cid        the row's character/servant id, or {@code 0} when the skill does not resolve
     * @param slot       the row's slot, or {@code -1} when the skill does not resolve
     * @param target     the first hit's target uid
     * @param hits       how many real (non-companion) instances the cast settled
     */
    public record Entry(int castIndex, long actor, String kind, long skillId, int cid, int slot, long target,
                        int hits, double percentage) {
    }

    /**
     * Reads a capture and derives one entry per cast.
     *
     * @param capture the parsed capture
     * @return the entries, in cast order
     */
    public static List<Entry> of(JsonObject capture) {
        Map<String, Map<String, Integer>> bySkillId = skillIndex();
        Map<Integer, Entry> out = new LinkedHashMap<>();
        Map<Integer, Integer> hits = new LinkedHashMap<>();
        for (JsonElement element : capture.getAsJsonArray("damage_events")) {
            JsonObject event = element.getAsJsonObject();
            JsonObject zones = event.getAsJsonObject("zones");
            int attackType = (int) Math.rint(zones.has("attack_type") ? zones.get("attack_type").getAsDouble() : 0);
            if (attackType == 13) {
                continue;                                       // a true-damage rider is not a cast
            }
            int castIndex = (int) Math.rint(event.get("cast_index").getAsDouble());
            hits.merge(castIndex, 1, Integer::sum);
            long skillId = event.has("skill_id") ? event.get("skill_id").getAsLong() : 0L;
            // One cast_index may hold more than one kind of instance (a break settled DURING the ultimate that caused
            // it). The cast is the instance that names a SKILL ROW; a break states skill_id 0 and is only the fallback
            // when the whole cast names no row.
            if (out.containsKey(castIndex) && (skillId == 0 || out.get(castIndex).skillId() != 0)) {
                continue;
            }
            String key = String.valueOf(skillId);
            int cid = 0;
            int slot = -1;
            Map<String, Integer> resolved = bySkillId.get(key);
            if (resolved != null) {
                cid = resolved.get("cid");
                slot = resolved.get("slot");
            }
            double percentage = zones.has("damage_percentage") ? zones.get("damage_percentage").getAsDouble() : 0;
            out.put(castIndex, new Entry(castIndex, event.getAsJsonObject("attacker").get("uid").getAsLong(),
                    event.get("type").getAsString(), skillId, cid, slot,
                    event.getAsJsonObject("target").get("uid").getAsLong(), 1, percentage));
        }
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : out.values()) {
            entries.add(new Entry(entry.castIndex(), entry.actor(), entry.kind(), entry.skillId(), entry.cid(),
                    entry.slot(), entry.target(), hits.getOrDefault(entry.castIndex(), 0), entry.percentage()));
        }
        return entries;
    }

    /**
     * {@code skill_id} to its {@code (cid, slot)} - read from {@code skills.json}, which keys rows the way the engine
     * addresses them.
     *
     * @return the index
     */
    private static Map<String, Map<String, Integer>> skillIndex() {
        Map<String, Map<String, Integer>> index = new LinkedHashMap<>();
        try (InputStream stream = DerivedCasts.class.getResourceAsStream("/game/data/skills.json")) {
            if (stream == null) {
                return index;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, JsonElement> cid : root.entrySet()) {
                if (!cid.getValue().isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> slot : cid.getValue().getAsJsonObject().entrySet()) {
                    if (!slot.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonElement skillId = slot.getValue().getAsJsonObject().get("skill_id");
                    if (skillId == null || skillId.isJsonNull()) {
                        continue;
                    }
                    index.put(skillId.getAsString(), Map.of(
                            "cid", Integer.parseInt(cid.getKey()),
                            "slot", Integer.parseInt(slot.getKey())));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("cannot read skills.json: " + e, e);
        }
        return index;
    }

    /**
     * {@code derive-casts <capture.json>} - prints the derived table and how many skills resolved.
     *
     * @param args the capture path
     * @return 0 when every cast that names a skill resolved, 1 when some did not
     */
    public static int run(String... args) {
        Path path = null;
        for (String arg : args) {
            if (!"derive-casts".equals(arg) && !arg.startsWith("--")) {
                path = Path.of(arg);
            }
        }
        if (path == null) {
            System.out.println("usage: derive-casts <capture.json>");
            return 2;
        }
        JsonObject capture;
        try {
            capture = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            System.out.println("derive-casts: cannot read " + path + " (" + e + ")");
            return 2;
        }
        List<Entry> entries = of(capture);
        System.out.printf(Locale.ROOT, "%-5s %-8s %-11s %-9s %-6s %-5s %-8s %5s %10s%n",
                "cast", "actor", "kind", "skill_id", "cid", "slot", "target", "hits", "percentage");
        System.out.println("-".repeat(70));
        int unresolved = 0;
        for (Entry entry : entries) {
            boolean resolved = entry.cid() != 0;
            if (!resolved && entry.skillId() != 0) {
                unresolved++;
            }
            System.out.printf(Locale.ROOT, "%-5d %-8d %-11s %-9d %-6s %-5s %-8d %5d %10.6f%n",
                    entry.castIndex(), entry.actor(), entry.kind(), entry.skillId(),
                    resolved ? String.valueOf(entry.cid()) : "-",
                    resolved ? String.valueOf(entry.slot()) : "-",
                    entry.target(), entry.hits(), entry.percentage());
        }
        System.out.println("-".repeat(70));
        System.out.printf(Locale.ROOT, "casts: %d ; every skill row resolved: %s%n",
                entries.size(),
                unresolved == 0 ? "yes" : "NO - " + unresolved + " skill id(s) are not in skills.json");
        return unresolved == 0 ? 0 : 1;
    }
}
