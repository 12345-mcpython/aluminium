"""Roll the debuff-class channel back, and record exactly what was measured (round 9 of the goal).

The probe says the failure is UPSTREAM of the filter: in a hand-built table, `APPLY_CONTROL{冻结, turns: 2, baseChance: 1.0,
target: all_enemies}` on BATTLE_START left the enemy unfrozen, and NEITHER the filtered nor the unfiltered DEBUFF_APPLIED rule fired.
That does not prove an engine bug -- it is my probe's scene that is unverified (a plain BATTLE_START rule in the same scene has not
been shown to work either) -- so the channel does not go into the tree unjudged.
"""
import io
import os
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
TABLE = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/DebuffClassConditionTest.java"
PROBE = "src/test/java/com/laosun/aluminium/test/DebuffClassProbeTest.java"


def drop(path, block, label):
    text = io.open(path, encoding="utf-8").read()
    if text.count(block) != 1:
        sys.stderr.write("REFUSING %s: the block appears %d times\n" % (label, text.count(block)))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(block, "", 1))
    print("ok   removed %s" % label)


drop(BATTLE, '\n\n    /**\n     * ⭐ The CLASS of the debuff that just landed (2026-10-02; reader: 1506\'s 「敌方对我方施加了**控制类**\n     * 负面状态」). Same shape as {@link #lastChangedResource}: a battle-level fact the condition DSL reads, set at the one chokepoint\n     * every landed debuff passes through. ⚠ Deliberately NOT folded into an existing argument slot -- the STATE_ENDED magnitude was once\n     * put in `hitCount` and every reading of it was 0 until that was found.\n     */\n    private com.laosun.aluminium.enums.DebuffClass lastAppliedDebuffClass;', "the battle field")

drop(BATTLE, '    /** Records the class of the debuff that just landed (see {@link #lastAppliedDebuffClass()}). */\n    public void noteAppliedDebuffClass(com.laosun.aluminium.enums.DebuffClass debuffClass) {\n        lastAppliedDebuffClass = debuffClass;\n    }\n\n    /** The class of the debuff that just landed, or {@code null} for a state belonging to neither family. */\n    public com.laosun.aluminium.enums.DebuffClass lastAppliedDebuffClass() {\n        return lastAppliedDebuffClass;\n    }\n\n', "the battle accessors")

drop(BATTLE, '        // ⭐ Tell the tables WHICH FAMILY landed, before the event goes out (2026-10-02): the class is already read one line up for\n        // the resistance roll, so it costs nothing to make the same fact askable.\n        noteAppliedDebuffClass(buff.debuffClass());\n', "the chokepoint call")

text = io.open(TABLE, encoding="utf-8").read()
start = text.index("        // ⭐ 「施加的是控制类")
end = text.index("        java.util.regex.Matcher changed =")
io.open(TABLE, "w", encoding="utf-8", newline="\n").write(text[:start] + text[end:])
print("ok   removed the parse site")

text = io.open(TABLE, encoding="utf-8").read()
start = text.index("    /** ✅ 「施加的是【X】类负面状态」")
end = text.index("    private static final class ResourceChanged implements Condition {")
io.open(TABLE, "w", encoding="utf-8", newline="\n").write(text[:start] + text[end:])
print("ok   removed the condition class")

for path in (JUDGE, PROBE):
    if os.path.exists(path):
        os.remove(path)
        print("ok   removed %s" % os.path.basename(path))
