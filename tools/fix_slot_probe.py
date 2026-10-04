"""Fix the probe to print with System.out (ASCII only, so the GBK console is safe) and run it."""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/SlotProbeTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.data.SkillData;
import org.junit.jupiter.api.Test;

/** Probe only: which named-by-number slots the data actually has. */
public class SlotProbeTest {
    @Test
    public void whichSlotsExist() {
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
            System.out.println("[slots] " + row);
        }
    }
}
''')
print("ok   the probe prints through System.out")
