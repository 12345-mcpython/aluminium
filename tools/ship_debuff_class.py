"""A landed debuff's CLASS becomes askable (round 9 of the goal).

The reader is registered rather than written: 1506's warehouse skill 「若敌方目标对我方施加了**控制类**负面状态，则使我方全体获得【防火墙】」
(EXPRESSION §3). Its three prerequisites were measured last round; this is the third -- the class filter. (The other two, a load point
for an owned-but-undeployed character and a delayed down, are still open.)

Everything it needs already exists: `DebuffClass` names the two families the documents use (control / dot), `ControlBuff.debuffClass()`
answers CONTROL, and the chokepoint every landed debuff passes through (`Battle:1807`) already reads that very method one line
earlier for the resistance roll. The shape is copied from `lastChangedResource` -> `resource_changed:<name>`, which the project
already uses and documents.
"""
import io
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
TABLE = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/DebuffClassConditionTest.java"
MUTATOR = "tools/mut_debuff_class.py"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


# ---- 1. the battle-side channel, beside lastChangedResource
patch(
    BATTLE,
    "    private String lastChangedResource;",
    "    private String lastChangedResource;\n\n"
    "    /**\n"
    "     * \u2b50 The CLASS of the debuff that just landed (2026-10-02; reader: 1506's \u300c\u654c\u65b9\u5bf9\u6211\u65b9\u65bd\u52a0\u4e86**\u63a7\u5236\u7c7b**\n"
    "     * \u8d1f\u9762\u72b6\u6001\u300d). Same shape as {@link #lastChangedResource}: a battle-level fact the condition DSL reads, set at the one chokepoint\n"
    "     * every landed debuff passes through. \u26a0 Deliberately NOT folded into an existing argument slot -- the STATE_ENDED magnitude was once\n"
    "     * put in `hitCount` and every reading of it was 0 until that was found.\n"
    "     */\n"
    "    private com.laosun.aluminium.enums.DebuffClass lastAppliedDebuffClass;",
    "the battle-side field",
)

patch(
    BATTLE,
    "    public void noteChangedResource(String resource) {",
    "    /** Records the class of the debuff that just landed (see {@link #lastAppliedDebuffClass()}). */\n"
    "    public void noteAppliedDebuffClass(com.laosun.aluminium.enums.DebuffClass debuffClass) {\n"
    "        lastAppliedDebuffClass = debuffClass;\n"
    "    }\n\n"
    "    /** The class of the debuff that just landed, or {@code null} for a state belonging to neither family. */\n"
    "    public com.laosun.aluminium.enums.DebuffClass lastAppliedDebuffClass() {\n"
    "        return lastAppliedDebuffClass;\n"
    "    }\n\n"
    "    public void noteChangedResource(String resource) {",
    "the battle-side accessors",
)

patch(
    BATTLE,
    "        fireTriggers(TriggerEvent.DEBUFF_APPLIED, caster, target, 0, 0);",
    "        // \u2b50 Tell the tables WHICH FAMILY landed, before the event goes out (2026-10-02): the class is already read one line up for\n"
    "        // the resistance roll, so it costs nothing to make the same fact askable.\n"
    "        noteAppliedDebuffClass(buff.debuffClass());\n"
    "        fireTriggers(TriggerEvent.DEBUFF_APPLIED, caster, target, 0, 0);",
    "the chokepoint records the class",
)

# ---- 2. the condition term, mirrored from resource_changed
patch(
    TABLE,
    """        java.util.regex.Matcher changed =
                java.util.regex.Pattern.compile("resource_changed:([^\\\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);""",
    """        // \u2b50 \u300c\u65bd\u52a0\u7684\u662f\u63a7\u5236\u7c7b\uff0f\u6301\u7eed\u4f24\u5bb3\u7c7b\u8d1f\u9762\u72b6\u6001\u300d (2026-10-02; reader: 1506's warehouse skill). Read like
        // `resource_changed:` -- a battle-level fact recorded at the chokepoint -- and placed beside it, first in the chain.
        java.util.regex.Matcher debuffClass =
                java.util.regex.Pattern.compile("debuff_class:([^\\\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);
        if (debuffClass.find()) {
            return new AppliedDebuffClass(raw, debuffClass.group(1).trim());
        }

        java.util.regex.Matcher changed =
                java.util.regex.Pattern.compile("resource_changed:([^\\\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);""",
    "the parse site",
)

patch(
    TABLE,
    "    private static final class ResourceChanged implements Condition {",
    "    /** \u2705 \u300c\u65bd\u52a0\u7684\u662f\u3010X\u3011\u7c7b\u8d1f\u9762\u72b6\u6001\u300d: the family of the debuff that just landed (control / dot). */\n"
    "    private static final class AppliedDebuffClass implements Condition {\n"
    "        private final String raw;\n"
    "        private final com.laosun.aluminium.enums.DebuffClass expected;\n\n"
    "        AppliedDebuffClass(String raw, String wanted) {\n"
    "            this.raw = raw;\n"
    "            this.expected = com.laosun.aluminium.enums.DebuffClass.from(wanted);\n"
    "        }\n\n"
    "        @Override\n"
    "        public boolean test(TriggerContext ctx) {\n"
    "            return ctx.battle() != null && expected == ctx.battle().lastAppliedDebuffClass();\n"
    "        }\n\n"
    "        @Override\n"
    "        public String source() {\n"
    "            return raw;\n"
    "        }\n\n"
    "        @Override\n"
    "        public String toString() {\n"
    "            return raw;\n"
    "        }\n"
    "    }\n\n"
    "    private static final class ResourceChanged implements Condition {",
    "the condition class",
)

# ---- 3. the reading
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u654c\u65b9\u5bf9\u6211\u65b9\u65bd\u52a0\u4e86**\u63a7\u5236\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d\u53ef\u4ee5\u88ab\u95ee\u4e86 (2026-10-02).
 *
 * <p>Two-way on purpose: the same rule must fire for a CONTROL debuff and stay silent for a DOT one, which is the only way the
 * reading tells "the filter works" apart from "the event fired at all".
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final String PROBE = "probeClass";

    /** A control debuff fires it; a dot debuff does not. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        Assertions.assertEquals(1, hitsFor("APPLY_CONTROL", "control"),
                "\u300c\u63a7\u5236\u7c7b\u300d-- a landed control fires the rule");
        Assertions.assertEquals(0, hitsFor("APPLY_CONTROL", "dot"),
                "and the same control does NOT fire a rule that asked for \u300c\u6301\u7eed\u4f24\u5bb3\u7c7b\u300d");
        Assertions.assertEquals(1, hitsFor("APPLY_DOT", "dot"),
                "\u300c\u6301\u7eed\u4f24\u5bb3\u7c7b\u300d-- a landed dot fires the dot rule");
        Assertions.assertEquals(0, hitsFor("APPLY_DOT", "control"),
                "and does not fire the control one");
    }

    /** How many times a rule asking for {@code wanted} fires when {@code op} lands a debuff. */
    private static int hitsFor(String op, String wanted) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", op);
        TriggerSpecs.set(land, "buff", "APPLY_CONTROL".equals(op) ? "\\u51bb\\u7ed3" : "probeDot");
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "percent", 0.1);
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "target", "all_enemies");

        EffectSpec watch = new EffectSpec();
        TriggerSpecs.set(watch, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(watch, "resource", PROBE);
        TriggerSpecs.set(watch, "amount", 1.0);
        TriggerSpecs.set(watch, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:" + wanted), watch)),
                List.of(new com.laosun.aluminium.beans.ResourceSpec(PROBE, 99, 0, null, null, "probe", null))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getResources().has(PROBE) ? owner.getResources().value(PROBE) : 0;
    }
}
''')
print("ok   the reading is written")

io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write('''"""Mutant for the debuff-class filter (round 9 of the goal).

The condition ignores the class, so both families fire the same rule and the two "does NOT fire" halves fail.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_debuff_class.py on|off")

LIVE = "            return ctx.battle() != null && expected == ctx.battle().lastAppliedDebuffClass();"
MUTATED = "            return ctx.battle() != null && ctx.battle().lastAppliedDebuffClass() != null;"
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: the live line appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the filter ignores which class landed")
else:
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutated line appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the filter compares the class")
''')
print("ok   the mutator is written")
