"""Wire the battle's resource reader into every summon it builds, and write the judge (2026-10-02)."""

import io
import re
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/MemospriteResourcePanelTest.java"

text = io.open(BATTLE, encoding="utf-8").read()
if "this::partyResourceValue" in text:
    print("skip Battle: already wired")
else:
    pattern = re.compile(r"SummonFactory\.(memosprite|servant)\(([^;]*?)\)")
    sites = pattern.findall(text)
    print("call sites found:", len(sites))
    if not sites:
        print("FAIL Battle: no SummonFactory call sites")
        sys.exit(1)
    text, count = pattern.subn(
        lambda m: "SummonFactory.%s(%s, this::partyResourceValue)" % (m.group(1), m.group(2)), text)
    io.open(BATTLE, "w", encoding="utf-8", newline="").write(text)
    print("ok   Battle: %d summon call sites now pass partyResourceValue" % count)

JUDGE_TEXT = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A summon panel that derives from a RESOURCE (2026-10-02): 「龙的生命 = 【新蕊】上限的 100%」.
 *
 * <p>Every shipped panel is a share of the master's ATTRIBUTE; this one is a share of a battle-level resource, so the
 * value is handed in and the derivation stays battle-free. The claims: the share follows the resource, the flat term is
 * added, the ATTRIBUTE panel beside it still reads the master, and a resource panel with no reader is refused loudly.
 */
public class MemospriteResourcePanelTest {
    private static final int MASTER = 1415;
    private static final int LEVEL = 80;

    private static MemospriteSpec spec(double share, String source) {
        return new MemospriteSpec("dragon", "test", "test",
                List.of(new MemospriteSpec.Panel("HEALTH", share, null, source),
                        new MemospriteSpec.Panel("SPEED", 1.0, null, null)),
                null, null);
    }

    /** ⭐ The panel follows the resource, and the attribute panel beside it does not. */
    @Test
    public void thePanelFollowsTheResource() {
        Character master = CharacterFactory.create(MASTER, LEVEL, false, null, null, 0);
        Assertions.assertEquals(1000, health(master, spec(1.0, "resource:xinrui"), 1000), 1e-6,
                "100% of a 1000-point resource");
        Assertions.assertEquals(4000, health(master, spec(1.0, "resource:xinrui"), 4000), 1e-6,
                "and 100% of a 4000-point one -- the share follows the resource");
        Assertions.assertEquals(2000, health(master, spec(0.5, "resource:xinrui"), 4000), 1e-6,
                "the share itself is applied");
        Assertions.assertEquals(1500, health(master, spec(1.0, null), 4000), 1e-6,
                "an attribute panel is untouched by the resource reader (100% of the master's own health)");
    }

    /** ⚠ A resource panel with no reader is refused, not silently derived as 0. */
    @Test
    public void aResourcePanelNeedsAReader() {
        Character master = CharacterFactory.create(MASTER, LEVEL, false, null, null, 0);
        Assertions.assertThrows(IllegalStateException.class,
                () -> SummonFactory.memosprite(master, spec(1.0, "resource:xinrui")),
                "the 2-arg entry point must refuse rather than derive 0");
    }

    // ==================================================================

    private static double health(Character master, MemospriteSpec spec, int resource) {
        return SummonFactory.memosprite(master, spec, name -> resource)
                .getAttribute(AttributeType.HEALTH).get();
    }
}
'''
io.open(JUDGE, "w", encoding="utf-8", newline="").write(JUDGE_TEXT)
print("ok   judge written")
