package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Light cone 21046: at battle start the units that share a Path with somebody else on our side gain 16% crit damage.
 *
 * <p>\u2b50 The candidates\u2019 Paths are collected FIRST and chosen by COUNT (a Path with two members, and one with exactly one), not
 * concluded while iterating -- and every "not applied" claim is compared against that unit\u2019s OWN baseline, because a character\u2019s
 * starting crit damage is 0.5, not 0 (both mistakes were made and measured on 2026-09-30).
 */
public class Cone21046Test {
    private static final int CONE = 21046;
    private static final int WEARER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int[] CANDIDATES = {1003, 1004, 1006, 1008, 1009, 1013, 1101, 1102, 1103, 1104, 1105, 1106,
            1107, 1108, 1109, 1110, 1111, 1112, 1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1210, 1211, 1212,
            1213, 1214, 1215, 1217, 1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1312, 1314, 1315};

    private Battle battle;

    @Test
    public void onlyUnitsWithAPathTwinAreBoosted() {
        Map<Path, List<Integer>> byPath = new LinkedHashMap<>();
        for (int id : CANDIDATES) {
            Path path = CharacterFactory.create(id, LEVEL).getPath();
            if (path == null || path == Path.OTHER) {
                continue;
            }
            byPath.computeIfAbsent(path, key -> new ArrayList<>()).add(id);
        }
        Path twinPath = byPath.entrySet().stream().filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey).findFirst().orElse(null);
        // \u2605 The loner is unique WITHIN THE PARTY, not within the pool: measured, every one of the seven Paths in the pool has at
        // least two members, so "a Path with exactly one member" does not exist there at all. What the sentence means is that
        // these characters have a twin on our side -- so the party is built to contain a pair and a single, and the single's
        // Path must be neither the pair\u2019s nor the wearer\u2019s (the wearer is on our side too).
        Path wearerPath = CharacterFactory.create(WEARER, LEVEL).getPath();
        Path lonerPath = byPath.keySet().stream()
                .filter(path -> !path.equals(twinPath) && !path.equals(wearerPath))
                .findFirst().orElse(null);
        System.out.println("[21046] the pool holds " + byPath.size() + " paths; twin=" + twinPath
                + " (x" + (twinPath == null ? 0 : byPath.get(twinPath).size()) + ") ; the loner\u2019s path=" + lonerPath
                + " ; the wearer walks " + wearerPath);
        Assertions.assertNotNull(twinPath, "the pool has a Path with two members");
        Assertions.assertNotNull(lonerPath, "and another Path to take a single member from");

        Character wearerWithCone = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character twinA = CharacterFactory.create(byPath.get(twinPath).get(0), LEVEL);
        Character twinB = CharacterFactory.create(byPath.get(twinPath).get(1), LEVEL);
        Character loner = CharacterFactory.create(byPath.get(lonerPath).get(0), LEVEL);
        Character wearerPlain = CharacterFactory.create(WEARER, LEVEL);

        battle = new Battle(List.of(wearerWithCone, twinA, twinB, loner), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        battle.startBattle();
        // baselines, read from a battle with no cone at all
        double twinABase = CharacterFactory.create(byPath.get(twinPath).get(0), LEVEL)
                .getAttribute(AttributeType.CRIT_ATTACK).get();
        double twinBBase = CharacterFactory.create(byPath.get(twinPath).get(1), LEVEL)
                .getAttribute(AttributeType.CRIT_ATTACK).get();
        double lonerBase = CharacterFactory.create(byPath.get(lonerPath).get(0), LEVEL)
                .getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[21046] twinA " + twinA.getName() + " (" + twinPath + ") " + twinABase + " -> "
                + twinA.getAttribute(AttributeType.CRIT_ATTACK).get() + " ; twinB " + twinB.getName() + " "
                + twinBBase + " -> " + twinB.getAttribute(AttributeType.CRIT_ATTACK).get() + " ; loner "
                + loner.getName() + " (" + lonerPath + ") " + lonerBase + " -> "
                + loner.getAttribute(AttributeType.CRIT_ATTACK).get());
        Assertions.assertEquals(twinABase + 0.16, twinA.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "a unit with a Path twin gains the crit damage");
        Assertions.assertEquals(twinBBase + 0.16, twinB.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "and so does the other half of the pair");
        Assertions.assertEquals(lonerBase, loner.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "the unit with no twin keeps its OWN baseline (false case)");
        Assertions.assertEquals(0.5, wearerPlain.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "and a plain character\u2019s baseline is 0.5, which is why \"not applied\" is never \"zero\"");
    }

    @Test
    public void withoutTheConeNobodyChanges() {
        Character a = CharacterFactory.create(1002, LEVEL);
        Character b = CharacterFactory.create(1002, LEVEL);
        Character c = CharacterFactory.create(1002, LEVEL);
        battle = new Battle(List.of(a, b, c), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        System.out.println("[21046] without the cone: " + a.getAttribute(AttributeType.CRIT_ATTACK).get());
        Assertions.assertEquals(0.5, a.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "no cone, no change to the baseline (false case)");
    }
}
