"""Probe: which data slots does 1404 have? (round 4 of the goal)

`REPLACE_SKILL` addresses a slot by NUMBER (`DefaultSkill`'s own comment: "Despite the field name, `skillId` here is the slot"),
and `Constant.SKILL_SLOT` only names 1-4, 6, 7, 20, 21. 1301's shipped swap uses slot 8, so enhanced forms live in the unnamed
slots -- and the register row I dismissed last round said "slot 11". This asks the data directly instead of guessing.

Calibration: 1301's slot 8 must come back loaded, since that swap ships.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/SlotProbeTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.data.SkillData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Probe only: which named-by-number slots the data actually has. */
public class SlotProbeTest {
    @Test
    public void whichSlotsExist() {
        List<String> lines = new ArrayList<>();
        for (int cid : new int[] {1301, 1404, 1415, 1409}) {
            StringBuilder row = new StringBuilder("cid " + cid + ": ");
            for (int slot = 1; slot <= 24; slot++) {
                boolean loaded;
                String category;
                try {
                    SkillData data = SkillData.init(cid, slot);
                    loaded = data != null && data.isLoaded();
                    category = loaded ? String.valueOf(data.getCategory()) : "";
                } catch (RuntimeException failure) {
                    loaded = false;
                    category = "threw:" + failure.getClass().getSimpleName();
                }
                if (loaded) {
                    row.append(slot).append('=').append(category).append(' ');
                }
            }
            lines.add(row.toString());
        }
        io.github.tbapi.Probe.dump(lines);
    }
}
''')
print("ok   the slot probe is written")
