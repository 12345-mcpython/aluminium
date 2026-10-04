"""Narrow the Hyacine pin to what it is actually protecting (round 1674).

The pin asserted `SKILL_CAST` count == 0, with the reason "the skill's heal comes from the skill data (measured), so a rule
would double-count it". That reason is about the HEAL. Her document also says 「召唤忆灵 小伊卡」 on the same skill, so exactly
one SKILL_CAST rule now exists and it only summons -- the heal is still nowhere in the file. The pin keeps its intent (no
double-counted heal) and gains the measured new fact (the summon).
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/HyacineTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = """        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the skill's heal comes from the skill data (measured), so a rule would double-count it");"""
NEW = """        // \u26a0 Narrowed 2026-10-02: the reason is about the HEAL, and the same skill also says \u300c\u53ec\u5524\u5fc6\u7075 \u5c0f\u4f0a\u5361\u300d.
        // Exactly one SKILL_CAST rule exists and it only summons -- the heal is still absent from the file, so nothing is
        // double-counted. What the pin protects is unchanged; what it counts is now what the document states.
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the summon rule the document states -- the heal still comes from the skill data, so no rule states it");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the pin is narrowed with its reason recorded")
