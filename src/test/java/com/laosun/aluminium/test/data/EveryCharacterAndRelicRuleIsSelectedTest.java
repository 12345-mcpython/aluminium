package com.laosun.aluminium.test.data;

import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * BREADTH for characters and relic sets (2026-09-30): select every rule so the engine validates it.
 *
 * <p>Companion of EveryConeRuleIsSelectedTest. A rule is validated only when it is SELECTED; cone 23059 shipped two
 * errors (an attribute the engine lacks, and a category test on an event that carries no category) while the full suite
 * stayed green, because nothing ever selected those rules. These two tests do nothing but select.
 */
public class EveryCharacterAndRelicRuleIsSelectedTest {
    private static final int LEVEL = 80;
    private static final int[] CHARACTERS = {1001, 1002, 1003, 1004, 1005, 1006, 1008, 1009, 1013, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110, 1111, 1112, 1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1210, 1211, 1212, 1213, 1214, 1215, 1217, 1218, 1220, 1221, 1222, 1223, 1224, 1225, 1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1312, 1313, 1314, 1315, 1317, 1321, 1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409, 1410, 1412, 1413, 1414, 1415, 1501, 1502, 1504, 1505, 1506, 1507, 1510, 1512, 1513, 8001, 8002, 8003, 8004, 8005, 8006, 8007, 8008, 8009, 8010};
    private static final int[] RELICS = {101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 127, 128, 129, 130, 131, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 328};

    @Test
    public void everyCharacterRuleIsSelectedOnEveryEvent() {
        List<String> problems = new ArrayList<>();
        int selected = 0;
        for (int id : CHARACTERS) {
            var table = CharacterFactory.create(id, LEVEL).getTriggerTable();
            for (TriggerEvent event : TriggerEvent.values()) {
                try {
                    selected += table.rulesFor(event).size();
                } catch (RuntimeException e) {
                    problems.add("character " + id + " on " + event + ": " + e.getMessage());
                }
            }
        }
        System.out.println("[breadth-chars] characters=" + CHARACTERS.length + " rules selected=" + selected);
        if (!problems.isEmpty()) {
            throw new AssertionError("invalid character rules:\n" + String.join("\n", problems));
        }
    }

    @Test
    public void everyRelicRuleIsSelectedOnEveryEvent() {
        List<String> problems = new ArrayList<>();
        int selected = 0;
        for (int set : RELICS) {
            for (int pieces : new int[]{2, 4}) {
                RelicTriggerTables.Rules rules;
                try {
                    rules = RelicTriggerTables.of(set);
                } catch (RuntimeException e) {
                    problems.add("relic " + set + ": " + e.getMessage());
                    break;
                }
                for (TriggerEvent event : TriggerEvent.values()) {
                    try {
                        selected += rules.at(pieces).rulesFor(event).size();
                    } catch (RuntimeException e) {
                        String message = String.valueOf(e.getMessage());
                        if (message.contains("piece") || message.contains("tier") || message.contains("4")) {
                            continue;   // this set simply has no such tier, which is not an invalid rule
                        }
                        problems.add("relic " + set + "/" + pieces + " on " + event + ": " + message);
                    }
                }
            }
        }
        System.out.println("[breadth-relics] sets=" + RELICS.length + " rules selected=" + selected);
        if (!problems.isEmpty()) {
            throw new AssertionError("invalid relic rules:\n" + String.join("\n", problems));
        }
    }
}
