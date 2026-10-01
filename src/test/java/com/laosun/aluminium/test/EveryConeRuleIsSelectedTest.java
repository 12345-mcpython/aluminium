package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * BREADTH: every shipped light cone, every rank, every event -- selected, so the engine validates it.
 *
 * <p>\u26a0 Why this exists (2026-09-30): a rule is validated only when it is SELECTED. Cone 23059 shipped with an
 * attribute the engine does not have (HP; it is HEALTH) and with a category test on SKILL_CAST (which carries no
 * category, so the rule could never fire), and the FULL SUITE WAS GREEN TWICE. Nothing selected those rules, so
 * nothing checked them. This test does nothing but select: it asks each table for its rules on every event, which is
 * what makes the engine parse and validate them. Behavioural judges live elsewhere; this one closes the "never
 * validated" hole.
 */
public class EveryConeRuleIsSelectedTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1210;

    private static final int[] CONES = {20000, 20001, 20002, 20003, 20004, 20005, 20006, 20007, 20008, 20009, 20010, 20011, 20012, 20013, 20014, 20015, 20016, 20017, 20018, 20019, 20020, 20021, 20022, 20024, 21000, 21001, 21002, 21003, 21004, 21005, 21006, 21007, 21008, 21009, 21010, 21011, 21012, 21013, 21014, 21015, 21016, 21017, 21018, 21019, 21020, 21022, 21023, 21024, 21026, 21027, 21028, 21030, 21031, 21033, 21034, 21035, 21036, 21037, 21039, 21040, 21041, 21042, 21043, 21044, 21045, 21046, 21047, 21048, 21050, 21051, 21052, 21053, 21054, 21055, 21056, 21057, 21058, 21060, 21061, 21062, 21064, 21065, 21066, 22000, 22001, 22002, 22003, 22004, 22005, 22007, 22008, 23000, 23002, 23003, 23004, 23005, 23006, 23007, 23008, 23009, 23010, 23011, 23012, 23014, 23015, 23016, 23017, 23018, 23019, 23020, 23021, 23022, 23023, 23024, 23025, 23026, 23027, 23029, 23030, 23031, 23032, 23033, 23034, 23035, 23036, 23037, 23038, 23040, 23041, 23044, 23046, 23047, 23051, 23052, 23053, 23054, 23056, 23057, 23058, 23059, 23061, 23062, 23063, 23064, 24000, 24001, 24002, 24003, 24004, 24005, 24006};

    @Test
    public void everyConeRuleIsSelectedOnEveryEvent() {
        List<String> problems = new ArrayList<>();
        int selected = 0;
        for (int cone : CONES) {
            for (int rank = 1; rank <= 5; rank++) {
                Character wearer;
                try {
                    wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
                } catch (RuntimeException e) {
                    problems.add("cone " + cone + " rank " + rank + ": " + e.getMessage());
                    break;
                }
                var table = wearer.getTriggerTable();
                for (TriggerEvent event : TriggerEvent.values()) {
                    try {
                        selected += table.rulesFor(event).size();
                    } catch (RuntimeException e) {
                        problems.add("cone " + cone + " rank " + rank + " on " + event + ": " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("[breadth] cones=" + CONES.length + " rules selected=" + selected);
        if (!problems.isEmpty()) {
            throw new AssertionError("unvalidated or invalid rules:\n" + String.join("\n", problems));
        }
    }
}
