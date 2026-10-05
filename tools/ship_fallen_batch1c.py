"""Batch 1, part 2: the two stale pins, and the rule guard's own judge (round 1694).

The pins: 1408's trace line 1408101 says 「战斗开始时，获得 1 点【火种】」, which is now written, so Coreflame starts at 1 -- not 0.
  * `PhainonTest`: 0 -> 1 at battle start, and 2 -> 3 after her first skill cast (the +2 is unchanged);
  * `CoreflameOverflowTest`: seven casts now reach the ceiling exactly (1 + 14 = 15), which no longer isolates "past the max of
    12" -- SIX casts read 13, which keeps the reading's intent with the new arithmetic.

The judge for the rule guard, in the shape that worked for the effect guard: prove the trap with Gson itself, then show the
guard letting the whole corpus through -- including `1205.json`, the file that really writes `on_any`.
"""
import io
import sys

PHAINON = "src/test/java/com/laosun/aluminium/test/PhainonTest.java"
OVERFLOW = "src/test/java/com/laosun/aluminium/test/CoreflameOverflowTest.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/RuleKeyGuardTest.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    PHAINON,
    '''        Assertions.assertEquals(0, coreflameOf(phainon), "the document states no initial value, so it starts at 0");''',
    '''        // ⚠ Updated 2026-10-02: the document DOES state one -- her trace 1408101 「战斗开始时，获得 1 点【火种】」, which is
        // now written, so the pool opens at one. The reading is unchanged in kind: it starts where the sentences say.
        Assertions.assertEquals(1, coreflameOf(phainon),
                "\\u300c\\u6218\\u6597\\u5f00\\u59cb\\u65f6\\uff0c\\u83b7\\u5f97 1 \\u70b9\\u3010\\u706b\\u79cd\\u3011\\u300d");''',
    "PhainonTest starts at one",
)

patch(
    PHAINON,
    """        Assertions.assertEquals(2, coreflameOf(phainon),
                "\\u300c\\u83b7\\u5f972\\u70b9\\u3010\\u706b\\u79cd\\u3011\\u300d");""",
    """        Assertions.assertEquals(3, coreflameOf(phainon),
                "\\u300c\\u83b7\\u5f972\\u70b9\\u3010\\u706b\\u79cd\\u3011\\u300d -- one from the battle start, two from the cast");""",
    "PhainonTest reads 3 after one cast",
)

patch(
    OVERFLOW,
    """    /** ⭐ Past the maximum: seven casts reach fourteen, which a cap of twelve could never allow. */
    @Test
    public void thePoolGoesPastItsMaximum() {
        Assertions.assertEquals(14.0, afterCasts(7), 1e-9,
                "「达到上限后还可溢出」-- 14 is above the declared max of 12");
    }""",
    """    /**
     * ⭐ Past the maximum: six casts reach thirteen, which a cap of twelve could never allow.
     *
     * <p>⚠ Updated 2026-10-02: 「战斗开始时，获得 1 点【火种】」 is now written (trace 1408101), so the pool opens at one. Seven casts
     * would then sit exactly ON the ceiling of 15 and stop isolating "past twelve" -- six reads 1 + 12 = 13, which is the same
     * reading the judge was written for.
     */
    @Test
    public void thePoolGoesPastItsMaximum() {
        Assertions.assertEquals(13.0, afterCasts(6), 1e-9,
                "「达到上限后还可溢出」-- 1 + 12 = 13 is above the declared max of 12");
    }""",
    "the overflow reading moves to six casts",
)

patch(
    OVERFLOW,
    """            // resource read a constant 3 for every cast count: the first self-aimed cast landed (+2 for the skill, +1 for item 38's
            // "being targeted" rule) and the later ones did nothing at all. Measured step by step, aimed at an enemy, the pool is
            // 2, 4, 6, 8, 10, 12 -- exactly two per cast, up to its declared cap.""",
    """            // resource read a constant 3 for every cast count: the first self-aimed cast landed (+2 for the skill, +1 for item 38's
            // "being targeted" rule) and the later ones did nothing at all. Measured step by step, aimed at an enemy, the pool is
            // 3, 5, 7, 9, 11, 13 -- the battle-start one plus exactly two per cast, up to and past its declared cap.""",
    "the measured sequence in the comment is current",
)

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A rule's key has to be a key Gson maps (2026-10-02).
 *
 * <p>Found the hard way twice: I wrote `on_any` in a rule that had no `on` and concluded the engine could not spell it -- when
 * the real story is that RULE OBJECTS had no key guard at all, so a mistyped key vanished and the rule arrived as "Unknown
 * trigger event 'null'". `on_any` itself is spelled and read (`1205.json` uses it).
 */
public class RuleKeyGuardTest {
    /** A key Gson does not know leaves the rule with no event at all -- the failure the guard refuses at load time. */
    @Test
    public void aMistypedEventKeyLeavesNothing() {
        TriggerSpec mistyped = new Gson().fromJson("{\\"onn\\":\\"BATTLE_START\\",\\"do\\":[]}", TriggerSpec.class);
        TriggerSpec spelled = new Gson().fromJson("{\\"on\\":\\"BATTLE_START\\",\\"on_any\\":[\\"TURN_START\\"]}",
                TriggerSpec.class);
        System.out.println("[rule-keys] onn -> on=" + mistyped.getOn() + " onAny=" + mistyped.getOnAny()
                + " ; on/on_any -> on=" + spelled.getOn() + " onAny=" + spelled.getOnAny());
        Assertions.assertNull(mistyped.getOn(), "the mistyped key is dropped, so the rule never fires");
        Assertions.assertNull(mistyped.getOnAny(), "and it has no fallback event either");
        Assertions.assertEquals("BATTLE_START", spelled.getOn(), "the spelled `on` lands");
        Assertions.assertEquals(1, spelled.getOnAny().size(), "and `on_any` ADDS events beside it");
    }

    /** And the guard lets the corpus through, including the file that really writes `on_any`. */
    @Test
    public void everyShippedRuleSetLoads() {
        for (int cid : new int[] {1205, 1408, 1412, 1513}) {
            Assertions.assertNotNull(TriggerTables.of(cid), "cid " + cid + " loads through the rule guard");
        }
    }
}
''')
print("ok   RuleKeyGuardTest written")
