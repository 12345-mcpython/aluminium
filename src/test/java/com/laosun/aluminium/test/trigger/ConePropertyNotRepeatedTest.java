package com.laosun.aluminium.test.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A light cone's own stat share is the weapon's {@code ability_property}; a rule that repeats the same attribute, with the
 * same per-rank value, unconditionally and on {@code self}, counts it <b>twice</b>.
 *
 * <p>Three cones shipped with exactly that defect (22006's crit damage read 24% where the row says 12%, and later 21004's
 * break effect and 23018's crit damage), and each was found by hand. This guard is the sweep: for every cone and rank, the
 * file must not restate a property that the weapon already grants.
 *
 * <p>What is NOT flagged, on purpose: a rule that names a <b>different</b> value (an extra share), an <b>instance</b> rule
 * (a per-hit share, not a stat), or one with a {@code when} (a conditional share).
 */
public class ConePropertyNotRepeatedTest {

    private static final int LEVEL = 80;

    @Test
    public void noConeRepeatsItsOwnProperty() throws IOException {
        JsonObject weapons = read(Path.of("src", "main", "resources", "game", "data", "weapons.json"))
                .getAsJsonObject();
        List<String> offenses = new ArrayList<>();
        try (var files = Files.list(Path.of("src", "main", "resources", "game", "light_cones"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("\\d+\\.json")).toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                JsonElement coneElement = read(file);
                if (!coneElement.isJsonObject()) {
                    continue;
                }
                JsonObject cone = coneElement.getAsJsonObject();
                JsonObject weapon = weapons.getAsJsonObject(id);
                if (weapon == null) {
                    continue;
                }
                Map<String, JsonObject> rows = new TreeMap<>();
                for (JsonElement row : weapon.getAsJsonArray("weapon_skill_data")) {
                    rows.put(row.getAsJsonObject().get("level").getAsString(), row.getAsJsonObject());
                }
                for (String rank : List.of("1", "2", "3", "4", "5")) {
                    JsonObject row = rows.get(rank);
                    JsonArray rules = cone.getAsJsonArray(rank);
                    if (row == null || rules == null) {
                        continue;
                    }
                    Map<String, Double> props = new TreeMap<>();
                    JsonArray propertyEntries = row.getAsJsonArray("ability_property");
                    if (propertyEntries != null) {
                        for (JsonElement entry : propertyEntries) {
                            JsonObject p = entry.getAsJsonObject();
                            props.put(p.get("attribute").getAsString(), p.get("value").getAsDouble());
                        }
                    }
                    for (JsonElement ruleElement : rules) {
                        JsonObject rule = ruleElement.getAsJsonObject();
                        JsonArray when = rule.getAsJsonArray("when");
                        if (when != null && !when.isEmpty()) {
                            continue;                       // a conditional share, not the property
                        }
                        for (JsonElement effectElement : rule.getAsJsonArray("do")) {
                            JsonObject effect = effectElement.getAsJsonObject();
                            if (!"MODIFY_ATTR".equals(effect.get("op").getAsString())) {
                                continue;
                            }
                            String attribute = effect.get("attribute") == null ? null
                                    : effect.get("attribute").getAsString();
                            if (attribute == null || !props.containsKey(attribute)
                                    || effect.get("percent") == null) {
                                continue;
                            }
                            if (effect.has("instance") && effect.get("instance").getAsBoolean()) {
                                continue;                       // a per-hit share, not a stat
                            }
                            String target = effect.get("target") == null ? "self" : effect.get("target").getAsString();
                            double percent = effect.get("percent").getAsDouble();
                            double property = props.get(attribute);
                            if ("self".equals(target)
                                    && Math.abs(Math.abs(percent) - Math.abs(property)) < 1e-9) {
                                offenses.add(id + " r" + rank + " " + rule.get("id").getAsString()
                                        + ": rule " + percent + " repeats property " + property);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[cones] property repeats: " + offenses);
        Assertions.assertTrue(offenses.isEmpty(), "a rule repeats the cone's own property: " + offenses);
    }

    /**
     * The two cones this guard found, judged on the wearer: the property once, not twice.
     */
    @Test
    public void theWearerGetsThePropertyOnce() {
        Assertions.assertEquals(0.28, gain(21004, AttributeType.BREAKING_EFFECT), 1e-9,
                "21004's break effect");
        Assertions.assertEquals(0.36, gain(23018, AttributeType.CRIT_ATTACK), 1e-9,
                "23018's crit damage");
    }

    private static double gain(int cone, AttributeType attribute) {
        Character withCone = CharacterFactory.create(1002, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
        Character bare = CharacterFactory.create(1002, LEVEL);
        battle(withCone);
        battle(bare);
        return withCone.getAttribute(attribute).get() - bare.getAttribute(attribute).get();
    }

    private static void battle(Character unit) {
        List<Character> party = new ArrayList<>();
        party.add(unit);
        new Battle(party, new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded()).startBattle();
    }

    private static JsonElement read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
}



