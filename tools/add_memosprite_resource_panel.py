"""A memosprite panel can be a share of a RESOURCE (2026-10-02).

The sentence: 1407/1415's dead dragon 「龙的生命 = 【新蕊】上限的 100%」. Every existing memosprite panel is a share of the
MASTER's ATTRIBUTE (MemospriteSpec.Panel.attribute + percent + flat, applied in SummonFactory), and resources live on the
BATTLE (Battle.partyResourceValue) rather than on the unit -- so the value has to be handed in, which keeps the panel
derivation battle-free (its own comment says it was split out to be exercised on its own).

Shape: `Panel.source` may be `"resource:<name>"`; empty keeps today's behaviour exactly, so the five shipped memosprites
are untouched. The 3-arg `memosprite(...)` takes the resource reader; the 2-arg one refuses a resource panel LOUDLY
instead of silently deriving 0.

Writes the engine patch and its judge. ASCII only.
"""
import io
import re
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/MemospriteSpec.java"
FACTORY = "src/main/java/com/laosun/aluminium/models/enemy/SummonFactory.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/MemospriteResourcePanelTest.java"

# 1) the spec gains `source` -----------------------------------------------------------------------------
spec = io.open(SPEC, encoding="utf-8").read()
if "String source," not in spec.split("record Panel(")[1].split(")")[0]:
    OLD = ('    public record Panel(@SerializedName("attribute") String attribute,\n'
           '                        @SerializedName("percent") Double percent,\n'
           '                        @SerializedName("flat") Double flat) {')
    NEW = ('    public record Panel(@SerializedName("attribute") String attribute,\n'
           '                        @SerializedName("percent") Double percent,\n'
           '                        @SerializedName("flat") Double flat,\n'
           '                        @SerializedName("source") String source) {')
    if spec.count(OLD) != 1:
        print("FAIL spec: Panel anchor matched %d times" % spec.count(OLD))
        sys.exit(1)
    spec = spec.replace(OLD, NEW)
    io.open(SPEC, "w", encoding="utf-8", newline="").write(spec)
    print("ok   MemospriteSpec.Panel.source")
else:
    print("skip spec")

# 2) the derivation reads a resource when the panel names one --------------------------------------------
factory = io.open(FACTORY, encoding="utf-8").read()
if "resourceValue" not in factory:
    OLD_LOOP = "            double value = share * master.getAttribute(attribute).get() + flat;\n"
    NEW_LOOP = ("            // ⭐ A panel may derive from a RESOURCE instead of an attribute (2026-10-02; reader: 1407/1415's\n"
                "            // dead dragon, 「龙的生命 = 【新蕊】上限的 100%」). Resources live on the battle, so the reader is\n"
                "            // handed in -- which keeps this derivation battle-free, as its own comment intends.\n"
                "            double value;\n"
                "            if (entry.source() != null && entry.source().startsWith(\"resource:\")) {\n"
                "                String name = entry.source().substring(\"resource:\".length()).trim();\n"
                "                if (resourceValue == null) {\n"
                "                    throw new IllegalStateException(\n"
                "                            \"the memosprite panel for \\\"\" + spec.name() + \"\\\" derives from resource \\\"\" + name\n"
                "                                    + \"\\\", but no resource reader was handed in: use the overload that takes one, \"\n"
                "                                    + \"or the panel would silently be 0\");\n"
                "                }\n"
                "                value = share * resourceValue.applyAsInt(name) + flat;\n"
                "            } else {\n"
                "                value = share * master.getAttribute(attribute).get() + flat;\n"
                "            }\n")
    if factory.count(OLD_LOOP) != 1:
        print("FAIL factory: value anchor matched %d times" % factory.count(OLD_LOOP))
        sys.exit(1)
    factory = factory.replace(OLD_LOOP, NEW_LOOP)

    # the 2-arg entry delegates with `null`, and a 3-arg entry takes the reader
    OLD_SIG = "    public static Summon memosprite(Character master, MemospriteSpec spec) {\n"
    NEW_SIG = ("    /**\n"
               "     * The same, for a panel that derives from a RESOURCE: {@code resourceValue} answers 「【新蕊】现在是多少」.\n"
               "     *\n"
               "     * <p>\u26a0 Handed in rather than reached for, because resources live on the battle and this derivation is\n"
               "     * deliberately battle-free.\n"
               "     */\n"
               "    public static Summon memosprite(Character master, MemospriteSpec spec,\n"
               "                                    java.util.function.ToIntFunction<String> resourceValue) {\n")
    if factory.count(OLD_SIG) != 1:
        print("FAIL factory: memosprite signature matched %d times" % factory.count(OLD_SIG))
        sys.exit(1)
    factory = factory.replace(OLD_SIG, NEW_SIG + "        return memospriteWithoutResource(master, spec, resourceValue);\n    }\n\n"
                              "    public static Summon memosprite(Character master, MemospriteSpec spec) {\n"
                              "        return memospriteWithoutResource(master, spec, null);\n    }\n\n"
                              "    private static Summon memospriteWithoutResource(Character master, MemospriteSpec spec,\n"
                              "                                                    java.util.function.ToIntFunction<String> resourceValue) {\n")
    io.open(FACTORY, "w", encoding="utf-8", newline="").write(factory)
    print("ok   SummonFactory: resource-aware panel + a battle-free 2-arg overload")
else:
    print("skip factory")

# 3) the judge -------------------------------------------------------------------------------------------
JUDGE_TEXT = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A memosprite panel that derives from a RESOURCE (2026-10-02): 「龙的生命 = 【新蕊】上限的 100%」.
 *
 * <p>Every shipped panel is a share of the master's ATTRIBUTE; this one is a share of a battle-level resource, so the
 * value is handed in. The claim: the same panel yields a different health for a different resource value, and an
 * attribute-based panel beside it does NOT move.
 */
public class MemospriteResourcePanelTest {
    private static final int MASTER = 1415;
    private static final int LEVEL = 80;

    private static MemospriteSpec spec(double share, String source) {
        MemospriteSpec.Panel resource = new MemospriteSpec.Panel("HEALTH", share, null, source);
        MemospriteSpec.Panel attribute = new MemospriteSpec.Panel("SPEED", 1.0, null, null);
        return new MemospriteSpec("\u6b7b\u9f99", "test", "test", List.of(resource, attribute), null, null);
    }

    /** ⭐ The panel follows the resource, and an attribute panel beside it does not. */
    @Test
    public void thePanelFollowsTheResource() {
        Character master = CharacterFactory.create(MASTER, LEVEL, false, null, null, 0);
        double small = SummonFactory.memosprite(master, spec(1.0, "resource:\u65b0\u854a"), name -> 1000)
                .getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get();
        double large = SummonFactory.memosprite(master, spec(1.0, "resource:\u65b0\u854a"), name -> 4000)
                .getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get();
        Assertions.assertEquals(1000, small, 1e-6, "100% of a 1000-point resource");
        Assertions.assertEquals(4000, large, 1e-6, "and 100% of a 4000-point one");

        double half = SummonFactory.memosprite(master, spec(0.5, "resource:\u65b0\u854a"), name -> 4000)
                .getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get();
        Assertions.assertEquals(2000, half, 1e-6, "the share is applied to the resource");

        double speed = SummonFactory.memosprite(master, spec(1.0, "resource:\u65b0\u854a"), name -> 4000)
                .getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        Assertions.assertEquals(master.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get() * 1.0,
                speed, 1e-6, "the attribute panel beside it still reads the master");
    }
}
'''
io.open(JUDGE, "w", encoding="utf-8", newline="").write(JUDGE_TEXT)
print("ok   judge written")
