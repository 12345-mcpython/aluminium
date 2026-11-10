package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.enums.SkillEffectType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * <b>Where a cast's damage actually comes from</b>: every rule that deals damage on a cast event, against the skill row that
 * would <i>also</i> swing.
 *
 * <p><b>Why this guard exists.</b> A skill's damage has exactly one owner. When a document says the swing is somebody else's
 * ("使迷迷对敌方全体造成…", "使账账对该目标造成…"), the row must be handed away with {@code CAST_SETUP} + {@code DELEGATE_DAMAGE},
 * or the engine settles <b>two</b> instances: the rule's and the row's, the second scaled off the caster's attack. That has now
 * been measured twice - 1413's ultimate (8818.5 on an empty table) and 8007/8008's (full 1464.2 against the rule's 509.7, whose
 * {@code summon_attr:} scale was not even readable by {@code DAMAGE} until 2026-11-09). Neither defect had a symptom: the damage
 * existed, it was just wrong.
 *
 * <p><b>What is pinned.</b> The inventory below is every cast rule that deals damage while its row is a damaging one. Each entry
 * is either
 * <ul>
 *   <li><b>delegated</b> - a {@code CAST_SETUP} rule hands the row away (1413 / 1112 / 8007 / 8008); or</li>
 *   <li><b>an EXTRA instance</b> - listed in {@link #EXTRA_DAMAGE} with the sentence that says so. These legitimately stack on
 *       top of the row ("额外造成", "追加发动 1 次", "造成的伤害提高…" with its own DAMAGE).</li>
 * </ul>
 * A new rule that fits neither fails here, with the two ways to resolve it. And a stale entry (a rule that no longer exists, or
 * one that has since been handed away) fails too - the inventory must describe the corpus as it is, not as it was.
 */
public class CastDamageDeliveryInventoryTest {
    private static final Path CHARACTERS = Path.of("src", "main", "resources", "game", "characters");
    private static final Path SKILLS = Path.of("src", "main", "resources", "game", "data", "skills.json");

    /** The cast events that produce one settlement of a skill row, and the data slot each one belongs to. */
    private static final Map<String, String> CAST_SLOTS = Map.of(
            "BASIC_ATTACK", "1",
            "SKILL_CAST", "2",
            "ULT_CAST", "3");

    /**
     * Cast rules that deliberately add an instance ON TOP of the row's swing, with the words that say so.
     *
     * <p>⚠ Every entry needs a reason, not just a name: the point of the list is that the next author can tell "this one is
     * allowed" from "this one was never looked at".
     */
    private static final Map<String, Set<String>> EXTRA_DAMAGE = Map.ofEntries(
            Map.entry("1004", Set.of("eidolon1_empowered_basic", "eidolon1_empowered_skill",
                    "eidolon6_extra_hit_on_a_random_enemy")),   // 星魂 1「附加伤害」/ 星魂 6「额外造成1次」
            Map.entry("1009", Set.of("eidolon1_extra_skill_hit")),                 // 星魂 1「额外造成 1 次伤害」
            Map.entry("1013", Set.of("eidolon1_the_basic_punishes_a_hurt_target")), // 星魂 1「额外造成…附加伤害」
            Map.entry("1014", Set.of("enhanced_basic_extra_on_two_enemies",
                    "enhanced_basic_extra_on_one_enemy")),      // 强化普攻「额外…」两档
            Map.entry("1105", Set.of("eidolon6_max_hp_additional_damage")),         // 星魂 6「额外造成…附加伤害」
            Map.entry("1207", Set.of("basic_effect")),                              // 天赋「普攻可额外造成…」
            Map.entry("1220", Set.of("skill_repeats_the_talent_followup")),         // 战技「额外发动 1 次天赋的追加攻击」
            Map.entry("1223", Set.of("ult_triggers_talent_followup")),              // 终结技「额外发动…」
            Map.entry("1402", Set.of("p1402_garmentmaker_hits_the_neighbours",
                    "p1402_garmentmaker_hits_the_same_target")),// 合击里**忆灵**自己那两发
            Map.entry("1403", Set.of("talent_followup_on_other_ult", "eidolon6_own_ult_talent_followup",
                    "eidolon6_other_ult_talent_damage_boost")), // 天赋追击 + 星魂 6 的「提高 729%」
            Map.entry("1504", Set.of("skill_strikes_the_existing_bait_twice",
                    "ult_every_four_layers_add_one_more_hit")), // 战技「额外对其造成…」/ 终结技「每 4 层额外 1 次」
            // 8003/8004 星魂 1「施放普攻时，**额外**造成等同于开拓者25%防御力的火属性伤害；施放强化普攻时…50%」——
            // 一段**叠加**在普攻之上的防御力伤害，不是接管那一发（普攻行仍然要打），所以是 EXTRA 而不是交接。
            Map.entry("8003", Set.of("eidolon1_plain_basic_adds_defence_damage",
                    "eidolon1_enhanced_basic_adds_defence_damage")),
            Map.entry("8004", Set.of("eidolon1_plain_basic_adds_defence_damage",
                    "eidolon1_enhanced_basic_adds_defence_damage")),
            Map.entry("8009", Set.of("talent_elation_extra_damage")),               // 天赋「额外造成…欢愉伤害」
            Map.entry("8010", Set.of("talent_elation_extra_damage")));

    @Test
    public void everyCastRuleEitherHandsTheRowAwayOrIsAnExtraInstance() throws IOException {
        Map<String, Set<String>> actual = inventory();
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : actual.entrySet()) {
            String cid = entry.getKey();
            Set<String> undecided = new LinkedHashSet<>(entry.getValue());
            undecided.removeAll(EXTRA_DAMAGE.getOrDefault(cid, Set.of()));
            for (String rule : undecided) {
                problems.add(cid + " / " + rule + ": its cast also swings the skill row, and nothing says which one owns the damage"
                        + " - either hand the row away (`CAST_SETUP` + `DELEGATE_DAMAGE`, and carry the row's toughness with `stance`)"
                        + " or add it to EXTRA_DAMAGE with the sentence that makes it an extra instance");
            }
        }

        // The other direction: a pinned entry that no longer exists (the corpus moved on, the list did not).
        for (Map.Entry<String, Set<String>> entry : EXTRA_DAMAGE.entrySet()) {
            for (String rule : entry.getValue()) {
                if (!actual.getOrDefault(entry.getKey(), Set.of()).contains(rule)) {
                    problems.add(entry.getKey() + " / " + rule + ": listed as an extra instance, but no such cast rule deals damage"
                            + " any more - drop the entry (or fix the rule id)");
                }
            }
        }

        Assertions.assertTrue(problems.isEmpty(),
                "every cast rule that could double-settle the skill row must be accounted for:\n" + String.join("\n", problems));
    }

    /**
     * 1413 / 1112 / 8007 / 8008 are the four documented hand-overs: their cast rules must still NOT be in EXTRA_DAMAGE, and a
     * {@code DELEGATE_DAMAGE} rule must exist for the slot. Written out because "the inventory has no complaint" is also what a
     * deleted delegation would look like.
     */
    @Test
    public void theDocumentedHandOversAreStillHandedOver() throws IOException {
        Map<String, Set<String>> delegated = delegatedSlots();
        for (String cid : List.of("1413", "1112", "8003", "8004", "8007", "8008")) {
            Assertions.assertTrue(delegated.containsKey(cid),
                    cid + " must keep a CAST_SETUP `DELEGATE_DAMAGE` rule: its document says the swing is somebody else's");
        }
        Assertions.assertTrue(delegated.get("1413").contains("ULTRA"), "1413's ultimate is handed to 长夜");
        Assertions.assertTrue(delegated.get("1112").contains("SKILL"), "1112's Skill is handed to 账账");
        Assertions.assertTrue(delegated.get("8007").contains("ULTRA"), "8007's ultimate is handed to 迷迷");
        Assertions.assertTrue(delegated.get("8008").contains("ULTRA"), "8008's ultimate is handed to 迷迷");
        Assertions.assertTrue(delegated.get("8003").contains("ULTRA"), "8003's ultimate is settled by the rule (a mixed ATK+DEF base)");
        Assertions.assertTrue(delegated.get("8004").contains("ULTRA"), "8004's ultimate is settled by the rule (a mixed ATK+DEF base)");
    }

    // ==================================================================

    /** cid → the cast rules that deal damage AND whose row would swing too (delegation already applied). */
    private static Map<String, Set<String>> inventory() throws IOException {
        JsonObject skills = JsonParser.parseString(Files.readString(SKILLS, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Set<String>> found = new TreeMap<>();
        for (String cid : characterIds()) {
            JsonObject rows = skills.has(cid) ? skills.getAsJsonObject(cid) : null;
            Set<String> delegated = delegatedSlots().getOrDefault(cid, Set.of());
            Set<String> hits = new TreeSet<>();
            for (JsonObject rule : rulesOf(cid)) {
                String event = string(rule, "on");
                String slot = CAST_SLOTS.get(event);
                if (slot == null || !dealsDamage(rule) || !isDamagingRow(rows, slot) || delegated.contains(slotName(slot))) {
                    continue;
                }
                hits.add(string(rule, "id"));
            }
            if (!hits.isEmpty()) {
                found.put(cid, hits);
            }
        }
        return found;
    }

    /** cid → the slots whose row is handed away at CAST_SETUP. */
    private static Map<String, Set<String>> delegatedSlots() throws IOException {
        Map<String, Set<String>> found = new LinkedHashMap<>();
        for (String cid : characterIds()) {
            Set<String> slots = new TreeSet<>();
            for (JsonObject rule : rulesOf(cid)) {
                if (!"CAST_SETUP".equals(string(rule, "on"))) {
                    continue;
                }
                for (JsonElement effect : array(rule, "do")) {
                    JsonObject one = effect.getAsJsonObject();
                    if ("DELEGATE_DAMAGE".equals(string(one, "op")) && one.has("skill")) {
                        slots.add(string(one, "skill"));
                    }
                }
            }
            if (!slots.isEmpty()) {
                found.put(cid, slots);
            }
        }
        return found;
    }

    private static String slotName(String dataSlot) {
        return switch (dataSlot) {
            case "1" -> "COMMON";
            case "2" -> "SKILL";
            case "3" -> "ULTRA";
            default -> "";
        };
    }

    private static boolean dealsDamage(JsonObject rule) {
        for (JsonElement effect : array(rule, "do")) {
            if ("DAMAGE".equals(string(effect.getAsJsonObject(), "op"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDamagingRow(JsonObject rows, String slot) {
        if (rows == null || !rows.has(slot) || !rows.get(slot).isJsonObject()) {
            return false;
        }
        JsonObject row = rows.getAsJsonObject(slot);
        SkillEffectType effect = SkillEffectType.fromString(string(row, "skill_effect"));
        return effect != null && effect.isDamaging();
    }

    private static List<JsonObject> rulesOf(String cid) throws IOException {
        Path file = CHARACTERS.resolve(cid + ".json");
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        List<JsonObject> rules = new ArrayList<>();
        JsonElement rulesNode = root.isJsonObject() ? root.getAsJsonObject().get("rules") : null;
        if (rulesNode != null && rulesNode.isJsonArray()) {
            for (JsonElement rule : rulesNode.getAsJsonArray()) {
                if (rule.isJsonObject()) {
                    rules.add(rule.getAsJsonObject());
                }
            }
        }
        return rules;
    }

    private static List<String> characterIds() throws IOException {
        try (Stream<Path> files = Files.list(CHARACTERS)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
}
