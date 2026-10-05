"""A summon's panel can be a share of a RESOURCE (2026-10-02); the duplicated derivation collapses into one helper.

Reader: 1407/1415's dead dragon 「龙的生命 = 【新蕊】上限的 100%」. Panels today are shares of the MASTER's ATTRIBUTE;
resources live on the BATTLE (Battle.partyResourceValue), so the reader is handed in, keeping this derivation battle-free.

Per kind (`memosprite`, `servant`), which previously carried byte-identical derivation blocks:
  1. the existing 2-arg method becomes a delegator to a private `<kind>With(master, spec, resourceValue)`;
  2. a public 3-arg overload is added for callers that have a battle;
  3. the private body's panel line uses the handed-in reader;
  4. both bodies call the ONE `panelOf` helper, where the `resource:` branch lives.
`Panel.source` is nullable: empty keeps today's behaviour exactly, so the five shipped memosprite specs are untouched.
A `resource:` panel with no reader is refused LOUDLY rather than deriving 0.
ASCII only.
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/MemospriteSpec.java"
FACTORY = "src/main/java/com/laosun/aluminium/models/enemy/SummonFactory.java"
READER = "java.util.function.ToIntFunction<String> resourceValue"

# 1) Panel gains `source` --------------------------------------------------------------------------------
spec = io.open(SPEC, encoding="utf-8").read()
OLD_PANEL = ('    public record Panel(@SerializedName("attribute") String attribute,\n'
             '                        @SerializedName("percent") Double percent,\n'
             '                        @SerializedName("flat") Double flat) {')
NEW_PANEL = ('    public record Panel(@SerializedName("attribute") String attribute,\n'
             '                        @SerializedName("percent") Double percent,\n'
             '                        @SerializedName("flat") Double flat,\n'
             '                        @SerializedName("source") String source) {')
if "String source)" in spec.split("record Panel(")[1].split(") {")[0]:
    print("skip spec")
elif spec.count(OLD_PANEL) == 1:
    spec = spec.replace(OLD_PANEL, NEW_PANEL)
    io.open(SPEC, "w", encoding="utf-8", newline="").write(spec)
    print("ok   MemospriteSpec.Panel.source")
else:
    print("FAIL spec: Panel anchor matched %d times" % spec.count(OLD_PANEL))
    sys.exit(1)

# 2) the factory -----------------------------------------------------------------------------------------
factory = io.open(FACTORY, encoding="utf-8").read()
if "panelOf(" in factory:
    print("skip factory: already done")
    raise SystemExit(0)

BLOCK = ("        AttributeBuilder panel = new AttributeBuilder();\n"
         "        for (MemospriteSpec.Panel entry : spec.panel()) {\n"
         "            AttributeType attribute = AttributeType.fromString(entry.attribute());\n"
         "            double share = entry.percent() == null ? 0 : entry.percent();\n"
         "            double flat = entry.flat() == null ? 0 : entry.flat();\n"
         "            double value = share * master.getAttribute(attribute).get() + flat;\n"
         "            // The builder's own convention for the two kinds of attribute (see AttributeBuilder): a base\n"
         "            // attribute is set outright, and a ratio attribute -- whose base is literally 0 -- is given a\n"
         "            // percentage-point modifier, which is exactly `ModifyAttr`'s rule for the same situation.\n"
         "            if (attribute.isPercent) {\n"
         "                panel.addPercentPoint(attribute, value, DoubleValue.Modifier.ModifierSource.BASE);\n"
         "            } else {\n"
         "                panel.setBase(attribute, value);\n"
         "            }\n"
         "        }\n")

HELPER = ("    /**\n"
          "     * The panel every summon shares: each entry is a share of the <b>master's attribute</b> plus a flat term\n"
          "     * -- or, when it names {@code resource:<name>}, a share of a <b>battle-level resource</b> (2026-10-02;\n"
          "     * reader: 1407/1415's dead dragon).\n"
          "     *\n"
          "     * <p>⚠ The resource reader is handed in rather than reached for: resources live on the battle, and this\n"
          "     * derivation was deliberately split out to be exercised on its own. A {@code resource:} panel with no reader\n"
          "     * is refused loudly -- deriving 0 would be a number that looks plausible and is wrong.\n"
          "     */\n"
          "    private static AttributeBuilder panelOf(Character master, MemospriteSpec spec,\n"
          "                                            " + READER + ") {\n"
          "        AttributeBuilder panel = new AttributeBuilder();\n"
          "        for (MemospriteSpec.Panel entry : spec.panel()) {\n"
          "            AttributeType attribute = AttributeType.fromString(entry.attribute());\n"
          "            double share = entry.percent() == null ? 0 : entry.percent();\n"
          "            double flat = entry.flat() == null ? 0 : entry.flat();\n"
          "            double value;\n"
          "            if (entry.source() != null && entry.source().startsWith(\"resource:\")) {\n"
          "                String name = entry.source().substring(\"resource:\".length()).trim();\n"
          "                if (resourceValue == null) {\n"
          "                    throw new IllegalStateException(\n"
          "                            \"the panel of \\\"\" + spec.name() + \"\\\" derives from resource \\\"\" + name\n"
          "                                    + \"\\\", but no resource reader was handed in: use the overload that takes one \"\n"
          "                                    + \"(a missing reader would silently derive 0)\");\n"
          "                }\n"
          "                value = share * resourceValue.applyAsInt(name) + flat;\n"
          "            } else {\n"
          "                value = share * master.getAttribute(attribute).get() + flat;\n"
          "            }\n"
          "            // The builder's own convention for the two kinds of attribute (see AttributeBuilder): a base\n"
          "            // attribute is set outright, and a ratio attribute -- whose base is literally 0 -- is given a\n"
          "            // percentage-point modifier, which is exactly `ModifyAttr`'s rule for the same situation.\n"
          "            if (attribute.isPercent) {\n"
          "                panel.addPercentPoint(attribute, value, DoubleValue.Modifier.ModifierSource.BASE);\n"
          "            } else {\n"
          "                panel.setBase(attribute, value);\n"
          "            }\n"
          "        }\n"
          "        return panel;\n"
          "    }\n"
          "\n")

if factory.count(BLOCK) != 2:
    print("FAIL factory: derivation block matched %d times (expected 2)" % factory.count(BLOCK))
    sys.exit(1)
factory = factory.replace(BLOCK, "        AttributeBuilder panel = panelOf(master, spec, resourceValue);\n")

for kind in ("memosprite", "servant"):
    OLD_SIG = "    public static Summon %s(Character master, MemospriteSpec spec) {\n" % kind
    NEW_SIG = ("    /** The same, for a panel that derives from a battle-level RESOURCE (see {@link #panelOf}). */\n"
               "    public static Summon %s(Character master, MemospriteSpec spec, %s) {\n"
               "        return %sWith(master, spec, resourceValue);\n"
               "    }\n"
               "\n"
               "    public static Summon %s(Character master, MemospriteSpec spec) {\n"
               "        return %sWith(master, spec, null);\n"
               "    }\n"
               "\n"
               "    private static Summon %sWith(Character master, MemospriteSpec spec, %s) {\n"
               % (kind, READER, kind, kind, kind, kind, READER))
    if factory.count(OLD_SIG) != 1:
        print("FAIL factory: %s signature matched %d times" % (kind, factory.count(OLD_SIG)))
        sys.exit(1)
    factory = factory.replace(OLD_SIG, NEW_SIG)

ANCHOR = ("    /**\n"
          "     * Builds a memosprite from an already-loaded spec.\n")
if factory.count(ANCHOR) != 1:
    print("FAIL factory: javadoc anchor matched %d times" % factory.count(ANCHOR))
    sys.exit(1)
factory = factory.replace(ANCHOR, HELPER + ANCHOR)

io.open(FACTORY, "w", encoding="utf-8", newline="").write(factory)
print("ok   SummonFactory: one panelOf + per-kind resource-aware overloads")
