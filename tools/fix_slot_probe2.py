"""Name the slot: 1404's two BPSKILL slots by their toughness (round 4 of the goal).

The corpus gives the two enhanced skills' toughness: 弑王成王 单体 60 / 扩散 30, 弑神登神 单体 90 / 扩散 60. So `stanceFor` decides
which of slots 9 and 11 is which, with no name matching and no guessing.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/SlotProbeTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.data.SkillData;
import org.junit.jupiter.api.Test;

/** Probe only: which slot is which, by the toughness the document states. */
public class SlotProbeTest {
    @Test
    public void whichSlotsExist() {
        for (int cid : new int[] {1301, 1404, 1415, 1409}) {
            StringBuilder row = new StringBuilder("cid " + cid + ": ");
            for (int slot = 1; slot <= 24; slot++) {
                try {
                    SkillData data = SkillData.init(cid, slot);
                    if (data != null && data.isLoaded()) {
                        row.append(slot).append('=').append(data.getCategory())
                                .append("[main ").append(data.stanceFor(true))
                                .append(" / diff ").append(data.stanceFor(false)).append("] ");
                    }
                } catch (RuntimeException failure) {
                    row.append(slot).append("=threw ");
                }
            }
            System.out.println("[slots] " + row);
        }
    }
}
''')
print("ok   the probe now reports toughness")
